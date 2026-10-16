import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { User } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { login } from '../../api/auth'
import { FormField, PasswordField } from '../../components/auth/FormField'
import { OAuthButtons } from '../../components/auth/OAuthButtons'
import { Logo } from '../../components/shared/icons'
import { Button } from '../../components/ui/button'
import { getAuthErrorMessage } from '../../features/auth/errors'
import { loginSchema, type LoginFormValues } from '../../features/auth/schemas'
import { useAuthStore } from '../../store/authStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const signIn = useAuthStore((state) => state.login)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  })

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      signIn(data.token)
      reset()
      toast.success('Signed in successfully')
      navigate('/app', { replace: true })
    },
    onError: (error) => {
      toast.error(getAuthErrorMessage(error, 'login'))
    },
  })

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(values)
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
            <h1 className="font-display text-2xl font-semibold text-slate-100">Welcome back</h1>
            <p className="text-sm text-slate-400">Sign in to your VibeMonitor workspace.</p>
          </header>

          <form className="space-y-5" onSubmit={onSubmit} noValidate>
            <div className="space-y-4">
              <FormField
                label="Username"
                icon={User}
                autoComplete="username"
                placeholder="jane.doe"
                error={errors.username?.message}
                {...register('username')}
              />
              <PasswordField
                label="Password"
                autoComplete="current-password"
                placeholder="••••••••"
                error={errors.password?.message}
                {...register('password')}
              />
            </div>

            <OAuthButtons mode="sign-in" />

            <Button type="submit" className="w-full" disabled={mutation.isLoading}>
              {mutation.isLoading ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-slate-400">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="text-cyan-300 hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}
