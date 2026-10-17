import type { AuditEvent } from '../../api/audit'
import { auditActionLabel } from '../../features/audit/actions'
import { formatDateTime } from '../../lib/datetime'
import { Badge } from '../ui/badge'
import { Skeleton } from '../ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'

interface AuditEventsTableProps {
  events: AuditEvent[]
  isLoading: boolean
}

export function AuditEventsTable({ events, isLoading }: AuditEventsTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead className="w-px whitespace-nowrap">Timestamp</TableHead>
          <TableHead>User</TableHead>
          <TableHead className="w-px whitespace-nowrap">Action</TableHead>
          <TableHead>Resource</TableHead>
          <TableHead className="w-px whitespace-nowrap">IP address</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <AuditEventsSkeletonRows />
        ) : events.length === 0 ? (
          <TableRow className="hover:bg-transparent">
            <TableCell colSpan={5} className="py-10 text-center text-slate-400">
              No audit events match the current filters.
            </TableCell>
          </TableRow>
        ) : (
          events.map((event) => (
            <TableRow key={event.id}>
              <TableCell className="whitespace-nowrap text-xs text-slate-400">
                <time dateTime={event.timestamp}>{formatDateTime(event.timestamp)}</time>
              </TableCell>
              <TableCell className="text-slate-300">
                {event.username ?? `User #${event.userId}`}
              </TableCell>
              <TableCell>
                <Badge variant="info">{auditActionLabel(event.action)}</Badge>
              </TableCell>
              <TableCell className="text-slate-300">
                {event.resourceId ?? <span className="text-slate-500">—</span>}
              </TableCell>
              <TableCell className="whitespace-nowrap text-xs text-slate-400">
                {event.ipAddress ?? <span className="text-slate-500">—</span>}
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}

function AuditEventsSkeletonRows() {
  return (
    <>
      {[0, 1, 2, 3, 4, 5].map((row) => (
        <TableRow key={row} className="hover:bg-transparent">
          <TableCell className="whitespace-nowrap">
            <Skeleton className="h-4 w-36" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-5 w-28 rounded-full" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
        </TableRow>
      ))}
    </>
  )
}
