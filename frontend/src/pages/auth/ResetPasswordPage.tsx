import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { AlertCircle } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { resetPassword } from '../../api/auth'
import { AuthShell } from '../../components/auth/AuthShell'
import { AuthStatusPanel } from '../../components/auth/AuthStatusPanel'
import { PasswordField } from '../../components/auth/FormField'
import { Button } from '../../components/ui/button'
import { getAuthErrorMessage } from '../../features/auth/errors'
import { resetPasswordSchema, type ResetPasswordFormValues } from '../../features/auth/schemas'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })

  const mutation = useMutation({
    mutationFn: (values: ResetPasswordFormValues) =>
      resetPassword({ token: token ?? '', newPassword: values.newPassword }),
    onSuccess: () => {
      toast.success('Password updated. Sign in with your new password.')
      navigate('/login', { replace: true })
    },
    onError: (error) => toast.error(getAuthErrorMessage(error, 'reset')),
  })

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(values)
  })

  return (
    <AuthShell
      title="Set a new password"
      subtitle="Choose a strong password you have not used before."
      footer={
        <>
          Back to{' '}
          <Link to="/login" className="text-cyan-300 hover:underline">
            sign in
          </Link>
        </>
      }
    >
      {!token ? (
        <div className="space-y-5">
          <AuthStatusPanel
            icon={<AlertCircle className="h-6 w-6 text-amber-300" aria-hidden="true" />}
            title="This link is incomplete"
            description="No reset token was found in the link. Request a new password reset email."
          />
          <Button variant="outline" className="w-full" onClick={() => navigate('/forgot-password', { replace: true })}>
            Request a new link
          </Button>
        </div>
      ) : (
        <form className="space-y-5" onSubmit={onSubmit} noValidate>
          <div className="space-y-4">
            <PasswordField
              label="New password"
              autoComplete="new-password"
              placeholder="••••••••"
              hint="At least 8 characters."
              error={errors.newPassword?.message}
              {...register('newPassword')}
            />
            <PasswordField
              label="Confirm password"
              autoComplete="new-password"
              placeholder="••••••••"
              error={errors.confirmPassword?.message}
              {...register('confirmPassword')}
            />
          </div>

          <Button type="submit" className="w-full" disabled={mutation.isLoading}>
            {mutation.isLoading ? 'Updating…' : 'Update password'}
          </Button>
        </form>
      )}
    </AuthShell>
  )
}
