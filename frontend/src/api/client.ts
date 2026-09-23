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

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
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
    if (error.isUnauthorized && !anonymous) {
      clearToken();
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
    }
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
