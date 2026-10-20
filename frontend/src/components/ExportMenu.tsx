import { ChevronDown, Download } from 'lucide-react'
import type { ExportFormat } from '../api/client'
import { Button } from './ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from './ui/dropdown-menu'

interface ExportMenuProps {
  onExport: (format: ExportFormat) => void
  disabled?: boolean
  isExporting?: boolean
}

export function ExportMenu({ onExport, disabled = false, isExporting = false }: ExportMenuProps) {
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="outline"
          disabled={disabled || isExporting}
          aria-label="Export results"
        >
          <Download className="h-4 w-4" aria-hidden="true" />
          {isExporting ? 'Exporting…' : 'Export'}
          <ChevronDown className="h-4 w-4" aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => onExport('csv')}>Export as CSV</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => onExport('json')}>Export as JSON</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
