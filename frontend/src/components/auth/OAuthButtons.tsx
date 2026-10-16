import { Chrome, Github } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '../ui/button'

interface OAuthButtonsProps {
  mode?: 'sign-in' | 'sign-up'
}

export function OAuthButtons({ mode = 'sign-in' }: OAuthButtonsProps) {
  const action = mode === 'sign-up' ? 'Sign up' : 'Sign in'

  const handleClick = (provider: string) => {
    toast.info('Coming soon', {
      description: `${action} with ${provider} will be available shortly.`,
    })
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3 text-[0.7rem] uppercase tracking-[0.2em] text-slate-500">
        <span className="h-px flex-1 bg-white/10" />
        or continue with
        <span className="h-px flex-1 bg-white/10" />
      </div>

      <div className="grid gap-3">
        <Button type="button" variant="outline" onClick={() => handleClick('GitHub')}>
          <Github className="h-4 w-4" aria-hidden="true" />
          {action} with GitHub
        </Button>
        <Button type="button" variant="outline" onClick={() => handleClick('Google')}>
          <Chrome className="h-4 w-4" aria-hidden="true" />
          {action} with Google
        </Button>
      </div>
    </div>
  )
}
