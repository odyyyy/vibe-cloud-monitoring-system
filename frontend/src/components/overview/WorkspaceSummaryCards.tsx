import { Link } from 'react-router-dom'
import { Activity, Layers, ScrollText, type LucideIcon } from 'lucide-react'
import { ApiError } from '../../api/client'
import { useWorkspaceSummaryQuery } from '../../api/overview'
import { Button } from '../ui/button'
import { Skeleton } from '../ui/skeleton'

interface SummaryCard {
  title: string
  icon: LucideIcon
  href: string
  description: string
  value: number | undefined
}

export function WorkspaceSummaryCards() {
  const { data, isLoading, isError, error, isFetching, refetch } = useWorkspaceSummaryQuery()

  const cards: SummaryCard[] = [
    {
      title: 'Applications',
      icon: Layers,
      href: '/app/applications',
      description: 'Applications and agents connected to your workspace',
      value: data?.applicationCount,
    },
    {
      title: 'Metrics',
      icon: Activity,
      href: '/app/metrics',
      description: 'Metric names received in the last 24 hours',
      value: data?.metricNameCount24h,
    },
    {
      title: 'Logs',
      icon: ScrollText,
      href: '/app/logs',
      description: 'Log events received in the last 24 hours',
      value: data?.logEventCount24h,
    },
  ]

  return (
    <section className="space-y-3" aria-busy={isLoading}>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((card) => {
          const Icon = card.icon
          return (
            <Link
              key={card.title}
              to={card.href}
              className="group rounded-2xl border border-white/10 bg-[#0b101b]/60 p-5 transition-colors hover:border-cyan-400/30 hover:bg-[#0b101b]"
            >
              <div className="flex items-center gap-2 text-slate-400">
                <Icon className="h-4 w-4" aria-hidden="true" />
                <h2 className="text-sm font-medium text-slate-200">{card.title}</h2>
              </div>
              {isLoading ? (
                <Skeleton className="mt-4 h-8 w-16" />
              ) : (
                <p className="mt-4 text-2xl font-semibold text-slate-100">
                  {isError || card.value === undefined ? '—' : card.value.toLocaleString()}
                </p>
              )}
              <p className="mt-1 text-xs text-slate-500">{card.description}</p>
            </Link>
          )
        })}
      </div>

      {isError ? (
        <div role="alert" className="flex flex-wrap items-center gap-3 text-xs text-slate-400">
          <span>{error instanceof ApiError ? error.message : 'Could not load the workspace summary.'}</span>
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : null}
    </section>
  )
}
