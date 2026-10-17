import { MoreHorizontal } from 'lucide-react'
import type { Application } from '../../api/applications'
import { formatDateTime } from '../../lib/datetime'
import { Button } from '../ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'

interface ApplicationsTableProps {
  applications: Application[]
  onEdit: (application: Application) => void
  onRegenerate: (application: Application) => void
  onDelete: (application: Application) => void
}

export function ApplicationsTable({ applications, onEdit, onRegenerate, onDelete }: ApplicationsTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Created At</TableHead>
          <TableHead className="w-16 text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {applications.map((application) => (
          <TableRow key={application.id}>
            <TableCell className="font-medium text-slate-100">{application.name}</TableCell>
            <TableCell className="whitespace-nowrap text-slate-400">
              {formatDateTime(application.createdAt)}
            </TableCell>
            <TableCell className="text-right">
              <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Actions for ${application.name}`}
                    className="h-8 w-8"
                  >
                    <MoreHorizontal className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onSelect={() => onEdit(application)}>Edit</DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => onRegenerate(application)}>Regenerate Key</DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    onSelect={() => onDelete(application)}
                    className="text-red-300 focus:text-red-200"
                  >
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
