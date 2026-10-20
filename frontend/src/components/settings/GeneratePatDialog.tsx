import { useEffect } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { KeyRound } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { CreatedPat } from '../../api/pats'
import { useCreatePatMutation } from '../../api/pats'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import {
  PAT_DEFAULT_EXPIRES_DAYS,
  PAT_EXPIRATION_OPTIONS,
  patFormSchema,
  type PatFormValues,
} from '../../features/pats/schemas'
import { groupedPatScopes } from '../../features/pats/scopes'

interface GeneratePatDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreated: (token: CreatedPat) => void
}

export function GeneratePatDialog({ open, onOpenChange, onCreated }: GeneratePatDialogProps) {
  const createMutation = useCreatePatMutation()

  const form = useForm<PatFormValues>({
    resolver: zodResolver(patFormSchema),
    defaultValues: { name: '', expiresDays: PAT_DEFAULT_EXPIRES_DAYS, scopes: [] },
  })
  const { reset, formState } = form

  useEffect(() => {
    if (open) {
      reset({ name: '', expiresDays: PAT_DEFAULT_EXPIRES_DAYS, scopes: [] })
    }
  }, [open, reset])

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const created = await createMutation.mutateAsync({
        name: values.name,
        expiresDays: values.expiresDays,
        scopes: values.scopes,
      })
      toast.success('Token created.')
      onCreated(created)
      onOpenChange(false)
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : 'Could not create the token. Please try again.'
      form.setError('root', { message })
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Generate personal access token</DialogTitle>
          <DialogDescription>
            Tokens act on your behalf, limited to the scopes you select and never beyond your current role.
          </DialogDescription>
        </DialogHeader>

        <form className="mt-6 space-y-4" noValidate onSubmit={onSubmit}>
          <FormField
            label="Name"
            icon={KeyRound}
            autoFocus
            autoComplete="off"
            placeholder="ci-deploy"
            error={formState.errors.name?.message}
            hint={<span>Use a name that describes where the token will be used.</span>}
            {...form.register('name')}
          />

          <Controller
            control={form.control}
            name="expiresDays"
            render={({ field }) => (
              <div className="space-y-1.5">
                <Label htmlFor="pat-expires">Expiration</Label>
                <Select value={String(field.value)} onValueChange={(value) => field.onChange(Number(value))}>
                  <SelectTrigger id="pat-expires">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PAT_EXPIRATION_OPTIONS.map((days) => (
                      <SelectItem key={days} value={String(days)}>
                        {days} days
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {formState.errors.expiresDays?.message ? (
                  <p className="text-xs text-red-400">{formState.errors.expiresDays.message}</p>
                ) : (
                  <p className="text-xs text-slate-500">The token stops working automatically after this period.</p>
                )}
              </div>
            )}
          />

          <fieldset className="space-y-3">
            <legend className="text-sm font-medium text-slate-200">Scopes</legend>
            <p className="text-xs text-slate-500">Select at least one scope. The token is denied everything else.</p>
            {groupedPatScopes().map((group) => (
              <div key={group.group} className="space-y-2">
                <p className="section-tag">{group.group}</p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {group.scopes.map((scope) => (
                    <label
                      key={scope.value}
                      className="flex cursor-pointer items-start gap-2 rounded-lg border border-white/10 bg-white/[0.02] p-2.5 text-sm hover:border-white/20"
                    >
                      <input
                        type="checkbox"
                        value={scope.value}
                        className="mt-0.5 h-4 w-4 rounded border-white/20 bg-canvas accent-cyan-400"
                        {...form.register('scopes')}
                      />
                      <span>
                        <span className="block text-slate-200">{scope.label}</span>
                        <span className="block text-xs text-slate-500">{scope.description}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
            {formState.errors.scopes?.message ? (
              <p role="alert" className="text-xs text-red-400">
                {formState.errors.scopes.message}
              </p>
            ) : null}
          </fieldset>

          {formState.errors.root?.message ? (
            <p role="alert" className="text-sm text-red-400">
              {formState.errors.root.message}
            </p>
          ) : null}

          <DialogFooter>
            <Button
              type="button"
              variant="secondary"
              onClick={() => onOpenChange(false)}
              disabled={createMutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Generating…' : 'Generate token'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
