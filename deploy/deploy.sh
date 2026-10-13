#!/usr/bin/env bash
# 서버에서 실행돼요 (GitHub Actions가 SSH로 접속해서 실행).
# 필요한 환경변수: IMAGE_TAG, APP_PORT, DB_ADDRESS, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, DB_SCHEMA, JWT_SECRET
# 있으면 쓰는 값: OAUTH_{GITHUB,GOOGLE,KAKAO,NAVER}_CLIENT_{ID,SECRET} (소셜 로그인, 없으면 그 버튼만 숨겨요)
#
# 무중단 배포 (블루-그린) — 자세한 설명: docs/guide/배포와-CI-CD.md
#
#   공용 Nginx ──▶ :APP_PORT  [ownlog-proxy: 우리 전용 Nginx 컨테이너]
#                                 │ 지금 활성인 색으로만 보내요
#                     ┌───────────┴───────────┐
#           :APP_PORT+1 ownlog-blue     :APP_PORT+2 ownlog-green
#
#   1) 쉬고 있는 색(예: green)에 새 버전을 띄우고
#   2) 그 포트로 직접 응답을 확인한 뒤(헬스 체크)
#   3) 프록시 설정만 green으로 바꿔 nginx -s reload (진행 중인 요청은 끊지 않고, 새 요청부터 green으로)
#   4) 남은 요청이 끝날 시간을 준 뒤 예전 색(blue)을 정상 종료해요 (Spring graceful shutdown)
#   새 버전이 헬스 체크를 통과하지 못하면 3)으로 가지 않아서, 예전 버전이 그대로 서비스를 계속해요.
#
# 로컬에서 시험할 때만 쓰는 값: NETWORK=bridge(기본 host), UPSTREAM_HOST(기본 127.0.0.1), SKIP_LOAD=1, KEEP_IMAGES=1, DRAIN_SECONDS
set -euo pipefail

APP_NAME=ownlog
DEPLOY_DIR="$HOME/ownlog-deploy"
UPLOAD_DIR="$HOME/ownlog-uploads"   # 업로드한 이미지는 컨테이너 밖에 둬서 새로 배포해도 남아요
PROXY_NAME="${APP_NAME}-proxy"
PROXY_DIR="$DEPLOY_DIR/proxy"
PROXY_IMAGE="${PROXY_IMAGE:-nginx:1.27-alpine}"
NETWORK="${NETWORK:-host}"
UPSTREAM_HOST="${UPSTREAM_HOST:-127.0.0.1}"
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"   # 프록시를 바꾼 뒤, 예전 버전에 남은 요청이 끝나길 기다리는 시간

# 비밀값이 빠졌으면 바로 멈춰요 (어떤 Secret이 비었는지 이름만 알려 줘요)
for name in IMAGE_TAG APP_PORT DB_ADDRESS DB_PORT DB_NAME DB_USERNAME DB_PASSWORD DB_SCHEMA; do
  if [ -z "${!name:-}" ]; then
    echo "❌ $name 값이 비어 있어요. GitHub Repository secrets를 확인해 주세요."
    exit 1
  fi
done

# JWT_SECRET Secret이 없으면 서버에 한 번 만들어 두고 계속 같은 값을 써요 (바뀌면 모두 로그아웃돼요)
if [ -z "${JWT_SECRET:-}" ]; then
  mkdir -p "$DEPLOY_DIR"
  if [ ! -s "$DEPLOY_DIR/jwt.secret" ]; then
    (umask 077 && openssl rand -base64 48 | tr -d '\n' > "$DEPLOY_DIR/jwt.secret")
  fi
  JWT_SECRET="$(cat "$DEPLOY_DIR/jwt.secret")"
fi
export JWT_SECRET

if [ -z "${SKIP_LOAD:-}" ]; then
  echo "▶ 이미지 불러오기: ${APP_NAME}:${IMAGE_TAG}"
  gunzip -c "$DEPLOY_DIR/ownlog.tar.gz" | docker load
fi

echo "▶ DB 연결 확인 (값은 GitHub이 ***로 가려요)"
if ! [[ "$DB_PORT" =~ ^[0-9]+$ ]]; then
  echo "❌ DB_PORT가 숫자만으로 되어 있지 않아요. 공백·줄바꿈이 들어갔는지 확인해 주세요."
  exit 1
fi
if [[ "$DB_ADDRESS" =~ [[:space:]/:] ]]; then
  echo "❌ DB_ADDRESS에 공백, http://, :포트 같은 게 섞여 있어요. 주소만 넣어 주세요."
  exit 1
