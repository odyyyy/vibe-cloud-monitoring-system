import type { KeyboardEvent } from 'react'
import type { LogEvent } from '../../api/logs'
import { formatDateTime } from '../../lib/datetime'
import { cn } from '../../lib/utils'
import { Skeleton } from '../ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'
import { LogLevelBadge } from './LogLevelBadge'

interface LogTableProps {
  logs: LogEvent[]
  isLoading: boolean
  onRowClick: (log: LogEvent) => void
  compact?: boolean
  emptyMessage?: string
}

export function LogTable({
  logs,
  isLoading,
  onRowClick,
  compact = false,
  emptyMessage = 'No logs match these filters.',
}: LogTableProps) {
  const openOnKeyDown = (event: KeyboardEvent<HTMLTableRowElement>, log: LogEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onRowClick(log)
    }
  }

  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead className={cn('w-px whitespace-nowrap', compact && 'px-2')}>Timestamp</TableHead>
          <TableHead className={cn('w-px', compact && 'px-2')}>Level</TableHead>
          <TableHead className={compact ? 'px-2' : undefined}>Message</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <LogTableSkeletonRows compact={compact} />
        ) : logs.length === 0 ? (
          <TableRow className="hover:bg-transparent">
            <TableCell colSpan={3} className={cn('text-center text-slate-400', compact && 'px-2 py-6')}>
              {emptyMessage}
            </TableCell>
          </TableRow>
        ) : (
          logs.map((log) => (
            <TableRow
              key={log.id}
              tabIndex={0}
              role="button"
              aria-label={`Open log from ${formatDateTime(log.timestamp)} (${log.level})`}
              className="cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-cyan-400/50"
              onClick={() => onRowClick(log)}
              onKeyDown={(event) => openOnKeyDown(event, log)}
            >
              <TableCell className={cn('whitespace-nowrap text-xs text-slate-400', compact && 'px-2')}>
                <time dateTime={log.timestamp}>{formatDateTime(log.timestamp)}</time>
              </TableCell>
              <TableCell className={cn('align-top', compact && 'px-2')}>
                <LogLevelBadge level={log.level} />
              </TableCell>
              <TableCell className={cn('text-slate-300', compact && 'px-2')}>
                <span className="block max-w-[48rem] truncate">{log.message}</span>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}

function LogTableSkeletonRows({ compact }: { compact: boolean }) {
  return (
    <>
      {[0, 1, 2, 3, 4, 5].map((row) => (
        <TableRow key={row} className="hover:bg-transparent">
          <TableCell className={cn('whitespace-nowrap', compact && 'px-2')}>
            <Skeleton className="h-4 w-36" />
          </TableCell>
          <TableCell className={cn(compact && 'px-2')}>
            <Skeleton className="h-5 w-14 rounded-full" />
          </TableCell>
          <TableCell className={cn(compact && 'px-2')}>
            <Skeleton className="h-4 w-full max-w-[28rem]" />
          </TableCell>
        </TableRow>
      ))}
    </>
  )
}
