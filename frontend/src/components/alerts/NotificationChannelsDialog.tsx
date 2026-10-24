import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BellRing, History, Lock, Mail, MessagesSquare, Pencil, Plus, Send, Slack, Trash2, Webhook, X } from 'lucide-react'
import { toast } from 'sonner'
import type {
  CreateNotificationChannelRequest,
  NotificationChannel,
  NotificationChannelType,
  UpdateNotificationChannelRequest,
} from '../../api/alerts'
import {
  useCreateNotificationChannelMutation,
  useDeleteNotificationChannelMutation,
  useNotificationChannelsQuery,
  useTestNotificationChannelMutation,
  useUpdateNotificationChannelMutation,
} from '../../api/alerts'
import { ApiError } from '../../api/client'
import {
  MAX_WEBHOOK_HEADERS,
  NOTIFICATION_CHANNEL_TYPES,
  notificationChannelFormSchema,
  updateNotificationChannelFormSchema,
  type NotificationChannelTypeValue,
} from '../../features/alerts/schemas'
import { notificationChannelTypeLabel } from '../../features/alerts/labels'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../ui/alert-dialog'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../ui/dialog'
import { FormField } from '../auth/FormField'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Skeleton } from '../ui/skeleton'
import { Switch } from '../ui/switch'

interface NotificationChannelsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

interface HeaderRow {
  name: string
  value: string
}

const TYPE_ICONS = {
  WEBHOOK: Webhook,
  SLACK: Slack,
  EMAIL: Mail,
  PAGERDUTY: BellRing,
  TEAMS: MessagesSquare,
} as const

const DESTINATION_FIELDS: Record<
  NotificationChannelTypeValue,
  { label: string; placeholder: string; hint: string }
> = {
  WEBHOOK: {
    label: 'Webhook URL',
    placeholder: 'https://hooks.example.com/alerts',
    hint: 'Must be an absolute https:// URL. Internal, loopback, private and metadata addresses are rejected.',
  },
  SLACK: {
    label: 'Slack webhook URL',
    placeholder: 'https://hooks.slack.com/services/T000/B000/XXXX',
    hint: 'Create an incoming webhook in Slack and paste its URL here.',
  },
  EMAIL: {
    label: 'Email address',
    placeholder: 'oncall@example.com',
    hint: 'Alert emails are delivered asynchronously and rate limited per rule.',
  },
  PAGERDUTY: {
    label: 'PagerDuty integration key',
    placeholder: '0123456789abcdef0123456789abcdef',
    hint: 'Routing key from a PagerDuty Events API v2 integration. The key is masked after saving.',
  },
  TEAMS: {
    label: 'Microsoft Teams webhook URL',
    placeholder: 'https://example.webhook.office.com/webhookb2/...',
    hint: 'Incoming webhook URL of the Teams channel. Only public https hosts are accepted.',
  },
}

/** Returns the single destination stored on a channel, regardless of its type. */
function channelDestination(channel: NotificationChannel): string {
  return (
    channel.url ??
    channel.slackWebhookUrl ??
    channel.emailAddress ??
    channel.teamsWebhookUrl ??
    channel.pagerDutyRoutingKey ??
    ''
  )
}

