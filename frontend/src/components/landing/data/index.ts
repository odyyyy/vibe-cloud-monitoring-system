export type LandingStat = {
  value: number
  suffix: string
  decimals: number
  label: string
}

export const landingStats: LandingStat[] = [
  { value: 99.95, suffix: '%', decimals: 2, label: 'Ingestion uptime' },
  { value: 1.2, suffix: 'M', decimals: 1, label: 'Series per minute' },
  { value: 248, suffix: 'ms', decimals: 0, label: 'Query p95' },
  { value: 34, suffix: 'ms', decimals: 0, label: 'Agent batch size' },
]

export const landingData = {
  marqueeItems: [
    'Metrics',
    'Logs',
    'Traces',
    'Dashboards',
    'Service health',
    'Alerts',
    'Tenant isolation',
    'OpenTelemetry',
    'Agent enrollment',
    'Time-series',
  ],
  metrics: [
    {
      label: 'CPU utilization',
      value: '42.8',
      color: '#22d3ee',
      gradient: 'g-cpu',
      series: [18, 24, 21, 30, 27, 38, 34, 44, 41, 52, 47, 42],
    },
    {
      label: 'Latency p99',
      value: '182',
      color: '#8b5cf6',
      gradient: 'g-lat',
      series: [90, 120, 104, 148, 132, 176, 158, 210, 186, 240, 198, 182],
    },
    {
      label: 'Throughput',
      value: '1.4k',
      color: '#a3e635',
      gradient: 'g-thr',
      series: [620, 780, 700, 940, 880, 1120, 1040, 1380, 1260, 1520, 1420, 1400],
    },
  ],
  logLines: [
    { t: '14:02:11', level: 'INFO', service: 'payments', msg: 'charge captured 42.00 USD in 88ms' },
    { t: '14:02:09', level: 'WARN', service: 'edge-proxy', msg: 'upstream latency above p99 threshold' },
    { t: '14:02:04', level: 'ERROR', service: 'auth', msg: 'token refresh failed, retrying (2/3)' },
    { t: '14:01:58', level: 'INFO', service: 'web', msg: 'GET /checkout 200 in 12ms' },
    { t: '14:01:47', level: 'INFO', service: 'worker', msg: 'batch job reconcile completed: 1,024 rows' },
    { t: '14:01:31', level: 'DEBUG', service: 'cache', msg: 'hit ratio 0.94 across 3 shards' },
  ],
}