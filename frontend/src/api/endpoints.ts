import { request } from './client';
import type {
  AiActivityResponse,
  AiStatusResponse,
  AnalyticsResponse,
  AnswerResponse,
  AuthResponse,
  ChatResponse,
  CommandResultResponse,
  DashboardResponse,
  DocumentContentResponse,
  DocumentInsightResponse,
  DocumentResponse,
  DocumentStatus,
  GoalResponse,
  GoalStatus,
  HealthResponse,
  InsightOperation,
  JobAnalysisResponse,
  NotificationResponse,
  PageResponse,
  Priority,
  ReindexResponse,
  ResearchResponse,
  SkillResponse,
  TaskResponse,
  TaskStatus,
  UserResponse,
} from './types';

/** One typed function per endpoint in docs/API.md. Components never build a URL themselves. */

export const authApi = {
  register: (body: { email: string; password: string; displayName: string }) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body, anonymous: true }),

  login: (body: { email: string; password: string }) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body, anonymous: true }),

  me: () => request<UserResponse>('/api/auth/me'),
};

export const userApi = {
  profile: () => request<UserResponse>('/api/users/me'),

  updateProfile: (body: { displayName?: string; headline?: string }) =>
    request<UserResponse>('/api/users/me', { method: 'PUT', body }),

  skills: () => request<SkillResponse[]>('/api/users/me/skills'),

  addSkill: (body: { skill: string; level?: string; verified?: boolean }) =>
    request<SkillResponse>('/api/users/me/skills', { method: 'POST', body }),

  removeSkill: (skillId: number) =>
    request<void>(`/api/users/me/skills/${skillId}`, { method: 'DELETE' }),
};

export interface TaskQuery {
  status?: TaskStatus | '';
  priority?: Priority | '';
  goalId?: number | '';
  search?: string;
  overdueOnly?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export const taskApi = {
  list: (query: TaskQuery = {}) => {
    const params = new URLSearchParams();
    if (query.status) params.set('status', query.status);
    if (query.priority) params.set('priority', query.priority);
    if (query.goalId !== undefined && query.goalId !== '') params.set('goalId', String(query.goalId));
    if (query.search) params.set('search', query.search);
    if (query.overdueOnly) params.set('overdueOnly', 'true');
    params.set('page', String(query.page ?? 0));
    params.set('size', String(query.size ?? 20));
    if (query.sort) params.set('sort', query.sort);
    return request<PageResponse<TaskResponse>>(`/api/tasks?${params.toString()}`);
  },

  overdue: () => request<TaskResponse[]>('/api/tasks/overdue'),

  get: (id: number) => request<TaskResponse>(`/api/tasks/${id}`),

  create: (body: {
    title: string;
    description?: string | null;
    status?: TaskStatus;
    priority?: Priority;
    dueDate?: string | null;
    goalId?: number | null;
    tags?: string[];
  }) => request<TaskResponse>('/api/tasks', { method: 'POST', body }),

  update: (id: number, body: Partial<{
    title: string;
    description: string | null;
    status: TaskStatus;
    priority: Priority;
    dueDate: string | null;
    goalId: number | null;
    tags: string[];
  }>) => request<TaskResponse>(`/api/tasks/${id}`, { method: 'PUT', body }),

  updateStatus: (id: number, status: TaskStatus) =>
    request<TaskResponse>(`/api/tasks/${id}/status`, { method: 'PATCH', body: { status } }),

  remove: (id: number) => request<void>(`/api/tasks/${id}`, { method: 'DELETE' }),
};

export const goalApi = {
  list: (status?: GoalStatus | '') =>
    request<GoalResponse[]>(`/api/goals${status ? `?status=${status}` : ''}`),

  get: (id: number) => request<GoalResponse>(`/api/goals/${id}`),

  create: (body: {
    title: string;
    description?: string | null;
    deadline?: string | null;
    priority?: Priority;
    status?: GoalStatus;
    notes?: string | null;
  }) => request<GoalResponse>('/api/goals', { method: 'POST', body }),

  update: (id: number, body: Partial<{
    title: string;
    description: string | null;
    deadline: string | null;
    priority: Priority;
    status: GoalStatus;
    notes: string | null;
  }>) => request<GoalResponse>(`/api/goals/${id}`, { method: 'PUT', body }),

  refreshStatus: (id: number) => request<GoalResponse>(`/api/goals/${id}/refresh-status`, { method: 'POST' }),

  remove: (id: number) => request<void>(`/api/goals/${id}`, { method: 'DELETE' }),
};

export const documentApi = {
  list: (query: { status?: DocumentStatus | ''; search?: string } = {}) => {
    const params = new URLSearchParams();
    if (query.status) params.set('status', query.status);
    if (query.search) params.set('search', query.search);
    const suffix = params.toString();
    return request<DocumentResponse[]>(`/api/documents${suffix ? `?${suffix}` : ''}`);
  },

  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<DocumentResponse>('/api/documents', { method: 'POST', body: form });
  },

