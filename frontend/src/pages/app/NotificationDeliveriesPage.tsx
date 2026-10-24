import { useMemo } from 'react'
import { ChevronLeft, ChevronRight, History } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import type { NotificationDeliveriesQuery, NotificationDeliveryStatus } from '../../api/alerts'
import { useNotificationDeliveriesQuery } from '../../api/alerts'
import { ApiError } from '../../api/client'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { Skeleton } from '../../components/ui/skeleton'
import {
  notificationDeliveryStatusLabel,
  notificationChannelTypeLabel,
  notificationEventTypeLabel,
} from '../../features/alerts/labels'

const PAGE_SIZES = [10, 20, 50] as const
const STATUSES: NotificationDeliveryStatus[] = ['PENDING', 'SUCCESS', 'FAILED']

function positiveInt(value: string | null): number | null {
  if (value === null) {
    return null
  }
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function statusVariant(status: NotificationDeliveryStatus): 'success' | 'danger' | 'warning' {
  return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
}

export default function NotificationDeliveriesPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const params = useMemo<NotificationDeliveriesQuery>(() => {
    const requestedPage = Number(searchParams.get('page') ?? '0')
    const requestedSize = Number(searchParams.get('size') ?? '20')
    const requestedStatus = searchParams.get('status')
    return {
      page: Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0,
      size: (PAGE_SIZES as readonly number[]).includes(requestedSize) ? requestedSize : 20,
      status: STATUSES.includes(requestedStatus as NotificationDeliveryStatus)
        ? (requestedStatus as NotificationDeliveryStatus)
        : undefined,
      channelId: positiveInt(searchParams.get('channelId')) ?? undefined,
      incidentId: positiveInt(searchParams.get('incidentId')) ?? undefined,
    }
  }, [searchParams])

  const query = useNotificationDeliveriesQuery(params)
  const deliveries = query.data?.content ?? []
  const totalPages = query.data?.totalPages ?? 0
  const currentPage = query.data?.page ?? params.page
  const lastPage = Math.max(totalPages - 1, 0)
  const isRefreshing = query.isFetching && !query.isLoading

  const updateParams = (patch: Partial<NotificationDeliveriesQuery>) => {
    const next = new URLSearchParams(searchParams)
    const merged = { ...params, ...patch }
    next.set('page', String(merged.page))
    next.set('size', String(merged.size))
    if (merged.status) next.set('status', merged.status)
    else next.delete('status')
    if (merged.channelId) next.set('channelId', String(merged.channelId))
    else next.delete('channelId')
    if (merged.incidentId) next.set('incidentId', String(merged.incidentId))
    else next.delete('incidentId')
    setSearchParams(next)
  }

  const errorMessage =
    query.error instanceof ApiError ? query.error.message : 'Something went wrong while loading deliveries.'

  const filterSummary =
    params.channelId !== undefined || params.incidentId !== undefined
      ? `Filtered by ${params.channelId !== undefined ? `channel #${params.channelId}` : ''}${
          params.channelId !== undefined && params.incidentId !== undefined ? ' and ' : ''
        }${params.incidentId !== undefined ? `incident #${params.incidentId}` : ''}.`
      : 'Delivery attempts for every notification channel in this organization, newest first.'

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="section-tag">Alerts</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">Notification deliveries</h1>
        <p className="text-sm text-slate-400">{filterSummary}</p>
      </header>

      <div className="flex flex-wrap items-end gap-4 rounded-2xl border border-white/10 bg-panel/60 p-4">
        <div className="space-y-1.5">
          <Label htmlFor="delivery-status" className="text-xs text-slate-400">
            Status
          </Label>
          <Select
            value={params.status ?? 'ALL'}
            onValueChange={(value) =>
              updateParams({
                status: value === 'ALL' ? undefined : (value as NotificationDeliveryStatus),
                page: 0,
              })
            }
          >
            <SelectTrigger id="delivery-status" className="h-9 w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All statuses</SelectItem>
              {STATUSES.map((status) => (
                <SelectItem key={status} value={status}>
                  {notificationDeliveryStatusLabel(status)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="delivery-page-size" className="text-xs text-slate-400">
            Rows
          </Label>
          <Select
            value={String(params.size)}
            onValueChange={(value) => updateParams({ size: Number(value), page: 0 })}
          >
            <SelectTrigger id="delivery-page-size" className="h-9 w-24">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZES.map((size) => (
                <SelectItem key={size} value={String(size)}>
                  {size}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {params.channelId !== undefined || params.incidentId !== undefined ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => updateParams({ channelId: undefined, incidentId: undefined, page: 0 })}
          >
            Clear channel/incident filter
          </Button>
        ) : null}

        <div className="ml-auto flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => query.refetch()}
            disabled={query.isFetching}
          >
            <History className="h-4 w-4" aria-hidden="true" />
            {query.isFetching ? 'Refreshing…' : 'Refresh'}
          </Button>
        </div>
      </div>

      {query.isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load notification deliveries</h2>
          <p className="mt-2 text-sm text-slate-400">{errorMessage}</p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => query.refetch()}
            disabled={query.isFetching}
          >
            Retry
          </Button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-white/10 bg-panel/60">
          <div className={isRefreshing ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-left text-sm">
                <thead className="border-b border-white/10 bg-white/[0.02] text-xs uppercase tracking-wide text-slate-400">
                  <tr>
                    <th scope="col" className="px-4 py-3">
                      Created
                    </th>
                    <th scope="col" className="px-4 py-3">
                      Event
                    </th>
                    <th scope="col" className="px-4 py-3">
                      Channel
                    </th>
                    <th scope="col" className="px-4 py-3">
                      Destination
                    </th>
                    <th scope="col" className="px-4 py-3">
                      Status
                    </th>
                    <th scope="col" className="px-4 py-3">
                      Attempts
                    </th>
                    <th scope="col" className="px-4 py-3">
                      Last error
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {query.isLoading ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-6" aria-busy="true">
                        <div className="space-y-2">
                          <Skeleton className="h-8 w-full rounded" />
                          <Skeleton className="h-8 w-full rounded" />
                          <Skeleton className="h-8 w-full rounded" />
                        </div>
                      </td>
                    </tr>
                  ) : deliveries.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-12 text-center text-slate-400">
                        No notification deliveries yet. Fire an alert or send a test notification.
                      </td>
                    </tr>
                  ) : (
                    deliveries.map((delivery) => (
                      <tr
                        key={delivery.id}
                        className={
                          delivery.status === 'FAILED'
                            ? 'border-b border-red-500/20 bg-red-500/[0.04] last:border-0'
                            : 'border-b border-white/5 last:border-0'
                        }
                      >
                        <td className="whitespace-nowrap px-4 py-3 text-slate-300">
                          {new Date(delivery.createdAt).toLocaleString()}
                        </td>
                        <td className="px-4 py-3">
                          <div className="text-slate-200">{notificationEventTypeLabel(delivery.eventType)}</div>
                          {delivery.scheduledReportName ? (
                            <div className="text-xs text-slate-500">{delivery.scheduledReportName}</div>
                          ) : null}
                          {delivery.test ? <div className="text-xs text-slate-500">Test notification</div> : null}
                        </td>
                        <td className="px-4 py-3 text-slate-300">
                          {delivery.channelType ? notificationChannelTypeLabel(delivery.channelType) : 'Deleted channel'}
                        </td>
                        <td className="max-w-[260px] break-all px-4 py-3 text-slate-400">
                          {delivery.channelDestination ?? '—'}
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant={statusVariant(delivery.status)}>
                            {notificationDeliveryStatusLabel(delivery.status)}
                          </Badge>
                          {delivery.lastHttpStatus !== null ? (
                            <div className="mt-1 text-xs text-slate-500">HTTP {delivery.lastHttpStatus}</div>
                          ) : null}
                        </td>
                        <td className="px-4 py-3 text-slate-300">{delivery.attempts}</td>
                        <td className="max-w-[320px] px-4 py-3 text-xs text-slate-400">
                          {delivery.lastError ?? '—'}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4 border-t border-white/5 px-4 py-3">
            <p className="text-xs text-slate-400">
              {(query.data?.totalElements ?? 0).toLocaleString()}{' '}
              {(query.data?.totalElements ?? 0) === 1 ? 'delivery' : 'deliveries'}
            </p>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => updateParams({ page: currentPage - 1 })}
                disabled={query.isFetching || currentPage <= 0}
              >
                <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                Previous
              </Button>
              <span className="text-xs text-slate-400">
                Page {totalPages === 0 ? 1 : currentPage + 1} of {Math.max(totalPages, 1)}
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => updateParams({ page: currentPage + 1 })}
                disabled={query.isFetching || totalPages === 0 || currentPage >= lastPage}
              >
                Next
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
