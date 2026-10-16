import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Mail, MailCheck } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { forgotPassword } from '../../api/auth'
import { AuthShell } from '../../components/auth/AuthShell'
import { AuthStatusPanel } from '../../components/auth/AuthStatusPanel'
import { FormField } from '../../components/auth/FormField'
import { Button } from '../../components/ui/button'
import { getAuthErrorMessage } from '../../features/auth/errors'
import { forgotPasswordSchema, type ForgotPasswordFormValues } from '../../features/auth/schemas'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  })

  const mutation = useMutation({
    mutationFn: forgotPassword,
    onSuccess: () => toast.success('If the account exists, a reset link is on its way.'),
    onError: (error) => toast.error(getAuthErrorMessage(error, 'forgot')),
  })

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(values.email)
  })

  return (
    <AuthShell
      title="Forgot your password?"
      subtitle="Enter your email and we will send you a reset link."
      footer={
        <>
          Remembered it?{' '}
          <Link to="/login" className="text-cyan-300 hover:underline">
            Back to sign in
          </Link>
        </>
      }
    >
      {mutation.isSuccess ? (
        <div className="space-y-5">
          <AuthStatusPanel
            icon={<MailCheck className="h-6 w-6 text-cyan-300" aria-hidden="true" />}
            title="Check your email"
            description={
              getValues('email')
                ? `If an account exists for ${getValues('email')}, we sent a password reset link.`
                : 'If an account exists for that address, we sent a password reset link.'
            }
          />
          <Button className="w-full" onClick={() => navigate('/login', { replace: true })}>
            Back to sign in
          </Button>
        </div>
      ) : (
        <form className="space-y-5" onSubmit={onSubmit} noValidate>
          <FormField
            label="Email"
            icon={Mail}
            type="email"
            autoComplete="email"
            placeholder="jane@company.com"
            error={errors.email?.message}
            {...register('email')}
          />

          <Button type="submit" className="w-full" disabled={mutation.isLoading}>
            {mutation.isLoading ? 'Sending…' : 'Send reset link'}
          </Button>
        </form>
      )}
    </AuthShell>
  )
}
