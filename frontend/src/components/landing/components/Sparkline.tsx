import React from 'react'

type SparklineProps = { values: number[]; stroke: string; gradientId: string; height?: number }

export function Sparkline({ values, stroke, gradientId, height = 40 }: SparklineProps) {
  const width = 160
  const pad = 3
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const stepX = (width - pad * 2) / (values.length - 1)

  const coords = values.map((value, index) => {
    const x = pad + index * stepX
    const y = height - pad - ((value - min) / range) * (height - pad * 2)
    return { x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) }
  })

  const points = coords.map((coord) => `${coord.x},${coord.y}`).join(' ')
  const area = `M ${pad},${height} L ${points.replace(/ /g, ' L ')} L ${width - pad},${height} Z`

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-10 w-full" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={stroke} stopOpacity="0.35" />
          <stop offset="100%" stopColor={stroke} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <polyline
        className="spark-path"
        points={points}
        fill="none"
        stroke={stroke}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={coords[coords.length - 1].x} cy={coords[coords.length - 1].y} r="3" fill={stroke} />
    </svg>
  )
}