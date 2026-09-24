<template>
  <el-dialog
    :model-value="modelValue"
    title="批量导入账号"
    width="1040px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <el-steps :active="step" finish-status="success" simple class="import-steps">
      <el-step title="上传文件" />
      <el-step title="校验预览" />
      <el-step title="导入并发码" />
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
        一行 = 一个账号绑定一个学员。同一个账号可以写多行（家长带两个孩子），系统按登录账号建一个账号、绑多条关系；
        学员编号必须是系统里已有的学员档案；手机号选填，学生没手机就留空，登录账号留空时依次取手机号、学员编号。导入后的账号是「待激活」，密码由本人凭激活码自设。
      </p>
      <div class="table-toolbar">
        <el-button :icon="Download" @click="handleDownloadTemplate">下载模板</el-button>
        <el-button :icon="Download" :loading="rosterDownloading" @click="handleDownloadRoster">
          导出待开账号学员名册
        </el-button>
      </div>
      <p class="education-context-text">
        名册已经把「还没有任何登录账号」的学员学号和姓名填好了（按当前筛选校区，未筛选则全机构），
        最少只补「账号类型」和「账号姓名」两列就能导，学生没有手机号留空即可，不用自己抄学号。
      </p>
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
          <div class="el-upload__tip">只支持模板格式的 xlsx，单次不超过 2000 行</div>
        </template>
      </el-upload>
    </div>

    <!-- 第二步：逐行校验结果 -->
    <div v-else-if="step === 1" class="import-step">
      <div class="dashboard-grid import-stat-grid">
        <article class="stat-card">
          <div class="stat-label">总行数</div>
          <div class="stat-value">{{ preview?.total ?? 0 }}</div>
          <div class="stat-hint">一行一条账号与学员的绑定</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">校验通过</div>
          <div class="stat-value">{{ preview?.validCount ?? 0 }}</div>
          <div class="stat-hint">导入后为待激活账号</div>
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
        <el-table-column prop="studentNo" label="学员编号" width="130" />
        <el-table-column prop="studentName" label="学员姓名" width="110" />
        <el-table-column prop="accountKind" label="账号类型" width="100" />
        <el-table-column prop="displayName" label="账号姓名" width="110" />
        <el-table-column prop="username" label="登录账号" width="140" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="说明" min-width="260">
          <template #default="{ row }">
            <span v-if="row.error" class="import-error-text">{{ row.error }}</span>
            <span v-else-if="row.notice" class="import-notice-text">{{ row.notice }}</span>
            <span v-else>可导入</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 第三步：导入结果 + 批量发码 -->
    <div v-else class="import-step">
      <div class="dashboard-grid import-stat-grid">
        <article class="stat-card">
          <div class="stat-label">新建账号</div>
          <div class="stat-value">{{ committed?.createdCount ?? 0 }}</div>
          <div class="stat-hint">状态为待激活</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">新增绑定</div>
          <div class="stat-value">{{ committed?.boundCount ?? 0 }}</div>
          <div class="stat-hint">账号与学员档案的关系</div>
        </article>
        <article class="stat-card">
          <div class="stat-label">已存在跳过</div>
          <div class="stat-value">{{ committed?.skippedCount ?? 0 }}</div>
          <div class="stat-hint">已绑过的关系不重复计</div>
        </article>
      </div>

      <div class="table-toolbar">
        <el-input-number
          v-model="validDays"
          :min="1"
          :max="365"
          controls-position="right"
          placeholder="有效天数"
        />
        <span class="import-field-label">激活码有效天数（留空按系统默认 7 天）</span>
        <el-button
          type="primary"
          :loading="issuing"
          :disabled="selected.length === 0"
          @click="issueCodes"
        >
          为选中账号生成激活码
        </el-button>
        <el-button v-if="accounts.length" :icon="Download" @click="exportAccounts">
          导出账号清单
        </el-button>
        <el-button v-if="codes.length" :icon="Download" @click="exportCodes">导出激活码</el-button>
      </div>
      <p class="education-context-text">
        勾选本次导入的账号后一次性出码，不用一个账号点一次；同一账号重复申请会复用仍在有效期内的原码。
        「导出账号清单」不含激活码，用于留档这次到底建了哪些账号。
      </p>

      <el-table
        ref="accountTableRef"
        :data="accounts"
        border
        stripe
        row-key="userId"
        max-height="280"
        @selection-change="handleSelect"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="displayName" label="账号姓名" width="120" fixed="left" />
        <el-table-column prop="username" label="登录账号" min-width="150" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="accountKind" label="账号类型" width="110" />
        <el-table-column prop="studentNo" label="绑定学员编号" width="140" />
        <el-table-column label="状态" min-width="180">
          <template #default="{ row }">
            <span v-if="row.notice" class="import-notice-text">{{ row.notice }}</span>
            <span v-else>本次新建</span>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-if="codes.length"
        :data="codes"
        border
        stripe
        row-key="userId"
        max-height="280"
        class="import-code-table"
      >
        <el-table-column prop="displayName" label="账号姓名" width="120" fixed="left" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="code" label="激活码" min-width="150" />
        <el-table-column label="有效期至" min-width="160">
          <template #default="{ row }">{{ formatTime(row.expiresAt) || '长期' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="160" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ step === 2 ? '完成' : '取消' }}
      </el-button>
      <el-button v-if="step === 1" @click="step = 0">上一步</el-button>
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
import type { TableInstance, UploadFile, UploadInstance, UploadRawFile } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { isApiError } from '@cornerstone/core/api/request'
import {
  commitStudentAccountImport,
  createStudentAccountActivationCodes,
  downloadStudentAccountRoster,
  downloadStudentAccountTemplate,
  previewStudentAccountImport,
  type StudentAccountActivationView,
  type StudentAccountImportResult,
  type StudentAccountImportRowView,
} from '@cornerstone/module-education/api/studentAccount'

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 导入成功后通知宿主刷新学员列表 */
  imported: []
}>()

