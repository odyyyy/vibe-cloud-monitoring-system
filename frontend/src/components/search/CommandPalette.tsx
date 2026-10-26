import { useEffect, useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, CornerDownLeft, Loader2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useResourceSearchQuery, type ResourceSearchResult } from '../../api/search'
import {
  accessibleEntries,
  type FeatureEntry,
  type PaletteAction,
  type QuickActionEntry,
} from '../../features/search/registry'
import { highlightSegments, rankEntries } from '../../features/search/matching'
import {
  readRecentSearches,
  rememberRecentSearch,
  type RecentSearchItem,
} from '../../features/search/recentSearches'
import {
  groupResourceResults,
  RESOURCE_TYPE_ICONS,
  RESOURCE_TYPE_LABELS,
  resourceRoute,
} from '../../features/search/resourceRoutes'
import { THEMES } from '../../features/theme/themes'
import { useAuthStore, type MembershipRole, type PlatformRole } from '../../store/authStore'
import { useThemeStore } from '../../store/themeStore'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../ui/dialog'
import {
  Command,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from '../ui/command'

interface CommandPaletteProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  membershipRole: MembershipRole | null
  platformRole: PlatformRole | null
  onLogout: () => void
  onOpenOnboarding: () => void
}

const RESOURCE_SEARCH_DEBOUNCE_MS = 250

