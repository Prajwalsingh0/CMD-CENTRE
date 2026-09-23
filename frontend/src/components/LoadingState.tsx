import type { ReactNode } from 'react';
import { ApiError } from '../api/client';

export function LoadingState({ label = 'Loading…', rows = 3 }: { label?: string; rows?: number }) {
  return (
    <div className="card" role="status" aria-live="polite">
      <span className="small muted">{label}</span>
      <div style={{ marginTop: 12 }} aria-hidden="true">
        {Array.from({ length: rows }).map((_, index) => (
          <div key={index} className="skeleton skeleton--line" style={{ width: `${100 - index * 12}%` }} />
        ))}
      </div>
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  message: string;
  action?: ReactNode;
}

/** Empty states must tell the user what to do next, otherwise they read as a bug. */
export function EmptyState({ title, message, action }: EmptyStateProps) {
  return (
    <div className="state">
      <h3>{title}</h3>
      <p>{message}</p>
      {action}
    </div>
  );
}

interface ErrorStateProps {
  error: unknown;
  onRetry?: () => void;
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong.';
  const code = error instanceof ApiError ? error.code : undefined;
  const fields = error instanceof ApiError ? Object.entries(error.fieldIssues) : [];

  return (
    <div className="alert alert--danger" role="alert">
      <strong>{message}</strong>
      {code ? <div className="small">Code: {code}</div> : null}
      {fields.length > 0 ? (
        <ul className="small" style={{ margin: '6px 0 0 16px' }}>
          {fields.map(([field, issue]) => (
            <li key={field}>
              <span className="mono">{field}</span>: {issue}
            </li>
          ))}
        </ul>
      ) : null}
      {onRetry ? (
        <div style={{ marginTop: 10 }}>
          <button type="button" className="btn btn--sm" onClick={onRetry}>
            Try again
          </button>
        </div>
      ) : null}
    </div>
  );
}
