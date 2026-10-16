import { avatarInitial } from '../../lib/avatar'
import { cn } from '../../lib/utils'

interface UserAvatarProps {
  username?: string | null
  avatarUrl?: string | null
  className?: string
}

export function UserAvatar({ username, avatarUrl, className }: UserAvatarProps) {
  const name = username?.trim() ?? ''

  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={name ? `${name} avatar` : 'User avatar'}
        className={cn('h-8 w-8 rounded-full object-cover', className)}
      />
    )
  }

  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-cyan-400 to-violet-500 text-sm font-semibold text-[#05070d]',
        className,
      )}
    >
      {avatarInitial(username)}
    </span>
  )
}
