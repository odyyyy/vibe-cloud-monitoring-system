import type { LucideIcon } from 'lucide-react'
import { Activity, Bell, Building2, Gauge, HeartPulse, History, LayoutDashboard, LayoutGrid, Layers, ScrollText, Server, Settings, ShieldCheck, Siren, Wrench } from 'lucide-react'

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
  { to: '/app/maintenance', label: 'Maintenance', icon: Wrench },
  { to: '/app/dashboards', label: 'Dashboards', icon: LayoutGrid },
  { to: '/app/audit', label: 'Audit log', icon: History },
  { to: '/app/organization', label: 'Organization', icon: Building2 },
  { to: '/app/usage', label: 'Usage', icon: Gauge },
  { to: '/app/settings', label: 'Settings', icon: Settings },
]

/**
 * Platform administration entry. It is rendered only for accounts with the PLATFORM_ADMIN role
 * and lives outside the regular workspace, because the dashboard shows internal infrastructure
 * telemetry that must not be visible to tenant users.
 */
export const PLATFORM_HEALTH_NAV_ITEM: NavItem = {
  to: '/admin/platform-health',
  label: 'Platform Health',
  icon: HeartPulse,
}

export function isPathActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`)
}
