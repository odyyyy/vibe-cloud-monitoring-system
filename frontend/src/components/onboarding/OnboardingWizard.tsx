import { useCallback, useEffect, useMemo, useState } from 'react'
import { BadgeCheck, Layers } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { AgentNode } from '../../api/agents'
import type { Application, CreatedApplication } from '../../api/applications'
import { useApplicationsQuery } from '../../api/useApplications'
import { useAuthStore } from '../../store/authStore'
import { useOnboardingStore } from '../../store/onboardingStore'
import { writeOnboardingStatus } from '../../features/onboarding/storage'
import { useAutoOpenOnboarding } from '../../features/onboarding/useAutoOpenOnboarding'
import { formatDateTime } from '../../lib/datetime'
import { ApplicationFormDialog } from '../applications/ApplicationFormDialog'
import { RegenerateKeyDialog } from '../applications/RegenerateKeyDialog'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Confetti } from './Confetti'
import { InstallAgentStep } from './InstallAgentStep'
import { OnboardingStepIndicator, type OnboardingStepId } from './OnboardingStepIndicator'
import { VerifyConnectionStep } from './VerifyConnectionStep'

interface WizardApplication {
  application: Application
  /** Plaintext key when the application was just created; `null` once it is out of sight. */
  apiKey: string | null
}

const STEP_HEADINGS: Record<OnboardingStepId, { title: string; description: string }> = {
  create: {
    title: 'Create your first application',
    description: 'Applications group the telemetry your agents send and issue the API keys they use.',
  },
  install: {
    title: 'Install the agent',
    description: 'Run the agent next to the service or host you want to monitor.',
  },
  verify: {
    title: 'Verify the connection',
    description: 'The wizard is watching for the first telemetry from your agent.',
  },
  success: {
    title: 'Your agent is connected',
    description: 'Data is flowing into your workspace.',
  },
}

/**
 * Guided four-step onboarding flow: create an application, run the agent, wait for the first
 * telemetry and finish with a success state. It auto-opens for users who have not completed or
 * dismissed it yet, and can be reopened anytime from the sidebar "Setup guide" button.
 */
