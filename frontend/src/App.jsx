import { Route, Routes } from 'react-router'
import Layout from './components/Layout'
import BlogPage from './pages/BlogPage'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import NotFoundPage from './pages/NotFoundPage'
import PostDetailPage from './pages/PostDetailPage'
import SearchPage from './pages/SearchPage'
import SignupPage from './pages/SignupPage'
import TagPage from './pages/TagPage'

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
        {/* 다음 작업: 🔒 /write, /write/:postId, /drafts, /settings, /settings/categories, /bookmarks */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
