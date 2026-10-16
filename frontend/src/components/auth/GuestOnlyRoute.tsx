import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { isTokenExpired } from '../../lib/jwt'
import { useAuthStore } from '../../store/authStore'

interface GuestOnlyRouteProps {
  children: ReactNode
}

export function GuestOnlyRoute({ children }: GuestOnlyRouteProps) {
  const token = useAuthStore((state) => state.token)

  if (token && !isTokenExpired(token)) {
    return <Navigate to="/app" replace />
  }

  return <>{children}</>
}
