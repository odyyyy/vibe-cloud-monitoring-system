import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useProfile } from '../../features/auth/useProfile'
import { useAuthStore, type PlatformRole } from '../../store/authStore'

interface RequireRoleProps {
  role: PlatformRole
  children: ReactNode
}

/**
 * Restricts a route to accounts with a given platform role.
 *
 * <p>The role is read from the authenticated profile, never from a client-side flag, and the
 * backend enforces the same rule on the matching API endpoint. A user without the role is
 * redirected to the regular workspace instead of seeing a forbidden page.
 */
export function RequireRole({ role, children }: RequireRoleProps) {
  const token = useAuthStore((state) => state.token)
  const storedRole = useAuthStore((state) => state.user?.platformRole ?? null)
  const { data: profile, isLoading } = useProfile()

  const currentRole = profile?.role ?? storedRole

  if (!token) {
    return <Navigate to="/login" replace />
  }

  if (currentRole === null && isLoading) {
    return (
      <div role="status" className="p-6 text-sm text-slate-400">
        Checking permissions…
      </div>
    )
  }

  if (currentRole !== role) {
    return <Navigate to="/app/overview" replace />
  }

  return <>{children}</>
}
