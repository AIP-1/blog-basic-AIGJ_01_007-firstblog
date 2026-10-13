/**
 * 우리 사이트 안의 주소로만 돌아가요 (오픈 리다이렉트 방지).
 * "//evil.com"뿐 아니라 "/\t/evil.com"처럼 제어 문자·역슬래시를 섞어 브라우저가 외부 주소로 해석하게 만드는 경우도 막으려고,
 * 실제 URL로 해석해서 우리 출처(origin)인지 확인하고 경로 부분만 써요.
 */
export function safeRedirect(redirect) {
  // 제어 문자(탭·줄바꿈 등)를 일부러 찾는 정규식이에요
  // eslint-disable-next-line no-control-regex
  if (!redirect || !redirect.startsWith('/') || /[\u0000-\u001f\\]/.test(redirect)) return '/'
  try {
    const url = new URL(redirect, window.location.origin)
    return url.origin === window.location.origin ? url.pathname + url.search + url.hash : '/'
  } catch {
    return '/'
  }
}
