<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { jobApi, type JobTemplate } from '@/api/job'
import type { InterviewSession } from '@/api/interview'
import InterviewSetupDialog from '@/components/InterviewSetupDialog.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const jobs = ref<JobTemplate[]>([])
const loading = ref(true)
const setupVisible = ref(false)
const selectedJob = ref<JobTemplate | null>(null)

onMounted(async () => {
  try {
    if (!auth.user) {
      await auth.fetchMe()
    }
    jobs.value = await jobApi.list()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
})

async function handleLogout() {
  await auth.logout()
  router.replace({ name: 'login' })
}

function startInterview(job: JobTemplate) {
  selectedJob.value = job
  setupVisible.value = true
}

function handleInterviewCreated(session: InterviewSession) {
  setupVisible.value = false
  router.push(`/interviews/${session.id}`)
}

function focusTags(job: JobTemplate): string[] {
  if (job.category.includes('AI')) {
    return ['RAG', 'Agent', '提示词']
  }
  if (job.category.includes('微服务')) {
    return ['Nacos', 'Gateway', '分布式']
  }
  return ['基础八股', '项目深挖', '场景设计']
}

function sampleTopics(job: JobTemplate): string[] {
  if (job.category.includes('AI')) {
    return ['RAG 检索与召回优化', 'Agent 工具调用设计', 'Token 成本与效果评估']
  }
  if (job.category.includes('微服务')) {
    return ['服务拆分与边界', 'Nacos 注册与配置', 'Gateway 限流与链路治理']
  }
  if (job.level.includes('校招')) {
    return ['HashMap 与并发容器', '线程池与 JVM 基础', '项目难点与个人贡献']
  }
  return ['项目架构与技术取舍', 'MySQL / Redis 性能优化', '线上故障排查与复盘']
}
</script>

<template>
  <div class="home-page">
    <header class="topbar">
      <div class="brand">OfferPilot</div>
      <div class="user-area">
        <span class="user-name">{{ auth.user?.nickname || '同学' }}</span>
        <el-button link type="primary" @click="router.push('/history')">成长记录</el-button>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">P0 · WEEK 2</p>
          <h1>选择你的目标岗位</h1>
          <p class="subtitle">
            AI 将根据岗位与简历生成一场带追问的模拟面试。先选一个方向，开始训练。
          </p>
        </div>
        <div class="hero-badge">
          <span>{{ jobs.length || 4 }}</span>
          <small>岗位模板</small>
        </div>
      </section>

      <el-skeleton v-if="loading" :rows="5" animated />

      <section v-else class="job-grid">
        <article v-for="(job, index) in jobs" :key="job.id" class="job-card">
          <span class="card-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="job-body">
            <div class="job-meta">
              <span>{{ job.level }}</span>
              <span>{{ job.category }}</span>
            </div>
            <h2>{{ job.title }}</h2>
            <p class="job-desc">{{ job.jdText }}</p>
            <div class="focus-row">
              <span v-for="tag in focusTags(job)" :key="tag">{{ tag }}</span>
            </div>
            <p class="job-facts">20–30 分钟 · 6–10 题 · 五维评分报告</p>
            <div class="sample-block">
              <p class="sample-label">本场会问</p>
              <ul class="sample-list">
                <li v-for="topic in sampleTopics(job)" :key="topic">{{ topic }}</li>
              </ul>
            </div>
          </div>
          <div class="card-footer">
            <el-button type="primary" size="large" @click="startInterview(job)">
              开始面试
            </el-button>
            <span class="coming">约 1 分钟</span>
          </div>
        </article>
      </section>

      <el-empty v-if="!loading && jobs.length === 0" description="暂无岗位模板" />
    </main>

    <InterviewSetupDialog
      v-model="setupVisible"
      :job="selectedJob"
      @created="handleInterviewCreated"
    />
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.82);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 20px;
  font-weight: 800;
  color: #17375e;
  letter-spacing: 0.04em;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  color: #5b6b7f;
  font-size: 14px;
}

.content {
  width: 100%;
  min-height: calc(100vh - 68px);
  padding: 20px 32px 32px;
  display: flex;
  flex-direction: column;
}

