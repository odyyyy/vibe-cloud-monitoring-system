import { describe, expect, it } from 'vitest'
import {
  isEditableTarget,
  isPaletteShortcut,
  isSlashShortcut,
  type PaletteKeyboardEvent,
} from './shortcuts'

function keyboard(overrides: Partial<PaletteKeyboardEvent> = {}): PaletteKeyboardEvent {
  return {
    key: 'k',
    ctrlKey: false,
    metaKey: false,
    altKey: false,
    target: document.body,
    ...overrides,
  }
}

describe('isPaletteShortcut', () => {
  it('accepts Ctrl+K and Cmd+K', () => {
    expect(isPaletteShortcut(keyboard({ ctrlKey: true }))).toBe(true)
    expect(isPaletteShortcut(keyboard({ metaKey: true }))).toBe(true)
    expect(isPaletteShortcut(keyboard({ key: 'K', ctrlKey: true }))).toBe(true)
  })

  it('rejects unmodified or alt-modified keys', () => {
    expect(isPaletteShortcut(keyboard())).toBe(false)
    expect(isPaletteShortcut(keyboard({ altKey: true, ctrlKey: true }))).toBe(false)
    expect(isPaletteShortcut(keyboard({ key: 'j', ctrlKey: true }))).toBe(false)
  })
})

describe('isSlashShortcut', () => {
  it('opens from a non-editable target', () => {
    expect(isSlashShortcut(keyboard({ key: '/', target: document.body }))).toBe(true)
  })

  it('does not hijack typing in form controls', () => {
    expect(isSlashShortcut(keyboard({ key: '/', target: document.createElement('input') }))).toBe(false)
    expect(isSlashShortcut(keyboard({ key: '/', target: document.createElement('textarea') }))).toBe(false)
    expect(isSlashShortcut(keyboard({ key: '/', target: document.createElement('select') }))).toBe(false)
  })

  it('does not hijack content-editable regions', () => {
    const editable = document.createElement('div')
    Object.defineProperty(editable, 'isContentEditable', { value: true })
    expect(isSlashShortcut(keyboard({ key: '/', target: editable }))).toBe(false)
  })

  it('requires an unmodified slash', () => {
    expect(isSlashShortcut(keyboard({ key: '/', ctrlKey: true }))).toBe(false)
    expect(isSlashShortcut(keyboard({ key: '/', metaKey: true }))).toBe(false)
    expect(isSlashShortcut(keyboard({ key: '/', altKey: true }))).toBe(false)
    expect(isSlashShortcut(keyboard({ key: '?' }))).toBe(false)
  })
})

describe('isEditableTarget', () => {
  it('handles non-elements and ordinary elements', () => {
    expect(isEditableTarget(null)).toBe(false)
    expect(isEditableTarget(document.createElement('div'))).toBe(false)
  })
})