fi
echo "  DB 주소의 IP: $(getent hosts "$DB_ADDRESS" | awk '{print $1}' | head -1)"
tcp_check='timeout 5 bash -c "</dev/tcp/$DB_ADDRESS/$DB_PORT" 2>/dev/null && echo OK || echo FAIL'
host_result=$(bash -c "$tcp_check")
container_result=$(docker run --rm --network "$NETWORK" -e DB_ADDRESS -e DB_PORT --entrypoint bash "${APP_NAME}:${IMAGE_TAG}" -c "$tcp_check")
echo "  서버 → DB: $host_result / 컨테이너 → DB: $container_result"
if [ "$container_result" != OK ]; then
  if [ "$host_result" = OK ]; then
    echo "❌ 서버에서는 DB에 닿는데 Docker 컨테이너 안에서는 안 닿아요 (Docker 네트워크 문제)."
  else
    echo "❌ 이 서버에서 DB_ADDRESS:DB_PORT로 연결이 안 돼요. 서버(학교 안)에서 쓰는 DB 주소·포트인지 확인해 주세요."
  fi
  exit 1
fi


# ── 어느 색에 띄울지 정하기 ─────────────────────────────────────────────
port_of() { if [ "$1" = blue ]; then echo $((APP_PORT + 1)); else echo $((APP_PORT + 2)); fi; }
ACTIVE="$(cat "$DEPLOY_DIR/active-color" 2>/dev/null || true)"
if [ "$ACTIVE" = blue ]; then NEXT=green; else NEXT=blue; fi
NEXT_PORT=$(port_of "$NEXT")
NEXT_NAME="${APP_NAME}-${NEXT}"
echo "▶ 지금 활성: ${ACTIVE:-없음} → 새 버전은 ${NEXT} (포트 ${NEXT_PORT})"

echo "▶ 새 버전 실행: ${NEXT_NAME}"
mkdir -p "$UPLOAD_DIR" "$PROXY_DIR"
docker rm -f "$NEXT_NAME" > /dev/null 2>&1 || true   # 지난번 실패로 남은 컨테이너가 있으면 치워요
if [ "$NETWORK" = host ]; then
  # --network host: 이 서버는 Docker 기본 네트워크에서 내부망 DB에 닿지 않아서 서버 네트워크를 그대로 써요 (트러블슈팅 1번)
  #                 그래서 -p 대신 SERVER_PORT로 앱이 그 포트에서 직접 열려요
  net_args=(--network host)
else
  net_args=(-p "${NEXT_PORT}:${NEXT_PORT}")
fi
# --user: 서버 계정 권한으로 실행해서 업로드 폴더에 쓸 수 있게 해요
# --stop-timeout: docker stop 때 Spring이 처리 중인 요청을 끝내고 꺼질 시간을 줘요
# -e 이름만: 값은 이 스크립트의 환경변수에서 가져와요 (명령어 기록에 비밀값이 남지 않아요. 값이 없으면 넘기지 않아요)
docker run -d --name "$NEXT_NAME" --restart unless-stopped \
  --user "$(id -u):$(id -g)" \
  "${net_args[@]}" -e SERVER_PORT="$NEXT_PORT" \
  --memory 768m -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
  --stop-timeout 30 \
  -e DB_ADDRESS -e DB_PORT -e DB_NAME -e DB_USERNAME -e DB_PASSWORD -e DB_SCHEMA -e JWT_SECRET \
  -e OAUTH_GITHUB_CLIENT_ID -e OAUTH_GITHUB_CLIENT_SECRET -e OAUTH_GOOGLE_CLIENT_ID -e OAUTH_GOOGLE_CLIENT_SECRET \
  -e OAUTH_KAKAO_CLIENT_ID -e OAUTH_KAKAO_CLIENT_SECRET -e OAUTH_NAVER_CLIENT_ID -e OAUTH_NAVER_CLIENT_SECRET \
  -v "$UPLOAD_DIR:/app/uploads" \
  "${APP_NAME}:${IMAGE_TAG}" > /dev/null

echo "▶ 새 버전 헬스 체크 (최대 90초, 그동안 예전 버전이 계속 서비스해요)"
healthy=no
for i in $(seq 1 45); do
  if curl -fs "http://localhost:${NEXT_PORT}/api/topics" > /dev/null; then healthy=yes; break; fi
  sleep 2
done
if [ "$healthy" != yes ]; then
  echo "❌ 새 버전이 90초 안에 응답하지 않아서 전환하지 않았어요. 예전 버전(${ACTIVE:-없음})이 그대로 서비스해요. 원인(Caused by):"
  docker logs "$NEXT_NAME" 2>&1 | grep -E "Caused by|ERROR|APPLICATION FAILED" | head -20 || true
  echo "--- 마지막 로그 ---"
  docker logs --tail 30 "$NEXT_NAME" 2>&1
  docker rm -f "$NEXT_NAME" > /dev/null 2>&1 || true
  exit 1
