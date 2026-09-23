/**
 * Types mirroring the backend DTOs in docs/API.md.
 *
 * They are hand-written rather than generated: the API surface is small and stable enough that
 * generation would add a build step and a code-generation dependency for very little gain.
 */

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type GoalStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';
export type InsightOperation = 'SUMMARY' | 'KEY_POINTS' | 'NOTES' | 'QUESTIONS';
export type SkillState = 'KNOWN' | 'UNVERIFIED' | 'MISSING';
export type ActivityStatus = 'SUCCESS' | 'REJECTED' | 'FAILED';
export type NotificationType =
  | 'OVERDUE_TASK'
  | 'DEADLINE_SOON'
  | 'GOAL_DEADLINE'
  | 'GOAL_COMPLETED'
  | 'AI_ACTION'
  | 'SYSTEM';

export interface SkillResponse {
  id: number;
  skill: string;
  level: string;
  verified: boolean;
  source: string;
}

export interface UserResponse {
  id: number;
  email: string;
  displayName: string;
  headline: string | null;
  createdAt: string;
  skills: SkillResponse[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserResponse;
}

export interface GoalResponse {
  id: number;
  title: string;
  description: string | null;
  deadline: string | null;
  priority: Priority;
  status: GoalStatus;
  notes: string | null;
  totalTasks: number;
  completedTasks: number;
  progressPercent: number;
  overdue: boolean;
  openTaskTitles: string[];
  createdAt: string;
  updatedAt: string;
}

export interface TaskResponse {
  id: number;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: Priority;
  dueDate: string | null;
  goalId: number | null;
  goalTitle: string | null;
  tags: string[];
  overdue: boolean;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}

export interface DocumentResponse {
  id: number;
  name: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  extractedChars: number;
  chunkCount: number;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentContentResponse {
  id: number;
  name: string;
  text: string;
  truncated: boolean;
  totalChars: number;
}

export interface DocumentInsightResponse {
  documentId: number;
  documentName: string;
  operation: InsightOperation;
  content: string;
  provider: string;
  generatedAt: string;
}

export interface AnswerSource {
  documentId: number;
  documentName: string;
  chunkIndex: number;
  score: number;
  excerpt: string;
}

export interface AnswerResponse {
  question: string;
  answer: string;
  sources: AnswerSource[];
  grounded: boolean;
  provider: string;
  remoteProvider: boolean;
}

export interface ReindexResponse {
  documentsProcessed: number;
  chunksEmbedded: number;
  provider: string;
}

export interface SkillMatch {
  skill: string;
  status: SkillState;
  required: boolean;
  userLevel: string | null;
}

export interface JobAnalysisResponse {
  id: number;
  jobTitle: string | null;
  company: string | null;
  experience: string | null;
  education: string | null;
  requiredSkills: string[];
  preferredSkills: string[];
  technologies: string[];
  responsibilities: string[];
  skillMatches: SkillMatch[];
  matchScore: number;
  missingSkills: string[];
  gapAnalysis: string;
  interviewTopics: string[];
  learningPlan: string[];
  interviewQuestions: string[];
  createdAt: string;
}

export interface ResearchResponse {
  id: number;
  topic: string;
  overview: string;
  keyFindings: string[];
  concepts: string[];
  recommendations: string[];
  sources: string[];
  summary: string;
  grounded: boolean;
  disclosure: string;
  provider: string;
  createdAt: string;
}

export interface AiActivityResponse {
  id: number;
  command: string;
  intent: string;
  status: ActivityStatus;
  resultSummary: string;
  createdAt: string;
}

export interface CommandResultResponse {
  activityId: number;
  command: string;
  intent: string;
  status: ActivityStatus;
  summary: string;
  steps: string[];
  data: Record<string, unknown>;
  parsedBy: string;
  rationale: string;
  provider: string;
}

export interface ChatResponse {
  reply: string;
  grounded: boolean;
  sources: AnswerSource[];
  provider: string;
  remoteProvider: boolean;
}

export interface NotificationResponse {
  id: number;
  eventType: NotificationType;
  title: string;
  message: string | null;
  read: boolean;
  createdAt: string;
}

export interface DashboardResponse {
  displayName: string;
  todayTasks: TaskResponse[];
  overdueTasks: TaskResponse[];
  upcomingTasks: TaskResponse[];
  activeGoals: GoalResponse[];
  upcomingDeadlines: { kind: string; refId: number; title: string; date: string; daysUntil: number }[];
  recentDocuments: DocumentResponse[];
  recentAiActivity: AiActivityResponse[];
  recentJobAnalyses: { id: number; jobTitle: string | null; company: string | null; matchScore: number; createdAt: string }[];
  unreadNotifications: NotificationResponse[];
  unreadNotificationCount: number;
  stats: {
    totalTasks: number;
    completedTasks: number;
    openTasks: number;
    overdueTasks: number;
    activeGoals: number;
    completionRate: number;
    completedLast7Days: number;
    documentsIndexed: number;
    aiActionsLast7Days: number;
  };
}

export interface DayPoint {
  date: string;
  count: number;
}

export interface AnalyticsResponse {
  days: number;
  totalTasks: number;
  completedTasks: number;
  openTasks: number;
  overdueTasks: number;
  cancelledTasks: number;
  completionRate: number;
  activeGoals: number;
  completedGoals: number;
  documents: number;
  indexedChunks: number;
  jobAnalyses: number;
  researchReports: number;
  aiActions: number;
  tasksCompleted: DayPoint[];
  tasksCreated: DayPoint[];
  aiActivity: DayPoint[];
  goalProgress: { id: number; title: string; status: GoalStatus; progressPercent: number; totalTasks: number; completedTasks: number; overdue: boolean }[];
  tasksByStatus: { label: string; count: number }[];
  tasksByPriority: { label: string; count: number }[];
}

export interface AiStatusResponse {
  provider: string;
  remote: boolean;
  supportsStructuredOutput: boolean;
  embeddingDimensions: number;
  supportedIntents: string[];
}

export interface HealthResponse {
  status: string;
  application: string;
  aiProvider: string;
  timestamp: string;
}
