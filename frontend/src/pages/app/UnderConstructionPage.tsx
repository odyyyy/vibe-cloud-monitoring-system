interface UnderConstructionPageProps {
  title: string
  description?: string
}

export default function UnderConstructionPage({ title, description }: UnderConstructionPageProps) {
  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="section-tag">{title}</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">{title}</h1>
      </header>

      <div className="gradient-border rounded-2xl p-8">
        <p className="text-sm text-slate-400">
          {description ?? `The ${title} experience is under construction and will arrive in a later milestone.`}
        </p>
      </div>
    </section>
  )
}
