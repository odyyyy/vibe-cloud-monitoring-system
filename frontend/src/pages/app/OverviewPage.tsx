import { Activity, Layers, ScrollText } from 'lucide-react'
import { useAuthStore } from '../../store/authStore'

const PLACEHOLDER_CARDS = [
  { title: 'Agents', description: 'No agents connected yet.', icon: Layers },
  { title: 'Metrics', description: 'No metrics received yet.', icon: Activity },
  { title: 'Logs', description: 'No log events received yet.', icon: ScrollText },
]

export default function OverviewPage() {
  const user = useAuthStore((state) => state.user)

  return (
    <section className="space-y-8">
      <header className="space-y-2">
        <p className="section-tag">Overview</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">
          Welcome back{user?.username ? `, ${user.username}` : ''}
        </h1>
        <p className="text-sm text-slate-400">
          Your workspace is ready. Telemetry views will appear here as you connect agents and start
          sending data.
        </p>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {PLACEHOLDER_CARDS.map((card) => {
          const Icon = card.icon
          return (
            <article key={card.title} className="rounded-2xl border border-white/10 bg-[#0b101b]/60 p-5">
              <div className="flex items-center gap-2 text-slate-400">
                <Icon className="h-4 w-4" aria-hidden="true" />
                <h2 className="text-sm font-medium text-slate-200">{card.title}</h2>
              </div>
              <p className="mt-4 text-2xl font-semibold text-slate-500">—</p>
              <p className="mt-1 text-xs text-slate-500">{card.description}</p>
            </article>
          )
        })}
      </div>
    </section>
  )
}
