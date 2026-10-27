<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import type { JobTemplate } from '@/api/job'
import { resumeApi, type ResumeView } from '@/api/resume'
import { interviewApi, type InterviewSession } from '@/api/interview'

const props = defineProps<{
  modelValue: boolean
  job: JobTemplate | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [session: InterviewSession]
}>()

const resumes = ref<ResumeView[]>([])
const loading = ref(false)
const uploading = ref(false)
const submitting = ref(false)

const form = reactive({
  resumeId: undefined as number | undefined,
  difficulty: 'STANDARD',
  durationMinutes: 30,
  persona: 'STANDARD'
})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const selectedResume = computed(() =>
  resumes.value.find((item) => item.id === form.resumeId)
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    await loadResumes()
  }
)

async function loadResumes() {
  loading.value = true
  try {
    resumes.value = await resumeApi.list()
    if (!form.resumeId && resumes.value.length > 0) {
      form.resumeId = resumes.value[0].id
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const resume = await resumeApi.upload(options.file as File)
    resumes.value.unshift(resume)
    form.resumeId = resume.id
    options.onSuccess(resume)
    ElMessage.success(
      resume.parseStatus === 'PARSED' ? '简历上传并解析成功' : '简历已上传，但解析失败'
    )
  } catch (error) {
    options.onError(error as never)
  } finally {
    uploading.value = false
  }
}

async function submit() {
  if (!props.job) return
  if (!form.resumeId) {
    ElMessage.warning('请先上传或选择一份简历')
    return
  }

  submitting.value = true
  try {
    const session = await interviewApi.create({
      jobTemplateId: props.job.id,
      resumeId: form.resumeId,
      difficulty: form.difficulty,
      durationMinutes: form.durationMinutes,
      persona: form.persona
    })
    ElMessage.success('面试已创建')
    emit('created', session)
    visible.value = false
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" width="640px" :close-on-click-modal="false">
    <template #header>
      <div class="dialog-header">
        <span>创建模拟面试</span>
        <small v-if="job">{{ job.title }}</small>
      </div>
    </template>

    <div v-loading="loading" class="setup-body">
      <section class="field-block">
        <div class="field-title">
          <span>选择简历</span>
          <el-upload
            :show-file-list="false"
            :http-request="handleUpload"
            accept=".pdf,.docx"
            :disabled="uploading"
          >
            <el-button size="small" :loading="uploading">上传 PDF / DOCX</el-button>
          </el-upload>
        </div>

        <el-empty
          v-if="!loading && resumes.length === 0"
          description="还没有简历，先上传一份"
          :image-size="80"
        />
        <el-select
          v-else
          v-model="form.resumeId"
          placeholder="选择一份简历"
          style="width: 100%"
        >
          <el-option
            v-for="resume in resumes"
            :key="resume.id"
            :label="`${resume.fileName} · ${resume.parseStatus === 'PARSED' ? '已解析' : '解析失败'}`"
            :value="resume.id"
          />
        </el-select>

        <div v-if="selectedResume?.profile" class="resume-summary">
          <div class="summary-row">
            <strong>{{ selectedResume.profile.name || '候选人' }}</strong>
            <span v-if="selectedResume.profile.yearsOfExperience">
              {{ selectedResume.profile.yearsOfExperience }} 年经验
            </span>
          </div>
          <div class="skill-tags">
            <el-tag
              v-for="skill in (selectedResume.profile.skills ?? []).slice(0, 8)"
              :key="skill"
              size="small"
              effect="plain"
            >
              {{ skill }}
            </el-tag>
          </div>
          <p v-if="selectedResume.profile.projects?.length" class="project-line">
            项目：{{ selectedResume.profile.projects?.map((p) => p.name).join('、') }}
          </p>
        </div>
      </section>

      <section class="field-block">
        <div class="field-title"><span>难度</span></div>
        <el-radio-group v-model="form.difficulty">
          <el-radio-button value="EASY">简单</el-radio-button>
          <el-radio-button value="STANDARD">标准</el-radio-button>
          <el-radio-button value="HARD">挑战</el-radio-button>
        </el-radio-group>
      </section>

      <section class="field-block">
        <div class="field-title"><span>时长</span></div>
        <el-radio-group v-model="form.durationMinutes">
          <el-radio-button :value="20">20 分钟</el-radio-button>
          <el-radio-button :value="30">30 分钟</el-radio-button>
          <el-radio-button :value="45">45 分钟</el-radio-button>
        </el-radio-group>
      </section>

      <section class="field-block">
        <div class="field-title"><span>面试官风格</span></div>
        <el-radio-group v-model="form.persona">
          <el-radio-button value="GENTLE">温和</el-radio-button>
          <el-radio-button value="STANDARD">标准</el-radio-button>
          <el-radio-button value="PRESSURE">压力面</el-radio-button>
        </el-radio-group>
      </section>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        生成面试蓝图
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #17375e;
  font-size: 20px;
  font-weight: 700;
}

.dialog-header small {
  color: #7f8c9d;
  font-size: 13px;
  font-weight: 400;
}

.setup-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.field-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #17375e;
  font-size: 14px;
  font-weight: 700;
}

.resume-summary {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f5f8fc;
  border: 1px solid #e4ecf5;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #17375e;
}

.summary-row span {
  color: #7f8c9d;
  font-size: 12px;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.project-line {
  margin: 10px 0 0;
  color: #6b7a8c;
  font-size: 13px;
  line-height: 1.6;
}
</style>
