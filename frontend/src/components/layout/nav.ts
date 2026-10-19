import type { LucideIcon } from 'lucide-react'
import { Activity, Bell, Building2, Gauge, History, LayoutDashboard, LayoutGrid, Layers, ScrollText, Server, Settings, ShieldCheck, Siren } from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/app/overview', label: 'Overview', icon: LayoutDashboard },
  { to: '/app/applications', label: 'Applications', icon: Layers },
  { to: '/app/agents', label: 'Agents', icon: Server },
  { to: '/app/metrics', label: 'Metrics', icon: Activity },
  { to: '/app/logs', label: 'Logs', icon: ScrollText },
  { to: '/app/health', label: 'Health', icon: ShieldCheck },
  { to: '/app/alerts', label: 'Alerts', icon: Bell },
  { to: '/app/incidents', label: 'Incidents', icon: Siren },
  { to: '/app/dashboards', label: 'Dashboards', icon: LayoutGrid },
  { to: '/app/audit', label: 'Audit log', icon: History },
  { to: '/app/organization', label: 'Organization', icon: Building2 },
  { to: '/app/usage', label: 'Usage', icon: Gauge },
  { to: '/app/settings', label: 'Settings', icon: Settings },
]

export function isPathActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`)
}
