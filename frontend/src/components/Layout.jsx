import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'

/** 모든 화면의 공통 틀: 상단 헤더 + 본문. 좁은 화면에서는 검색창이 아래 줄로 내려가요 (반응형) */
export default function Layout() {
  const { user, loading, logout } = useAuth()
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')

  const search = (e) => {
    e.preventDefault()
    const q = keyword.trim()
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`)
  }

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link to="/" className="text-xl font-bold text-indigo-600">
            OwnLog
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

          <nav className="ml-auto flex items-center gap-3 text-sm">
            {loading ? null : user ? (
              <>
                <Link to="/write" className="rounded-md bg-indigo-600 px-3 py-1.5 font-medium text-white hover:bg-indigo-700">
                  글쓰기
                </Link>
                <Link to={`/@${user.username}`} className="hover:text-indigo-600">
                  내 블로그
                </Link>
                <Link to="/bookmarks" className="hidden hover:text-indigo-600 sm:inline">
                  북마크
                </Link>
                <Link to="/settings" className="hidden hover:text-indigo-600 sm:inline">
                  설정
                </Link>
                <button type="button" onClick={handleLogout} className="text-gray-500 hover:text-gray-900">
                  로그아웃
                </button>
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
        <Outlet />
      </main>
    </div>
  )
}