const props = defineProps<{
  modelValue: boolean
  institutionId: string | null
  /** 名册按宿主页当前筛选的校区导出，不填为全机构 */
  campusId?: string | null
}>()

/** 发码列表的一行：后端导入结果里带 userId 的行才是可发码账号 */
type AccountRow = StudentAccountImportRowView & { userId: string }

const step = ref(0)
const rosterDownloading = ref(false)
const file = ref<File | null>(null)
const errorText = ref('')
const previewing = ref(false)
const committing = ref(false)
const issuing = ref(false)
const onlyProblems = ref(false)
const validDays = ref<number | undefined>(undefined)
const preview = ref<StudentAccountImportResult | null>(null)
const committed = ref<StudentAccountImportResult | null>(null)
const codes = ref<StudentAccountActivationView[]>([])
const selected = ref<AccountRow[]>([])
const uploadRef = ref<UploadInstance>()
const accountTableRef = ref<TableInstance>()

const canCommit = computed(() => (preview.value?.errorCount ?? 1) === 0)

const previewRows = computed(() => {
  const rows = preview.value?.rows ?? []
  return onlyProblems.value ? rows.filter((row) => row.error || row.notice) : rows
})

/** 一个账号可能占多行（家长带两个孩子），发码要按账号去重后勾选 */
const accounts = computed<AccountRow[]>(() => {
  const seen = new Set<string>()
  const rows: AccountRow[] = []
  for (const row of committed.value?.rows ?? []) {
    if (!row.userId || seen.has(row.userId)) {
      continue
    }
    seen.add(row.userId)
    rows.push(row as AccountRow)
  }
  return rows
})

