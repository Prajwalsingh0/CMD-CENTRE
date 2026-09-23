import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getToken, request, setToken } from '../api/client';

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
