import { z } from 'zod'

export const STATUS_PAGE_SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/
export const STATUS_PAGE_SLUG_MIN_LENGTH = 3
export const STATUS_PAGE_SLUG_MAX_LENGTH = 50
export const STATUS_PAGE_TITLE_MAX_LENGTH = 150
export const STATUS_PAGE_DESCRIPTION_MAX_LENGTH = 500
export const STATUS_PAGE_COMPONENT_NAME_MAX_LENGTH = 80
export const STATUS_PAGE_MAX_COMPONENTS = 20

export const RESERVED_STATUS_PAGE_SLUGS = [
  'api',
  'admin',
  'app',
  's',
  'login',
  'register',
  'static',
  'health',
  'status',
] as const

export function isReservedStatusPageSlug(slug: string): boolean {
  return (RESERVED_STATUS_PAGE_SLUGS as readonly string[]).includes(slug)
}

export function isValidStatusPageSlug(slug: string): boolean {
  return (
    slug.length >= STATUS_PAGE_SLUG_MIN_LENGTH &&
    slug.length <= STATUS_PAGE_SLUG_MAX_LENGTH &&
    STATUS_PAGE_SLUG_PATTERN.test(slug) &&
    !isReservedStatusPageSlug(slug)
  )
}

const slugSchema = z
  .string()
  .trim()
  .min(STATUS_PAGE_SLUG_MIN_LENGTH, `Slug must be at least ${STATUS_PAGE_SLUG_MIN_LENGTH} characters`)
  .max(STATUS_PAGE_SLUG_MAX_LENGTH, `Slug must be at most ${STATUS_PAGE_SLUG_MAX_LENGTH} characters`)
  .regex(STATUS_PAGE_SLUG_PATTERN, 'Use lowercase letters, digits and single hyphens')
  .refine((value) => !isReservedStatusPageSlug(value), 'This slug is reserved')

const titleSchema = z
  .string()
  .trim()
  .min(1, 'Title is required')
  .max(STATUS_PAGE_TITLE_MAX_LENGTH, `Title must be at most ${STATUS_PAGE_TITLE_MAX_LENGTH} characters`)

const descriptionSchema = z
  .string()
  .trim()
  .max(STATUS_PAGE_DESCRIPTION_MAX_LENGTH, `Description must be at most ${STATUS_PAGE_DESCRIPTION_MAX_LENGTH} characters`)

export const statusPageComponentSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Component name is required')
    .max(
      STATUS_PAGE_COMPONENT_NAME_MAX_LENGTH,
      `Component name must be at most ${STATUS_PAGE_COMPONENT_NAME_MAX_LENGTH} characters`,
    ),
  linkedHealthCheckId: z.number().int().positive().nullable(),
})

export const statusPageFormSchema = z.object({
  title: titleSchema,
  description: descriptionSchema,
  slug: slugSchema,
  isActive: z.boolean(),
  components: z
    .array(statusPageComponentSchema)
    .max(STATUS_PAGE_MAX_COMPONENTS, `A status page may have at most ${STATUS_PAGE_MAX_COMPONENTS} components`),
})

export type StatusPageFormValues = z.infer<typeof statusPageFormSchema>
