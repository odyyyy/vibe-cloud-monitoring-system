export interface PaletteKeyboardEvent {
  key: string
  ctrlKey: boolean
  metaKey: boolean
  altKey: boolean
  target: EventTarget | null
}

/** True for inputs, textareas, selects and content-editable regions. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (target === null || typeof HTMLElement === 'undefined' || !(target instanceof HTMLElement)) {
    return false
  }
  const tagName = target.tagName.toLowerCase()
  return tagName === 'input'
    || tagName === 'textarea'
    || tagName === 'select'
    || target.isContentEditable === true
}

/** Ctrl+K / Cmd+K opens the palette from anywhere in the authenticated layout. */
export function isPaletteShortcut(event: PaletteKeyboardEvent): boolean {
  return event.key.toLowerCase() === 'k'
    && (event.ctrlKey || event.metaKey)
    && !event.altKey
}

/** "/" opens the palette only when the user is not already typing. */
export function isSlashShortcut(event: PaletteKeyboardEvent): boolean {
  return event.key === '/'
    && !event.ctrlKey
    && !event.metaKey
    && !event.altKey
    && !isEditableTarget(event.target)
}
