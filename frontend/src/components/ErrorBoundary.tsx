import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last line of defence. Without this a render error anywhere leaves the user on a blank page with
 * no way back; with it they get an explanation and a reload button.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Console rather than a remote collector: this project has no telemetry endpoint, and
    // inventing one that silently swallows errors would be worse than an honest console log.
    console.error('Unhandled UI error', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="content">
          <div className="alert alert--danger" role="alert">
            <h2>This screen crashed</h2>
            <p>
              The interface hit an unexpected error. Your data is unaffected — nothing on this page
              writes anything on its own.
            </p>
            <p className="small mono">{this.state.error.message}</p>
            <button type="button" className="btn btn--primary" onClick={() => window.location.reload()}>
              Reload the app
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
