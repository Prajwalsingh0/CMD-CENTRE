import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { ProtectedRoute } from '../auth/ProtectedRoute';

function renderProtected(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>Sign in screen</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/tasks" element={<p>Private task list</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('redirects a visitor with no session to the login route', async () => {
    window.localStorage.clear();

    renderProtected('/tasks');

    await waitFor(() => expect(screen.getByText('Sign in screen')).toBeInTheDocument());
    expect(screen.queryByText('Private task list')).not.toBeInTheDocument();
  });

  it('shows a restoring state while the stored token is being validated', () => {
    // A token exists, so the provider goes async before it can decide.
    window.localStorage.setItem('aicc.accessToken', 'some-token');
    const fetchMock = async () => ({
      ok: false,
      status: 401,
      text: async () => JSON.stringify({ error: 'UNAUTHORIZED', message: 'Authentication required' }),
    });
    // @ts-expect-error assigning a test double over the global fetch
    globalThis.fetch = fetchMock;

    renderProtected('/tasks');

    expect(screen.getByText('Restoring your session…')).toBeInTheDocument();
  });
});
