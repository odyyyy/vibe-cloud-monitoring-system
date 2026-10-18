import { useEffect, useRef } from 'react'
import { useApplicationsQuery } from '../../api/useApplications'
import { useWorkspaceSummaryQuery } from '../../api/overview'
import { useAuthStore } from '../../store/authStore'
import { useOnboardingStore } from '../../store/onboardingStore'
import { readOnboardingStatus } from './storage'

/**
 * Opens the onboarding wizard once for users who have never completed or dismissed it, when the
 * workspace either has no applications or has not received any telemetry yet. The decision is
 * made after the first successful load of both queries, so an empty cache is never mistaken for
 * an empty workspace.
 */
export function useAutoOpenOnboarding(): void {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const openWizard = useOnboardingStore((state) => state.openWizard)
  const applicationsQuery = useApplicationsQuery()
  const summaryQuery = useWorkspaceSummaryQuery()
  const evaluatedRef = useRef(false)

  const applications = applicationsQuery.isSuccess ? applicationsQuery.data : null
  const summary = summaryQuery.isSuccess ? summaryQuery.data : null

  useEffect(() => {
    if (evaluatedRef.current) {
      return
    }
    if (!applications) {
      return
    }
    // The summary decides whether an existing workspace received any telemetry; without it an
    // empty workspace can still be onboarded.
    if (applications.length > 0 && !summary) {
      return
    }
    evaluatedRef.current = true

    if (readOnboardingStatus(userId) !== null) {
      return
    }

    const hasTelemetry = summary !== null && (summary.metricNameCount24h > 0 || summary.logEventCount24h > 0)

    if (applications.length === 0 || !hasTelemetry) {
      openWizard()
    }
  }, [applications, summary, userId, openWizard])
}
