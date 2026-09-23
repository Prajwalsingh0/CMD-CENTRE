import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';

interface LocationState {
  from?: string;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const from = (location.state as LocationState | null)?.from ?? '/dashboard';

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Sign in failed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="center-narrow">
      <div className="card">
        <h1 style={{ marginBottom: 4 }}>AI Command Center</h1>
        <p className="muted">Sign in to your workspace.</p>

        {error ? (
          <div className="alert alert--danger" role="alert" style={{ marginBottom: 12 }}>
            {error}
          </div>
        ) : null}

        <form onSubmit={(event) => void submit(event)} noValidate>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          <button type="submit" className="btn btn--primary btn--block" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <hr className="divider" />
        <p className="small muted">
          No account yet? <Link to="/register">Create one</Link>.
        </p>
      </div>
    </div>
  );
}
