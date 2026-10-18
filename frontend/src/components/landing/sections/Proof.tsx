import React from 'react'
import { Stat } from '../components/Stat'
import { landingStats, type LandingStat } from '../data'

export function Proof() {
  return (
    <section className="relative border-y border-white/5 bg-canvas-2 py-16">
      <div className="mx-auto grid max-w-7xl gap-10 px-6 sm:grid-cols-2 lg:grid-cols-4">
        {landingStats.map((stat: LandingStat) => (
          <Stat key={stat.label} {...stat} />
        ))}
      </div>
    </section>
  )
}