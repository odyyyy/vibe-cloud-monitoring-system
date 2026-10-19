import { useEffect, useState } from 'react'
import { Mail, Pencil, Plus, Send, Slack, Trash2, Webhook } from 'lucide-react'
import { toast } from 'sonner'
import type { NotificationChannel, NotificationChannelType } from '../../api/alerts'
import {
  useCreateNotificationChannelMutation,
  useDeleteNotificationChannelMutation,
  useNotificationChannelsQuery,
  useTestNotificationChannelMutation,
  useUpdateNotificationChannelMutation,
} from '../../api/alerts'
import { ApiError } from '../../api/client'
import {
  NOTIFICATION_CHANNEL_TYPES,
  notificationChannelFormSchema,
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

const TYPE_ICONS = {
  WEBHOOK: Webhook,
  SLACK: Slack,
  EMAIL: Mail,
} as const

const DESTINATION_FIELDS: Record<
  NotificationChannelTypeValue,
  { label: string; placeholder: string; hint: string }
> = {
  WEBHOOK: {
    label: 'Webhook URL',
    placeholder: 'https://hooks.example.com/alerts',
    hint: 'Must be an absolute https:// URL. Internal, loopback and private addresses are rejected.',
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
}

/** Returns the single destination stored on a channel, regardless of its type. */
function channelDestination(channel: NotificationChannel): string {
  return channel.url ?? channel.slackWebhookUrl ?? channel.emailAddress ?? ''
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
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<NotificationChannel | null>(null)
  const [testingId, setTestingId] = useState<number | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setEditing(null)
    setType('WEBHOOK')
    setUrl('')
    setSlackWebhookUrl('')
    setEmailAddress('')
    setIsActive(true)
    setError(null)
    setDeleteTarget(null)
    setTestingId(null)
  }, [open])

  function resetForm() {
    setEditing(null)
    setType('WEBHOOK')
    setUrl('')
    setSlackWebhookUrl('')
    setEmailAddress('')
    setIsActive(true)
    setError(null)
  }

  const startEdit = (channel: NotificationChannel) => {
    setEditing(channel)
    setType(channel.type)
    setUrl(channel.url ?? '')
    setSlackWebhookUrl(channel.slackWebhookUrl ?? '')
    setEmailAddress(channel.emailAddress ?? '')
    setIsActive(channel.isActive)
    setError(null)
  }

  const handleSubmit = async () => {
    const parsed = notificationChannelFormSchema.safeParse(
      type === 'WEBHOOK'
        ? { type, url, isActive }
        : type === 'SLACK'
          ? { type, slackWebhookUrl, isActive }
          : { type, emailAddress, isActive },
    )
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    setError(null)
    try {
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, data: parsed.data })
        toast.success('Channel updated.')
      } else {
        await createMutation.mutateAsync(parsed.data)
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
      await testMutation.mutateAsync(channel.id)
      toast.success('Test notification queued.')
    } catch (testError) {
      toast.error(testError instanceof ApiError ? testError.message : 'Could not queue the test notification.')
    } finally {
      setTestingId(null)
    }
  }

  const DestinationIcon = TYPE_ICONS[type]
  const destination = DESTINATION_FIELDS[type]

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Notification channels</DialogTitle>
          <DialogDescription>
            When an alert fires or recovers, the platform delivers a message to every active webhook, Slack channel and
            email address.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-6 space-y-6">
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
                onValueChange={(value) => setType(value as NotificationChannelType)}
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

            {type === 'WEBHOOK' ? (
              <FormField
                label={editing ? 'Edit webhook URL' : 'New webhook URL'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={url}
                onChange={(event) => setUrl(event.target.value)}
                hint={destination.hint}
              />
            ) : type === 'SLACK' ? (
              <FormField
                label={editing ? 'Edit Slack webhook URL' : 'New Slack webhook URL'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={slackWebhookUrl}
                onChange={(event) => setSlackWebhookUrl(event.target.value)}
                hint={destination.hint}
              />
            ) : (
              <FormField
                label={editing ? 'Edit email address' : 'New email address'}
                icon={DestinationIcon}
                autoComplete="off"
                placeholder={destination.placeholder}
                value={emailAddress}
                onChange={(event) => setEmailAddress(event.target.value)}
                hint={destination.hint}
              />
            )}

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
