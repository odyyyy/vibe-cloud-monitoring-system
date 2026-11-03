import type { LucideIcon } from 'lucide-react'
import {
  Bookmark,
  Building2,
  Database,
  Download,
  KeyRound,
  LifeBuoy,
  Lock,
  LogOut,
  Palette,
  Sun,
  User,
  Users,
  Webhook,
} from 'lucide-react'
import { NAV_ITEMS, PLATFORM_HEALTH_NAV_ITEM, type NavItem } from '../../components/layout/nav'
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
 * Optional search metadata for a sidebar page. A missing entry is not an error: the page is
 * still indexed automatically from {@link NAV_ITEMS}, using its label as the title, a generic
 * description and keywords derived from the label and route.
 */
interface NavPageMetadata {
  id?: string
  title?: string
  description: string
  keywords: string[]
  requiredRole?: FeatureAccessRole
}

/**
 * Extra search metadata keyed by the route used in the sidebar. Keep it in sync with
 * {@link NAV_ITEMS}; this map only improves ranking and wording, it does not decide which
 * pages are searchable.
 */
const NAV_PAGE_METADATA: Record<string, NavPageMetadata> = {
  '/app/overview': {
    description: 'Workspace summary, recent activity and quick links.',
    keywords: ['home', 'summary', 'workspace', 'activity'],
  },
  '/app/applications': {
    description: 'Manage applications, ingest keys and agent credentials.',
    keywords: ['apps', 'services', 'api key', 'ingest key', 'credentials'],
  },
  '/app/agents': {
    description: 'Monitor collector enrollment, connection state and heartbeats.',
    keywords: ['collector', 'fleet', 'enrollment', 'docker', 'heartbeat', 'install agent'],
  },
  '/app/metrics': {
    description: 'Explore time-series metrics and chart aggregation.',
    keywords: ['time series', 'charts', 'aggregation', 'cpu', 'memory'],
  },
  '/app/logs': {
    description: 'Search and filter log events across applications.',
    keywords: ['log explorer', 'search', 'events', 'severity'],
  },
  '/app/log-pipelines': {
    id: 'page-log-pipelines',
    title: 'Log pipelines',
    description: 'Process, parse and redact log events before storage.',
    keywords: ['pipelines', 'pipeline rules', 'rules', 'parsing', 'redact', 'log processing', 'pii'],
  },
  '/app/health': {
    id: 'page-health-checks',
    title: 'Health checks',
    description: 'Synthetic HTTP checks, multi-step API checks and uptime status.',
    keywords: ['synthetic', 'uptime', 'http check', 'ping', 'multi-step api'],
  },
  '/app/status-pages': {
    description: 'Publish public status pages for your services.',
    keywords: ['public status', 'uptime page', 'incident page'],
  },
  '/app/alerts': {
    description: 'Alert rules, thresholds and incident triggers.',
    keywords: ['alert rules', 'rules', 'thresholds', 'triggers'],
  },
  '/app/incidents': {
    description: 'Review firing, acknowledged and resolved incidents.',
    keywords: ['war room', 'firing', 'acknowledged', 'resolved', 'ack', 'on call'],
  },
  '/app/notification-deliveries': {
    title: 'Notification deliveries',
    description: 'Inspect notification delivery history and failures.',
    keywords: ['delivery log', 'deliveries', 'history', 'failed notifications', 'retries'],
  },
  '/app/maintenance': {
    title: 'Maintenance windows',
    description: 'Mute alerts during planned maintenance.',
    keywords: ['mute', 'silence', 'planned downtime', 'maintenance'],
  },
  '/app/reports': {
    description: 'Scheduled reports and delivery configuration.',
    keywords: ['scheduled reports', 'email report', 'summary'],
  },
  '/app/dashboards': {
    description: 'Build dashboards from metric, log and status widgets.',
    keywords: ['widgets', 'charts', 'boards', 'visualization'],
  },
  '/app/audit': {
    description: 'Review configuration and access changes.',
    keywords: ['trail', 'events', 'history'],
  },
  '/app/organization': {
    description: 'Switch organizations and review workspace access.',
    keywords: ['tenant', 'workspace', 'team', 'access'],
  },
  '/app/usage': {
    title: 'Usage & quotas',
    description: 'Review telemetry volume and monthly quota usage.',
    keywords: ['quota', 'billing', 'volume', 'limits'],
  },
  '/app/settings': {
    description: 'Manage your profile, appearance and account.',
    keywords: ['profile', 'account', 'appearance', 'theme', 'password', 'email', 'username'],
  },
  '/admin/platform-health': {
    description: 'Internal platform self-monitoring dashboard.',
    keywords: ['platform', 'self monitoring', 'admin', 'internal'],
    requiredRole: 'PLATFORM_ADMIN',
  },
}

