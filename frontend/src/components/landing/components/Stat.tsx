import React from 'react'
import { useCountUp } from '../hooks/useCountUp'
import { type LandingStat } from '../data'

export function Stat({ value, suffix, decimals, label }: LandingStat) {
  const display = useCountUp(value, 1800, decimals)

  return (
    <div className="text-center">
      <div className="font-display text-4xl font-bold tracking-tight text-slate-100 sm:text-5xl">
        {display.toFixed(decimals)}
        <span className="text-gradient">{suffix}</span>
      </div>
      <div className="mono mt-2 text-xs uppercase tracking-[0.2em] text-slate-500">{label}</div>
    </div>
  )
}