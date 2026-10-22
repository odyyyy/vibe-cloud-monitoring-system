import { useParams } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, Clock, HelpCircle, XCircle } from 'lucide-react'
import { ApiError } from '../../api/client'
import { usePublicStatusPageQuery } from '../../api/statusPages'
import type { StatusPageComponentStatus } from '../../api/statusPages'
import { Logo } from '../../components/shared/icons'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import {
  componentStatusPresentation,
  overallStatusPresentation,
  type StatusPageBadgeVariant,
} from '../../features/statuspages/status'
import { formatDateTime } from '../../lib/datetime'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

const POLL_INTERVAL_MS = 60_000

const BANNER_STYLES: Record<StatusPageBadgeVariant, string> = {
  success: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200',
  warning: 'border-amber-400/30 bg-amber-400/10 text-amber-200',
  danger: 'border-red-500/30 bg-red-500/10 text-red-200',
  muted: 'border-white/10 bg-white/[0.04] text-slate-300',
}

const STATUS_ICONS: Record<StatusPageComponentStatus, typeof CheckCircle2> = {
  UP: CheckCircle2,
  DEGRADED: AlertTriangle,
  DOWN: XCircle,
  UNKNOWN: HelpCircle,
}

export default function PublicStatusPage() {
  const params = useParams<{ slug: string }>()
  const slug = params.slug ?? null
  const isVisible = useDocumentVisible()
  const refetchInterval = isVisible ? POLL_INTERVAL_MS : false

  const { data, isLoading, isError, error, refetch, isFetching } = usePublicStatusPageQuery(
    slug,
    refetchInterval,
  )

  return (
    <div className="noise relative min-h-screen bg-canvas text-slate-100">
      <div className="bg-grid pointer-events-none absolute inset-0 opacity-30" aria-hidden="true" />
      <div className="orb left-[-10%] top-[-10%] h-[320px] w-[320px] bg-cyan-500/15" />
      <div className="orb bottom-[-12%] right-[-10%] h-[360px] w-[360px] bg-violet-600/15" />

      <div className="relative mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
        <header className="mb-8 flex items-center justify-between gap-4">
          <a href="/" className="flex items-center gap-2.5" aria-label="VibeMonitoring home">
            <Logo />
            <span className="font-display text-lg font-semibold tracking-tight text-slate-100">
              Vibe<span className="text-cyan-300">Monitor</span>
            </span>
          </a>
          <span className="text-xs text-slate-500">Public status</span>
        </header>

        {isLoading ? (
          <PublicStatusSkeleton />
        ) : isError ? (
          <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
            <h1 className="font-display text-lg font-semibold text-red-200">Could not load this status page</h1>
            <p className="mt-2 text-sm text-slate-400">
              {error instanceof ApiError ? error.message : 'Something went wrong while loading the status page.'}
            </p>
            <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
              {isFetching ? 'Retrying…' : 'Retry'}
            </Button>
          </div>
        ) : data === null ? (
          <div className="rounded-2xl border border-white/10 bg-panel/60 p-10 text-center">
            <h1 className="font-display text-xl font-semibold text-slate-100">Status page not found</h1>
            <p className="mt-2 text-sm text-slate-400">
              This status page does not exist or is no longer publicly available.
            </p>
          </div>
        ) : (
          <main className="space-y-6">
            <div>
              <h1 className="font-display text-2xl font-semibold text-slate-100">{data.title}</h1>
              {data.description ? <p className="mt-2 text-sm text-slate-400">{data.description}</p> : null}
            </div>

            <StatusBanner status={data.overallStatus} />

            <section aria-labelledby="status-components-heading" className="space-y-3">
              <h2 id="status-components-heading" className="font-display text-lg font-semibold text-slate-100">
                Components
              </h2>
              {data.components.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-6 text-center text-sm text-slate-400">
                  No components are configured on this status page.
                </div>
              ) : (
                <ul className="divide-y divide-white/5 overflow-hidden rounded-2xl border border-white/10 bg-panel/60">
                  {data.components.map((component, index) => {
                    const presentation = componentStatusPresentation(component.status)
                    const Icon = STATUS_ICONS[component.status]
                    return (
                      <li
                        key={`${component.name}-${index}`}
                        className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-5"
                      >
                        <span className="flex items-center gap-2.5 text-sm font-medium text-slate-200">
                          <Icon className="h-4 w-4 text-slate-500" aria-hidden="true" />
                          {component.name}
                        </span>
                        <Badge variant={presentation.variant}>
                          <Icon className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                          {presentation.label}
                        </Badge>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>

            <section aria-labelledby="status-incidents-heading" className="space-y-3">
              <h2 id="status-incidents-heading" className="font-display text-lg font-semibold text-slate-100">
                Recent incidents
              </h2>
              {data.recentOutages.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-6 text-center text-sm text-slate-400">
                  No incidents in the last 14 days.
                </div>
              ) : (
                <ol className="space-y-3">
                  {data.recentOutages.map((outage, index) => (
                    <li
                      key={`${outage.componentName}-${outage.startedAt}-${index}`}
                      className="rounded-2xl border border-white/10 bg-panel/60 p-4"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <p className="text-sm font-medium text-slate-200">{outage.title}</p>
                          <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                            <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                            Started {formatDateTime(outage.startedAt)}
                          </p>
                        </div>
                        <Badge variant={outage.resolvedAt === null ? 'warning' : 'success'}>
                          {outage.resolvedAt === null ? 'Ongoing' : 'Resolved'}
                        </Badge>
                      </div>
                      {outage.resolvedAt !== null ? (
                        <p className="mt-2 text-xs text-slate-500">Resolved {formatDateTime(outage.resolvedAt)}</p>
                      ) : null}
                    </li>
                  ))}
                </ol>
              )}
            </section>

            <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-white/5 pt-4 text-xs text-slate-500">
              <span>Last updated {formatDateTime(data.updatedAt)}</span>
              <span>{isVisible ? 'Refreshes every 60 seconds.' : 'Refresh paused while this tab is hidden.'}</span>
            </footer>
          </main>
        )}
      </div>
    </div>
  )
}

function StatusBanner({ status }: { status: StatusPageComponentStatus }) {
  const presentation = overallStatusPresentation(status)
  return (
    <div
      role="status"
      className={`rounded-2xl border p-6 text-center ${BANNER_STYLES[presentation.variant]}`}
    >
      <p className="font-display text-xl font-semibold">{presentation.label}</p>
    </div>
  )
}

function PublicStatusSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true">
      <div className="space-y-2">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-80" />
      </div>
      <Skeleton className="h-24 w-full rounded-2xl" />
      <Skeleton className="h-40 w-full rounded-2xl" />
      <Skeleton className="h-32 w-full rounded-2xl" />
    </div>
  )
}
