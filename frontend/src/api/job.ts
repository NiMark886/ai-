import http from './http'

export interface JobTemplate {
  id: number
  title: string
  level: string
  category: string
  jdText: string
}

export const jobApi = {
  list: () => http.get<never, JobTemplate[]>('/job-templates')
}
