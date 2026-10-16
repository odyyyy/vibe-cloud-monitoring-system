import React from 'react'

type SectionHeadingProps = {
  tag: string
  title: React.ReactNode
  body?: string
}

export function SectionHeading({ tag, title, body }: SectionHeadingProps) {
  return (
    <div className="max-w-2xl">
      <span className="section-tag mono">{tag}</span>
      <h2 className="font-display mt-4 text-3xl font-semibold leading-tight tracking-tight text-slate-100 sm:text-4xl">
        {title}
      </h2>
      {body ? <p className="mt-4 text-base leading-relaxed text-slate-400">{body}</p> : null}
    </div>
  )
}