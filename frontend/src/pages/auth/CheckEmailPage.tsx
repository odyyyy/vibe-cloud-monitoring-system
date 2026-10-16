import { useMutation } from '@tanstack/react-query'
import { MailCheck } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { resendVerification } from '../../api/auth'
import { AuthShell } from '../../components/auth/AuthShell'
import { Button } from '../../components/ui/button'
import { getAuthErrorMessage } from '../../features/auth/errors'

export default function CheckEmailPage() {
  const [searchParams] = useSearchParams()
  const email = searchParams.get('email') ?? ''

  const resend = useMutation({
    mutationFn: resendVerification,
    onSuccess: (data) => toast.success(data.message),
    onError: (error) => toast.error(getAuthErrorMessage(error, 'resend')),
  })

  return (
    <AuthShell
      title="Check your email"
      subtitle="We sent you a link to confirm your address before you can sign in."
      footer={
        <>
          Already confirmed?{' '}
          <Link to="/login" className="text-cyan-300 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <div className="space-y-5">
        <div className="flex items-start gap-3 rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-slate-300">
          <MailCheck className="mt-0.5 h-5 w-5 shrink-0 text-cyan-300" aria-hidden="true" />
          <p>
            {email ? (
              <>
                A confirmation link has been sent to{' '}
                <span className="font-medium text-slate-100">{email}</span>.
              </>
            ) : (
              'A confirmation link has been sent to your email address.'
            )}
          </p>
        </div>

        {email ? (
          <Button
            type="button"
            variant="outline"
            className="w-full"
            disabled={resend.isLoading}
            onClick={() => resend.mutate(email)}
          >
            {resend.isLoading ? 'Resending…' : 'Resend confirmation email'}
          </Button>
        ) : null}
      </div>
    </AuthShell>
  )
}