export function CommandPalette({
  open,
  onOpenChange,
  membershipRole,
  platformRole,
  onLogout,
  onOpenOnboarding,
}: CommandPaletteProps) {
  const navigate = useNavigate()
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const theme = useThemeStore((state) => state.theme)
  const setTheme = useThemeStore((state) => state.setTheme)

  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [recent, setRecent] = useState<RecentSearchItem[]>([])

  useEffect(() => {
    if (!open) {
      return
    }
    setQuery('')
    setDebouncedQuery('')
    setRecent(readRecentSearches(userId))
  }, [open, userId])

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < 2) {
      setDebouncedQuery('')
      return
    }
    const handle = window.setTimeout(() => setDebouncedQuery(trimmed), RESOURCE_SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(handle)
  }, [query])

  const availableEntries = useMemo(
    () => accessibleEntries(membershipRole, platformRole),
    [membershipRole, platformRole],
  )
  const availableFeatures = useMemo(
    () => availableEntries.filter((entry): entry is FeatureEntry => entry.group === 'Pages & Features'),
    [availableEntries],
  )
  const availableActions = useMemo(
    () => availableEntries.filter((entry): entry is QuickActionEntry => entry.group === 'Quick actions'),
    [availableEntries],
  )
  const featureMatches = useMemo(
    () => rankEntries(availableFeatures, query),
    [availableFeatures, query],
  )
  const actionMatches = useMemo(
    () => rankEntries(availableActions, query),
    [availableActions, query],
  )
  const accessibleFeatureIds = useMemo(
    () => new Set(availableFeatures.map((entry) => entry.id)),
    [availableFeatures],
  )
  const visibleRecent = useMemo(
    () => recent.filter((item) => item.type !== 'feature' || accessibleFeatureIds.has(item.id)),
    [recent, accessibleFeatureIds],
  )

  const resourceQuery = useResourceSearchQuery(debouncedQuery, open)
  const resourceGroups = useMemo(
    () => groupResourceResults(resourceQuery.data?.results ?? []),
    [resourceQuery.data],
  )

  const trimmedQuery = query.trim()
  const resourceSearchActive = trimmedQuery.length >= 2
  const resourcePending = resourceSearchActive && resourceQuery.isLoading
  const resourceError = resourceSearchActive && resourceQuery.isError
  const noFeatureMatches = featureMatches.length === 0 && actionMatches.length === 0
  const noResourceMatches = resourceSearchActive
    && !resourcePending
    && !resourceError
    && resourceGroups.length === 0

  const remember = (item: RecentSearchItem) => {
    rememberRecentSearch(userId, item)
    setRecent(readRecentSearches(userId))
  }

  const navigateTo = (route: string) => {
    onOpenChange(false)
    navigate(route)
    const hash = route.split('#')[1]
    if (hash) {
      window.setTimeout(() => {
        document.getElementById(hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }, 150)
    }
  }

  const selectFeature = (entry: FeatureEntry) => {
    remember({ id: entry.id, type: 'feature', title: entry.title, route: entry.route })
    navigateTo(entry.route)
  }

  const selectRecent = (item: RecentSearchItem) => {
    remember(item)
    navigateTo(item.route)
  }

  const selectResource = (result: ResourceSearchResult) => {
    const route = resourceRoute(result)
    remember({
      id: `${result.type}:${result.id}`,
      type: result.type,
      title: result.title,
      route,
    })
    navigateTo(route)
  }

  const runAction = (action: PaletteAction) => {
    onOpenChange(false)
    if (action === 'toggle-theme') {
      const currentIndex = THEMES.findIndex((candidate) => candidate.id === theme)
      const next = THEMES[(currentIndex + 1) % THEMES.length] ?? THEMES[0]
      setTheme(next.id)
      return
    }
    if (action === 'open-onboarding') {
      onOpenOnboarding()
      return
    }
    onLogout()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="top-[10%] w-[calc(100vw-1.5rem)] max-w-2xl translate-y-0 gap-0 overflow-hidden p-0 sm:top-[15%]"
        hideCloseButton
      >
        <DialogTitle className="sr-only">Global search</DialogTitle>
        <DialogDescription className="sr-only">
          Search pages, features and organization resources, or run a quick action.
        </DialogDescription>

        <Command shouldFilter={false} loop label="Global search">
          <CommandInput
            value={query}
            onValueChange={setQuery}
            maxLength={64}
            placeholder="Search pages, resources and actions…"
            aria-label="Search pages, resources and actions"
          />

          <CommandList>
            {trimmedQuery === '' && visibleRecent.length > 0 ? (
              <>
                <CommandGroup heading="Recent">
                  {visibleRecent.map((item) => {
                    const Icon = recentIcon(item, availableFeatures)
                    return (
                      <CommandItem
                        key={`recent:${item.type}:${item.id}`}
                        value={`recent:${item.type}:${item.id}`}
                        onSelect={() => selectRecent(item)}
                      >
                        <Icon className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                        <span className="min-w-0">
                          <span className="block truncate text-slate-200">{item.title}</span>
                          <span className="block truncate text-xs text-slate-500">Recently opened</span>
                        </span>
                      </CommandItem>
                    )
                  })}
                </CommandGroup>
                <CommandSeparator />
              </>
            ) : null}

            {featureMatches.length > 0 ? (
              <CommandGroup heading="Pages & Features">
                {featureMatches.map((entry) => {
                  const Icon = entry.icon
                  return (
                    <CommandItem
                      key={entry.id}
                      value={`feature:${entry.id}`}
                      onSelect={() => selectFeature(entry)}
                    >
                      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-cyan-300" aria-hidden="true" />
                      <span className="min-w-0">
                        <span className="block truncate text-slate-100">
                          <HighlightedText text={entry.title} query={trimmedQuery} />
                        </span>
                        <span className="mt-0.5 block truncate text-xs text-slate-500">
                          {entry.description}
                        </span>
                      </span>
                    </CommandItem>
                  )
                })}
              </CommandGroup>
            ) : null}

            {resourceSearchActive ? (
              <>
                <CommandSeparator />
                {resourcePending ? (
                  <div className="flex items-center gap-2 px-3 py-3 text-xs text-slate-500" role="status">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                    Searching resources…
                  </div>
                ) : resourceError ? (
                  <p className="px-3 py-3 text-xs text-amber-300" role="status">
                    Resource search is unavailable. {resourceQuery.error instanceof ApiError ? resourceQuery.error.message : 'Try again shortly.'}
                  </p>
                ) : resourceGroups.length > 0 ? (
                  resourceGroups.map((group) => {
                    const Icon = RESOURCE_TYPE_ICONS[group.type]
                    return (
                      <CommandGroup key={group.type} heading={RESOURCE_TYPE_LABELS[group.type]}>
                        {group.results.map((result) => (
                          <CommandItem
                            key={`resource:${result.type}:${result.id}`}
                            value={`resource:${result.type}:${result.id}`}
                            onSelect={() => selectResource(result)}
                          >
                            <Icon className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                            <span className="min-w-0">
                              <span className="block truncate text-slate-100">
                                <HighlightedText text={result.title} query={trimmedQuery} />
                              </span>
                              <span className="mt-0.5 block truncate text-xs text-slate-500">
                                {result.subtitle}
                              </span>
                            </span>
                          </CommandItem>
                        ))}
                      </CommandGroup>
                    )
                  })
                ) : (
                  <p className="px-3 py-3 text-xs text-slate-500" role="status">
                    No organization resources match “{trimmedQuery}”.
                  </p>
                )}
              </>
            ) : null}

            {actionMatches.length > 0 ? (
              <>
                <CommandSeparator />
                <CommandGroup heading="Quick actions">
                  {actionMatches.map((entry) => {
                    const Icon = entry.icon
                    return (
                      <CommandItem
                        key={entry.id}
                        value={`action:${entry.id}`}
                        onSelect={() => runAction(entry.action)}
                      >
                        <Icon className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                        <span className="min-w-0">
                          <span className="block truncate text-slate-100">
                            <HighlightedText text={entry.title} query={trimmedQuery} />
                          </span>
                          <span className="mt-0.5 block truncate text-xs text-slate-500">
                            {entry.description}
                          </span>
                        </span>
                        {entry.action === 'toggle-theme' ? <CommandShortcut>Theme</CommandShortcut> : null}
                      </CommandItem>
                    )
                  })}
                </CommandGroup>
              </>
            ) : null}

            {trimmedQuery === '' && noFeatureMatches ? (
              <p className="px-3 py-6 text-center text-sm text-slate-500">No pages available for your role.</p>
            ) : null}

            {trimmedQuery !== '' && trimmedQuery.length < 2 && noFeatureMatches ? (
              <p className="px-3 py-6 text-center text-sm text-slate-500">
                Keep typing to search organization resources.
              </p>
            ) : null}

            {trimmedQuery.length >= 2 && noFeatureMatches && noResourceMatches && !resourcePending && !resourceError ? (
              <p className="px-3 py-6 text-center text-sm text-slate-400">
                No results for “{trimmedQuery}”. Try a page name, application or dashboard.
              </p>
            ) : null}
          </CommandList>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-white/10 px-4 py-2 text-[11px] text-slate-500">
            <span className="flex items-center gap-1">
              <kbd className="rounded border border-white/10 px-1 py-0.5"><ArrowUp className="h-3 w-3" aria-hidden="true" /></kbd>
              <kbd className="rounded border border-white/10 px-1 py-0.5"><ArrowDown className="h-3 w-3" aria-hidden="true" /></kbd>
              to navigate
            </span>
            <span className="flex items-center gap-1">
              <kbd className="rounded border border-white/10 px-1 py-0.5"><CornerDownLeft className="h-3 w-3" aria-hidden="true" /></kbd>
              to open
            </span>
            <span className="ml-auto">Esc to close</span>
          </div>
        </Command>
      </DialogContent>
    </Dialog>
  )
}

function recentIcon(item: RecentSearchItem, features: FeatureEntry[]) {
  if (item.type === 'feature') {
    return features.find((entry) => entry.id === item.id)?.icon ?? RESOURCE_TYPE_ICONS.applications
  }
  return RESOURCE_TYPE_ICONS[item.type] ?? RESOURCE_TYPE_ICONS.applications
}

function HighlightedText({ text, query }: { text: string; query: string }) {
  return (
    <>
      {highlightSegments(text, query).map((segment, index) => (
        segment.match ? (
          <mark key={index} className="rounded-sm bg-cyan-400/20 px-0.5 text-cyan-100">
            {segment.text}
          </mark>
        ) : (
          <span key={index}>{segment.text}</span>
        )
      ))}
    </>
  )
}
