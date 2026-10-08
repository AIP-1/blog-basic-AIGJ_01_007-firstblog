import { createContext } from 'react'

/** 로그인 상태. 값은 AuthProvider가 채우고, 화면에서는 useAuth()로 꺼내요 */
export const AuthContext = createContext(null)
