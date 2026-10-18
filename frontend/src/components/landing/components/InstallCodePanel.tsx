import React, { useState } from 'react'
import { IconCopy, IconCheck } from '../../shared/icons'

type Tab = 'docker' | 'config'

const installItems = [
  {
    id: 'docker',
    label: 'Docker',
    code: `# one container, scoped to your tenant
docker run -d \\
  --name cloudmonitor-agent \\
  -e CM_AGENT_TOKEN=cmrt_live_9f3...a2 \\
  -e CM_PROJECT=acme-prod \\
  -e CM_ENV=production \\
  ghcr.io/cloudmonitor/agent:latest`,
  },
  {
    id: 'config',
    label: 'Config',
    code: `# agent.yaml
server: https://ingest.cloudmonitor.io
project: acme-prod
environment: production
host: prod-worker-01

metrics:
  interval: 15s
  batch: 200
  buffers: 2048

logs:
  - path: /var/log/app/*.log
    service: payments`,
  },
]

export function InstallCodePanel() {
  const [activeTab, setActiveTab] = useState<Tab>('docker')
  const [copied, setCopied] = useState(false)

  const handleCopy = async (code: string) => {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="relative rounded-2xl border border-white/10 bg-panel/70 overflow-hidden">
      <div className="flex border-b border-white/10 bg-white/[0.02] px-4">
        {installItems.map((item) => (
          <button
            key={item.id}
            onClick={() => setActiveTab(item.id as Tab)}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-medium transition-colors ${
              activeTab === item.id
                ? 'text-cyan-300 border-b-2 border-cyan-400'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            {item.label}
          </button>
        ))}
        <div className="flex-1" />
        <button
          onClick={() => handleCopy(installItems.find((i) => i.id === activeTab)?.code ?? '')}
          className="flex items-center gap-1.5 px-3 py-1.5 mr-3 text-xs text-slate-400 hover:text-slate-200 transition-colors"
          disabled={copied}
        >
          {copied ? (
            <>
              <IconCheck className="h-3.5 w-3.5 text-lime-400" />
              Copied
            </>
          ) : (
            <>
              <IconCopy className="h-3.5 w-3.5" />
              Copy
            </>
          )}
        </button>
      </div>
      <pre className="p-5 overflow-x-auto">
        <code className="mono text-sm text-slate-300 leading-relaxed">
          {installItems.find((i) => i.id === activeTab)?.code}
        </code>
      </pre>
    </div>
  )
}