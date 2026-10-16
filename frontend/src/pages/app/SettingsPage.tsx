import { zodResolver } from '@hookform/resolvers/zod'
import { AtSign, User } from 'lucide-react'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import { FormField, PasswordField } from '../../components/auth/FormField'
import { Button } from '../../components/ui/button'
import { changePasswordSchema, type ChangePasswordFormValues } from '../../features/auth/schemas'
import { useProfile } from '../../features/auth/useProfile'

interface ProfileFormValues {
  username: string
  email: string
}

export default function SettingsPage() {
  const { data: profile, isLoading, isError, error, isFetching, refetch } = useProfile()

  const profileForm = useForm<ProfileFormValues>({ defaultValues: { username: '', email: '' } })
  const { reset: resetProfile } = profileForm

  useEffect(() => {
    if (profile) {
      resetProfile({ username: profile.username, email: profile.email ?? '' })
    }
  }, [profile, resetProfile])

  const passwordForm = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const onSubmitPassword = passwordForm.handleSubmit(async () => {
    // Mock: the password-change endpoint is not implemented on the backend yet.
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

  return (
    <section className="space-y-6">
      <SettingsHeading />

      <div className="rounded-2xl border border-white/10 bg-[#0b101b]/60 p-6">
        <header>
          <h2 className="font-display text-lg font-semibold text-slate-100">Profile</h2>
          <p className="mt-1 text-sm text-slate-400">Account details loaded from the platform.</p>
        </header>
        <form className="mt-6 grid gap-4 sm:grid-cols-2" noValidate onSubmit={(event) => event.preventDefault()}>
          <FormField
            label="Username"
            icon={User}
            readOnly
            disabled
            autoComplete="username"
            error={profileForm.formState.errors.username?.message}
            {...profileForm.register('username')}
          />
          <FormField
            label="Email"
            icon={AtSign}
            type="email"
            readOnly
            disabled
            autoComplete="email"
            error={profileForm.formState.errors.email?.message}
            {...profileForm.register('email')}
          />
        </form>
        <p className="mt-3 text-xs text-slate-500">Profile editing is not available yet.</p>
      </div>

      <div className="rounded-2xl border border-white/10 bg-[#0b101b]/60 p-6">
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
      <p className="text-sm text-slate-400">Manage your account details.</p>
    </header>
  )
}

function SettingsSkeleton() {
  return (
    <section className="space-y-6" aria-busy="true">
      <SettingsHeading />
      {[0, 1].map((index) => (
        <div key={index} className="h-40 animate-pulse rounded-2xl border border-white/10 bg-white/[0.03]" />
      ))}
    </section>
  )
}
