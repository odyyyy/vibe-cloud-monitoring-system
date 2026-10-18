import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore, type MembershipRole } from '../store/authStore'
import { apiRequest } from './client'

export type { MembershipRole }

export interface Organization {
  id: number
  name: string
  role: MembershipRole
}

export interface OrganizationMember {
  membershipId: number
  userId: number
  username: string | null
  email: string | null
  role: MembershipRole
}

const BASE_PATH = '/v1/organizations'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getOrganizations(): Promise<Organization[]> {
  return apiRequest<Organization[]>(BASE_PATH, { token: authToken() })
}

export function createOrganization(name: string): Promise<Organization> {
  return apiRequest<Organization>(BASE_PATH, { method: 'POST', body: { name }, token: authToken() })
}

export function getOrganizationMembers(organizationId: number): Promise<OrganizationMember[]> {
  return apiRequest<OrganizationMember[]>(`${BASE_PATH}/${organizationId}/members`, { token: authToken() })
}

export function inviteOrganizationMember(organizationId: number, email: string): Promise<OrganizationMember> {
  return apiRequest<OrganizationMember>(`${BASE_PATH}/${organizationId}/members`, {
    method: 'POST',
    body: { email },
    token: authToken(),
  })
}

export function updateOrganizationMemberRole(
  organizationId: number,
  userId: number,
  role: MembershipRole,
): Promise<OrganizationMember> {
  return apiRequest<OrganizationMember>(`${BASE_PATH}/${organizationId}/members/${userId}`, {
    method: 'PATCH',
    body: { role },
    token: authToken(),
  })
}

export function removeOrganizationMember(organizationId: number, userId: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${organizationId}/members/${userId}`, {
    method: 'DELETE',
    token: authToken(),
  })
}

export function deleteOrganization(organizationId: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${organizationId}`, { method: 'DELETE', token: authToken() })
}

export const organizationsQueryKey = ['organizations'] as const
export const organizationMembersQueryKey = (organizationId: number) =>
  ['organizations', organizationId, 'members'] as const

export function useOrganizationsQuery() {
  return useQuery({
    queryKey: organizationsQueryKey,
    queryFn: getOrganizations,
  })
}

export function useCreateOrganizationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => createOrganization(name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: organizationsQueryKey })
    },
  })
}

export function useOrganizationMembersQuery(organizationId: number | null) {
  return useQuery({
    queryKey: organizationMembersQueryKey(organizationId ?? 0),
    queryFn: () => getOrganizationMembers(organizationId as number),
    enabled: organizationId !== null,
  })
}

export function useInviteMemberMutation(organizationId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (email: string) => inviteOrganizationMember(organizationId, email),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: organizationMembersQueryKey(organizationId) })
    },
  })
}

export function useUpdateMemberRoleMutation(organizationId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: MembershipRole }) =>
      updateOrganizationMemberRole(organizationId, userId, role),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: organizationMembersQueryKey(organizationId) })
    },
  })
}

export function useRemoveMemberMutation(organizationId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => removeOrganizationMember(organizationId, userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: organizationMembersQueryKey(organizationId) })
    },
  })
}

export function useDeleteOrganizationMutation(organizationId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteOrganization(organizationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: organizationsQueryKey })
    },
  })
}
