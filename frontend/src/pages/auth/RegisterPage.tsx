import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Mail, User } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { register as registerAccount } from '../../api/auth'
import { FormField, PasswordField } from '../../components/auth/FormField'
import { OAuthButtons } from '../../components/auth/OAuthButtons'
import { Logo } from '../../components/shared/icons'
import { Button } from '../../components/ui/button'
import { getAuthErrorMessage } from '../../features/auth/errors'
import { registerSchema, type RegisterFormValues } from '../../features/auth/schemas'
import { useAuthStore } from '../../store/authStore'

export default function RegisterPage() {
  const navigate = useNavigate()
  const signIn = useAuthStore((state) => state.login)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { username: '', email: '', password: '', confirmPassword: '' },
  })

  const mutation = useMutation({
    mutationFn: registerAccount,
    onSuccess: (data) => {
      signIn(data.token)
      reset()
      toast.success('Account created. Welcome aboard!')
      navigate('/app', { replace: true })
    },
    onError: (error) => {
      toast.error(getAuthErrorMessage(error, 'register'))
    },
  })

  const onSubmit = handleSubmit((values) => {
    mutation.mutate({
      username: values.username,
      email: values.email,
      password: values.password,
    })
  })

  return (
    <div className="noise relative min-h-screen bg-[#05070d]">
      <div className="bg-grid pointer-events-none absolute inset-0 opacity-40" aria-hidden="true" />
      <div className="orb left-[-10%] top-[-10%] h-[360px] w-[360px] bg-cyan-500/25" />
      <div className="orb bottom-[-12%] right-[-10%] h-[420px] w-[420px] bg-violet-600/25" />

      <div className="relative mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-12">
        <Link to="/" className="mb-8 flex items-center gap-2.5 self-center">
          <Logo />
          <span className="font-display text-lg font-semibold tracking-tight text-slate-100">
            Vibe<span className="text-cyan-300">Monitor</span>
          </span>
        </Link>

        <div className="gradient-border relative overflow-hidden rounded-2xl p-6 sm:p-8">
          <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-300/60 to-transparent" />
          <header className="mb-6 space-y-1.5">
            <h1 className="font-display text-2xl font-semibold text-slate-100">Create your account</h1>
            <p className="text-sm text-slate-400">Start monitoring your infrastructure in minutes.</p>
          </header>

          <form className="space-y-5" onSubmit={onSubmit} noValidate>
            <div className="space-y-4">
              <FormField
                label="Username"
                icon={User}
                autoComplete="username"
                placeholder="jane.doe"
                hint="3–50 characters: letters, numbers, dot, dash or underscore."
                error={errors.username?.message}
                {...register('username')}
              />
              <FormField
                label="Email"
                icon={Mail}
                type="email"
                autoComplete="email"
                placeholder="jane@company.com"
                error={errors.email?.message}
                {...register('email')}
              />
              <PasswordField
                label="Password"
                autoComplete="new-password"
                placeholder="••••••••"
                hint="At least 8 characters."
                error={errors.password?.message}
                {...register('password')}
              />
              <PasswordField
                label="Confirm password"
                autoComplete="new-password"
                placeholder="••••••••"
                error={errors.confirmPassword?.message}
                {...register('confirmPassword')}
              />
            </div>

            <OAuthButtons mode="sign-up" />

            <Button type="submit" className="w-full" disabled={mutation.isLoading}>
              {mutation.isLoading ? 'Creating account…' : 'Create account'}
            </Button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-slate-400">
          Already have an account?{' '}
          <Link to="/login" className="text-cyan-300 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
