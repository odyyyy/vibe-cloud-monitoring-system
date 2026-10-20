import { z } from 'zod'
import { PAT_SCOPE_VALUES } from './scopes'

export const PAT_NAME_MAX_LENGTH = 100
export const PAT_MIN_EXPIRES_DAYS = 1
export const PAT_MAX_EXPIRES_DAYS = 365
export const PAT_DEFAULT_EXPIRES_DAYS = 30

/** Offered expiration presets, in days. */
export const PAT_EXPIRATION_OPTIONS = [30, 90, 365] as const

export const patFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Name is required')
    .max(PAT_NAME_MAX_LENGTH, `Name must be at most ${PAT_NAME_MAX_LENGTH} characters`),
  expiresDays: z.coerce
    .number({ invalid_type_error: 'Expiration is required' })
    .int('Expiration must be a whole number of days')
    .min(PAT_MIN_EXPIRES_DAYS, `Expiration must be between ${PAT_MIN_EXPIRES_DAYS} and ${PAT_MAX_EXPIRES_DAYS} days`)
    .max(PAT_MAX_EXPIRES_DAYS, `Expiration must be between ${PAT_MIN_EXPIRES_DAYS} and ${PAT_MAX_EXPIRES_DAYS} days`),
  scopes: z
    .array(z.enum(PAT_SCOPE_VALUES), { invalid_type_error: 'Select at least one scope' })
    .min(1, 'Select at least one scope'),
})

export type PatFormValues = z.infer<typeof patFormSchema>

export const PAT_FORM_DEFAULTS: PatFormValues = {
  name: '',
  expiresDays: PAT_DEFAULT_EXPIRES_DAYS,
  scopes: [],
}