export function OnboardingWizard() {
  const navigate = useNavigate()
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const isOpen = useOnboardingStore((state) => state.isOpen)
  const closeWizard = useOnboardingStore((state) => state.closeWizard)
  useAutoOpenOnboarding()

  const applicationsQuery = useApplicationsQuery()
  const applications = useMemo(() => applicationsQuery.data ?? [], [applicationsQuery.data])

  const [step, setStep] = useState<OnboardingStepId>('create')
  const [selected, setSelected] = useState<WizardApplication | null>(null)
  const [connectedAgent, setConnectedAgent] = useState<AgentNode | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [regenerateOpen, setRegenerateOpen] = useState(false)

  const resetFlow = useCallback(() => {
    setFormOpen(false)
    setRegenerateOpen(false)
    setSelected(null)
    setConnectedAgent(null)
    setStep('create')
  }, [])

  const dismiss = useCallback(() => {
    writeOnboardingStatus(userId, 'dismissed')
    closeWizard()
    resetFlow()
  }, [userId, closeWizard, resetFlow])

  const complete = useCallback(() => {
    writeOnboardingStatus(userId, 'completed')
    closeWizard()
    resetFlow()
  }, [userId, closeWizard, resetFlow])

  // Users who already have applications do not need the create step.
  useEffect(() => {
    if (!isOpen || step !== 'create' || selected !== null || formOpen) {
      return
    }
    if (applications.length === 0) {
      return
    }
    setSelected({ application: applications[0], apiKey: null })
    setStep('install')
  }, [isOpen, step, selected, formOpen, applications])

  const handleCreated = useCallback((created: CreatedApplication) => {
    setSelected({ application: created, apiKey: created.apiKey })
    setStep('install')
  }, [])

  const handleApplicationChange = useCallback((application: Application) => {
    setSelected({ application, apiKey: null })
  }, [])

  const handleRegenerated = useCallback((apiKey: string) => {
    setSelected((current) => (current ? { ...current, apiKey } : current))
  }, [])

  const handleConnected = useCallback((agent: AgentNode) => {
    setConnectedAgent(agent)
    setStep('success')
  }, [])

  const goToMetrics = useCallback(() => {
    if (selected) {
      navigate(`/app/metrics?applicationId=${selected.application.id}`)
    }
    complete()
  }, [navigate, selected, complete])

  const goToOverview = useCallback(() => {
    navigate('/app/overview')
    complete()
  }, [navigate, complete])

  const heading = STEP_HEADINGS[step]

  return (
    <>
      <Dialog
        open={isOpen}
        onOpenChange={(open) => {
          if (!open) {
            dismiss()
          }
        }}
      >
        <DialogContent className="max-h-[85vh] w-[calc(100%-2rem)] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{heading.title}</DialogTitle>
            <DialogDescription>{heading.description}</DialogDescription>
          </DialogHeader>

          <OnboardingStepIndicator current={step} />

          {step === 'create' ? (
            <CreateStep onStart={() => setFormOpen(true)} onDismiss={dismiss} />
          ) : step === 'install' && selected ? (
            <InstallAgentStep
              application={selected.application}
              apiKey={selected.apiKey}
              applications={applications}
              onApplicationChange={handleApplicationChange}
              onRequestRegenerate={() => setRegenerateOpen(true)}
              onContinue={() => setStep('verify')}
              onDismiss={dismiss}
            />
          ) : step === 'verify' && selected ? (
            <VerifyConnectionStep
              application={selected.application}
              onConnected={handleConnected}
              onDismiss={dismiss}
            />
          ) : step === 'success' && selected && connectedAgent ? (
            <SuccessStep
              application={selected.application}
              agent={connectedAgent}
              onClose={complete}
              onViewMetrics={goToMetrics}
              onGoToOverview={goToOverview}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <ApplicationFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        application={null}
        onCreated={handleCreated}
      />
      <RegenerateKeyDialog
        open={regenerateOpen}
        onOpenChange={setRegenerateOpen}
        application={selected?.application ?? null}
        onRegenerated={handleRegenerated}
      />
    </>
  )
}

function CreateStep({ onStart, onDismiss }: { onStart: () => void; onDismiss: () => void }) {
  return (
    <>
      <div className="mt-6 rounded-xl border border-dashed border-white/10 bg-white/[0.02] p-6 text-center">
        <Layers className="mx-auto h-6 w-6 text-cyan-300" aria-hidden="true" />
        <p className="mt-3 text-sm text-slate-300">
          An application provides the API key your agent authenticates with and groups its metrics
          and logs.
        </p>
      </div>
      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onDismiss}>
          Not now
        </Button>
        <Button type="button" onClick={onStart}>
          Create application
        </Button>
      </DialogFooter>
    </>
  )
}

interface SuccessStepProps {
  application: Application
  agent: AgentNode
  onClose: () => void
  onViewMetrics: () => void
  onGoToOverview: () => void
}

function SuccessStep({ application, agent, onClose, onViewMetrics, onGoToOverview }: SuccessStepProps) {
  return (
    <>
      <div className="relative mt-6 overflow-hidden rounded-xl border border-emerald-400/20 bg-emerald-400/5 p-6">
        <Confetti />
        <div className="relative flex flex-col items-center text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-400/15">
            <BadgeCheck className="h-6 w-6 text-emerald-300" aria-hidden="true" />
          </span>
          <p className="mt-3 text-sm text-slate-200">
            <span className="font-medium text-slate-100">{agent.hostname}</span> is sending telemetry
            for <span className="font-medium text-slate-100">{application.name}</span>.
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Agent version {agent.agentVersion ?? 'unknown'} · last seen {formatDateTime(agent.lastSeenAt)}
          </p>
        </div>
      </div>
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onClose}>
          Close
        </Button>
        <Button type="button" variant="outline" onClick={onViewMetrics}>
          View metrics
        </Button>
        <Button type="button" onClick={onGoToOverview}>
          Go to overview
        </Button>
      </DialogFooter>
    </>
  )
}
