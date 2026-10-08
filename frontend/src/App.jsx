import { Route, Routes } from 'react-router'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import NotFoundPage from './pages/NotFoundPage'
import SignupPage from './pages/SignupPage'

/** 화면 주소 (요구사항 4장). 화면을 만들 때마다 여기에 추가해요 */
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<MainPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        {/* 다음 작업: /search, /tags/:tagName, /:handle(=@아이디), /:handle/posts/:postId,
            🔒 /write, /write/:postId, /drafts, /settings, /settings/categories, /bookmarks */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
