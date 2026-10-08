#!/usr/bin/env bash
# 서버에서 실행돼요 (GitHub Actions가 SSH로 접속해서 실행).
# 필요한 환경변수: IMAGE_TAG, APP_PORT, DB_ADDRESS, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, DB_SCHEMA, JWT_SECRET
set -euo pipefail

APP_NAME=ownlog
DEPLOY_DIR="$HOME/ownlog-deploy"
UPLOAD_DIR="$HOME/ownlog-uploads"   # 업로드한 이미지는 컨테이너 밖에 둬서 새로 배포해도 남아요

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

echo "▶ 이미지 불러오기: ${APP_NAME}:${IMAGE_TAG}"
gunzip -c "$DEPLOY_DIR/ownlog.tar.gz" | docker load

echo "▶ 기존 컨테이너 정리"
docker rm -f "$APP_NAME" 2>/dev/null || true

echo "▶ 새 컨테이너 실행 (포트 ${APP_PORT})"
mkdir -p "$UPLOAD_DIR"
# --user: 서버 계정 권한으로 실행해서 업로드 폴더에 쓸 수 있게 해요
# -e 이름만: 값은 이 스크립트의 환경변수에서 가져와요 (명령어 기록에 비밀값이 남지 않아요)
docker run -d --name "$APP_NAME" --restart unless-stopped \
  --user "$(id -u):$(id -g)" \
  -p "${APP_PORT}:8080" \
  --memory 768m -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
  -e DB_ADDRESS -e DB_PORT -e DB_NAME -e DB_USERNAME -e DB_PASSWORD -e DB_SCHEMA -e JWT_SECRET \
  -v "$UPLOAD_DIR:/app/uploads" \
  "${APP_NAME}:${IMAGE_TAG}"

echo "▶ 서버가 뜰 때까지 기다리기 (최대 90초)"
for i in $(seq 1 45); do
  if curl -fs "http://localhost:${APP_PORT}/api/topics" > /dev/null; then
    echo "✅ 배포 성공: http://localhost:${APP_PORT}"
    # 지금 쓰는 이미지만 남기고 예전 ownlog 이미지는 지워서 디스크를 아껴요
    docker images "$APP_NAME" --format '{{.Tag}}' | grep -vx "$IMAGE_TAG" | xargs -r -I{} docker rmi "${APP_NAME}:{}" > /dev/null || true
    rm -f "$DEPLOY_DIR/ownlog.tar.gz"
    exit 0
  fi
  sleep 2
done

echo "❌ 90초 안에 서버가 뜨지 않았어요. 최근 로그:"
docker logs --tail 80 "$APP_NAME"
exit 1
