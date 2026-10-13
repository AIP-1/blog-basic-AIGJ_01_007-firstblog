import { useEffect, useState } from 'react'
import { getOAuthProviders, oauthRedirect } from '../api/oauth'

const STYLE = {
  kakao: { label: '카카오로 계속하기', className: 'bg-[#FEE500] text-[#191919] hover:brightness-95', mark: '💬' },
  naver: { label: '네이버로 계속하기', className: 'bg-[#03C75A] text-white hover:brightness-95', mark: 'N' },
  google: { label: '구글로 계속하기', className: 'border border-gray-300 bg-white text-gray-800 hover:bg-gray-50', mark: 'G' },
  github: { label: '깃허브로 계속하기', className: 'bg-[#24292f] text-white hover:bg-black', mark: '' },
}

/**
 * 소셜 로그인 버튼 (U-09). 서버에 키가 등록된 서비스만 보여요. 하나도 없으면 아무것도 그리지 않아요.
 * 버튼은 링크예요: 서버(/oauth2/authorization/kakao)가 그 서비스의 동의 화면으로 보내고, 끝나면 /oauth/callback으로 돌아와요.
 */
export default function SocialLoginButtons({ redirect = '/' }) {
  const [providers, setProviders] = useState([])

  useEffect(() => {
    getOAuthProviders().then(setProviders).catch(() => setProviders([]))
  }, [])

  if (providers.length === 0) return null
  return (
    <div className="mt-6" data-social-login>
      <div className="mb-4 flex items-center gap-3 text-xs text-gray-400">
        <span className="h-px flex-1 bg-gray-200" />
        또는 간편하게
        <span className="h-px flex-1 bg-gray-200" />
      </div>
      <div className="space-y-2">
        {providers.filter((p) => STYLE[p]).map((p) => (
          <a
            key={p}
            href={`/oauth2/authorization/${p}`}
            onClick={() => oauthRedirect.save(redirect)}
            className={`flex w-full items-center justify-center gap-2 rounded-md py-2 text-sm font-medium ${STYLE[p].className}`}
          >
            <span aria-hidden="true" className="w-4 text-center font-bold">{STYLE[p].mark}</span>
            {STYLE[p].label}
          </a>
        ))}
      </div>
    </div>
  )
}