  get: (id: number) => request<DocumentResponse>(`/api/documents/${id}`),

  content: (id: number) => request<DocumentContentResponse>(`/api/documents/${id}/content`),

  insight: (id: number, operation: InsightOperation) =>
    request<DocumentInsightResponse>(`/api/documents/${id}/insights`, { method: 'POST', body: { operation } }),

  remove: (id: number) => request<void>(`/api/documents/${id}`, { method: 'DELETE' }),
};

export const knowledgeApi = {
  ask: (body: { question: string; documentIds?: number[]; topK?: number }) =>
    request<AnswerResponse>('/api/knowledge/ask', { method: 'POST', body }),

  reindex: () => request<ReindexResponse>('/api/knowledge/reindex', { method: 'POST' }),
};

export const jobApi = {
  list: () => request<JobAnalysisResponse[]>('/api/jobs'),

  get: (id: number) => request<JobAnalysisResponse>(`/api/jobs/${id}`),

  analyze: (body: { description: string; jobTitle?: string; company?: string }) =>
    request<JobAnalysisResponse>('/api/jobs/analyze', { method: 'POST', body }),

  regeneratePrep: (id: number) => request<JobAnalysisResponse>(`/api/jobs/${id}/prep`, { method: 'POST' }),

  remove: (id: number) => request<void>(`/api/jobs/${id}`, { method: 'DELETE' }),
};

export const researchApi = {
  list: () => request<ResearchResponse[]>('/api/research'),

  get: (id: number) => request<ResearchResponse>(`/api/research/${id}`),

  create: (body: { topic: string; depth?: 'quick' | 'deep' }) =>
    request<ResearchResponse>('/api/research', { method: 'POST', body }),

  remove: (id: number) => request<void>(`/api/research/${id}`, { method: 'DELETE' }),
};

export const aiApi = {
  command: (command: string) =>
    request<CommandResultResponse>('/api/ai/command', { method: 'POST', body: { command } }),

  chat: (message: string) => request<ChatResponse>('/api/ai/chat', { method: 'POST', body: { message } }),

  activity: (limit = 50) => request<AiActivityResponse[]>(`/api/ai/activity?limit=${limit}`),

  clearActivity: () => request<void>('/api/ai/activity', { method: 'DELETE' }),

  status: () => request<AiStatusResponse>('/api/ai/status'),

  examples: () => request<string[]>('/api/ai/examples'),
};

export const dashboardApi = {
  load: () => request<DashboardResponse>('/api/dashboard'),
};

export const analyticsApi = {
  load: (days = 30) => request<AnalyticsResponse>(`/api/analytics?days=${days}`),
};

export const notificationApi = {
  list: (unreadOnly = false) =>
    request<NotificationResponse[]>(`/api/notifications?unreadOnly=${unreadOnly}`),

  unreadCount: () => request<{ count: number }>('/api/notifications/unread-count'),

  refresh: () => request<NotificationResponse[]>('/api/notifications/refresh', { method: 'POST' }),

  markRead: (id: number) => request<NotificationResponse>(`/api/notifications/${id}/read`, { method: 'POST' }),

  markAllRead: () => request<{ updated: number }>('/api/notifications/read-all', { method: 'POST' }),

  remove: (id: number) => request<void>(`/api/notifications/${id}`, { method: 'DELETE' }),
};

export const healthApi = {
  load: () => request<HealthResponse>('/api/health', { anonymous: true }),
};
