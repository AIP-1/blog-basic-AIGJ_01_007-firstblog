import { Navigate, useLocation } from 'react-router'
import { useAuth } from './useAuth'

/** 🔒 화면: 로그인하지 않았으면 로그인 화면으로 보내고, 로그인 후 원래 주소로 돌아와요 (U-08) */
export default function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <p className="p-8 text-center text-gray-500">불러오는 중…</p>
  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  return children
}
