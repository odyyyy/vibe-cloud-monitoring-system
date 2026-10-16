import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createApplication,
  deleteApplication,
  getApplications,
  regenerateApiKey,
  updateApplication,
  type CreateApplicationRequest,
} from './applications'

export const applicationsQueryKey = ['applications'] as const

export function useApplicationsQuery() {
  return useQuery({
    queryKey: applicationsQueryKey,
    queryFn: getApplications,
  })
}

function useApplicationsInvalidation() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: applicationsQueryKey })
}

export function useCreateApplicationMutation() {
  const invalidate = useApplicationsInvalidation()
  return useMutation({
    mutationFn: (data: CreateApplicationRequest) => createApplication(data),
    onSuccess: invalidate,
  })
}

export function useUpdateApplicationMutation() {
  const invalidate = useApplicationsInvalidation()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: CreateApplicationRequest }) => updateApplication(id, data),
    onSuccess: invalidate,
  })
}

export function useDeleteApplicationMutation() {
  const invalidate = useApplicationsInvalidation()
  return useMutation({
    mutationFn: (id: number) => deleteApplication(id),
    onSuccess: invalidate,
  })
}

export function useRegenerateApiKeyMutation() {
  const invalidate = useApplicationsInvalidation()
  return useMutation({
    mutationFn: (id: number) => regenerateApiKey(id),
    onSuccess: invalidate,
  })
}
