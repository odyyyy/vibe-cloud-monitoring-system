import { Component, type ErrorInfo, type ReactNode } from 'react'

interface WidgetErrorBoundaryProps {
  children: ReactNode
}

interface WidgetErrorBoundaryState {
  hasError: boolean
}

/**
 * Isolates a single widget so an unexpected render error shows an inline error
 * card instead of taking down the whole dashboard.
 */
export class WidgetErrorBoundary extends Component<WidgetErrorBoundaryProps, WidgetErrorBoundaryState> {
  state: WidgetErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): WidgetErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('Widget render failed', error, info.componentStack)
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div role="alert" className="flex h-full items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5 p-4">
          <p className="text-center text-sm text-red-300">This widget could not be rendered.</p>
        </div>
      )
    }
    return this.props.children
  }
}
