import type { ReactNode } from 'react'
import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { isTokenExpired } from '../../lib/jwt'
import { useAuthStore } from '../../store/authStore'

interface RequireAuthProps {
  children: ReactNode
}

export function RequireAuth({ children }: RequireAuthProps) {
  const token = useAuthStore((state) => state.token)
  const logout = useAuthStore((state) => state.logout)
  const location = useLocation()

  const hasValidSession = token !== null && !isTokenExpired(token)

  useEffect(() => {
    if (token && isTokenExpired(token)) {
      logout()
    }
  }, [token, logout])

  if (!hasValidSession) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