function messageOf(error: unknown, fallback: string) {
  return isApiError(error) ? error.message : fallback
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

function handleSelect(rows: StudentAccountImportRowView[]) {
  selected.value = rows.filter((row): row is AccountRow => Boolean(row.userId))
}

async function handleDownloadTemplate() {
  try {
    await downloadStudentAccountTemplate()
  } catch (error) {
    ElMessage.error(messageOf(error, '模板下载失败，请稍后重试'))
  }
}

async function handleDownloadRoster() {
  if (!props.institutionId) {
    errorText.value = '请先确认已选择机构'
    return
  }
  rosterDownloading.value = true
  errorText.value = ''
  try {
    await downloadStudentAccountRoster(props.institutionId, props.campusId)
  } catch (error) {
    errorText.value = messageOf(error, '名册导出失败，请缩小筛选范围后重试')
  } finally {
    rosterDownloading.value = false
  }
}

async function runPreview() {
  if (!file.value || !props.institutionId) {
    errorText.value = '请先选择机构再上传文件'
    return
  }
  previewing.value = true
  errorText.value = ''
  try {
    preview.value = await previewStudentAccountImport(file.value, props.institutionId)
    step.value = 1
  } catch (error) {
    errorText.value = messageOf(error, '校验失败，请检查文件内容后重试')
  } finally {
    previewing.value = false
  }
}

async function runCommit() {
  if (!file.value || !props.institutionId) {
    errorText.value = '请先选择机构再上传文件'
    return
  }
  committing.value = true
  errorText.value = ''
  try {
    committed.value = await commitStudentAccountImport(file.value, props.institutionId)
    step.value = 2
    emit('imported')
    // 默认勾上本次导入的全部账号，业务员一次点击就能出完整批的码
    await nextTick()
    accounts.value.forEach((row) => accountTableRef.value?.toggleRowSelection(row, true))
  } catch (error) {
    errorText.value = messageOf(error, '导入失败，请修正文件后重新上传')
  } finally {
    committing.value = false
  }
}

async function issueCodes() {
  if (!props.institutionId) {
    errorText.value = '请先选择机构'
    return
  }
  issuing.value = true
  errorText.value = ''
  try {
    codes.value = await createStudentAccountActivationCodes(
      selected.value.map((row) => row.userId),
      props.institutionId,
      validDays.value
    )
    ElMessage.success(`已生成 ${codes.value.filter((item) => item.code).length} 个激活码`)
  } catch (error) {
    errorText.value = messageOf(error, '生成激活码失败，请稍后重试')
  } finally {
    issuing.value = false
  }
}

/** 交给业务员分发/留档的表要能直接贴进微信或 Excel，所以导 CSV 带 BOM 让 Excel 认出 UTF-8 */
function saveCsv(filename: string, header: string[], rows: (string | number | null | undefined)[][]) {
  const lines = rows.map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
  const blob = new Blob(['\uFEFF' + [header.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function exportCodes() {
  saveCsv(
    '激活码清单.csv',
    ['账号姓名', '手机号', '登录账号', '激活码', '有效期至', '说明'],
    codes.value.map((item) => [
      item.displayName,
      item.phone,
      item.username,
      item.code,
      formatTime(item.expiresAt),
      item.message,
    ])
  )
}

function exportAccounts() {
  // 按行导而不是按上面去重后的账号表导：一个家长带两个孩子时要去重表只剩一行，留档会漏绑定
  saveCsv(
    '账号清单.csv',
    ['账号ID', '账号姓名', '登录账号', '手机号', '账号类型', '绑定学员编号', '学员姓名', '本次结果'],
    (committed.value?.rows ?? []).map((item) => [
      item.userId,
      item.displayName,
      item.username,
      item.phone,
      item.accountKind,
      item.studentNo,
      item.studentName,
      item.notice || '本次新建',
    ])
  )
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function rowClassName({ row }: { row: StudentAccountImportRowView }) {
  if (row.error) {
    return 'import-row-error'
  }
  return row.notice ? 'import-row-notice' : ''
}

function reset() {
  step.value = 0
  file.value = null
  errorText.value = ''
  preview.value = null
  committed.value = null
  codes.value = []
  selected.value = []
  onlyProblems.value = false
  validDays.value = undefined
  uploadRef.value?.clearFiles()
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

.import-field-label {
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
}

.import-code-table {
  margin-top: 12px;
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
