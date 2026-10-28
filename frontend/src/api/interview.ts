import http from './http'

export interface QuestionView {
  id: number
  seq: number
  type: string
  difficulty: number
  content: string
  knowledgeTags: string[]
  referencePoints: string[]
  status: string
  followUpCount: number
  maxFollowUps: number
  hintCount: number
}

export interface InterviewSession {
  id: number
  jobTemplateId: number
  jobTitle: string
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  mode: string
  status: string
  currentQuestionId: number | null
  createdAt: string
  questions: QuestionView[]
}

export interface MessageView {
  id: number
  sessionId: number
  questionId: number | null
  role: string
  messageType: string
  content: string
  createdAt: string
}

export interface CreateInterviewRequest {
  jobTemplateId: number
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  mode: string
}

export interface ReportDimensions {
  accuracy: number
  depth: number
  project: number
  expression: number
  match: number
}

export interface QuestionReview {
  questionId: number
  seq: number
  type: string
  question: string
  answer: string
  promptType: string | null
  promptText: string | null
  correctness: string
  score: number
  comment: string
  missingPoints: string[]
}

export interface InterviewReport {
  id: number
  sessionId: number
  jobTitle: string
  mode: string
  overallScore: number
  dimensions: ReportDimensions
  summary: string
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  status: string
  createdAt: string
  questionReviews: QuestionReview[]
}

export interface InterviewHistoryItem {
  sessionId: number
  jobTitle: string
  mode: string
  status: string
  totalScore: number | null
  createdAt: string
  hasReport: boolean
}

export interface ScorePoint {
  date: string
  score: number
  jobTitle: string
}

export interface StatsOverview {
  totalInterviews: number
  completedInterviews: number
  averageScore: number
  bestScore: number
  dimensionAverages: ReportDimensions
  scoreTrend: ScorePoint[]
}

export interface QuestionBankItem {
  id: number
  category: string
  level: string
  tags: string[]
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
}

export interface WeaknessView {
  tag: string
  count: number
  lastSeen: string
  recommendedQuestions: QuestionBankItem[]
}

export interface AnswerEvaluationView {
  id: number
  candidateMessageId: number
  questionId: number
  promptText: string | null
  promptType: string | null
  correctness: 'CORRECT' | 'PARTIAL' | 'INSUFFICIENT' | 'INCORRECT'
  score: number
  comment: string
  missingPoints: string[]
  confidence: number
  createdAt: string
}

export interface HintResult {
  messageId: number
  hint: string
}

export interface QuotaView {
  plan: string
  sessionLimit: number
  sessionUsed: number
  remainingSessions: number
  tokenLimit: number
  tokenUsed: number
  resetAt: string | null
}

export interface AdminQuestion {
  id: number
  category: string
  level: string
  tags: string[]
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
  trackIds: number[]
  trackNames: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminQuestionRequest {
  category: string
  level: string
  tags: string
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
  trackIds?: number[]
}

export interface AdminTrack {
  id: number
  code: string
  name: string
  roleFamily: string
  primaryLanguage: string
  level: string
  description: string
  rubricCode: string
  status: string
  sortOrder: number
  questionCount: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface StreamHandlers {
  onMeta?: (meta: {
    candidateMessageId: number
    interviewerMessageId: number
    questionId: number
    replyType: string
  }) => void
  onDelta?: (text: string) => void
  onEvaluation?: (evaluation: AnswerEvaluationView) => void
  onDone?: (done: { sessionStatus: string; currentQuestionId: number | null; completed: boolean }) => void
  onError?: (message: string) => void
}

export const interviewApi = {
  create: (data: CreateInterviewRequest) =>
    http.post<never, InterviewSession>('/interviews', data, { timeout: 60000 }),

  get: (id: number) => http.get<never, InterviewSession>(`/interviews/${id}`),

  list: () => http.get<never, InterviewSession[]>('/interviews'),

  generateReport: (id: number) => http.post<never, InterviewReport>(`/interviews/${id}/report`),

  getReport: (id: number) => http.get<never, InterviewReport>(`/interviews/${id}/report`),

  history: () => http.get<never, InterviewHistoryItem[]>('/interviews/history'),

  stats: () => http.get<never, StatsOverview>('/stats/overview'),

  weaknesses: () => http.get<never, WeaknessView[]>('/stats/weaknesses'),

  start: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/start`),

  messages: (id: number) => http.get<never, MessageView[]>(`/interviews/${id}/messages`),

  evaluations: (id: number) => http.get<never, AnswerEvaluationView[]>(`/interviews/${id}/evaluations`),

  hint: (id: number) => http.post<never, HintResult>(`/interviews/${id}/hint`),

  skip: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/skip`),

  finish: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/finish`)
}

export const quotaApi = {
  me: () => http.get<never, QuotaView>('/quota/me')
}

export const adminQuestionApi = {
  page: (params: { page: number; size: number; keyword?: string; trackId?: number }) =>
    http.get<never, PageResult<AdminQuestion>>('/admin/questions', { params }),

  get: (id: number) => http.get<never, AdminQuestion>(`/admin/questions/${id}`),

  create: (data: AdminQuestionRequest) =>
    http.post<never, AdminQuestion>('/admin/questions', data),

  update: (id: number, data: AdminQuestionRequest) =>
    http.put<never, AdminQuestion>(`/admin/questions/${id}`, data),

  remove: (id: number) => http.delete<never, void>(`/admin/questions/${id}`)
}

export const adminTrackApi = {
  list: () => http.get<never, AdminTrack[]>('/admin/tracks'),

  bind: (trackId: number, questionIds: number[]) =>
    http.post<never, void>(`/admin/tracks/${trackId}/questions`, { questionIds }),

  unbind: (trackId: number, questionId: number) =>
    http.delete<never, void>(`/admin/tracks/${trackId}/questions/${questionId}`)
}

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

export async function streamAnswer(
  sessionId: number,
  questionId: number,
  content: string,
  handlers: StreamHandlers
): Promise<void> {
  const token = localStorage.getItem(ACCESS_KEY)
  const response = await fetch(`/api/interviews/${sessionId}/answers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ questionId, content })
  })

  if (response.status === 401) {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
    window.location.assign('/login')
    throw new Error('登录已过期')
  }

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || '提交回答失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    for (const raw of events) {
      const lines = raw.split('\n')
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('\n')
      if (!event || !data) continue

      const payload = JSON.parse(data)
      if (event === 'meta') handlers.onMeta?.(payload)
      else if (event === 'delta') handlers.onDelta?.(payload.text)
      else if (event === 'evaluation') handlers.onEvaluation?.(payload)
      else if (event === 'done') handlers.onDone?.(payload)
      else if (event === 'error') handlers.onError?.(payload.message)
    }
  }
}