function routeSlug(route: string): string {
  return route
    .replace(/^\/(app|admin)\//, '')
    .replace(/^\/+/, '')
    .replace(/[^a-z0-9]+/gi, '-')
    .replace(/(^-|-$)/g, '')
    .toLowerCase()
}

function labelKeywords(label: string, route: string): string[] {
  const normalizedLabel = label.trim().toLowerCase()
  const words = normalizedLabel.split(/[^a-z0-9]+/).filter((word) => word.length > 0)
  const routeSegments = route
    .split(/[/#?]+/)
    .filter((segment) => segment.length > 0 && segment !== 'app' && segment !== 'admin')
    .map((segment) => segment.replace(/-/g, ' ').toLowerCase())
  return [normalizedLabel, ...words, ...routeSegments]
}

function uniqueKeywords(values: string[]): string[] {
  return Array.from(new Set(values.filter((value) => value.length > 0)))
}

/**
 * Converts a sidebar item into a palette entry.
 *
 * <p>This is exported so tests can prove that a newly added {@link NAV_ITEMS} entry is
 * searchable even before anyone writes dedicated metadata for it.
 */
export function featureEntryForNavItem(item: NavItem): FeatureEntry {
  const metadata = NAV_PAGE_METADATA[item.to]
  return {
    id: metadata?.id ?? `page-${routeSlug(item.to)}`,
    title: metadata?.title ?? item.label,
    description: metadata?.description ?? `Open ${item.label} in the workspace.`,
    route: item.to,
    group: 'Pages & Features',
    keywords: uniqueKeywords([
      ...(metadata?.keywords ?? []),
      ...labelKeywords(item.label, item.to),
    ]),
    requiredRole: metadata?.requiredRole,
    icon: item.icon,
  }
}

/**
 * Sections and options that live inside a page rather than in the sidebar. They use hash
 * anchors that the pages render themselves. Sidebar pages are indexed automatically from
 * {@link NAV_ITEMS}; only these extra entries need to be maintained by hand.
 */
const EXTRA_FEATURE_ENTRIES: FeatureEntry[] = [
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
    id: 'feature-log-export',
    title: 'Export logs',
    description: 'Download the current log query as CSV or JSON.',
    route: '/app/logs',
    group: 'Pages & Features',
    keywords: ['export', 'csv', 'json', 'download logs', 'logs export'],
    icon: Download,
  },
  {
    id: 'feature-metrics-export',
    title: 'Export metrics',
    description: 'Download the current metric query as CSV or JSON.',
    route: '/app/metrics',
    group: 'Pages & Features',
    keywords: ['export', 'csv', 'json', 'download metrics', 'metrics export'],
    icon: Download,
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
    id: 'feature-create-organization',
    title: 'Create organization',
    description: 'Start a new organization and invite its members.',
    route: '/app/organization#create-organization',
    group: 'Pages & Features',
    keywords: ['new organization', 'create organization', 'new workspace', 'tenant'],
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
    keywords: ['retention', 'data retention', 'storage', 'purge', 'delete old data', 'logs metrics traces', 'ttl'],
    icon: Database,
  },
  {
    id: 'feature-appearance',
    title: 'Appearance & themes',
    description: 'Choose the console color theme.',
    route: '/app/settings#appearance',
    group: 'Pages & Features',
    keywords: ['appearance', 'theme', 'dark', 'light', 'color', 'colors'],
    icon: Palette,
  },
  {
    id: 'feature-profile',
    title: 'Profile settings',
    description: 'Change your username or email address.',
    route: '/app/settings#profile',
    group: 'Pages & Features',
    keywords: ['profile', 'username', 'email', 'change email', 'account'],
    icon: User,
  },
  {
    id: 'feature-change-password',
    title: 'Change password',
    description: 'Update the password used to sign in.',
    route: '/app/settings#change-password',
    group: 'Pages & Features',
    keywords: ['password', 'change password', 'security', 'credentials'],
    icon: Lock,
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
]

/**
 * Client-side feature registry for the command palette.
 *
 * <p>Every sidebar item is indexed automatically, including platform-only entries, so adding a
 * new item to {@link NAV_ITEMS} makes it searchable without touching this module. The route-drift
 * test keeps every route honest. Features that live inside another page use a hash anchor rather
 * than a new route.
 */
export const FEATURE_ENTRIES: FeatureEntry[] = [
  ...NAV_ITEMS.map(featureEntryForNavItem),
  featureEntryForNavItem(PLATFORM_HEALTH_NAV_ITEM),
  ...EXTRA_FEATURE_ENTRIES,
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
