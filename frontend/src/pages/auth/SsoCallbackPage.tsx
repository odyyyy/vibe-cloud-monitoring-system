import { AlertCircle, Loader2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { AuthShell } from '../../components/auth/AuthShell'
import { AuthStatusPanel } from '../../components/auth/AuthStatusPanel'
import { Button } from '../../components/ui/button'
import { getSsoLoginUrl, parseSsoToken } from '../../features/auth/sso'
import { useAuthStore } from '../../store/authStore'

export default function SsoCallbackPage() {
  const navigate = useNavigate()
  const signIn = useAuthStore((state) => state.login)

  const [failed, setFailed] = useState(false)
  // The token is single-use and comes from the URL, so it must be consumed exactly once
  // even though React StrictMode mounts the component twice in development.
  const handled = useRef(false)

  useEffect(() => {
    if (handled.current) {
      return
    }
    handled.current = true

    const token = parseSsoToken(window.location.hash)
    // Strip the token from the address bar immediately so it is not kept in history or
    // leaked when the URL is copied or shared.
    if (window.location.hash) {
      window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
    }

    if (!token) {
      setFailed(true)
      return
    }

    signIn(token)
    toast.success('Signed in successfully')
    navigate('/app', { replace: true })
  }, [navigate, signIn])

  return (
    <AuthShell
      title="Signing you in"
      subtitle="Completing single sign-on."
      footer={
        <>
          Back to{' '}
          <Link to="/login" className="text-cyan-300 hover:underline">
            sign in
          </Link>
        </>
      }
    >
      {failed ? (
        <div className="space-y-5">
          <AuthStatusPanel
            icon={<AlertCircle className="h-6 w-6 text-red-400" aria-hidden="true" />}
            title="Single sign-on failed"
            description="We could not complete the sign-in. Please try again or use your password."
          />
          <Button className="w-full" onClick={() => window.location.assign(getSsoLoginUrl())}>
            Try again
          </Button>
        </div>
      ) : (
        <div className="flex items-center gap-3 text-sm text-slate-300" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-cyan-300" aria-hidden="true" />
          Completing sign-in…
        </div>
      )}
    </AuthShell>
  )
}
