import { useState } from 'react'
import { KeyRound, Plus, RefreshCw } from 'lucide-react'
import { ApiError } from '../../api/client'
import { usePatsQuery, type CreatedPat, type PatSummary } from '../../api/pats'
import { formatDateTime } from '../../lib/datetime'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { Skeleton } from '../ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'
import { GeneratePatDialog } from './GeneratePatDialog'
import { PatRevealDialog } from './PatRevealDialog'
import { RevokePatDialog } from './RevokePatDialog'
import { patScopeLabel } from '../../features/pats/scopes'

/**
 * Settings section that lets a user issue, review and revoke their own Personal Access Tokens.
 *
 * <p>Tokens are shown only once at creation time; the list never returns the secret. The
 * generate dialog surfaces loading, error and the one-time value, and revocation is guarded by
 * a hold-to-confirm action.
 */
export function PersonalAccessTokensSection() {
  const tokensQuery = usePatsQuery()

  const [generateOpen, setGenerateOpen] = useState(false)
  const [revealOpen, setRevealOpen] = useState(false)
  const [createdToken, setCreatedToken] = useState<CreatedPat | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<PatSummary | null>(null)

  const handleCreated = (token: CreatedPat) => {
    setCreatedToken(token)
    setRevealOpen(true)
  }

  const summaries = tokensQuery.data?.summaries ?? []

  return (
    <div id="personal-access-tokens" className="scroll-mt-24 rounded-2xl border border-white/10 bg-panel/60 p-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="font-display text-lg font-semibold text-slate-100">Personal Access Tokens</h2>
          <p className="mt-1 text-sm text-slate-400">
            Tokens allow scripts and agents to call the API without an interactive login. They are limited to the
            scopes you choose and never exceed your role in the organization.
          </p>
        </div>
        <Button type="button" onClick={() => setGenerateOpen(true)}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Generate token
        </Button>
      </header>

      <div className="mt-6">
        {tokensQuery.isLoading ? (
          <div className="space-y-2" aria-busy="true">
            {[0, 1, 2].map((index) => (
              <Skeleton key={index} className="h-11 w-full" />
            ))}
          </div>
        ) : tokensQuery.isError ? (
          <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/5 p-4">
            <p className="text-sm text-red-200">
              {tokensQuery.error instanceof ApiError
                ? tokensQuery.error.message
                : 'Could not load your tokens. Please try again.'}
            </p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-3"
              onClick={() => void tokensQuery.refetch()}
              disabled={tokensQuery.isFetching}
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              {tokensQuery.isFetching ? 'Retrying…' : 'Retry'}
            </Button>
          </div>
        ) : summaries.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-white/10 p-8 text-center">
            <KeyRound className="h-6 w-6 text-slate-500" aria-hidden="true" />
            <p className="text-sm text-slate-400">No active tokens.</p>
            <p className="text-xs text-slate-500">Generate one to automate API access.</p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Scopes</TableHead>
                <TableHead>Expires</TableHead>
                <TableHead>Last used</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {summaries.map((token) => (
                <TableRow key={token.id}>
                  <TableCell className="font-medium text-slate-200">{token.name}</TableCell>
                  <TableCell>
                    {token.scopes.length > 0 ? (
                      <div className="flex flex-wrap gap-1">
                        {token.scopes.map((scope) => (
                          <Badge key={scope} variant="muted">
                            {patScopeLabel(scope)}
                          </Badge>
                        ))}
                      </div>
                    ) : (
                      <span className="text-slate-500">—</span>
                    )}
                  </TableCell>
                  <TableCell>{token.expiresAt ? formatDateTime(token.expiresAt) : '—'}</TableCell>
                  <TableCell>{token.lastUsedAt ? formatDateTime(token.lastUsedAt) : 'Never'}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="border-red-500/40 bg-red-500/10 text-red-200 hover:bg-red-500/20 hover:text-red-100"
                      onClick={() => setRevokeTarget(token)}
                      aria-label={`Revoke token ${token.name}`}
                    >
                      Revoke
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <GeneratePatDialog open={generateOpen} onOpenChange={setGenerateOpen} onCreated={handleCreated} />
      <PatRevealDialog
        open={revealOpen}
        onOpenChange={(open) => {
          setRevealOpen(open)
          if (!open) {
            setCreatedToken(null)
          }
        }}
        rawToken={createdToken?.rawToken ?? null}
        tokenName={createdToken?.name}
      />
      <RevokePatDialog
        open={revokeTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRevokeTarget(null)
          }
        }}
        token={revokeTarget}
      />
    </div>
  )
}
