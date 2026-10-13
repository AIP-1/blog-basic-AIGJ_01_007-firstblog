import { api } from './client'

/**
 * API 4.9 켜져 있는 소셜 로그인 (키를 등록한 서비스만).
 * 버튼을 그릴지 정하는 보조 요청이라, 실패해도(만료 토큰의 401 등) 로그인 화면으로 보내지 않고 버튼만 숨겨요
 */
export const getOAuthProviders = () =>
  api.get('/auth/oauth/providers', { skipAuthRedirect: true }).then((r) => r.data.providers)

/** API 4.10 소셜 로그인이 끝나고 받은 1회용 코드 → 로그인 응답(토큰) */
export const exchangeOAuthCode = (code) => api.post('/auth/oauth/exchange', { code }).then((r) => r.data)

/** 소셜 로그인 뒤 돌아갈 주소. 서비스 화면을 다녀오는 동안 React 상태가 사라져서 sessionStorage에 잠깐 둬요 */
export const oauthRedirect = {
  save: (path) => {
    try { sessionStorage.setItem('ownlog.oauthRedirect', path) } catch { /* 저장이 막혀 있으면 메인으로 가요 */ }
  },
  take: () => {
    try {
      const path = sessionStorage.getItem('ownlog.oauthRedirect')
      sessionStorage.removeItem('ownlog.oauthRedirect')
      return path
    } catch {
      return null
    }
  },
}
