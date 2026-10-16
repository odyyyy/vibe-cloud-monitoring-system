import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import { GuestOnlyRoute } from './components/auth/GuestOnlyRoute'
import { RequireAuth } from './components/auth/RequireAuth'
import { AppLayout } from './components/layout/AppLayout'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/auth/LoginPage'
import ApplicationsPage from './pages/app/ApplicationsPage'
import DashboardsPage from './pages/app/DashboardsPage'
import DashboardViewPage from './pages/app/DashboardViewPage'
import LogExplorerPage from './pages/app/LogExplorerPage'
import MetricsExplorerPage from './pages/app/MetricsExplorerPage'
import OverviewPage from './pages/app/OverviewPage'
import RegisterPage from './pages/auth/RegisterPage'
import SettingsPage from './pages/app/SettingsPage'

const router = createBrowserRouter([
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
      { path: 'metrics', element: <MetricsExplorerPage /> },
      { path: 'logs', element: <LogExplorerPage /> },
      { path: 'dashboards', element: <DashboardsPage /> },
      { path: 'dashboards/:id', element: <DashboardViewPage /> },
      { path: 'settings', element: <SettingsPage /> },
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
