import { useState } from 'react'
import { Copy, Eye, EyeOff } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '../../lib/utils'
import { maskApiKey } from '../../lib/apiKey'
import { copyText } from '../../lib/clipboard'
import { Button } from '../ui/button'

interface ApiKeyCellProps {
  apiKey: string
  className?: string
}

export function ApiKeyCell({ apiKey, className }: ApiKeyCellProps) {
  const [revealed, setRevealed] = useState(false)

  const handleCopy = async () => {
    const copied = await copyText(apiKey)
    if (copied) {
      toast.success('API key copied to clipboard.')
    } else {
      setRevealed(true)
      toast.info('Clipboard is unavailable. Reveal the key and copy it manually.')
    }
  }

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <code className="mono max-w-[14rem] truncate rounded-md border border-white/5 bg-white/[0.02] px-2 py-1 text-xs text-slate-300">
        {revealed ? apiKey : maskApiKey(apiKey)}
      </code>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-7 w-7"
        aria-label={revealed ? 'Hide API key' : 'Show API key'}
        onClick={() => setRevealed((value) => !value)}
      >
        {revealed ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-7 w-7"
        aria-label="Copy API key"
        onClick={handleCopy}
      >
        <Copy className="h-3.5 w-3.5" />
      </Button>
    </div>
  )
}
