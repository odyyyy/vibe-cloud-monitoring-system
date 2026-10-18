import React from 'react'
import { Link } from 'react-router-dom'
import { useProfile } from '../../../features/auth/useProfile'
import { useAuthStore } from '../../../store/authStore'
import { IconArrow, Logo } from '../../shared/icons'
import { UserAvatar } from '../../shared/UserAvatar'

export function Navbar() {
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)
  useProfile()

  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-white/5 bg-canvas/70 backdrop-blur-xl">
      <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        <a href="#top" className="flex items-center gap-2.5">
          <Logo />
          <span className="font-display text-lg font-semibold tracking-tight text-slate-100">
            Vibe<span className="text-cyan-300">Monitor</span>
          </span>
        </a>

        <div className="hidden items-center gap-8 text-sm text-slate-400 md:flex">
          <a href="#features" className="transition-colors hover:text-slate-100">
            Features
          </a>
          <a href="#flow" className="transition-colors hover:text-slate-100">
            How it works
          </a>
          <a href="#install" className="transition-colors hover:text-slate-100">
            Install
          </a>
          <a href="#cta" className="transition-colors hover:text-slate-100">
            Pricing
          </a>
        </div>

        <div className="flex items-center gap-3">
          {token ? (
            <Link
              to="/app"
              className="flex items-center gap-2.5 rounded-full border border-white/10 bg-white/[0.04] py-1.5 pl-1.5 pr-4 transition-colors hover:border-cyan-400/40"
            >
              <UserAvatar username={user?.username} avatarUrl={user?.avatarUrl} />
              <span className="max-w-[9rem] truncate text-sm font-medium text-slate-200">
                {user?.username ?? 'Account'}
              </span>
            </Link>
          ) : (
            <>
              <Link to="/login" className="hidden text-sm text-slate-400 transition-colors hover:text-slate-100 sm:block">
                Sign in
              </Link>
              <Link
                to="/register"
                className="group inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 px-4 py-2 text-sm font-semibold text-canvas transition-transform hover:scale-[1.03]"
              >
                Start free
                <IconArrow className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
              </Link>
            </>
          )}
        </div>
      </nav>
    </header>
  )
}
