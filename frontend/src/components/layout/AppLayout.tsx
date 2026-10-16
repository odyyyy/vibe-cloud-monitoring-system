import { useEffect, useState } from 'react'
import { ChevronDown, LogOut, Menu, Settings as SettingsIcon, X } from 'lucide-react'
import { NavLink, Outlet, useLocation, useNavigate, Link } from 'react-router-dom'
import { useProfile } from '../../features/auth/useProfile'
import { cn } from '../../lib/utils'
import { useAuthStore } from '../../store/authStore'
import { Logo } from '../shared/icons'
import { UserAvatar } from '../shared/UserAvatar'
import { Button } from '../ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu'
import { NAV_ITEMS, isPathActive } from './nav'

function Brand() {
  return (
    <div className="flex items-center gap-2.5">
      <Logo />
      <span className="font-display text-lg font-semibold tracking-tight text-slate-100">
        Vibe<span className="text-cyan-300">Monitor</span>
      </span>
    </div>
  )
}

export function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)

  const { data: profile } = useProfile()

  const username = profile?.username ?? user?.username ?? 'Account'
  const email = profile?.email ?? user?.email ?? null

  useEffect(() => {
    setMobileNavOpen(false)
  }, [pathname])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const navigation = (
    <nav className="flex flex-col gap-1" aria-label="Primary">
      {NAV_ITEMS.map((item) => {
        const Icon = item.icon
        const active = isPathActive(pathname, item.to)
        return (
          <NavLink
            key={item.to}
            to={item.to}
            aria-current={active ? 'page' : undefined}
            className={cn(
              'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              active ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/5 hover:text-slate-100',
            )}
          >
            <Icon className={cn('h-4 w-4', active ? 'text-cyan-300' : 'text-slate-500')} aria-hidden="true" />
            {item.label}
          </NavLink>
        )
      })}
    </nav>
  )

  const accountFooter = (
    <div className="flex items-center gap-2.5 rounded-lg border border-white/5 bg-white/[0.02] px-3 py-2">
      <UserAvatar username={username} avatarUrl={user?.avatarUrl} className="h-7 w-7 text-xs" />
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-slate-200">{username}</p>
        {email ? <p className="truncate text-xs text-slate-500">{email}</p> : null}
      </div>
    </div>
  )

  return (
    <div className="noise min-h-screen bg-[#05070d] text-slate-100">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 flex-col border-r border-white/5 bg-[#080b14] px-4 py-5 lg:flex">
        <Brand />
        <div className="mt-8 flex-1">{navigation}</div>
        {accountFooter}
      </aside>

      {mobileNavOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/60"
            aria-hidden="true"
            onClick={() => setMobileNavOpen(false)}
          />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col border-r border-white/10 bg-[#080b14] px-4 py-5">
            <div className="flex items-center justify-between">
              <Link to="/">
              <Brand />
              </Link>
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                aria-label="Close navigation"
                className="inline-flex h-9 w-9 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/5 hover:text-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="mt-8 flex-1">{navigation}</div>
            {accountFooter}
          </aside>
        </div>
      ) : null}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-30 border-b border-white/5 bg-[#05070d]/80 backdrop-blur-xl">
          <div className="flex h-16 items-center justify-between gap-3 px-4 sm:px-6">
            <div className="flex items-center gap-3">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="lg:hidden"
                aria-label="Open navigation"
                onClick={() => setMobileNavOpen(true)}
              >
                <Menu className="h-5 w-5" />
              </Button>
              <span className="font-display text-sm font-semibold uppercase tracking-wide text-slate-400">
                Console
              </span>
            </div>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  type="button"
                  variant="ghost"
                  className="h-auto gap-2.5 rounded-full border border-white/10 bg-white/[0.04] py-1.5 pl-1.5 pr-3 hover:bg-white/[0.08]"
                >
                  <UserAvatar username={username} avatarUrl={user?.avatarUrl} />
                  <span className="hidden max-w-[9rem] truncate text-sm font-medium text-slate-200 sm:block">
                    {username}
                  </span>
                  <ChevronDown className="h-4 w-4 text-slate-500" aria-hidden="true" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-60">
                <DropdownMenuLabel>
                  <span className="block truncate text-sm font-medium text-slate-200">{username}</span>
                  {email ? <span className="mt-0.5 block truncate text-xs text-slate-500">{email}</span> : null}
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => navigate('/app/settings')}>
                  <SettingsIcon className="h-4 w-4" aria-hidden="true" />
                  Settings
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={handleLogout} className="text-red-300 focus:text-red-200">
                  <LogOut className="h-4 w-4" aria-hidden="true" />
                  Log out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>

        <main className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
