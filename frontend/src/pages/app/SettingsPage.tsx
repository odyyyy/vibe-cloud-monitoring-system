import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AtSign, CheckCircle2, User } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import { checkUsernameAvailable, requestEmailChange, updateUsername } from '../../api/profile'
import { FormField, PasswordField } from '../../components/auth/FormField'
import { Button } from '../../components/ui/button'
import { changePasswordSchema, type ChangePasswordFormValues } from '../../features/auth/schemas'
import { useProfile } from '../../features/auth/useProfile'
import {
  updateEmailSchema,
  updateUsernameSchema,
  type UpdateEmailFormValues,
  type UpdateUsernameFormValues,
} from '../../features/profile/schemas'
import { THEMES } from '../../features/theme/themes'
import { cn } from '../../lib/utils'
import { useAuthStore } from '../../store/authStore'
import { useThemeStore } from '../../store/themeStore'

const AVAILABILITY_DEBOUNCE_MS = 400

export default function SettingsPage() {
  const token = useAuthStore((state) => state.token)
  const setUser = useAuthStore((state) => state.setUser)
  const queryClient = useQueryClient()

  const { data: profile, isLoading, isError, error, isFetching, refetch } = useProfile()

  // --- appearance ---
  const theme = useThemeStore((state) => state.theme)
  const setTheme = useThemeStore((state) => state.setTheme)

  // --- username ---
  const usernameForm = useForm<UpdateUsernameFormValues>({
    resolver: zodResolver(updateUsernameSchema),
    defaultValues: { username: '', currentPassword: '' },
  })
  const watchedUsername = usernameForm.watch('username')
  const [debouncedUsername, setDebouncedUsername] = useState('')

  useEffect(() => {
    const handle = window.setTimeout(() => setDebouncedUsername(watchedUsername), AVAILABILITY_DEBOUNCE_MS)
    return () => window.clearTimeout(handle)
  }, [watchedUsername])

  const usernameIsValid = updateUsernameSchema.shape.username.safeParse(debouncedUsername).success
  const usernameDiffers = debouncedUsername.length > 0 && debouncedUsername !== (profile?.username ?? '')

  const availabilityQuery = useQuery({
    queryKey: ['username-available', debouncedUsername],
    queryFn: () => checkUsernameAvailable(token as string, debouncedUsername),
    enabled: Boolean(token) && usernameIsValid && usernameDiffers,
    staleTime: 30_000,
    retry: false,
  })

  const usernameTaken = availabilityQuery.data?.available === false

  const onSubmitUsername = usernameForm.handleSubmit(async (values) => {
    if (!token) {
      return
    }
    try {
      const updated = await updateUsername(token, values)
      queryClient.setQueryData(['auth', 'me', token], updated)
      setUser({ id: updated.id, username: updated.username, email: updated.email, avatarUrl: updated.avatarUrl })
      usernameForm.reset({ username: updated.username, currentPassword: '' })
      toast.success('Username updated.')
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        usernameForm.setError('username', { message: err.message })
      } else if (err instanceof ApiError && err.status === 401) {
        usernameForm.setError('currentPassword', { message: 'Incorrect password.' })
      } else {
        toast.error(err instanceof ApiError ? err.message : 'Could not update your username.')
      }
    }
  })

  // --- email ---
  const emailForm = useForm<UpdateEmailFormValues>({
    resolver: zodResolver(updateEmailSchema),
    defaultValues: { newEmail: '', currentPassword: '' },
  })

  const onSubmitEmail = emailForm.handleSubmit(async (values) => {
    if (!token) {
      return
    }
    try {
      const response = await requestEmailChange(token, values)
      emailForm.reset({ newEmail: '', currentPassword: '' })
      toast.success(response.message)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        emailForm.setError('newEmail', { message: err.message })
      } else if (err instanceof ApiError && err.status === 401) {
        emailForm.setError('currentPassword', { message: 'Incorrect password.' })
      } else {
        toast.error(err instanceof ApiError ? err.message : 'Could not start the email change.')
      }
    }
  })

  // --- password (still a preview; the endpoint is not implemented yet) ---
  const passwordForm = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const onSubmitPassword = passwordForm.handleSubmit(async () => {
    await new Promise((resolve) => setTimeout(resolve, 600))
    passwordForm.reset()
    toast.info('Password changes are not available yet.')
  })

  const unauthorized = error instanceof ApiError && error.status === 401

  if (isLoading || unauthorized) {
    return <SettingsSkeleton />
  }

  if (isError) {
    return (
      <section className="space-y-6">
        <SettingsHeading />
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load your profile</h2>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'}
          </p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      </section>
    )
  }

  const usernameHint = !usernameDiffers ? (
    <span>This is your current username.</span>
  ) : !usernameIsValid ? undefined : availabilityQuery.isFetching ? (
    <span>Checking availability…</span>
  ) : usernameTaken ? (
    <span className="text-amber-300">That username is already taken.</span>
  ) : availabilityQuery.data?.available ? (
    <span className="flex items-center gap-1.5 text-emerald-400">
      <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
      That username is available.
    </span>
  ) : undefined

  return (
    <section className="space-y-6">
      <SettingsHeading />

      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header>
          <h2 className="font-display text-lg font-semibold text-slate-100">Appearance</h2>
          <p className="mt-1 text-sm text-slate-400">Choose the console color theme. Your choice is saved on this device.</p>
        </header>
        <div role="radiogroup" aria-label="Theme" className="mt-6 grid gap-3 sm:grid-cols-3">
          {THEMES.map((option) => {
            const selected = option.id === theme
            return (
              <button
                key={option.id}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => setTheme(option.id)}
                className={cn(
                  'rounded-xl border p-4 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50',
                  selected
                    ? 'border-cyan-400/60 bg-cyan-400/5'
                    : 'border-white/10 bg-white/[0.02] hover:border-white/20',
                )}
              >
                <span className="flex items-center gap-2">
                  <span className="flex items-center gap-1" aria-hidden="true">
                    <span className="h-4 w-4 rounded-full" style={{ background: option.canvas }} />
                    <span className="h-4 w-4 rounded-full" style={{ background: option.accent }} />
                  </span>
                  <span className="text-sm font-medium text-slate-100">{option.label}</span>
                </span>
                <span className="mt-2 block text-xs text-slate-400">{option.description}</span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header>
          <h2 className="font-display text-lg font-semibold text-slate-100">Profile</h2>
          <p className="mt-1 text-sm text-slate-400">
            Signed in as <span className="text-slate-200">{profile?.username}</span>
            {profile?.email ? <> · {profile.email}</> : null}
          </p>
        </header>

        <form className="mt-6 max-w-md space-y-4" noValidate onSubmit={onSubmitUsername}>
          <FormField
            label="Username"
            icon={User}
            autoComplete="username"
            error={usernameForm.formState.errors.username?.message}
            hint={usernameHint}
            {...usernameForm.register('username')}
          />
          <PasswordField
            label="Current password"
            autoComplete="current-password"
            error={usernameForm.formState.errors.currentPassword?.message}
            {...usernameForm.register('currentPassword')}
          />
          <Button type="submit" disabled={usernameForm.formState.isSubmitting || usernameTaken}>
            {usernameForm.formState.isSubmitting ? 'Saving…' : 'Change username'}
          </Button>
        </form>

        <form className="mt-8 max-w-md space-y-4 border-t border-white/10 pt-6" noValidate onSubmit={onSubmitEmail}>
          <FormField
            label="New email"
            icon={AtSign}
            type="email"
            autoComplete="email"
            error={emailForm.formState.errors.newEmail?.message}
            hint={<span>We send a confirmation link to the new address before it takes effect.</span>}
            {...emailForm.register('newEmail')}
          />
          <PasswordField
            label="Current password"
            autoComplete="current-password"
            error={emailForm.formState.errors.currentPassword?.message}
            {...emailForm.register('currentPassword')}
          />
          <Button type="submit" variant="outline" disabled={emailForm.formState.isSubmitting}>
            {emailForm.formState.isSubmitting ? 'Sending…' : 'Send confirmation link'}
          </Button>
        </form>
      </div>

      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header>
          <h2 className="font-display text-lg font-semibold text-slate-100">Change password</h2>
          <p className="mt-1 text-sm text-slate-400">
            This form is a preview and is not wired to the backend yet.
          </p>
        </header>
        <form className="mt-6 max-w-md space-y-4" noValidate onSubmit={onSubmitPassword}>
          <PasswordField
            label="Current password"
            autoComplete="current-password"
            error={passwordForm.formState.errors.currentPassword?.message}
            {...passwordForm.register('currentPassword')}
          />
          <PasswordField
            label="New password"
            autoComplete="new-password"
            error={passwordForm.formState.errors.newPassword?.message}
            {...passwordForm.register('newPassword')}
          />
          <PasswordField
            label="Confirm new password"
            autoComplete="new-password"
            error={passwordForm.formState.errors.confirmPassword?.message}
            {...passwordForm.register('confirmPassword')}
          />
          <Button type="submit" disabled={passwordForm.formState.isSubmitting}>
            {passwordForm.formState.isSubmitting ? 'Updating…' : 'Update password'}
          </Button>
        </form>
      </div>
    </section>
  )
}

function SettingsHeading() {
  return (
    <header className="space-y-2">
      <p className="section-tag">Settings</p>
      <h1 className="font-display text-2xl font-semibold text-slate-100">Profile &amp; settings</h1>
      <p className="text-sm text-slate-400">Manage your account details and appearance.</p>
    </header>
  )
}

function SettingsSkeleton() {
  return (
    <section className="space-y-6" aria-busy="true">
      <SettingsHeading />
      {[0, 1, 2].map((index) => (
        <div key={index} className="h-40 animate-pulse rounded-2xl border border-white/10 bg-white/[0.03]" />
      ))}
    </section>
  )
}
