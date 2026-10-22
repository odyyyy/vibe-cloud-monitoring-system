import { describe, expect, it } from 'vitest'
import {
  STATUS_PAGE_MAX_COMPONENTS,
  isReservedStatusPageSlug,
  isValidStatusPageSlug,
  statusPageFormSchema,
} from './schemas'

describe('status page schemas', () => {
  it('accepts a valid slug and rejects malformed or reserved ones', () => {
    expect(isValidStatusPageSlug('acme-status')).toBe(true)
    expect(isValidStatusPageSlug('a1-b2-c3')).toBe(true)
    expect(isValidStatusPageSlug('ab')).toBe(false)
    expect(isValidStatusPageSlug('Acme-Status')).toBe(false)
    expect(isValidStatusPageSlug('acme--status')).toBe(false)
    expect(isValidStatusPageSlug('acme_status')).toBe(false)
    expect(isReservedStatusPageSlug('admin')).toBe(true)
    expect(isValidStatusPageSlug('admin')).toBe(false)
  })

  it('trims valid form values', () => {
    const parsed = statusPageFormSchema.parse({
      title: '  Acme Status  ',
      description: '  Current availability  ',
      slug: '  acme-status  ',
      isActive: true,
      components: [{ name: '  API  ', linkedHealthCheckId: 7 }],
    })

    expect(parsed).toEqual({
      title: 'Acme Status',
      description: 'Current availability',
      slug: 'acme-status',
      isActive: true,
      components: [{ name: 'API', linkedHealthCheckId: 7 }],
    })
  })

  it('requires a component name and allows an unlinked component', () => {
    expect(
      statusPageFormSchema.safeParse({
        title: 'Acme',
        description: '',
        slug: 'acme-status',
        isActive: false,
        components: [{ name: '   ', linkedHealthCheckId: null }],
      }).success,
    ).toBe(false)

    expect(
      statusPageFormSchema.safeParse({
        title: 'Acme',
        description: '',
        slug: 'acme-status',
        isActive: false,
        components: [{ name: 'API', linkedHealthCheckId: null }],
      }).success,
    ).toBe(true)
  })

  it('enforces the component limit', () => {
    const components = Array.from({ length: STATUS_PAGE_MAX_COMPONENTS + 1 }, (_, index) => ({
      name: `Component ${index}`,
      linkedHealthCheckId: null,
    }))

    expect(
      statusPageFormSchema.safeParse({
        title: 'Acme',
        description: '',
        slug: 'acme-status',
        isActive: false,
        components,
      }).success,
    ).toBe(false)
  })
})
