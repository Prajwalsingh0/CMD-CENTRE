/**
 * The single HTTP entry point for the app.
 *
 * Responsibilities: attach the bearer token, normalise every failure into a typed {@link ApiError}
 * that matches the backend's error envelope, and broadcast a session-expiry event exactly once so
 * the auth layer can react without any component knowing about it.
 */

export interface ApiErrorDetail {
  field: string;
  issue: string;
}

interface ApiErrorEnvelope {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  details?: ApiErrorDetail[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: ApiErrorDetail[];
  readonly path: string;

  constructor(status: number, code: string, message: string, details: ApiErrorDetail[] = [], path = '') {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.path = path;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isNetworkFailure(): boolean {
    return this.status === 0;
  }

  /** Field-level messages from a validation failure, ready to render under an input. */
  get fieldIssues(): Record<string, string> {
    const issues: Record<string, string> = {};
    for (const detail of this.details) {
      if (detail?.field && detail?.issue && !issues[detail.field]) {
        issues[detail.field] = detail.issue;
      }
    }
    return issues;
  }
}

const TOKEN_KEY = 'aicc.accessToken';
export const SESSION_EXPIRED_EVENT = 'aicc:session-expired';
const REFRESH_TOKEN_KEY = 'aicc.refreshToken';

export function getRefreshToken(): string | null {
  try {
    return window.localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setRefreshToken(token: string | null): void {
  try {
    if (token === null) {
      window.localStorage.removeItem(REFRESH_TOKEN_KEY);
    } else {
      window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
    }
  } catch {
    // Same reasoning as the access token: storage may be unavailable.
  }
}

/** Replaces both halves of a session in one place. */
export function setSession(accessToken: string, refreshToken: string): void {
  setToken(accessToken);
  setRefreshToken(refreshToken);
}

export function clearSession(): void {
  clearToken();
  setRefreshToken(null);
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Renews the session, at most once at a time.
 *
 * Single-flight matters for two reasons: a screen that fires several requests at once must not
 * trigger several rotations (the backend answers a raced rotation with a 401 on purpose), and the
 * rotated token has to be stored before any retry reads it.
 */
async function renewSession(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const attempt = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      return false;
    }

    try {
      const response = await fetch(`${apiBaseUrl()}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) {
        return false;
      }
      const text = await response.text();
      const data = (text.length > 0 ? safeParse(text) : null) as { accessToken?: string; refreshToken?: string } | null;
      if (!data?.accessToken || !data?.refreshToken) {
        return false;
      }
      setSession(data.accessToken, data.refreshToken);
      return true;
    } catch {
      return false;
    }
  })();

  // Clear the slot only once this attempt settles, and only if nothing newer took it over.
  // Clearing it from inside the async function would run before this assignment, leaving a
  // resolved promise cached - which would silently stop every later renewal attempt.
  refreshInFlight = attempt;
  void attempt.finally(() => {
    if (refreshInFlight === attempt) {
      refreshInFlight = null;
    }
  });

  return attempt;
}

function isRenewalPath(path: string): boolean {
  return path.startsWith('/api/auth/refresh') || path.startsWith('/api/auth/logout');
}

function expireSession(): void {
  clearSession();
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
}

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null): void {
  try {
    if (token === null) {
      window.localStorage.removeItem(TOKEN_KEY);
    } else {
      window.localStorage.setItem(TOKEN_KEY, token);
    }
  } catch {
    // A browser with storage disabled still works for the current tab; the token simply
    // does not survive a reload.
  }
}

export function clearToken(): void {
  setToken(null);
}

export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  return configured && configured.length > 0 ? configured.replace(/\/+$/, '') : '';
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  /** Skip the Authorization header, for the two public endpoints. */
  anonymous?: boolean;
}

async function perform<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, anonymous = false } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = getToken();
  if (!anonymous && token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    // Let the browser set the multipart boundary.
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl()}${path}`, { method, headers, body: payload, signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server. Is the backend running?');
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const parsed = text.length > 0 ? safeParse(text) : null;

  if (!response.ok) {
    const envelope = (parsed ?? {}) as ApiErrorEnvelope;
    const error = new ApiError(
      response.status,
      envelope.error ?? `HTTP_${response.status}`,
      envelope.message ?? defaultMessage(response.status),
      Array.isArray(envelope.details) ? envelope.details : [],
      envelope.path ?? path,
    );
    throw error;
  }

  return parsed as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function defaultMessage(status: number): string {
  if (status === 401) {
    return 'Your session has expired. Please sign in again.';
  }
  if (status === 403) {
    return 'You do not have access to that.';
  }
  if (status === 404) {
    return 'That item no longer exists.';
  }
  if (status === 413) {
    return 'That file is too large.';
  }
  if (status >= 500) {
    return 'The server hit an unexpected problem.';
  }
  return 'The request could not be completed.';
}

/**
 * The app's HTTP entry point.
 *
 * A 401 no longer means "signed out" straight away: when the call was authenticated and a refresh
 * token is on hand, the session is renewed once and the call is replayed with the new access
 * token. Only if that fails (or there is nothing to renew with) does the session end and the
 * expiry event fire, so a screen can distinguish "renewed silently" from "signed out".
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    return await perform<T>(path, options);
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.isUnauthorized &&
      !options.anonymous &&
      !isRenewalPath(path)
    ) {
      const renewed = await renewSession();
      if (renewed) {
        try {
          return await perform<T>(path, options);
        } catch (retryError) {
          expireSession();
          throw retryError;
        }
      }
    }

    if (error instanceof ApiError && error.isUnauthorized && !options.anonymous) {
      expireSession();
    }
    throw error;
  }
}
/**
 * Downloads a file from an authenticated endpoint.
 *
 * Separate from {@link request} because the response is a blob with a filename, not JSON - but it
 * follows the same session rules: one renewal and one retry on a 401, and signing out only when
 * that renewal fails.
 */
export async function downloadFile(path: string): Promise<void> {
  const attempt = async (): Promise<Response> => {
    const token = getToken();
    try {
      return await fetch(`${apiBaseUrl()}${path}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server. Is the backend running?', [], path);
    }
  };

  let response = await attempt();
  if (response.status === 401 && (await renewSession())) {
    response = await attempt();
  }

  if (response.status === 401) {
    expireSession();
    throw new ApiError(401, 'UNAUTHORIZED', 'Your session has expired. Please sign in again.', [], path);
  }
  if (!response.ok) {
    throw new ApiError(response.status, `HTTP_${response.status}`, defaultMessage(response.status), [], path);
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filenameFrom(response.headers.get('Content-Disposition')) ?? 'export.json';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}

/** Reads the server's suggested file name, falling back to the caller's default. */
function filenameFrom(header: string | null): string | null {
  if (!header) {
    return null;
  }
  const match = /filename="?([^";]+)"?/i.exec(header);
  return match ? match[1] : null;
}