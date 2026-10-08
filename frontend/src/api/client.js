import axios from 'axios'

const TOKEN_KEY = 'ownlog.accessToken'

/** 토큰은 localStorage에 저장해요 (요구사항 8장 결정). XSS 방지(rehype-sanitize)가 전제예요 */
export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export const api = axios.create({ baseURL: '/api' })

// 요청마다 토큰을 자동으로 붙여요 (API 명세서 1.2)
api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 토큰이 없거나 만료되면(401 UNAUTHORIZED) 로그아웃하고 로그인 화면으로 보내요 (U-08)
let handleUnauthorized = () => {}
export function setUnauthorizedHandler(handler) {
  handleUnauthorized = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 로그인 실패(INVALID_CREDENTIALS)도 401이지만, 그건 로그인 화면이 직접 보여줘야 해서 제외해요
    if (error.response?.status === 401 && error.response.data?.code === 'UNAUTHORIZED') {
      tokenStorage.clear()
      handleUnauthorized()
    }
    return Promise.reject(error)
  },
)

/** 사용자에게 보여줄 에러 문장 (서버가 보낸 message, 서버가 응답하지 않으면 기본 문장) */
export function errorMessage(error) {
  return error.response?.data?.message ?? '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'
}

/** 입력칸별 에러 { email: '이메일 형식이 아니에요.', ... } (API 명세서 1.4 fieldErrors) */
export function fieldErrorsOf(error) {
  return Object.fromEntries((error.response?.data?.fieldErrors ?? []).map((e) => [e.field, e.message]))
}
