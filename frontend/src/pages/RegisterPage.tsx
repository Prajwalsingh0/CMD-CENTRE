import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(email.trim(), password, displayName.trim());
      navigate('/dashboard', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, 'UNKNOWN', 'Registration failed.'));
    } finally {
      setBusy(false);
    }
  };

  const fieldIssues = error?.fieldIssues ?? {};

  return (
    <div className="center-narrow">
      <div className="card">
        <h1 style={{ marginBottom: 4 }}>Create your workspace</h1>
        <p className="muted">One account holds your goals, tasks, documents and AI history.</p>

        {error ? (
          <div className="alert alert--danger" role="alert" style={{ marginBottom: 12 }}>
            {error.message}
          </div>
        ) : null}

        <form onSubmit={(event) => void submit(event)} noValidate>
          <div className="field">
            <label htmlFor="displayName">Display name</label>
            <input
              id="displayName"
              type="text"
              autoComplete="name"
              required
              maxLength={120}
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              aria-invalid={Boolean(fieldIssues.displayName)}
            />
            {fieldIssues.displayName ? <span className="field__error">{fieldIssues.displayName}</span> : null}
          </div>

          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={Boolean(fieldIssues.email)}
            />
            {fieldIssues.email ? <span className="field__error">{fieldIssues.email}</span> : null}
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(fieldIssues.password)}
            />
            <span className="field__hint">
              At least 8 characters, with one letter and one digit.
            </span>
            {fieldIssues.password ? <span className="field__error">{fieldIssues.password}</span> : null}
          </div>

          <button type="submit" className="btn btn--primary btn--block" disabled={busy}>
            {busy ? 'Creating…' : 'Create account'}
          </button>
        </form>

        <hr className="divider" />
        <p className="small muted">
          Already registered? <Link to="/login">Sign in</Link>.
        </p>
      </div>
    </div>
  );
}
