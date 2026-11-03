import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  Bell,
  Building2,
  CalendarClock,
  Gauge,
  Globe,
  HeartPulse,
  History,
  LayoutDashboard,
  LayoutGrid,
  Layers,
  ScrollText,
  Send,
  Server,
  Settings,
  ShieldCheck,
  Siren,
  Workflow,
  Wrench,
} from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

export interface NavGroup {
  id: string
  label: string
  items: NavItem[]
}

/**
 * Primary navigation grouped by what the user is trying to do. Important cross-cutting pages
 * (Overview, Dashboards) stay at the top; Settings is rendered separately at the bottom.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    id: 'workspace',
    label: 'Workspace',
    items: [
      { to: '/app/overview', label: 'Overview', icon: LayoutDashboard },
      { to: '/app/dashboards', label: 'Dashboards', icon: LayoutGrid },
    ],
  },
  {
    id: 'observability',
    label: 'Observability',
    items: [
      { to: '/app/metrics', label: 'Metrics', icon: Activity },
      { to: '/app/logs', label: 'Logs', icon: ScrollText },
      { to: '/app/log-pipelines', label: 'Log pipelines', icon: Workflow },
      { to: '/app/health', label: 'Health', icon: ShieldCheck },
      { to: '/app/status-pages', label: 'Status pages', icon: Globe },
    ],
  },
  {
    id: 'alerting',
    label: 'Alerting',
    items: [
      { to: '/app/alerts', label: 'Alerts', icon: Bell },
      { to: '/app/incidents', label: 'Incidents', icon: Siren },
      { to: '/app/maintenance', label: 'Maintenance', icon: Wrench },
      { to: '/app/notification-deliveries', label: 'Delivery log', icon: Send },
      { to: '/app/reports', label: 'Reports', icon: CalendarClock },
    ],
  },
  {
    id: 'administration',
    label: 'Administration',
    items: [
      { to: '/app/applications', label: 'Applications', icon: Layers },
      { to: '/app/agents', label: 'Agents', icon: Server },
      { to: '/app/organization', label: 'Organization', icon: Building2 },
      { to: '/app/usage', label: 'Usage', icon: Gauge },
      { to: '/app/audit', label: 'Audit log', icon: History },
    ],
  },
]

/** Rendered below the primary groups, together with the setup guide. */
export const FOOTER_NAV_ITEMS: NavItem[] = [
  { to: '/app/settings', label: 'Settings', icon: Settings },
]

/**
 * Flat list used by search and route checks. Keep this derived from the grouped definitions so
 * a new menu item cannot exist in the sidebar without being indexed by the command palette.
 */
export const NAV_ITEMS: NavItem[] = [
  ...NAV_GROUPS.flatMap((group) => group.items),
  ...FOOTER_NAV_ITEMS,
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