export function NotificationChannelsDialog({ open, onOpenChange }: NotificationChannelsDialogProps) {
  const { data, isLoading, isError } = useNotificationChannelsQuery()
  const createMutation = useCreateNotificationChannelMutation()
  const updateMutation = useUpdateNotificationChannelMutation()
  const deleteMutation = useDeleteNotificationChannelMutation()
  const testMutation = useTestNotificationChannelMutation()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const channels = data ?? []
  const [editing, setEditing] = useState<NotificationChannel | null>(null)
  const [type, setType] = useState<NotificationChannelTypeValue>('WEBHOOK')
  const [url, setUrl] = useState('')
  const [slackWebhookUrl, setSlackWebhookUrl] = useState('')
  const [emailAddress, setEmailAddress] = useState('')
  const [teamsWebhookUrl, setTeamsWebhookUrl] = useState('')
  const [routingKey, setRoutingKey] = useState('')
  const [secret, setSecret] = useState('')
  const [headerRows, setHeaderRows] = useState<HeaderRow[]>([])
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<NotificationChannel | null>(null)
  const [testingId, setTestingId] = useState<number | null>(null)

  function resetForm() {
    setEditing(null)
    setType('WEBHOOK')
    setUrl('')
    setSlackWebhookUrl('')
    setEmailAddress('')
    setTeamsWebhookUrl('')
    setRoutingKey('')
    setSecret('')
    setHeaderRows([])
    setIsActive(true)
    setError(null)
  }

  useEffect(() => {
    if (!open) {
      return
    }
    resetForm()
    setDeleteTarget(null)
    setTestingId(null)
  }, [open])

  const startEdit = (channel: NotificationChannel) => {
    setEditing(channel)
    setType(channel.type)
    // URLs are returned masked and must never be re-submitted; require a fresh value.
    setUrl('')
    setSlackWebhookUrl('')
    setEmailAddress(channel.emailAddress ?? '')
    setTeamsWebhookUrl('')
    setRoutingKey('')
    setSecret('')
    setHeaderRows([])
    setIsActive(channel.isActive)
    setError(null)
  }

  function buildHeaders(): { headers: Record<string, string> | null; error: string | null } {
    const filled = headerRows.filter((row) => row.name.trim() !== '' || row.value.trim() !== '')
    if (filled.length > MAX_WEBHOOK_HEADERS) {
      return { headers: null, error: `At most ${MAX_WEBHOOK_HEADERS} static headers are allowed.` }
    }
    const headers: Record<string, string> = {}
    const seen = new Set<string>()
    for (const row of filled) {
      const name = row.name.trim()
      if (name === '' || row.value.trim() === '') {
        return { headers: null, error: 'Each header needs both a name and a value.' }
      }
      const normalized = name.toLowerCase()
      if (seen.has(normalized)) {
        return { headers: null, error: `Header "${name}" is configured more than once.` }
      }
      seen.add(normalized)
      headers[name] = row.value.trim()
    }
    return { headers: filled.length === 0 ? null : headers, error: null }
  }

  const handleSubmit = async () => {
    const built = buildHeaders()
    if (type === 'WEBHOOK' && built.error) {
      setError(built.error)
      return
    }

    try {
      if (editing) {
        const candidate: Record<string, unknown> = { type, isActive }
        if (secret.trim() !== '') {
          candidate.secret = secret.trim()
        }
        if (built.headers) {
          candidate.headers = built.headers
        }
        if (url.trim() !== '') candidate.url = url.trim()
        if (slackWebhookUrl.trim() !== '') candidate.slackWebhookUrl = slackWebhookUrl.trim()
        if (emailAddress.trim() !== '') candidate.emailAddress = emailAddress.trim()
        if (teamsWebhookUrl.trim() !== '') candidate.teamsWebhookUrl = teamsWebhookUrl.trim()
        if (routingKey.trim() !== '') candidate.routingKey = routingKey.trim()

        const parsed = updateNotificationChannelFormSchema.safeParse(candidate)
        if (!parsed.success) {
          setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
          return
        }
        await updateMutation.mutateAsync({
          id: editing.id,
          data: parsed.data as UpdateNotificationChannelRequest,
        })
        toast.success('Channel updated.')
      } else {
        const candidate =
          type === 'WEBHOOK'
            ? {
                type,
                url,
                secret: secret.trim() === '' ? undefined : secret.trim(),
                headers: built.headers ?? {},
                isActive,
              }
            : type === 'SLACK'
              ? { type, slackWebhookUrl, isActive }
              : type === 'EMAIL'
                ? { type, emailAddress, isActive }
                : type === 'PAGERDUTY'
                  ? { type, routingKey, isActive }
                  : { type, teamsWebhookUrl, isActive }

        const parsed = notificationChannelFormSchema.safeParse(candidate)
        if (!parsed.success) {
          setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
          return
        }
        await createMutation.mutateAsync(parsed.data as CreateNotificationChannelRequest)
        toast.success('Channel added.')
      }
      resetForm()
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the channel.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    const destination = channelDestination(deleteTarget)
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success(`Removed ${destination}.`)
      if (editing?.id === deleteTarget.id) {
        resetForm()
      }
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(deleteError instanceof ApiError ? deleteError.message : 'Could not delete the channel.')
    }
  }

  const handleToggle = async (channel: NotificationChannel, next: boolean) => {
    try {
      await updateMutation.mutateAsync({ id: channel.id, data: { isActive: next } })
      toast.success(next ? 'Channel enabled.' : 'Channel disabled.')
    } catch (toggleError) {
      toast.error(toggleError instanceof ApiError ? toggleError.message : 'Could not update the channel.')
    }
  }

  const handleTest = async (channel: NotificationChannel) => {
    setTestingId(channel.id)
    try {
      const result = await testMutation.mutateAsync(channel.id)
      if (result.status === 'SUCCESS') {
        toast.success(result.message)
      } else {
        toast.error(result.message)
      }
    } catch (testError) {
      toast.error(testError instanceof ApiError ? testError.message : 'Could not send the test notification.')
    } finally {
      setTestingId(null)
    }
  }

  const updateHeaderRow = (index: number, patch: Partial<HeaderRow>) => {
    setHeaderRows((rows) => rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)))
  }

  const DestinationIcon = TYPE_ICONS[type]
  const destination = DESTINATION_FIELDS[type]
  const currentDestination = editing ? channelDestination(editing) : null
  const existingHeaderNames = editing?.webhookHeaderNames ?? []

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Notification channels</DialogTitle>
          <DialogDescription>
            When an alert opens, is acknowledged or recovers, the platform delivers a message to every active
            webhook, Slack, Teams, PagerDuty and email destination.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-2 flex justify-end">
          <Button asChild variant="link" size="sm">
            <Link to="/app/notification-deliveries" onClick={() => onOpenChange(false)}>
              <History className="h-4 w-4" aria-hidden="true" />
              View delivery log
            </Link>
          </Button>
        </div>

        <div className="mt-4 space-y-6">
          <div className="space-y-2">
            {isLoading ? (
              <div className="space-y-2" aria-busy="true">
                <Skeleton className="h-12 w-full rounded-lg" />
                <Skeleton className="h-12 w-full rounded-lg" />
              </div>
            ) : isError ? (
              <p role="alert" className="text-sm text-red-400">
                Could not load notification channels.
              </p>
            ) : channels.length === 0 ? (
              <p className="text-sm text-slate-400">No channels yet. Add one below.</p>
            ) : (
              channels.map((channel) => {
                const destinationText = channelDestination(channel)
                return (
                  <div
                    key={channel.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5"
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <Badge variant="muted">{notificationChannelTypeLabel(channel.type)}</Badge>
                      <span className="min-w-0 break-all text-sm text-slate-300">{destinationText}</span>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <Switch
                        checked={channel.isActive}
                        onCheckedChange={(next) => void handleToggle(channel, next)}
                        disabled={updateMutation.isPending}
                        aria-label={channel.isActive ? `Disable ${destinationText}` : `Enable ${destinationText}`}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`Send test notification to ${destinationText}`}
                        disabled={testingId === channel.id}
                        onClick={() => void handleTest(channel)}
                      >
                        <Send className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`Edit ${destinationText}`}
                        onClick={() => startEdit(channel)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="text-red-300 hover:text-red-200"
                        aria-label={`Delete ${destinationText}`}
                        onClick={() => setDeleteTarget(channel)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                )
              })
            )}
          </div>

          <form
            className="space-y-4 rounded-lg border border-white/10 bg-white/[0.02] p-4"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              void handleSubmit()
            }}
          >
            <div className="space-y-1.5">
              <Label htmlFor="channel-type">Channel type</Label>
              <Select
                value={type}
                onValueChange={(value) => {
                  setType(value as NotificationChannelType)
                  setError(null)
                }}
              >
                <SelectTrigger id="channel-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {NOTIFICATION_CHANNEL_TYPES.map((value) => (
                    <SelectItem key={value} value={value}>
                      {notificationChannelTypeLabel(value)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {currentDestination ? (
              <p className="text-xs text-slate-500">
                Current destination: <span className="break-all text-slate-400">{currentDestination}</span>
              </p>
            ) : null}

            {type === 'WEBHOOK' ? (
              <FormField
                label={editing ? 'New webhook URL (leave blank to keep current)' : 'New webhook URL'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={url}
                onChange={(event) => setUrl(event.target.value)}
                hint={destination.hint}
              />
            ) : type === 'SLACK' ? (
              <FormField
                label={editing ? 'New Slack webhook URL (leave blank to keep current)' : 'New Slack webhook URL'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={slackWebhookUrl}
                onChange={(event) => setSlackWebhookUrl(event.target.value)}
                hint={destination.hint}
              />
            ) : type === 'EMAIL' ? (
              <FormField
                label={editing ? 'Email address' : 'New email address'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={emailAddress}
                onChange={(event) => setEmailAddress(event.target.value)}
                hint={destination.hint}
              />
            ) : type === 'PAGERDUTY' ? (
              <FormField
                label={editing ? 'New integration key (leave blank to keep current)' : 'Integration key'}
                icon={DestinationIcon}
                autoComplete="off"
                type="password"
                placeholder={destination.placeholder}
                value={routingKey}
                onChange={(event) => setRoutingKey(event.target.value)}
                hint={destination.hint}
              />
            ) : (
              <FormField
                label={editing ? 'New Teams webhook URL (leave blank to keep current)' : 'New Teams webhook URL'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={teamsWebhookUrl}
                onChange={(event) => setTeamsWebhookUrl(event.target.value)}
                hint={destination.hint}
              />
            )}

            {type === 'WEBHOOK' ? (
              <div className="space-y-3 rounded-md border border-white/5 bg-black/10 p-3">
                <FormField
                  label={editing ? 'New signing secret (leave blank to keep current)' : 'Signing secret (optional)'}
                  icon={Lock}
                  type="password"
                  autoComplete="new-password"
                  placeholder="Used to compute X-Signature-256"
                  value={secret}
                  onChange={(event) => setSecret(event.target.value)}
                  hint="When set, every request is signed with HMAC-SHA256 over the exact JSON body."
                />

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <p className="text-sm text-slate-200">Static headers (optional)</p>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={headerRows.length >= MAX_WEBHOOK_HEADERS}
                      onClick={() => setHeaderRows((rows) => [...rows, { name: '', value: '' }])}
                    >
                      <Plus className="h-4 w-4" aria-hidden="true" />
                      Add header
                    </Button>
                  </div>
                  {existingHeaderNames.length > 0 ? (
                    <p className="text-xs text-slate-500">
                      Currently configured: {existingHeaderNames.join(', ')}. Saving new headers replaces the set.
                    </p>
                  ) : (
                    <p className="text-xs text-slate-500">
                      Up to {MAX_WEBHOOK_HEADERS} headers. Platform-managed headers are not allowed.
                    </p>
                  )}
                  {headerRows.map((row, index) => (
                    <div key={index} className="flex items-start gap-2">
                      <FormField
                        label="Header name"
                        icon={Webhook}
                        autoComplete="off"
                        placeholder="X-Custom-Header"
                        value={row.name}
                        onChange={(event) => updateHeaderRow(index, { name: event.target.value })}
                      />
                      <FormField
                        label="Value"
                        icon={Lock}
                        autoComplete="off"
                        type="password"
                        placeholder="Header value"
                        value={row.value}
                        onChange={(event) => updateHeaderRow(index, { value: event.target.value })}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label="Remove header"
                        className="mt-6"
                        onClick={() => setHeaderRows((rows) => rows.filter((_, rowIndex) => rowIndex !== index))}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            <div className="flex items-center justify-between">
              <label htmlFor="channel-active" className="text-sm text-slate-200">
                Active
              </label>
              <Switch id="channel-active" checked={isActive} onCheckedChange={setIsActive} />
            </div>

            {error ? (
              <p role="alert" className="text-sm text-red-400">
                {error}
              </p>
            ) : null}

            <div className="flex justify-end gap-2">
              {editing ? (
                <Button type="button" variant="secondary" onClick={resetForm} disabled={isSubmitting}>
                  Cancel edit
                </Button>
              ) : null}
              <Button type="submit" disabled={isSubmitting}>
                {!editing ? <Plus className="h-4 w-4" aria-hidden="true" /> : null}
                {isSubmitting ? 'Saving…' : editing ? 'Save channel' : 'Add channel'}
              </Button>
            </div>
          </form>
        </div>
      </DialogContent>

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(next) => {
          if (!next) {
            setDeleteTarget(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete channel</AlertDialogTitle>
            <AlertDialogDescription>
              This removes “{deleteTarget ? channelDestination(deleteTarget) : ''}”. Alerts will no longer be delivered
              to it.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={(event) => {
                event.preventDefault()
                void handleDelete()
              }}
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Dialog>
  )
}