.hero {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero h1 {
  margin: 0 0 10px;
  color: #17375e;
  font-size: clamp(30px, 3.2vw, 42px);
  line-height: 1.15;
}

.subtitle {
  margin: 0;
  max-width: 720px;
  color: #6b7a8c;
  font-size: 15px;
  line-height: 1.8;
}

.hero-badge {
  flex: none;
  width: 96px;
  height: 96px;
  border-radius: 22px;
  background: linear-gradient(145deg, #1f4e79, #2e75b6);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(31, 78, 121, 0.22);
}

.hero-badge span {
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
}

.hero-badge small {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}

.job-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.job-card {
  position: relative;
  height: 100%;
  min-height: 0;
  padding: 30px 34px;
  border-radius: 24px;
  border: 1px solid #e4ecf5;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 252, 255, 0.94));
  box-shadow: 0 10px 30px rgba(31, 78, 121, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.job-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 6px;
  background: var(--accent);
  z-index: 0;
}

.job-card::after {
  content: '';
  position: absolute;
  right: -90px;
  bottom: -110px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: var(--accent-soft);
  z-index: 0;
}

.job-card > * {
  position: relative;
  z-index: 1;
}

.job-body {
  flex: 1;
  min-height: 0;
  max-width: 920px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.focus-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.focus-row span {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--accent);
  background: #fff;
  color: var(--accent);
  font-size: 12px;
}

.job-facts {
  margin: 14px 0 0;
  color: #8a97a8;
  font-size: 12px;
  letter-spacing: 0.02em;
}

.sample-block {
  max-width: 780px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #e4ecf5;
}

.sample-label {
  margin: 0 0 10px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.sample-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 7px;
}

.sample-list li {
  position: relative;
  padding-left: 16px;
  color: #5b6b7f;
  font-size: 13px;
  line-height: 1.6;
}

.sample-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.6;
}

.job-card:nth-child(1) {
  --accent: #2e75b6;
  --accent-soft: rgba(46, 117, 182, 0.1);
}

.job-card:nth-child(2) {
  --accent: #2f9e8f;
  --accent-soft: rgba(47, 158, 143, 0.1);
}

.job-card:nth-child(3) {
  --accent: #c55a11;
  --accent-soft: rgba(197, 90, 17, 0.1);
}

.job-card:nth-child(4) {
  --accent: #6c5ce7;
  --accent-soft: rgba(108, 92, 231, 0.1);
}

.job-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent);
  box-shadow: 0 20px 44px rgba(31, 78, 121, 0.13);
}

.card-index {
  position: absolute;
  top: 24px;
  right: 30px;
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  color: var(--accent);
  opacity: 0.12;
  pointer-events: none;
}

.job-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding-right: 80px;
}

.job-meta span {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
}

.job-card h2 {
  max-width: 780px;
  margin: 0 0 14px;
  padding-right: 60px;
  color: #17375e;
  font-size: clamp(24px, 2vw, 32px);
  line-height: 1.3;
}

.job-desc {
  max-width: 780px;
  margin: 0;
  color: #6b7a8c;
  font-size: 16px;
  line-height: 1.9;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: none;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 24px;
}

.job-card :deep(.el-button--primary) {
  height: 46px;
  padding: 0 28px;
  border-radius: 12px;
  background: var(--accent);
  border-color: var(--accent);
  font-size: 15px;
}

.job-card :deep(.el-button--primary:hover) {
  background: var(--accent);
  border-color: var(--accent);
  opacity: 0.9;
}

.coming {
  color: #a0aab6;
  font-size: 12px;
}

@media (max-width: 1000px) {
  .content {
    padding: 20px 24px 32px;
  }

  .job-card {
    padding: 24px;
  }

  .card-index {
    font-size: 52px;
    top: 20px;
    right: 22px;
  }
}

@media (max-height: 820px) and (min-width: 761px) {
  .content {
    min-height: auto;
  }

  .job-card {
    min-height: 300px;
  }
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 20px;
  }

  .content {
    min-height: auto;
    padding: 24px 18px 44px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-badge {
    display: none;
  }

  .job-grid {
    flex: none;
    grid-template-columns: 1fr;
    grid-template-rows: none;
  }

  .job-card {
    height: auto;
    min-height: 300px;
    padding: 26px 22px;
  }

  .job-body {
    justify-content: flex-start;
  }

  .job-card h2 {
    padding-right: 50px;
  }

  .job-desc {
    -webkit-line-clamp: 4;
  }
}
</style>
