<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminQuestionApi,
  adminTrackApi,
  type AdminQuestion,
  type AdminQuestionRequest,
  type AdminTrack
} from '@/api/interview'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const records = ref<AdminQuestion[]>([])
const tracks = ref<AdminTrack[]>([])
const activeTrackId = ref<number | null>(null)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const dialogVisible = ref(false)

const form = reactive<AdminQuestionRequest>({
  category: 'Java 基础',
  level: '校招',
  tags: '',
  difficulty: 1,
  question: '',
  referenceAnswer: '',
  source: '个人整理',
  status: 'ACTIVE',
  trackIds: []
})

const difficultyLabels: Record<number, string> = { 1: '简单', 2: '中等', 3: '困难' }

onMounted(async () => {
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      router.replace('/login')
      return
    }
  }
  if (auth.user?.role !== 'ADMIN') {
    ElMessage.warning('需要管理员权限')
    router.replace('/')
    return
  }
  await loadTracks()
  await load()
})

async function loadTracks() {
  try {
    const data = await adminTrackApi.list()
    tracks.value = data.filter((track) => track.status !== 'INACTIVE')
  } catch {
    // 拦截器已提示
  }
}

async function load() {
  loading.value = true
  try {
    const data = await adminQuestionApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      trackId: activeTrackId.value ?? undefined
    })
    records.value = data.records
    total.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function switchTrack(trackId: number | null) {
  activeTrackId.value = trackId
  page.value = 1
  load()
}

function openCreate() {
  Object.assign(form, {
    category: defaultCategory(),
    level: activeTrackId.value
      ? tracks.value.find((track) => track.id === activeTrackId.value)?.level ?? '校招'
      : '校招',
    tags: '',
    difficulty: 1,
    question: '',
    referenceAnswer: '',
    source: '个人整理',
    status: 'ACTIVE',
    trackIds: activeTrackId.value ? [activeTrackId.value] : []
  })
  dialogVisible.value = true
}

function defaultCategory(): string {
  const track = tracks.value.find((item) => item.id === activeTrackId.value)
  if (track?.primaryLanguage === 'JAVA') return 'Java 基础'
  if (track?.primaryLanguage === 'JS_TS') return 'JavaScript'
  return '基础'
}

async function submit() {
  if (!form.question.trim() || !form.referenceAnswer.trim() || !form.tags.trim()) {
    ElMessage.warning('题目、参考答案、标签不能为空')
    return
  }
  try {
    await adminQuestionApi.create({ ...form })
    ElMessage.success('题目已创建')
    dialogVisible.value = false
    await load()
    await loadTracks()
  } catch {
    // 拦截器已提示
  }
}

async function toggleStatus(row: AdminQuestion) {
  const nextStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const action = nextStatus === 'ACTIVE' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${action}「${row.question}」吗？${nextStatus === 'INACTIVE' ? '禁用后该题不会再被面试检索。' : ''}`,
      `${action}题目`,
      { type: 'warning' }
    )
    await adminQuestionApi.setStatus(row.id, nextStatus)
    ElMessage.success(`题目已${action}`)
    await load()
    await loadTracks()
  } catch {
    // 用户取消或接口失败
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="admin-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <div class="brand">OfferPilot · 题库管理</div>
      <span class="status">ADMIN</span>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">QUESTION BANK</p>
          <h1>题库管理</h1>
          <p class="subtitle">维护面试题目、参考答案和标签，供面试蓝图检索使用。</p>
        </div>
        <el-button type="primary" size="large" @click="openCreate">新增题目</el-button>
      </section>

      <section class="track-tabs">
        <button
          type="button"
          class="track-tab"
          :class="{ active: activeTrackId === null }"
          @click="switchTrack(null)"
        >
          全部题目
        </button>
        <button
          v-for="track in tracks"
          :key="track.id"
          type="button"
          class="track-tab"
          :class="{ active: activeTrackId === track.id }"
          @click="switchTrack(track.id)"
        >
          {{ track.name }}
          <small>{{ track.questionCount }}</small>
        </button>
      </section>

      <section class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索题目 / 分类 / 标签"
          clearable
          style="max-width: 360px"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">搜索</el-button>
      </section>

      <el-table v-loading="loading" :data="records" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="question" label="题目" min-width="260" show-overflow-tooltip />
        <el-table-column label="所属题库" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="name in row.trackNames"
              :key="name"
              size="small"
              effect="plain"
              style="margin-right: 4px"
            >
              {{ name }}
            </el-tag>
            <span v-if="!row.trackNames?.length" class="muted">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain" style="margin-right: 4px">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="80">
          <template #default="{ row }">{{ difficultyLabels[row.difficulty] || row.difficulty }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              link
              :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="changePage"
        />
      </div>
    </main>

    <el-dialog v-model="dialogVisible" title="新增题目" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="分类">
            <el-input v-model="form.category" />
          </el-form-item>
          <el-form-item label="适用级别">
            <el-input v-model="form.level" />
          </el-form-item>
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio-button :value="1">简单</el-radio-button>
              <el-radio-button :value="2">中等</el-radio-button>
              <el-radio-button :value="3">困难</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button value="ACTIVE">启用</el-radio-button>
              <el-radio-button value="INACTIVE">禁用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="所属题库">
          <el-select
            v-model="form.trackIds"
            multiple
            placeholder="选择该题目所属的岗位题库"
            style="width: 100%"
          >
            <el-option
              v-for="track in tracks"
              :key="track.id"
              :label="`${track.name} · ${track.level}`"
              :value="track.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签（逗号分隔）">
          <el-input v-model="form.tags" placeholder="Java,HashMap,集合" />
        </el-form-item>
        <el-form-item label="题目">
          <el-input v-model="form.question" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="form.referenceAnswer" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
}

.brand {
  color: #17375e;
  font-size: 20px;
  font-weight: 800;
}

.back-link {
  border: 0;
  background: transparent;
  color: #2e75b6;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}

.status {
  color: #7f8c9d;
  font-size: 13px;
}

.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 36px 28px 72px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 38px);
}

.subtitle {
  margin: 0;
  color: #7f8c9d;
  font-size: 14px;
}

.track-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.track-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid #dbe6f2;
  background: #fff;
  color: #5b6b7f;
  font-size: 13px;
  cursor: pointer;
}

.track-tab small {
  color: #a0aab6;
  font-size: 11px;
}

.track-tab.active {
  border-color: #1f4e79;
  background: #1f4e79;
  color: #fff;
}

.track-tab.active small {
  color: rgba(255, 255, 255, 0.75);
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.muted {
  color: #a0aab6;
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 24px 16px 56px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
