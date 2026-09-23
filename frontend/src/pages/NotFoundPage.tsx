import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div>
      <h1>Page not found</h1>
      <p className="muted">That route does not exist in this app.</p>
      <Link className="btn btn--primary" to="/dashboard">
        Back to the dashboard
      </Link>
    </div>
  );
}
