import { useAuthStore } from '../../store/authStore'

export function useCanManage(): boolean {
  const role = useAuthStore((state) => state.currentRole)
  return role === 'OWNER' || role === 'ADMIN'
}

export function useIsOwner(): boolean {
  return useAuthStore((state) => state.currentRole) === 'OWNER'
}
