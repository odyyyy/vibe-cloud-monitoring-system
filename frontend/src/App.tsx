import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import { GuestOnlyRoute } from './components/auth/GuestOnlyRoute'
import { RequireAuth } from './components/auth/RequireAuth'
import { AppLayout } from './components/layout/AppLayout'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import OverviewPage from './pages/app/OverviewPage'
import RegisterPage from './pages/RegisterPage'
import SettingsPage from './pages/app/SettingsPage'
import UnderConstructionPage from './pages/app/UnderConstructionPage'

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
      {
        path: 'applications',
        element: (
          <UnderConstructionPage
            title="Applications"
            description="Application management will let you create projects, enroll agents and inspect service health."
          />
        ),
      },
      {
        path: 'metrics',
        element: (
          <UnderConstructionPage title="Metrics" description="Metric exploration and charts are coming soon." />
        ),
      },
      {
        path: 'logs',
        element: (
          <UnderConstructionPage title="Logs" description="Log search and exploration are coming soon." />
        ),
      },
      {
        path: 'dashboards',
        element: (
          <UnderConstructionPage title="Dashboards" description="Configurable dashboards are coming soon." />
        ),
      },
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
