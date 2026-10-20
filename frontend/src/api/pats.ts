import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export interface PatSummary {
  id: number
  name: string
  expiresAt: string
  lastUsedAt: string | null
  scopes: string[]
}

export interface PatListResponse {
  summaries: PatSummary[]
}

export interface CreatePatRequest {
  name: string
  expiresDays: number
  scopes: string[]
}

export interface CreatedPat {
  rawToken: string
  tokenId: number
  name: string
  expiresAt: string
  scopes: string[]
}

export interface RevokedPat {
  tokenId: number
  message: string
}

const BASE_PATH = '/v1/settings/pats'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function listPats(): Promise<PatListResponse> {
  return apiRequest<PatListResponse>(BASE_PATH, { token: authToken() })
}

export function createPat(data: CreatePatRequest): Promise<CreatedPat> {
  return apiRequest<CreatedPat>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function revokePat(tokenId: number): Promise<RevokedPat> {
  return apiRequest<RevokedPat>(`${BASE_PATH}/${tokenId}`, { method: 'DELETE', token: authToken() })
}

export const patsQueryKey = ['personal-access-tokens'] as const

export function usePatsQuery() {
  const token = useAuthStore((state) => state.token)
  return useQuery({
    queryKey: patsQueryKey,
    queryFn: listPats,
    enabled: Boolean(token),
  })
}

export function useCreatePatMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreatePatRequest) => createPat(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: patsQueryKey })
    },
  })
}

export function useRevokePatMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tokenId: number) => revokePat(tokenId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: patsQueryKey })
    },
  })
}
