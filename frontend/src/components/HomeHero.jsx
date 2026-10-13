import { Link } from 'react-router'
import { getUniverse } from '../api/blogs'
import { useAuth } from '../auth/useAuth'
import { useAsync } from '../hooks/useAsync'

/**
 * 메인 첫 화면: 서비스 컨셉 "기록이 시간과 연결로 자라는 블로그"를 한눈에 보여줘요.
 * 숫자는 서비스 전체 우주(API 14.3, 공개 글만)에서 세요. 불러오지 못하면 숫자 줄만 빠져요.
 */
export default function HomeHero() {
  const { user } = useAuth()
  const { data } = useAsync(() => getUniverse(), 'hero-universe')
  const stats = data && {
    posts: data.nodes.length,
    links: data.edges.filter((e) => e.type === 'LINK').length,
    blogs: new Set(data.nodes.map((n) => n.username)).size,
  }

  return (
    <section aria-label="OwnLog 소개" className="bg-cosmos relative overflow-hidden rounded-2xl px-6 py-10 text-white shadow-lg sm:px-10 sm:py-14">
      {/* 오른쪽 위의 작은 별자리 장식 */}
      <svg viewBox="0 0 200 120" className="pointer-events-none absolute -right-6 top-4 hidden h-40 w-64 opacity-70 sm:block" aria-hidden="true">
        <g stroke="#a5b4fc" strokeOpacity="0.5" strokeWidth="1">
          <line x1="20" y1="90" x2="70" y2="40" /><line x1="70" y1="40" x2="120" y2="60" />
          <line x1="120" y1="60" x2="170" y2="20" /><line x1="120" y1="60" x2="150" y2="100" />
        </g>
        <g fill="#e0e7ff">
          <circle cx="20" cy="90" r="3" /><circle cx="70" cy="40" r="4.5" fill="#c4b5fd" />
          <circle cx="120" cy="60" r="6" fill="#a78bfa" /><circle cx="170" cy="20" r="3" /><circle cx="150" cy="100" r="3.5" />
        </g>
      </svg>

      <p className="text-sm font-medium tracking-widest text-indigo-200">OWNLOG · 기록 유니버스</p>
      <h1 className="mt-3 text-3xl leading-tight font-extrabold sm:text-4xl">
        기록이 <span className="bg-gradient-to-r from-indigo-300 to-sky-300 bg-clip-text text-transparent">시간</span>과{' '}
        <span className="bg-gradient-to-r from-violet-300 to-pink-300 bg-clip-text text-transparent">연결</span>로
        <br className="hidden sm:block" /> 자라는 블로그
      </h1>
      <p className="mt-4 max-w-xl text-sm leading-6 text-indigo-100/90 sm:text-base">
        글은 별이 되고, <code className="rounded bg-white/10 px-1">[[연결]]</code>은 별자리가 돼요.
        미래의 나에게 타임캡슐을 보내고, 매일의 기록을 잔디로 쌓아 보세요.
      </p>

      <div className="mt-6 flex flex-wrap gap-3">
        <Link to="/universe" className="rounded-full bg-white px-5 py-2 text-sm font-semibold text-space-950 shadow hover:bg-indigo-50">
          🌌 우주 둘러보기
        </Link>
        <Link to={user ? '/write' : '/signup'}
          className="rounded-full px-5 py-2 text-sm font-semibold text-white ring-1 ring-white/40 hover:bg-white/10">
          ✍️ {user ? '오늘의 기록 남기기' : '나만의 블로그 시작하기'}
        </Link>
        <Link to="/guide" className="rounded-full px-5 py-2 text-sm font-semibold text-indigo-100 hover:bg-white/10 hover:text-white">
          📖 기능 가이드
        </Link>
      </div>

      {stats && (
        <dl className="mt-8 flex flex-wrap gap-x-8 gap-y-2 text-sm text-indigo-100/80">
          <div><dt className="inline">기록 </dt><dd className="inline font-bold text-white">{stats.posts}</dd></div>
          <div><dt className="inline">연결 </dt><dd className="inline font-bold text-white">{stats.links}</dd></div>
          <div><dt className="inline">블로그 </dt><dd className="inline font-bold text-white">{stats.blogs}</dd></div>
        </dl>
      )}
    </section>
  )
}
