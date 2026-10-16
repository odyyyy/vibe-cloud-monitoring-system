import type { LucideIcon } from 'lucide-react'
import { Activity, LayoutDashboard, LayoutGrid, Layers, ScrollText, Settings, ShieldCheck } from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/app/overview', label: 'Overview', icon: LayoutDashboard },
  { to: '/app/applications', label: 'Applications', icon: Layers },
  { to: '/app/metrics', label: 'Metrics', icon: Activity },
  { to: '/app/logs', label: 'Logs', icon: ScrollText },
  { to: '/app/health', label: 'Health', icon: ShieldCheck },
  { to: '/app/dashboards', label: 'Dashboards', icon: LayoutGrid },
  { to: '/app/settings', label: 'Settings', icon: Settings },
]

export function isPathActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`)
}
