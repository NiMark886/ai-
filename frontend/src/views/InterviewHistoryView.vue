<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { interviewApi, type InterviewHistoryItem, type StatsOverview } from '@/api/interview'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const stats = ref<StatsOverview | null>(null)
const history = ref<InterviewHistoryItem[]>([])
const loading = ref(true)
const trendEl = ref<HTMLElement | null>(null)
const dimensionEl = ref<HTMLElement | null>(null)
let trendChart: ReturnType<typeof echarts.init> | null = null
let dimensionChart: ReturnType<typeof echarts.init> | null = null

onMounted(async () => {
  window.addEventListener('resize', resize)
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  dimensionChart?.dispose()
})

async function load() {
  loading.value = true
  try {
    const [statsData, historyData] = await Promise.all([
      interviewApi.stats(),
      interviewApi.history()
    ])
    stats.value = statsData
    history.value = historyData
    await nextTick()
    renderTrend()
    renderDimensions()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function renderTrend() {
  if (!trendEl.value || !stats.value?.scoreTrend?.length) return
  trendChart?.dispose()
  trendChart = echarts.init(trendEl.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: stats.value.scoreTrend.map((point) => point.date),
      axisLine: { lineStyle: { color: '#d6e4f0' } },
      axisLabel: { color: '#7f8c9d' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: '#eef4fb' } },
      axisLabel: { color: '#7f8c9d' }
    },
    series: [{
      type: 'line',
      data: stats.value.scoreTrend.map((point) => point.score),
      smooth: true,
      symbolSize: 8,
      lineStyle: { color: '#2e75b6', width: 3 },
      itemStyle: { color: '#2e75b6' },
      areaStyle: { color: 'rgba(46,117,182,0.12)' }
    }]
  })
}

function renderDimensions() {
  if (!dimensionEl.value || !stats.value) return
  const d = stats.value.dimensionAverages
  const labels = [
    ['技术准确性', d.accuracy],
    ['技术深度', d.depth],
    ['项目表现', d.project],
    ['表达逻辑', d.expression],
    ['岗位匹配', d.match]
  ] as Array<[string, number]>
  dimensionChart?.dispose()
  dimensionChart = echarts.init(dimensionEl.value)
  dimensionChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 88, right: 24, top: 12, bottom: 20 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: '#eef4fb' } },
      axisLabel: { color: '#7f8c9d' }
    },
    yAxis: {
      type: 'category',
      data: labels.map((item) => item[0]),
      axisLine: { lineStyle: { color: '#d6e4f0' } },
      axisLabel: { color: '#5b6b7f' }
    },
    series: [{
      type: 'bar',
      data: labels.map((item) => item[1]),
      barWidth: 14,
      itemStyle: { color: '#2e75b6', borderRadius: [0, 7, 7, 0] }
    }]
  })
}

function resize() {
  trendChart?.resize()
  dimensionChart?.resize()
}

function openReport(item: InterviewHistoryItem) {
  router.push(`/interviews/${item.sessionId}/report`)
}

function goHome() {
  router.push('/')
}

function modeLabel(mode: string) {
  return mode === 'PRACTICE' ? '练习' : '模拟'
}

function statusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'IN_PROGRESS') return '进行中'
  if (status === 'READY') return '待开始'
  return '已放弃'
}
</script>

<template>
  <div class="history-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <div class="brand">OfferPilot</div>
      <span class="status">成长记录</span>
    </header>

    <main v-loading="loading" class="content">
      <section class="hero">
        <p class="eyebrow">GROWTH TRACKER</p>
        <h1>成长记录</h1>
        <p class="subtitle">用数据看见每一次面试的进步。</p>
      </section>

      <section v-if="stats" class="stat-grid">
        <div class="stat-card"><span>{{ stats.totalInterviews }}</span><small>总场次</small></div>
        <div class="stat-card"><span>{{ stats.completedInterviews }}</span><small>完成场次</small></div>
        <div class="stat-card"><span>{{ stats.averageScore }}</span><small>平均分</small></div>
        <div class="stat-card"><span>{{ stats.bestScore }}</span><small>最高分</small></div>
      </section>

      <section class="chart-grid">
        <div class="card">
          <h2>分数趋势</h2>
          <div v-if="stats?.scoreTrend?.length" ref="trendEl" class="chart"></div>
          <el-empty v-else description="完成面试后这里会出现分数趋势" :image-size="80" />
        </div>
        <div class="card">
          <h2>五维平均</h2>
          <div ref="dimensionEl" class="chart"></div>
        </div>
      </section>

      <section class="card">
        <h2>面试历史</h2>
        <el-table v-if="history.length" :data="history" style="width: 100%">
          <el-table-column prop="jobTitle" label="岗位" min-width="200" />
          <el-table-column label="模式" width="90">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="总分" width="90">
            <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="130">
            <template #default="{ row }">{{ row.createdAt?.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'COMPLETED'"
                link
                type="primary"
                @click="openReport(row)"
              >
                查看报告
              </el-button>
              <span v-else class="muted">未完成</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="还没有面试记录" :image-size="100" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.history-page {
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
  max-width: 1100px;
  margin: 0 auto;
  padding: 36px 28px 72px;
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
  font-size: clamp(28px, 3vw, 40px);
}

.subtitle {
  margin: 0 0 28px;
  color: #7f8c9d;
  font-size: 14px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 22px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #e4ecf5;
  box-shadow: 0 10px 28px rgba(31, 78, 121, 0.05);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-card span {
  color: #17375e;
  font-size: 32px;
  font-weight: 800;
}

.stat-card small {
  color: #8a97a8;
  font-size: 13px;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}

.card {
  background: #fff;
  border: 1px solid #e4ecf5;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 10px 28px rgba(31, 78, 121, 0.05);
}

.card h2 {
  margin: 0 0 18px;
  color: #17375e;
  font-size: 19px;
}

.chart {
  width: 100%;
  height: 300px;
}

.muted {
  color: #a0aab6;
  font-size: 13px;
}

@media (max-width: 860px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 24px 16px 56px;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
