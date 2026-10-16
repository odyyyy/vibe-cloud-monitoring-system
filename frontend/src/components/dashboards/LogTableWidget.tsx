import { useState } from 'react'
import type { LogEvent } from '../../api/logs'
import type { LogTableConfig } from '../../features/dashboards/schemas'
import { useLogTableWidget } from '../../features/dashboards/useWidgetData'
import { LogDetailModal } from '../logs/LogDetailModal'
import { LogTable } from '../logs/LogTable'

interface LogTableWidgetProps {
  widgetId: number
  config: LogTableConfig
  refetchInterval: number
}

export function LogTableWidget({ widgetId, config, refetchInterval }: LogTableWidgetProps) {
  const query = useLogTableWidget(widgetId, config, refetchInterval)
  const [selectedLog, setSelectedLog] = useState<LogEvent | null>(null)

  if (query.isError) {
    return (
      <div role="alert" className="flex h-full items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5 p-4">
        <p className="text-center text-sm text-red-300">
          {query.error instanceof Error ? query.error.message : 'Could not load logs.'}
        </p>
      </div>
    )
  }

  return (
    <div className="h-full min-h-0 overflow-auto">
      <LogTable logs={query.data?.content ?? []} isLoading={query.isLoading} onRowClick={setSelectedLog} compact />
      <LogDetailModal
        log={selectedLog}
        open={selectedLog !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedLog(null)
          }
        }}
      />
    </div>
  )
}
