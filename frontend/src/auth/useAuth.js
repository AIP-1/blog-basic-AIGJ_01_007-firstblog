import { useContext } from 'react'
import { AuthContext } from './authContext'

/** { user, loading, login, logout, reloadMe } — user는 GET /api/users/me 응답, 로그인 전이면 null */
export function useAuth() {
  return useContext(AuthContext)
}
