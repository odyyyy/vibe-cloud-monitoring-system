import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import { GuestOnlyRoute } from './components/auth/GuestOnlyRoute'
import { RequireAuth } from './components/auth/RequireAuth'
import { RequireRole } from './components/auth/RequireRole'
import { AppLayout } from './components/layout/AppLayout'
import LandingPage from './pages/LandingPage'
import CheckEmailPage from './pages/auth/CheckEmailPage'
import ConfirmEmailChangePage from './pages/auth/ConfirmEmailChangePage'
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage'
import LoginPage from './pages/auth/LoginPage'
import ResetPasswordPage from './pages/auth/ResetPasswordPage'
import SsoCallbackPage from './pages/auth/SsoCallbackPage'
import VerifyEmailPage from './pages/auth/VerifyEmailPage'
import AlertsPage from './pages/app/AlertsPage'
import AgentFleetPage from './pages/app/AgentFleetPage'
import ApplicationsPage from './pages/app/ApplicationsPage'
import AuditLogPage from './pages/app/AuditLogPage'
import DashboardsPage from './pages/app/DashboardsPage'
import DashboardViewPage from './pages/app/DashboardViewPage'
import HealthChecksPage from './pages/app/HealthChecksPage'
import IncidentsPage from './pages/app/IncidentsPage'
import LogExplorerPage from './pages/app/LogExplorerPage'
import MaintenancePage from './pages/app/MaintenancePage'
import MetricsExplorerPage from './pages/app/MetricsExplorerPage'
import NotificationDeliveriesPage from './pages/app/NotificationDeliveriesPage'
import OverviewPage from './pages/app/OverviewPage'
import OrganizationSettingsPage from './pages/app/OrganizationSettingsPage'
import PublicDashboardViewPage from './pages/app/PublicDashboardViewPage'
import ReportsPage from './pages/app/ReportsPage'
import RegisterPage from './pages/auth/RegisterPage'
import PlatformHealthPage from './pages/admin/PlatformHealthPage'
import SettingsPage from './pages/app/SettingsPage'
import StatusPagesPage from './pages/app/StatusPagesPage'
import PublicStatusPage from './pages/public/PublicStatusPage'
import UsagePage from './pages/app/UsagePage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingPage />,
  },
  {
    path: '/login',
    element: (
      <GuestOnlyRoute>
        <LoginPage />
      </GuestOnlyRoute>
    ),
  },
  {
    path: '/register',
    element: (
      <GuestOnlyRoute>
        <RegisterPage />
      </GuestOnlyRoute>
    ),
  },
  {
    path: '/check-email',
    element: <CheckEmailPage />,
  },
  {
    path: '/verify-email',
    element: <VerifyEmailPage />,
  },
  {
    path: '/forgot-password',
    element: <ForgotPasswordPage />,
  },
  {
    path: '/reset-password',
    element: <ResetPasswordPage />,
  },
  {
    path: '/sso/callback',
    element: <SsoCallbackPage />,
  },
  {
    path: '/confirm-email-change',
    element: <ConfirmEmailChangePage />,
  },
  {
    path: '/share/:token',
    element: <PublicDashboardViewPage />,
  },
  {
    path: '/s/:slug',
    element: <PublicStatusPage />,
  },
  {
    path: '/app',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/app/overview" replace /> },
      { path: 'overview', element: <OverviewPage /> },
      { path: 'applications', element: <ApplicationsPage /> },
      { path: 'agents', element: <AgentFleetPage /> },
      { path: 'metrics', element: <MetricsExplorerPage /> },
      { path: 'logs', element: <LogExplorerPage /> },
      { path: 'health', element: <HealthChecksPage /> },
      { path: 'status-pages', element: <StatusPagesPage /> },
      { path: 'alerts', element: <AlertsPage /> },
      { path: 'incidents', element: <IncidentsPage /> },
      { path: 'notification-deliveries', element: <NotificationDeliveriesPage /> },
      { path: 'maintenance', element: <MaintenancePage /> },
      { path: 'reports', element: <ReportsPage /> },
      { path: 'dashboards', element: <DashboardsPage /> },
      { path: 'dashboards/:id', element: <DashboardViewPage /> },
      { path: 'audit', element: <AuditLogPage /> },
      { path: 'organization', element: <OrganizationSettingsPage /> },
      { path: 'usage', element: <UsagePage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
  {
    path: '/admin',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/admin/platform-health" replace /> },
      {
        path: 'platform-health',
        element: (
          <RequireRole role="PLATFORM_ADMIN">
            <PlatformHealthPage />
          </RequireRole>
        ),
      },
    ],
  },
  {
    path: '/profile',
    element: <Navigate to="/app/settings" replace />,
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
