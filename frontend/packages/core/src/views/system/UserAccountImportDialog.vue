<template>
  <el-dialog
    :model-value="modelValue"
    title="批量导入账号"
    width="960px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <el-steps :active="step" finish-status="success" simple class="import-steps">
      <el-step title="上传文件" />
      <el-step title="校验预览" />
      <el-step title="导入结果" />
    </el-steps>

    <el-alert
      v-if="errorText"
      :title="errorText"
      type="error"
      show-icon
      :closable="false"
      class="import-alert"
    />

    <!-- 第一步：取文件 -->
    <div v-if="step === 0" class="import-step">
      <p class="education-context-text">
        一行 = 一个要新建的登录账号。登录账号全系统唯一，本机构已有的同号会跳过、被其他机构占用的必须换号；
        初始口令必填，留空不会被补成默认口令。新建的账号一律挂在当前登录账号所属机构下，本人首次登录会被要求改密。
      </p>
      <div class="table-toolbar">
        <el-button :icon="Download" :loading="templateDownloading" @click="handleDownloadTemplate">
          下载模板
        </el-button>
      </div>
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="handleFileChange"
        :on-exceed="handleFileExceed"
        :on-remove="handleFileRemove"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把填好的 xlsx 拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">只支持模板格式的 xlsx，内容须在第一个工作表，单次不超过 500 行</div>
        </template>
      </el-upload>
    </div>

    <!-- 第二步：逐行校验结果 -->
    <div v-else-if="step === 1" class="import-step">
      <div class="dashboard-grid import-stat-grid">
        <article class="stat-card">
          <div class="stat-label">总行数</div>
          <div class="stat-value">{{ preview?.total ?? 0 }}</div>
          <div class="stat-hint">整行为空的已剔除</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">校验通过</div>
          <div class="stat-value">{{ preview?.validCount ?? 0 }}</div>
          <div class="stat-hint">含本机构已存在、将跳过的行</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">需修正</div>
          <div class="stat-value">{{ preview?.errorCount ?? 0 }}</div>
          <div class="stat-hint">有错误时整批不导入</div>
        </article>
      </div>

      <div class="table-toolbar">
        <el-checkbox v-model="onlyProblems">只看待修正与有提示的行</el-checkbox>
      </div>

      <el-table
        :data="previewRows"
        border
        stripe
        row-key="rowNum"
        max-height="360"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="rowNum" label="行号" width="70" fixed="left" />
        <el-table-column prop="username" label="登录账号" width="160" fixed="left" />
        <el-table-column prop="displayName" label="显示名称" width="130" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="roleCodes" label="角色编码" min-width="170" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="说明" min-width="260">
          <template #default="{ row }">
            <span v-if="row.error" class="import-error-text">{{ row.error }}</span>
            <span v-else-if="row.notice" class="import-notice-text">{{ row.notice }}</span>
            <span v-else>可导入</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 第三步：导入结果 -->
    <div v-else class="import-step">
      <div class="dashboard-grid import-stat-grid">
        <article class="stat-card">
          <div class="stat-label">新建账号</div>
          <div class="stat-value">{{ committed?.createdCount ?? 0 }}</div>
          <div class="stat-hint">首次登录须改密</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">跳过</div>
          <div class="stat-value">{{ committed?.skippedCount ?? 0 }}</div>
          <div class="stat-hint">本机构已有同号，未做改动</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">总计</div>
          <div class="stat-value">{{ committed?.total ?? 0 }}</div>
          <div class="stat-hint">本次文件里的数据行</div>
        </article>
      </div>

      <p class="education-context-text">
        把「登录账号 + 初始口令」两列交给本人，不要替他登录。账号出现在用户管理列表即可分配角色、重置密码。
      </p>

      <el-table :data="committed?.rows ?? []" border stripe row-key="rowNum" max-height="320">
        <el-table-column prop="rowNum" label="行号" width="70" fixed="left" />
        <el-table-column prop="username" label="登录账号" width="160" fixed="left" />
        <el-table-column prop="displayName" label="显示名称" width="130" />
        <el-table-column prop="userId" label="账号ID" width="180" />
        <el-table-column label="结果" min-width="220">
          <template #default="{ row }">
            <span v-if="row.notice" class="import-notice-text">{{ row.notice }}</span>
            <span v-else>已新建</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button v-if="step === 2" type="primary" @click="emit('update:modelValue', false)">
        完成
      </el-button>
      <el-button v-if="step === 1" @click="backToUpload">上一步</el-button>
      <el-button
        v-if="step === 0"
        type="primary"
        :loading="previewing"
        :disabled="!file"
        @click="runPreview"
      >
        下一步：校验
      </el-button>
      <el-button
        v-if="step === 1"
        type="primary"
        :loading="committing"
        :disabled="!canCommit"
        @click="runCommit"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { isApiError } from '@cornerstone/core/api/request'
