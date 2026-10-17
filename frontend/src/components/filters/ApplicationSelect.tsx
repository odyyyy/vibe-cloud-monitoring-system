import { useApplicationsQuery } from '../../api/useApplications'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface ApplicationSelectProps {
  value: number | null
  onChange: (value: number | null) => void
  id?: string
  disabled?: boolean
}

export function ApplicationSelect({ value, onChange, id, disabled }: ApplicationSelectProps) {
  const { data, isLoading, isError } = useApplicationsQuery()
  const applications = data ?? []

  const handleChange = (next: string) => {
    onChange(next === '' ? null : Number(next))
  }

  return (
    <Select
      // Keep the Select controlled with an empty string instead of `undefined`, which
      // would make Radix treat it as uncontrolled and retain a stale selection.
      value={value === null ? '' : String(value)}
      onValueChange={handleChange}
      disabled={disabled || isLoading || isError || applications.length === 0}
    >
      <SelectTrigger id={id} aria-label="Application">
        <SelectValue
          placeholder={
            isLoading
              ? 'Loading applications…'
              : isError
                ? 'Could not load applications'
                : applications.length === 0
                  ? 'No applications available'
                  : 'Select an application'
          }
        />
      </SelectTrigger>
      <SelectContent>
        {applications.map((application) => (
          <SelectItem key={application.id} value={String(application.id)}>
            {application.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
