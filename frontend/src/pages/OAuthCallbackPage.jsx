import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { errorMessage } from '../api/client'
import { exchangeOAuthCode, oauthRedirect } from '../api/oauth'
import { useAuth } from '../auth/useAuth'
import { safeRedirect } from '../utils/safeRedirect'

/**
 * 소셜 로그인의 마지막 화면 (/oauth/callback#code=…).
 * 서버가 주소 # 뒤에 1회용 코드를 실어 보내면, 코드를 토큰으로 바꾸고 원래 보던 화면으로 돌아가요.
 */
export default function OAuthCallbackPage() {
  const { loginWithToken } = useAuth()
  const navigate = useNavigate()
  // 서버가 주소 # 뒤에 실어 보낸 1회용 코드 (처음 그릴 때 한 번만 읽어요)
  const [code] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('code'))
  const [error, setError] = useState(code ? '' : '로그인 정보가 없어요. 다시 시도해 주세요.')
  const started = useRef(false)   // 개발 모드(StrictMode)에서 두 번 실행돼도 코드는 한 번만 써요

  useEffect(() => {
    // 코드가 주소창·방문 기록에 남지 않게 바로 지워요
    window.history.replaceState(null, '', window.location.pathname)
    if (!code || started.current) return
    started.current = true
    exchangeOAuthCode(code)
      .then((data) => loginWithToken(data.accessToken))
      .then(() => navigate(safeRedirect(oauthRedirect.take()), { replace: true }))
      .catch((err) => setError(errorMessage(err)))
  }, [code, loginWithToken, navigate])

  return (
    <div className="mx-auto max-w-sm rounded-2xl border border-gray-200 bg-white p-6 text-center shadow-sm">
      {error ? (
        <>
          <p className="text-sm text-red-600">{error}</p>
          <Link to="/login" className="mt-4 inline-block text-sm text-indigo-600 hover:underline">로그인 화면으로</Link>
        </>
      ) : (
        <p className="text-sm text-gray-600">✦ 로그인하는 중이에요…</p>
      )}
    </div>
  )
}