fi
echo "  ✅ ${NEXT} 응답 확인"

# ── 프록시를 새 색으로 바꾸기 ───────────────────────────────────────────
# conf.d 폴더째 연결해서 파일을 다시 쓰면 컨테이너도 바로 새 내용을 봐요 (이미지 기본 default.conf의 80번 포트도 덮여요)
cat > "$PROXY_DIR/ownlog.conf" <<CONF
# deploy.sh가 배포할 때마다 다시 써요. 지금 활성: ${NEXT} (${NEXT_PORT})
map \$http_x_forwarded_proto \$forwarded_proto {
    default \$http_x_forwarded_proto;
    ''      \$scheme;
}

server {
    listen ${APP_PORT};
    client_max_body_size 20m;

    location / {
        proxy_pass http://${UPSTREAM_HOST}:${NEXT_PORT};
        proxy_http_version 1.1;
        proxy_set_header Connection        "";
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        # 앞단(공용 Nginx)이 붙인 https 정보를 그대로 넘겨요. 없으면(로컬) 이 프록시가 받은 방식을 써요
        proxy_set_header X-Forwarded-Proto \$forwarded_proto;
        proxy_read_timeout 60s;
    }
}
CONF

if docker ps --format '{{.Names}}' | grep -qx "$PROXY_NAME"; then
  echo "▶ 프록시 전환: ${ACTIVE:-없음} → ${NEXT} (nginx -s reload, 연결을 끊지 않아요)"
  docker exec "$PROXY_NAME" nginx -t
  docker exec "$PROXY_NAME" nginx -s reload
else
  # 처음 한 번만: 프록시가 없던 예전 방식(ownlog 컨테이너가 APP_PORT를 직접 씀)에서 넘어와요.
  # 예전 컨테이너를 지우고 같은 포트에 프록시를 띄우는 1~2초만 끊겨요. 다음 배포부터는 끊기지 않아요
  echo "▶ 프록시 처음 설치 (예전 방식 → 블루-그린, 이번 한 번만 1~2초 끊길 수 있어요)"
  docker rm -f "$PROXY_NAME" > /dev/null 2>&1 || true
  docker rm -f "$APP_NAME" > /dev/null 2>&1 || true
  if [ "$NETWORK" = host ]; then proxy_net=(--network host); else proxy_net=(-p "${APP_PORT}:${APP_PORT}"); fi
  docker run -d --name "$PROXY_NAME" --restart unless-stopped "${proxy_net[@]}" \
    -v "$PROXY_DIR:/etc/nginx/conf.d:ro" "$PROXY_IMAGE" > /dev/null
fi

echo "▶ 프록시를 거쳐서 확인"
for i in $(seq 1 15); do
  if curl -fs "http://localhost:${APP_PORT}/api/topics" > /dev/null; then break; fi
  if [ "$i" = 15 ]; then echo "❌ 프록시(${APP_PORT})로 응답이 없어요"; docker logs --tail 20 "$PROXY_NAME" 2>&1; exit 1; fi
  sleep 1
done
echo "$NEXT" > "$DEPLOY_DIR/active-color"
echo "  ✅ 이제 ${NEXT}가 서비스해요"

# ── 예전 버전 정리 ─────────────────────────────────────────────────────
if [ -n "$ACTIVE" ] && [ "$ACTIVE" != "$NEXT" ]; then
  echo "▶ 예전 버전(${ACTIVE}) 정리: ${DRAIN_SECONDS}초 기다린 뒤 정상 종료 (처리 중인 요청은 끝내고 꺼져요)"
  sleep "$DRAIN_SECONDS"
  docker stop -t 30 "${APP_NAME}-${ACTIVE}" > /dev/null 2>&1 || true
  docker rm "${APP_NAME}-${ACTIVE}" > /dev/null 2>&1 || true
fi

# 지금 쓰는 이미지만 남기고 예전 ownlog 이미지는 지워서 디스크를 아껴요 (KEEP_IMAGES=1이면 건너뛰어요: 내 컴퓨터에서 시험할 때)
[ -n "${KEEP_IMAGES:-}" ] || docker images "$APP_NAME" --format '{{.Tag}}' | grep -vx "$IMAGE_TAG" | xargs -r -I{} docker rmi "${APP_NAME}:{}" > /dev/null 2>&1 || true
rm -f "$DEPLOY_DIR/ownlog.tar.gz"
echo "✅ 배포 완료: ${IMAGE_TAG} (${NEXT}, 포트 ${NEXT_PORT} ← 프록시 ${APP_PORT})"