import {
  commitUserAccountImport,
  downloadUserAccountTemplate,
  previewUserAccountImport,
  type UserAccountImportResult,
  type UserAccountImportRowView,
} from '@cornerstone/core/api/userImport'

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 导入成功后通知宿主刷新用户列表 */
  imported: []
}>()

defineProps<{
  modelValue: boolean
}>()

const step = ref(0)
const file = ref<File | null>(null)
const errorText = ref('')
const previewing = ref(false)
const committing = ref(false)
const templateDownloading = ref(false)
const onlyProblems = ref(false)
const preview = ref<UserAccountImportResult | null>(null)
const committed = ref<UserAccountImportResult | null>(null)
const uploadRef = ref<UploadInstance>()

const canCommit = computed(() => (preview.value?.errorCount ?? 1) === 0)

const previewRows = computed(() => {
  const rows = preview.value?.rows ?? []
  return onlyProblems.value ? rows.filter((row) => row.error || row.notice) : rows
})

function messageOf(error: unknown, fallback: string) {
  return isApiError(error) ? error.message : fallback
}

function rowClassName({ row }: { row: UserAccountImportRowView }) {
  return row.error ? 'import-row-error' : ''
}

function handleFileChange(uploadFile: UploadFile) {
  file.value = uploadFile.raw ?? null
  errorText.value = ''
}

/** limit=1 时再次选文件走这里：清掉上一个再入列，否则界面一直显示首次选中的文件 */
function handleFileExceed(files: File[]) {
  const first = files[0]
  if (!first) {
    return
  }
  uploadRef.value?.clearFiles()
  // handleStart 要 EP 给文件编的 uid 作列表 key，同批只有一个文件，用时间戳即可
  Object.assign(first, { uid: Date.now() })
  uploadRef.value?.handleStart(first as UploadRawFile)
  file.value = first
  errorText.value = ''
}

function handleFileRemove() {
  file.value = null
  errorText.value = ''
}

async function handleDownloadTemplate() {
  templateDownloading.value = true
  try {
    await downloadUserAccountTemplate()
  } catch (error) {
    ElMessage.error(messageOf(error, '模板下载失败，请稍后重试'))
  } finally {
    templateDownloading.value = false
  }
}

/** 上传区挂在 v-if 上，回到第一步会重建为空白；不清掉旧文件会出现「看不见文件但仍能继续校验」 */
function backToUpload() {
  file.value = null
  preview.value = null
  step.value = 0
}

async function runPreview() {
  if (!file.value) {
    return
  }
  previewing.value = true
  errorText.value = ''
  try {
    preview.value = await previewUserAccountImport(file.value)
    step.value = 1
  } catch (error) {
    errorText.value = messageOf(error, '校验失败，请检查文件内容')
  } finally {
    previewing.value = false
  }
}

async function runCommit() {
  if (!file.value) {
    return
  }
  committing.value = true
  errorText.value = ''
  try {
    committed.value = await commitUserAccountImport(file.value)
    step.value = 2
    emit('imported')
  } catch (error) {
    // 整批被拒时后端给的是逐行结论，留在预览页比跳结果页更看得懂
    errorText.value = messageOf(error, '导入失败，请检查文件内容')
  } finally {
    committing.value = false
  }
}

function reset() {
  step.value = 0
  file.value = null
  errorText.value = ''
  preview.value = null
  committed.value = null
  onlyProblems.value = false
  void nextTick(() => uploadRef.value?.clearFiles())
}
</script>

<style scoped>
.import-steps {
  margin-bottom: 16px;
}

.import-alert {
  margin-bottom: 12px;
}

.import-step {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.import-stat-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.import-error-text {
  color: var(--el-color-danger, #f56c6c);
}

.import-notice-text {
  color: var(--el-color-warning, #e6a23c);
}

:deep(.import-row-error) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9, #fef0f0);
}
</style>
