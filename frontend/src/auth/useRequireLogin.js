import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useAuth } from './useAuth'

/**
 * 로그인이 필요한 버튼(좋아요, 댓글, 신고 등)에서 써요 (U-08).
 * 로그인했으면 true, 아니면 로그인 화면으로 보내고(돌아올 주소 기억) false.
 */
export function useRequireLogin() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  return useCallback(() => {
    if (user) return true
    navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
    return false
  }, [user, navigate, location])
}
