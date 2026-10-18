import { z } from 'zod'

export const updateUsernameSchema = z.object({
  username: z
    .string()
    .min(3, 'Username must be at least 3 characters')
    .max(50, 'Username must be at most 50 characters')
    .regex(/^[A-Za-z0-9._-]+$/, 'Use letters, numbers, dot, dash or underscore only'),
  currentPassword: z.string().min(1, 'Enter your current password to confirm'),
})

export const updateEmailSchema = z.object({
  newEmail: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  currentPassword: z.string().min(1, 'Enter your current password to confirm'),
})

export type UpdateUsernameFormValues = z.infer<typeof updateUsernameSchema>
export type UpdateEmailFormValues = z.infer<typeof updateEmailSchema>
