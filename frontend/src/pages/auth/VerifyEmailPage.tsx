import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { verifyEmail } from '../../api/auth'
import { AuthShell } from '../../components/auth/AuthShell'
import { AuthStatusPanel } from '../../components/auth/AuthStatusPanel'
import { Button } from '../../components/ui/button'
import { getAuthErrorMessage } from '../../features/auth/errors'

type VerifyStatus = 'loading' | 'success' | 'error'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')

  const [status, setStatus] = useState<VerifyStatus>('loading')
  const [error, setError] = useState<unknown>(null)
  // Confirmation tokens are single-use, so the request must fire exactly once per token.
  // A ref (not component state) survives React StrictMode's extra mount cycle.
  const requestedToken = useRef<string | null>(null)

  useEffect(() => {
    if (!token || requestedToken.current === token) {
      return
    }
    requestedToken.current = token
    setStatus('loading')
    setError(null)
    verifyEmail(token)
      .then(() => setStatus('success'))
      .catch((err: unknown) => {
        setError(err)
        setStatus('error')
      })
  }, [token])

  return (
    <AuthShell
      title="Confirm your email"
      subtitle="We are validating the confirmation link."
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
        <AuthStatusPanel
          icon={<AlertCircle className="h-6 w-6 text-amber-300" aria-hidden="true" />}
          title="This link is incomplete"
          description="No confirmation token was found in the link. Open the link from your email again."
        />
      ) : status === 'loading' ? (
        <div className="flex items-center gap-3 text-sm text-slate-300" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-cyan-300" aria-hidden="true" />
          Confirming your email…
        </div>
      ) : status === 'success' ? (
        <div className="space-y-5">
          <AuthStatusPanel
            icon={<CheckCircle2 className="h-6 w-6 text-emerald-400" aria-hidden="true" />}
            title="Email confirmed"
            description="Your address is confirmed. You can now sign in."
          />
          <Button className="w-full" onClick={() => navigate('/login', { replace: true })}>
            Go to sign in
          </Button>
        </div>
      ) : (
        <div className="space-y-5">
          <AuthStatusPanel
            icon={<AlertCircle className="h-6 w-6 text-red-400" aria-hidden="true" />}
            title="Confirmation failed"
            description={getAuthErrorMessage(error, 'resend')}
          />
          <Button variant="outline" className="w-full" onClick={() => navigate('/login', { replace: true })}>
            Back to sign in
          </Button>
        </div>
      )}
    </AuthShell>
  )
}
