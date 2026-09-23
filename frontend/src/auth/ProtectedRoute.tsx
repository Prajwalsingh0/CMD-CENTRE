import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

/**
 * Gate for every authenticated route. While the stored token is being validated it shows a
 * placeholder rather than flashing the login screen, and an unauthenticated visitor is redirected
 * with the attempted path remembered so login can return them to it.
 */
export function ProtectedRoute() {
  const { user, initialising } = useAuth();
  const location = useLocation();

  if (initialising) {
    return (
      <div className="content" role="status" aria-live="polite">
        <p className="muted">Restoring your session…</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
}
