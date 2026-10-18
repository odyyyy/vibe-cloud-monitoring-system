export type ThemeId = 'midnight' | 'daylight' | 'aurora'

export interface ThemeDefinition {
  id: ThemeId
  label: string
  description: string
  /** Accent swatch shown in the picker (mirrors --c-accent-400 of the theme). */
  accent: string
  canvas: string
  panel: string
}

export const THEMES: ThemeDefinition[] = [
  {
    id: 'midnight',
    label: 'Midnight',
    description: 'Deep navy surfaces with cyan accents.',
    accent: '#22d3ee',
    canvas: '#05070d',
    panel: '#0b101b',
  },
  {
    id: 'daylight',
    label: 'Daylight',
    description: 'White surfaces with monochrome ink accents.',
    accent: '#111827',
    canvas: '#ffffff',
    panel: '#ffffff',
  },
  {
    id: 'aurora',
    label: 'Aurora',
    description: 'Deep teal surfaces with emerald accents.',
    accent: '#34d399',
    canvas: '#040d0b',
    panel: '#091c17',
  },
]

export const DEFAULT_THEME: ThemeId = 'midnight'

export function isThemeId(value: unknown): value is ThemeId {
  return typeof value === 'string' && THEMES.some((theme) => theme.id === value)
}

/** Applies a theme by setting data-theme on <html>; the CSS variables do the rest. */
export function applyTheme(theme: ThemeId): void {
  if (typeof document === 'undefined') {
    return
  }
  document.documentElement.setAttribute('data-theme', theme)
}
