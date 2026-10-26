import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  BarChart3,
  Bell,
  Bookmark,
  Bot,
  Boxes,
  Building2,
  Database,
  FileText,
  Gauge,
  Globe2,
  HeartPulse,
  KeyRound,
  LayoutDashboard,
  LifeBuoy,
  LogOut,
  ScrollText,
  Send,
  Settings,
  ShieldCheck,
  Siren,
  Sun,
  Users,
  Webhook,
  Wrench,
} from 'lucide-react'
import type { MembershipRole, PlatformRole } from '../../store/authStore'

export type FeatureAccessRole = MembershipRole | 'PLATFORM_ADMIN'
export type FeatureGroup = 'Pages & Features' | 'Quick actions'
export type PaletteAction = 'toggle-theme' | 'logout' | 'open-onboarding'

interface PaletteEntryBase {
  id: string
  title: string
  description: string
  group: FeatureGroup
  keywords: string[]
  requiredRole?: FeatureAccessRole
  icon: LucideIcon
}

export interface FeatureEntry extends PaletteEntryBase {
  route: string
  action?: never
}

export interface QuickActionEntry extends PaletteEntryBase {
  action: PaletteAction
  route?: never
}

export type PaletteEntry = FeatureEntry | QuickActionEntry

/**
 * Client-side feature registry for the command palette.
 *
 * <p>Routes must point at routes that actually exist in `App.tsx`; the route-drift test keeps
 * this list honest. Features that live inside another page use a hash anchor rather than a new
 * route. There is deliberately no entry for SSO settings or Traces because no such page exists.
 */
export const FEATURE_ENTRIES: FeatureEntry[] = [
  {
    id: 'page-overview',
    title: 'Overview',
    description: 'Workspace summary, recent activity and quick links.',
    route: '/app/overview',
    group: 'Pages & Features',
    keywords: ['overview', 'home', 'summary', 'workspace'],
    icon: LayoutDashboard,
  },
  {
    id: 'page-applications',
    title: 'Applications',
    description: 'Manage applications, ingest keys and agent credentials.',
    route: '/app/applications',
    group: 'Pages & Features',
    keywords: ['applications', 'apps', 'services', 'api key', 'ingest key', 'credentials'],
    icon: Boxes,
  },
  {
    id: 'page-agents',
    title: 'Agents',
    description: 'Monitor collector enrollment, connection state and heartbeats.',
    route: '/app/agents',
    group: 'Pages & Features',
    keywords: ['agents', 'collector', 'fleet', 'enrollment', 'docker', 'heartbeat'],
    icon: Bot,
  },
  {
    id: 'page-metrics',
    title: 'Metrics',
    description: 'Explore time-series metrics and chart aggregation.',
    route: '/app/metrics',
    group: 'Pages & Features',
    keywords: ['metrics', 'time series', 'charts', 'aggregation', 'cpu', 'memory'],
    icon: BarChart3,
  },
  {
    id: 'page-logs',
    title: 'Logs',
    description: 'Search and filter log events across applications.',
    route: '/app/logs',
    group: 'Pages & Features',
    keywords: ['logs', 'log explorer', 'search', 'events', 'severity'],
    icon: ScrollText,
  },
  {
    id: 'feature-saved-views',
    title: 'Saved log views',
    description: 'Reusable log filters stored for this organization.',
    route: '/app/logs#saved-views',
    group: 'Pages & Features',
    keywords: ['saved views', 'saved', 'views', 'filters', 'bookmarks'],
    icon: Bookmark,
  },
  {
    id: 'page-health-checks',
    title: 'Health checks',
    description: 'Synthetic HTTP checks and uptime status.',
    route: '/app/health',
    group: 'Pages & Features',
    keywords: ['health', 'health checks', 'synthetic', 'uptime', 'http check', 'ping'],
    icon: HeartPulse,
  },
  {
    id: 'page-status-pages',
    title: 'Status pages',
    description: 'Publish public status pages for your services.',
    route: '/app/status-pages',
    group: 'Pages & Features',
    keywords: ['status', 'status pages', 'public status', 'uptime page', 'incident page'],
    icon: Globe2,
  },
  {
    id: 'page-alerts',
    title: 'Alerts',
    description: 'Alert rules, thresholds and incident triggers.',
    route: '/app/alerts',
    group: 'Pages & Features',
    keywords: ['alerts', 'alert rules', 'rules', 'thresholds', 'triggers'],
    icon: Bell,
  },
  {
    id: 'feature-notification-channels',
    title: 'Notification channels',
    description: 'Configure Slack, email, webhook and PagerDuty delivery.',
    route: '/app/alerts',
    group: 'Pages & Features',
    keywords: ['notification channels', 'notifications', 'channels', 'slack', 'email', 'webhook', 'pagerduty', 'teams'],
    requiredRole: 'ADMIN',
    icon: Webhook,
  },
  {
    id: 'page-incidents',
    title: 'Incidents',
    description: 'Review firing, acknowledged and resolved incidents.',
    route: '/app/incidents',
    group: 'Pages & Features',
    keywords: ['incidents', 'war room', 'firing', 'acknowledged', 'resolved'],
    icon: Siren,
  },
  {
    id: 'page-notification-deliveries',
    title: 'Notification deliveries',
    description: 'Inspect notification delivery history and failures.',
    route: '/app/notification-deliveries',
    group: 'Pages & Features',
    keywords: ['notification deliveries', 'deliveries', 'history', 'failed notifications', 'retries'],
    icon: Send,
  },
  {
    id: 'page-maintenance',
    title: 'Maintenance windows',
    description: 'Mute alerts during planned maintenance.',
    route: '/app/maintenance',
    group: 'Pages & Features',
    keywords: ['maintenance', 'maintenance windows', 'mute', 'silence', 'planned downtime'],
    icon: Wrench,
  },
  {
    id: 'page-reports',
    title: 'Reports',
    description: 'Scheduled reports and delivery configuration.',
    route: '/app/reports',
    group: 'Pages & Features',
    keywords: ['reports', 'scheduled reports', 'email report', 'summary'],
    icon: FileText,
  },
  {
    id: 'page-dashboards',
    title: 'Dashboards',
    description: 'Build dashboards from metric, log and status widgets.',
    route: '/app/dashboards',
    group: 'Pages & Features',
    keywords: ['dashboards', 'widgets', 'charts', 'boards', 'visualization'],
    icon: Gauge,
  },
  {
    id: 'page-audit',
    title: 'Audit log',
    description: 'Review configuration and access changes.',
    route: '/app/audit',
    group: 'Pages & Features',
    keywords: ['audit', 'audit log', 'trail', 'events', 'history'],
    icon: ShieldCheck,
  },
  {
    id: 'page-organization',
    title: 'Organization',
    description: 'Switch organizations and review workspace access.',
    route: '/app/organization',
    group: 'Pages & Features',
    keywords: ['organization', 'tenant', 'workspace', 'team', 'access'],
    icon: Building2,
  },
  {
    id: 'feature-members',
    title: 'Members & roles',
    description: 'Invite teammates and manage organization roles.',
    route: '/app/organization#members',
    group: 'Pages & Features',
    keywords: ['members', 'membership', 'team', 'roles', 'invite', 'access control'],
    requiredRole: 'ADMIN',
    icon: Users,
  },
  {
    id: 'feature-retention',
    title: 'Data retention',
    description: 'Control how long logs, metrics and traces are kept.',
    route: '/app/organization#retention',
    group: 'Pages & Features',
    keywords: ['retention', 'data retention', 'storage', 'purge', 'delete old data', 'logs metrics traces'],
    icon: Database,
  },
  {
    id: 'page-usage',
    title: 'Usage & quotas',
    description: 'Review telemetry volume and monthly quota usage.',
    route: '/app/usage',
    group: 'Pages & Features',
    keywords: ['usage', 'quota', 'billing', 'volume', 'limits'],
    icon: Activity,
  },
  {
    id: 'page-settings',
    title: 'Settings',
    description: 'Manage your profile, appearance and account.',
    route: '/app/settings',
    group: 'Pages & Features',
    keywords: ['settings', 'profile', 'account', 'appearance', 'theme', 'password'],
    icon: Settings,
  },
  {
    id: 'feature-personal-access-tokens',
    title: 'Personal Access Tokens',
    description: 'Issue scoped API tokens for scripts and automation.',
    route: '/app/settings#personal-access-tokens',
    group: 'Pages & Features',
    keywords: ['personal access tokens', 'token', 'pat', 'api token', 'automation', 'credentials'],
    icon: KeyRound,
  },
  {
    id: 'page-platform-health',
    title: 'Platform Health',
    description: 'Internal platform self-monitoring dashboard.',
    route: '/admin/platform-health',
    group: 'Pages & Features',
    keywords: ['platform health', 'platform', 'self monitoring', 'admin', 'internal'],
    requiredRole: 'PLATFORM_ADMIN',
    icon: LifeBuoy,
  },
]

