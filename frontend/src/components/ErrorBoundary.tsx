import { Component, type ErrorInfo, type ReactNode } from 'react';

type ErrorBoundaryProps = {
  children: ReactNode;
};

type ErrorBoundaryState = {
  hasError: boolean;
};

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[frontend-error-boundary]', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="fatal-error" role="alert">
          <strong>La interfaz no pudo continuar</strong>
          <p>Recarga la aplicación para recuperar el flujo. Si el problema persiste, conserva el correlation-id del backend.</p>
          <button className="button button--primary" type="button" onClick={() => window.location.reload()}>
            Recargar aplicación
          </button>
        </main>
      );
    }

    return this.props.children;
  }
}
