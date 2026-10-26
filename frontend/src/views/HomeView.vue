<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jobApi, type JobTemplate } from '@/api/job'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const jobs = ref<JobTemplate[]>([])
const loading = ref(true)

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
  ElMessage.info(`「${job.title}」面试功能将在下一阶段开放`)
}
</script>

<template>
  <div class="home-page">
    <header class="topbar">
      <div class="brand">OfferPilot</div>
      <div class="user-area">
        <span class="user-name">{{ auth.user?.nickname || '同学' }}</span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">P0 · WEEK 1</p>
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
        <article v-for="job in jobs" :key="job.id" class="job-card">
          <div class="job-meta">
            <span>{{ job.level }}</span>
            <span>{{ job.category }}</span>
          </div>
          <h2>{{ job.title }}</h2>
          <p class="job-desc">{{ job.jdText }}</p>
          <div class="card-footer">
            <el-button type="primary" @click="startInterview(job)">开始面试</el-button>
            <span class="coming">W2 开放</span>
          </div>
        </article>
      </section>

      <el-empty v-if="!loading && jobs.length === 0" description="暂无岗位模板" />
    </main>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 48px;
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
  max-width: 1180px;
  margin: 0 auto;
  padding: 56px 32px 80px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 36px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero h1 {
  margin: 0 0 12px;
  color: #17375e;
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.15;
}

.subtitle {
  margin: 0;
  max-width: 560px;
  color: #6b7a8c;
  font-size: 15px;
  line-height: 1.8;
}

.hero-badge {
  flex: none;
  width: 112px;
  height: 112px;
  border-radius: 24px;
  background: linear-gradient(145deg, #1f4e79, #2e75b6);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(31, 78, 121, 0.22);
}

.hero-badge span {
  font-size: 36px;
  font-weight: 800;
  line-height: 1;
}

.hero-badge small {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}

.job-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.job-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e4ecf5;
  border-radius: 18px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  min-height: 250px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.job-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 34px rgba(31, 78, 121, 0.12);
}

.job-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.job-meta span {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 12px;
}

.job-card h2 {
  margin: 0 0 12px;
  color: #17375e;
  font-size: 20px;
  line-height: 1.4;
}

.job-desc {
  margin: 0;
  color: #6b7a8c;
  font-size: 14px;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 22px;
}

.coming {
  color: #a0aab6;
  font-size: 12px;
}

@media (max-width: 720px) {
  .topbar {
    padding: 0 20px;
  }

  .content {
    padding: 36px 20px 60px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-badge {
    display: none;
  }
}
</style>