export const QUICK_ACTIONS: QuickActionEntry[] = [
  {
    id: 'action-toggle-theme',
    title: 'Toggle theme',
    description: 'Switch between the available console color themes.',
    action: 'toggle-theme',
    group: 'Quick actions',
    keywords: ['theme', 'appearance', 'dark', 'light', 'color'],
    icon: Sun,
  },
  {
    id: 'action-open-onboarding',
    title: 'Open setup guide',
    description: 'Show the guided onboarding checklist.',
    action: 'open-onboarding',
    group: 'Quick actions',
    keywords: ['setup', 'guide', 'onboarding', 'getting started', 'help'],
    icon: LifeBuoy,
  },
  {
    id: 'action-logout',
    title: 'Log out',
    description: 'End this browser session.',
    action: 'logout',
    group: 'Quick actions',
    keywords: ['logout', 'sign out', 'session', 'account'],
    icon: LogOut,
  },
]

export const PALETTE_ENTRIES: PaletteEntry[] = [...FEATURE_ENTRIES, ...QUICK_ACTIONS]

/** Mirrors the route guards: platform roles and organization roles are separate dimensions. */
export function canAccessEntry(
  entry: PaletteEntry,
  membershipRole: MembershipRole | null,
  platformRole: PlatformRole | null,
): boolean {
  if (!entry.requiredRole) {
    return true
  }
  if (entry.requiredRole === 'PLATFORM_ADMIN') {
    return platformRole === 'PLATFORM_ADMIN'
  }
  if (membershipRole === null) {
    return false
  }
  if (entry.requiredRole === 'VIEWER') {
    return true
  }
  if (entry.requiredRole === 'ADMIN') {
    return membershipRole === 'ADMIN' || membershipRole === 'OWNER'
  }
  return membershipRole === 'OWNER'
}

export function accessibleEntries(
  membershipRole: MembershipRole | null,
  platformRole: PlatformRole | null,
): PaletteEntry[] {
  return PALETTE_ENTRIES.filter((entry) => canAccessEntry(entry, membershipRole, platformRole))
}
