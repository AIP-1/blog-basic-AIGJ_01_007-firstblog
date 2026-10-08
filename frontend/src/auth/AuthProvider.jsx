import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, setUnauthorizedHandler, tokenStorage } from '../api/client'
import { AuthContext } from './authContext'

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null) // GET /api/users/me 응답 (블로그 정보 포함)
  // 새로고침 직후 로그인 상태를 복원하는 중. 토큰이 없으면 복원할 것도 없어서 처음부터 false예요
  const [loading, setLoading] = useState(() => Boolean(tokenStorage.get()))
  const navigate = useNavigate()

  const loadMe = useCallback(async () => {
    const { data } = await api.get('/users/me')
    setUser(data)
    return data
  }, [])

  // 새로고침해도 토큰이 있으면 로그인 상태를 복원해요 (API 4.4)
  useEffect(() => {
    if (!tokenStorage.get()) return
    api
      .get('/users/me')
      .then(({ data }) => setUser(data))
      .catch(() => tokenStorage.clear()) // 만료됐거나 탈퇴한 회원의 토큰이면 지워요
      .finally(() => setLoading(false))
  }, [])

  // 토큰 만료 → 로그아웃 + 지금 보던 주소를 기억하고 로그인 화면으로 (로그인 후 돌아와요, U-08)
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      const here = window.location.pathname + window.location.search
      navigate(`/login?redirect=${encodeURIComponent(here)}`)
    })
  }, [navigate])

  const login = useCallback(
    async (email, password) => {
      const { data } = await api.post('/auth/login', { email, password })
      tokenStorage.set(data.accessToken)
      return loadMe()
    },
    [loadMe],
  )

  /** 로그아웃은 토큰을 지우는 것뿐이에요 (U-03, 서버 API 없음) */
  const logout = useCallback(() => {
    tokenStorage.clear()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, reloadMe: loadMe }}>
      {children}
    </AuthContext.Provider>
  )
}
