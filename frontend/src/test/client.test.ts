import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, SESSION_EXPIRED_EVENT, getRefreshToken, getToken, request, setSession, setToken } from '../api/client';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

const fetchMock = vi.fn();

beforeEach(() => {
  window.localStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api client', () => {
  it('attaches the bearer token and returns parsed JSON', async () => {
    setToken('token-123');
    fetchMock.mockResolvedValue(jsonResponse({ id: 7, title: 'Ship it' }));

    const result = await request<{ id: number; title: string }>('/api/tasks/7');

    expect(result.title).toBe('Ship it');
    const [, init] = fetchMock.mock.calls[0];
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer token-123');
  });

  it('omits the Authorization header for anonymous endpoints', async () => {
    setToken('token-123');
    fetchMock.mockResolvedValue(jsonResponse({ status: 'UP' }));

    await request('/api/health', { anonymous: true });

    const [, init] = fetchMock.mock.calls[0];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it('maps the backend error envelope onto ApiError, including field issues', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          status: 400,
          error: 'VALIDATION_FAILED',
          message: 'Request validation failed',
          path: '/api/tasks',
          details: [{ field: 'title', issue: 'must not be blank' }],
        },
        400,
      ),
    );

    await expect(request('/api/tasks', { method: 'POST', body: {} })).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Request validation failed',
    });

    try {
      await request('/api/tasks', { method: 'POST', body: {} });
    } catch (caught) {
      expect(caught).toBeInstanceOf(ApiError);
      expect((caught as ApiError).fieldIssues).toEqual({ title: 'must not be blank' });
      expect((caught as ApiError).isNotFound).toBe(false);
    }
  });

  it('clears a stored token on 401 and announces the expiry exactly once', async () => {
    setToken('stale-token');
    const listener = vi.fn();
    window.addEventListener('aicc:session-expired', listener);
    fetchMock.mockResolvedValue(jsonResponse({ status: 401, error: 'UNAUTHORIZED', message: 'Authentication required' }, 401));

    await expect(request('/api/tasks')).rejects.toBeInstanceOf(ApiError);

    expect(getToken()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener('aicc:session-expired', listener);
  });

  it('turns a network failure into a readable ApiError instead of a raw TypeError', async () => {
    fetchMock.mockRejectedValue(new TypeError('fetch failed'));

    try {
      await request('/api/tasks');
      expect.unreachable('the request should have failed');
    } catch (caught) {
      expect(caught).toBeInstanceOf(ApiError);
      expect((caught as ApiError).isNetworkFailure).toBe(true);
      expect((caught as ApiError).message).toContain('Could not reach the server');
    }
  });

  it('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 204, text: async () => '' } as unknown as Response);
    await expect(request('/api/tasks/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});

describe('session renewal', () => {
  const refreshPayload = {
    accessToken: 'fresh-access',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    refreshToken: 'refresh-2',
    refreshExpiresInSeconds: 1209600,
    user: {},
  };

  function unauthorized(): Response {
    return jsonResponse({ error: 'UNAUTHORIZED', message: 'Authentication required' }, 401);
  }

  it('renews the session once and replays the call after a 401', async () => {
    setSession('stale-access', 'refresh-1');
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(jsonResponse(refreshPayload))
      .mockResolvedValueOnce(jsonResponse({ id: 1, title: 'Recovered' }));

    const result = await request<{ title: string }>('/api/tasks/1');

    expect(result.title).toBe('Recovered');
    expect(String(fetchMock.mock.calls[1][0])).toContain('/api/auth/refresh');
    const replayHeaders = fetchMock.mock.calls[2][1].headers as Record<string, string>;
    expect(replayHeaders.Authorization).toBe('Bearer fresh-access');
    expect(getToken()).toBe('fresh-access');
    expect(getRefreshToken()).toBe('refresh-2');
  });

  it('ends the session and announces expiry when renewal is refused', async () => {
    setSession('stale-access', 'refresh-1');
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(jsonResponse({ error: 'INVALID_REFRESH_TOKEN' }, 401));

    await expect(request('/api/tasks/1')).rejects.toBeInstanceOf(ApiError);

    expect(expired).toHaveBeenCalledTimes(1);
    expect(getToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
    const refreshAttempts = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/auth/refresh'));
    expect(refreshAttempts).toHaveLength(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('renews only once when several calls are refused together', async () => {
    setSession('stale-access', 'refresh-1');
    let refreshCalls = 0;
    const seen = new Set<string>();

    fetchMock.mockImplementation(async (url: string) => {
      if (String(url).includes('/api/auth/refresh')) {
        refreshCalls += 1;
        return jsonResponse(refreshPayload);
      }
      // Every endpoint refuses the stale token first, then answers with the refreshed one
      if (!seen.has(String(url))) {
        seen.add(String(url));
        return unauthorized();
      }
      return jsonResponse({ ok: true });
    });

    const results = await Promise.all([request('/api/tasks'), request('/api/goals')]);

    expect(results).toHaveLength(2);
    expect(refreshCalls).toBe(1);
  });

  it('does not renew for anonymous calls', async () => {
    setSession('stale-access', 'refresh-1');
    fetchMock.mockResolvedValueOnce(unauthorized());

    await expect(request('/api/auth/login', { method: 'POST', body: {}, anonymous: true }))
      .rejects.toBeInstanceOf(ApiError);

    const refreshAttempts = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/auth/refresh'));
    expect(refreshAttempts).toHaveLength(0);
    expect(getToken()).toBe('stale-access');
  });

  it('does not try to renew a failed renewal', async () => {
    setSession('stale-access', 'refresh-1');
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(jsonResponse({ error: 'INVALID_REFRESH_TOKEN' }, 401))
      .mockResolvedValueOnce(jsonResponse(refreshPayload));

    await expect(request('/api/tasks/1')).rejects.toBeInstanceOf(ApiError);

    const refreshAttempts = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/auth/refresh'));
    expect(refreshAttempts).toHaveLength(1);
  });

  it('cannot renew when no refresh token is stored', async () => {
    setToken('stale-access');
    fetchMock.mockResolvedValueOnce(unauthorized());

    await expect(request('/api/tasks/1')).rejects.toBeInstanceOf(ApiError);

    const refreshAttempts = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/auth/refresh'));
    expect(refreshAttempts).toHaveLength(0);
  });
});