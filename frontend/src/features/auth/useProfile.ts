import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getProfile } from '../../api/auth'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../store/authStore'

export function useProfile() {
  const token = useAuthStore((state) => state.token)
  const setUser = useAuthStore((state) => state.setUser)
  const logout = useAuthStore((state) => state.logout)

  const query = useQuery({
    queryKey: ['auth', 'me', token],
    queryFn: () => getProfile(token as string),
    enabled: Boolean(token),
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  useEffect(() => {
    const profile = query.data
    if (profile) {
      setUser({
        id: profile.id,
        username: profile.username,
        email: profile.email,
        avatarUrl: profile.avatarUrl,
      })
    }
  }, [query.data, setUser])

  useEffect(() => {
    if (query.error instanceof ApiError && query.error.status === 401) {
      logout()
    }
  }, [query.error, logout])

  return query
}
