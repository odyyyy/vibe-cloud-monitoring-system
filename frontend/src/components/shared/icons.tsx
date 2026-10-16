import type { ComponentType, SVGProps } from 'react'

export type IconProps = SVGProps<SVGSVGElement>

type IconComponent = ComponentType<IconProps>

const iconDefaults: IconProps = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
}

const createIcon =
  (path: JSX.Element, defaultClassName = 'h-5 w-5'): IconComponent =>
  ({ className = defaultClassName, ...props }: IconProps) => (
    <svg {...iconDefaults} viewBox="0 0 24 24" className={className} {...props}>
      {path}
    </svg>
  )

export const IconArrow = createIcon(<path d="M5 12h14M13 6l6 6-6 6" />, 'h-4 w-4')

export const IconCopy = createIcon(
  <>
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V6a2 2 0 0 1 2-2h9" />
  </>,
  'h-4 w-4',
)

export const IconCheck = createIcon(<path d="M20 6 9 17l-5-5" />, 'h-4 w-4')

export const IconPulse = createIcon(<path d="M2 12h4l3-8 4 16 3-10 2 2h4" />)

export const IconLogs = createIcon(
  <path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01" />,
)

export const IconChart = createIcon(
  <>
    <path d="M3 3v18h18" />
    <path d="M7 14l3-4 3 3 4-6" />
  </>,
)

export const IconShield = createIcon(
  <>
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
    <path d="m9 12 2 2 4-4" />
  </>,
)

export const IconBolt = createIcon(<path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />)

export const IconLayers = createIcon(
  <>
    <path d="m12 2 9 5-9 5-9-5 9-5Z" />
    <path d="m3 12 9 5 9-5M3 17l9 5 9-5" />
  </>,
)

export const IconGlobe = createIcon(
  <>
    <circle cx="12" cy="12" r="9" />
    <path d="M3 12h18M12 3a15 15 0 0 1 0 18 15 15 0 0 1 0-18Z" />
  </>,
)

export function Logo({ className = 'h-7 w-7' }: IconProps) {
  return (
    <svg viewBox="0 0 32 32" fill="none" className={className} aria-hidden="true">
      <rect x="1" y="1" width="30" height="30" rx="8" stroke="url(#logo-gradient)" strokeWidth="1.6" />
      <path
        d="M7 20.5c3.2 0 4.2-8 7.6-8s3 6 5.2 6 1.9-2.4 4.2-2.4"
        stroke="#67e8f9"
        strokeWidth="2"
        strokeLinecap="round"
      />
      <circle cx="24.5" cy="22.5" r="2.2" fill="#a78bfa" />
      <defs>
        <linearGradient id="logo-gradient" x1="0" y1="0" x2="32" y2="32">
          <stop stopColor="#22d3ee" />
          <stop offset="1" stopColor="#8b5cf6" />
        </linearGradient>
      </defs>
    </svg>
  )
}