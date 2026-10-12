import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import BlogPage from './pages/BlogPage'
import BookmarksPage from './pages/BookmarksPage'
import CategorySettingsPage from './pages/CategorySettingsPage'
import DraftsPage from './pages/DraftsPage'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import NotFoundPage from './pages/NotFoundPage'
import PostDetailPage from './pages/PostDetailPage'
import SearchPage from './pages/SearchPage'
import SettingsPage from './pages/SettingsPage'
import SignupPage from './pages/SignupPage'
import TagPage from './pages/TagPage'

// 마크다운 편집기가 커서 글쓰기 화면은 들어갈 때만 불러와요 (첫 화면 로딩을 가볍게)
const WritePage = lazy(() => import('./pages/WritePage'))
// 기록 우주는 그래프 라이브러리(d3-force)를 써서 이 화면에서만 불러와요
const UniversePage = lazy(() => import('./pages/UniversePage'))
const loadingFallback = <p className="py-16 text-center text-gray-500">불러오는 중…</p>

/** 화면 주소 (요구사항 4장). 화면을 만들 때마다 여기에 추가해요 */
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<MainPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/tags/:tagName" element={<TagPage />} />
        {/* /@아이디: React Router는 "/@:username"처럼 글자와 변수를 섞을 수 없어서 /:handle로 받고 화면에서 @를 확인해요 */}
        <Route path="/:handle" element={<BlogPage />} />
        <Route path="/:handle/posts/:postId" element={<PostDetailPage />} />
        <Route path="/:handle/universe" element={<Suspense fallback={loadingFallback}><UniversePage /></Suspense>} />
        {/* 🔒 로그인 필요 */}
        <Route path="/write" element={<RequireAuth><Suspense fallback={loadingFallback}><WritePage /></Suspense></RequireAuth>} />
        <Route path="/write/:postId" element={<RequireAuth><Suspense fallback={loadingFallback}><WritePage /></Suspense></RequireAuth>} />
        <Route path="/drafts" element={<RequireAuth><DraftsPage /></RequireAuth>} />
        <Route path="/settings" element={<RequireAuth><SettingsPage /></RequireAuth>} />
        <Route path="/settings/categories" element={<RequireAuth><CategorySettingsPage /></RequireAuth>} />
        <Route path="/bookmarks" element={<RequireAuth><BookmarksPage /></RequireAuth>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
