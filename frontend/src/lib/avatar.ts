export function avatarInitial(username?: string | null): string {
  const trimmed = username?.trim() ?? ''
  return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?'
}
