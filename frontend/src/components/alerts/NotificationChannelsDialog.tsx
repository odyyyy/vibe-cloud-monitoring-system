import { useEffect, useState } from 'react'
import { Pencil, Plus, Trash2, Webhook } from 'lucide-react'
import { toast } from 'sonner'
import type { NotificationChannel } from '../../api/alerts'
import {
  useCreateNotificationChannelMutation,
  useDeleteNotificationChannelMutation,
  useNotificationChannelsQuery,
  useUpdateNotificationChannelMutation,
} from '../../api/alerts'
import { ApiError } from '../../api/client'
import { notificationChannelFormSchema } from '../../features/alerts/schemas'
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
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../ui/dialog'
import { FormField } from '../auth/FormField'
import { Skeleton } from '../ui/skeleton'
import { Switch } from '../ui/switch'

interface NotificationChannelsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function NotificationChannelsDialog({ open, onOpenChange }: NotificationChannelsDialogProps) {
  const { data, isLoading, isError } = useNotificationChannelsQuery()
  const createMutation = useCreateNotificationChannelMutation()
  const updateMutation = useUpdateNotificationChannelMutation()
  const deleteMutation = useDeleteNotificationChannelMutation()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const channels = data ?? []
  const [editing, setEditing] = useState<NotificationChannel | null>(null)
  const [url, setUrl] = useState('')
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<NotificationChannel | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setEditing(null)
    setUrl('')
    setIsActive(true)
    setError(null)
    setDeleteTarget(null)
  }, [open])

  const startEdit = (channel: NotificationChannel) => {
    setEditing(channel)
    setUrl(channel.url)
    setIsActive(channel.isActive)
    setError(null)
  }

  const resetForm = () => {
    setEditing(null)
    setUrl('')
    setIsActive(true)
    setError(null)
  }

  const handleSubmit = async () => {
    const parsed = notificationChannelFormSchema.safeParse({ type: 'WEBHOOK' as const, url, isActive })
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    setError(null)
    try {
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, data: parsed.data })
        toast.success('Webhook updated.')
      } else {
        await createMutation.mutateAsync(parsed.data)
        toast.success('Webhook added.')
      }
      resetForm()
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the webhook.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Webhook deleted.')
      if (editing?.id === deleteTarget.id) {
        resetForm()
      }
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(deleteError instanceof ApiError ? deleteError.message : 'Could not delete the webhook.')
    }
  }

  const handleToggle = async (channel: NotificationChannel, next: boolean) => {
    try {
      await updateMutation.mutateAsync({ id: channel.id, data: { isActive: next } })
      toast.success(next ? 'Webhook enabled.' : 'Webhook disabled.')
    } catch (toggleError) {
      toast.error(toggleError instanceof ApiError ? toggleError.message : 'Could not update the webhook.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Notification channels</DialogTitle>
          <DialogDescription>
            When an alert fires or recovers, an HTTPS POST is sent to each active webhook.
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
              <p className="text-sm text-slate-400">No webhooks yet. Add one below.</p>
            ) : (
              channels.map((channel) => (
                <div
                  key={channel.id}
                  className="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5"
                >
                  <span className="min-w-0 break-all text-sm text-slate-300">{channel.url}</span>
                  <div className="flex shrink-0 items-center gap-1">
                    <Switch
                      checked={channel.isActive}
                      onCheckedChange={(next) => void handleToggle(channel, next)}
                      disabled={updateMutation.isPending}
                      aria-label={channel.isActive ? `Disable ${channel.url}` : `Enable ${channel.url}`}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={`Edit ${channel.url}`}
                      onClick={() => startEdit(channel)}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="text-red-300 hover:text-red-200"
                      aria-label={`Delete ${channel.url}`}
                      onClick={() => setDeleteTarget(channel)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))
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
            <FormField
              label={editing ? 'Edit webhook URL' : 'New webhook URL'}
              icon={Webhook}
              autoComplete="off"
              placeholder="https://hooks.example.com/alerts"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              hint="Must be an absolute https:// URL. Internal, loopback and private addresses are rejected."
            />

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
                {isSubmitting ? 'Saving…' : editing ? 'Save webhook' : 'Add webhook'}
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
            <AlertDialogTitle>Delete webhook</AlertDialogTitle>
            <AlertDialogDescription>
              This removes “{deleteTarget?.url}”. Alerts will no longer be delivered to it.
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
