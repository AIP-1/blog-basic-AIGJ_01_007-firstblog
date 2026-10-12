import { useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import ErrorBoundary from './ErrorBoundary'

/**
 * 모든 화면의 공통 틀: 상단 헤더 + 본문 (반응형).
 * 좁은 화면에서는 검색창이 아래 줄로 내려가고, 북마크·임시저장·설정·로그아웃은 "메뉴" 안으로 들어가요.
 */
export default function Layout() {
  const { user, loading, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [keyword, setKeyword] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)

  const search = (e) => {
    e.preventDefault()
    const q = keyword.trim()
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`)
  }

  const handleLogout = () => {
    setMenuOpen(false)
    logout()
    navigate('/')
  }

  const closeMenu = () => setMenuOpen(false)
  const userLinks = [
    { to: '/capsules', label: '💌 받은 캡슐' },
    { to: '/bookmarks', label: '북마크' },
    { to: '/drafts', label: '임시저장' },
    { to: '/settings', label: '설정' },
  ]

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link to="/" className="text-xl font-bold text-indigo-600">
            OwnLog
          </Link>
          <Link to="/universe" className="rounded-md px-2 py-1 text-sm text-gray-700 hover:bg-indigo-50 hover:text-indigo-700" title="OwnLog 우주">
            🌌<span className="ml-1 hidden sm:inline">우주</span>
          </Link>

          <form onSubmit={search} className="order-last w-full sm:order-none sm:w-auto sm:flex-1">
            <input
              type="search"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="글 검색"
              maxLength={50}
              className="w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none"
            />
          </form>

          <nav className="relative ml-auto flex items-center gap-3 text-sm">
            {loading ? null : user ? (
              <>
                <Link to="/write" className="rounded-md bg-indigo-600 px-3 py-1.5 font-medium text-white hover:bg-indigo-700">
                  글쓰기
                </Link>
                <Link to={`/@${user.username}`} className="hover:text-indigo-600">
                  내 블로그
                </Link>
                {userLinks.map(({ to, label }) => (
                  <Link key={to} to={to} className="hidden hover:text-indigo-600 sm:inline">
                    {label}
                  </Link>
                ))}
                <button type="button" onClick={handleLogout} className="hidden text-gray-500 hover:text-gray-900 sm:inline">
                  로그아웃
                </button>

                {/* 좁은 화면 전용 메뉴 */}
                <button
                  type="button"
                  onClick={() => setMenuOpen(!menuOpen)}
                  aria-expanded={menuOpen}
                  className="rounded-md border border-gray-300 px-2 py-1 sm:hidden"
                >
                  메뉴
                </button>
                {menuOpen && (
                  <div className="absolute right-0 top-full mt-2 w-36 rounded-md border border-gray-200 bg-white py-1 shadow-lg sm:hidden">
                    {userLinks.map(({ to, label }) => (
                      <Link key={to} to={to} onClick={closeMenu} className="block px-4 py-2 hover:bg-gray-50">
                        {label}
                      </Link>
                    ))}
                    <button type="button" onClick={handleLogout} className="block w-full px-4 py-2 text-left text-gray-500 hover:bg-gray-50">
                      로그아웃
                    </button>
                  </div>
                )}
              </>
            ) : (
              <>
                <Link to="/login" className="hover:text-indigo-600">
                  로그인
                </Link>
                <Link to="/signup" className="rounded-md border border-indigo-600 px-3 py-1.5 text-indigo-600 hover:bg-indigo-50">
                  회원가입
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6">
        {/* 주소가 바뀌면 오류 화면도 초기화돼요 */}
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}
