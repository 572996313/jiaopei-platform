<template>
  <section class="education-page">
    <div class="education-hero">
      <div>
        <div class="page-kicker">STUDENT / ENROLLMENT</div>
        <h2 class="panel-headline">学员管理</h2>
        <p class="panel-subtitle">
          当前页面先承接报名主链路。先选机构范围，再用正式 enrollment 接口完成查询与新增。
        </p>
      </div>

      <div class="education-hero-actions">
        <el-button @click="refreshAll" :loading="lookupLoading || tableLoading">刷新数据</el-button>
        <el-button type="primary" @click="openDialog">新增报名</el-button>
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">机构数</div>
        <div class="stat-value">{{ institutions.length }}</div>
        <div class="stat-hint">来自 /api/organizations/institutions</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">学员数</div>
        <div class="stat-value">{{ students.length }}</div>
        <div class="stat-hint">按当前机构/校区范围加载</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classes.length }}</div>
        <div class="stat-hint">按当前机构/校区/学期范围加载</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">报名记录</div>
        <div class="stat-value">{{ records.length }}</div>
        <div class="stat-hint">来自 /api/education/enrollments</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="panel-title">当前范围</div>
      <div class="education-toolbar" v-loading="lookupLoading">
        <el-select v-model="institutionId" placeholder="选择机构" clearable filterable @change="handleInstitutionChange">
          <el-option
            v-for="item in institutions"
            :key="item.id"
            :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="campusId" placeholder="选择校区" clearable filterable @change="handleCampusChange">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="termId" placeholder="选择学期" clearable filterable @change="handleTermChange">
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="classId" placeholder="筛选班级" clearable filterable @change="loadRecords">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="studentId" placeholder="筛选学员" clearable filterable @change="loadRecords">
          <el-option
            v-for="item in students"
            :key="item.id"
            :label="item.studentName || item.studentNo || `学员 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="education-context-text">
        新增报名会直接使用当前已选机构、班级和学员，并把重复编号或关系校验错误留在弹窗内提示。
      </div>
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">报名记录</div>
        <div class="education-context-text">
          当前机构：{{ formatInstitution(institutionId) }}，班级：{{ formatClass(classId) }}，学员：{{ formatStudent(studentId) }}
        </div>
      </div>

      <el-table :data="records" border v-loading="tableLoading">
        <el-table-column prop="enrollmentNo" label="报名编号" width="160" />
        <el-table-column label="学员" min-width="180">
          <template #default="{ row }">
            {{ formatStudent(row.studentId) }}
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="180">
          <template #default="{ row }">
            {{ formatClass(row.classId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="enrollmentTagType(row.status)">
              {{ enrollmentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="channel" label="来源渠道" width="140" />
        <el-table-column prop="enrolledAt" label="报名日期" width="140" />
        <el-table-column prop="remark" label="备注" min-width="220" />
      </el-table>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无报名记录。
      </div>
    </article>

    <el-dialog v-model="dialogVisible" title="新增报名" width="540px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>班级：</strong>{{ formatClass(classId) }}</div>
        <div><strong>学员：</strong>{{ formatStudent(studentId) }}</div>
      </div>

      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <el-form-item label="报名编号">
          <el-input v-model="form.enrollmentNo" placeholder="例如 ENR-20260608-001" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="选择状态">
            <el-option label="已提交" value="submitted" />
            <el-option label="已确认" value="confirmed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源渠道">
          <el-select v-model="form.channel" placeholder="选择渠道" clearable>
            <el-option label="前台报名" value="front_desk" />
            <el-option label="顾问录入" value="consultant" />
            <el-option label="线上线索" value="online" />
            <el-option label="转介绍" value="referral" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名日期">
          <el-date-picker
            v-model="form.enrolledAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择报名日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充报名说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEnrollment">提交报名</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { createEnrollment, fetchEnrollments, type EnrollmentRecord } from '@/api/education'
import { useEducationScope } from '@/composables/useEducationScope'

const { institutions, campuses, terms, classes, students, loadInstitutions, loadScope } = useEducationScope()

const institutionId = ref<string | null>(null)
const campusId = ref<string | null>(null)
const termId = ref<string | null>(null)
const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)

const records = ref<EnrollmentRecord[]>([])
const pageError = ref('')
const formError = ref('')
const dialogVisible = ref(false)
const lookupLoading = ref(false)
const tableLoading = ref(false)
const submitting = ref(false)

const form = reactive({
  enrollmentNo: '',
  status: 'submitted',
  channel: 'front_desk',
  enrolledAt: todayValue(),
  remark: '',
})

const institutionMap = computed(() => {
  return new Map(
    institutions.value.map((item) => [item.id, item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`]),
  )
})

const classMap = computed(() => {
  return new Map(classes.value.map((item) => [item.id, item.className || item.classCode || `班级 #${item.id}`]))
})

const studentMap = computed(() => {
  return new Map(students.value.map((item) => [item.id, item.studentName || item.studentNo || `学员 #${item.id}`]))
})

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return institutionMap.value.get(id) || `机构 #${id}`
}

function formatClass(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classMap.value.get(id) || `班级 #${id}`
}

function formatStudent(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return studentMap.value.get(id) || `学员 #${id}`
}

function enrollmentStatusLabel(status?: string | null) {
  switch (status) {
    case 'confirmed':
      return '已确认'
    case 'cancelled':
      return '已取消'
    case 'submitted':
    default:
      return '已提交'
  }
}

function enrollmentTagType(status?: string | null) {
  switch (status) {
    case 'confirmed':
      return 'success'
    case 'cancelled':
      return 'info'
    case 'submitted':
    default:
      return 'warning'
  }
}

function validateContext() {
  if (!institutionId.value) {
    return '请先选择机构范围'
  }
  if (!classId.value) {
    return '请先选择报名班级'
  }
  if (!studentId.value) {
    return '请先选择报名学员'
  }
  return ''
}

function resetForm() {
  form.enrollmentNo = ''
  form.status = 'submitted'
  form.channel = 'front_desk'
  form.enrolledAt = todayValue()
  form.remark = ''
  formError.value = ''
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    terms.value = []
    classes.value = []
    students.value = []
    return
  }

  lookupLoading.value = true
  try {
    await loadScope(institutionId.value, campusId.value, termId.value)
    if (classId.value && !classes.value.some((item) => item.id === classId.value)) {
      classId.value = null
    }
    if (studentId.value && !students.value.some((item) => item.id === studentId.value)) {
      studentId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function loadRecords() {
  if (!institutionId.value) {
    records.value = []
    return
  }

  tableLoading.value = true
  pageError.value = ''
  try {
    records.value = await fetchEnrollments({
      institutionId: institutionId.value,
      classId: classId.value,
      studentId: studentId.value,
    })
  } catch (error) {
    records.value = []
    pageError.value = toMessage(error, '报名记录加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function handleInstitutionChange() {
  campusId.value = null
  termId.value = null
  classId.value = null
  studentId.value = null
  await refreshScopeData()
  await loadRecords()
}

async function handleCampusChange() {
  classId.value = null
  studentId.value = null
  await refreshScopeData()
  await loadRecords()
}

async function handleTermChange() {
  classId.value = null
  await refreshScopeData()
  await loadRecords()
}

function openDialog() {
  const contextError = validateContext()
  if (contextError) {
    ElMessage.warning(contextError)
    return
  }
  resetForm()
  dialogVisible.value = true
}

async function submitEnrollment() {
  const contextError = validateContext()
  if (contextError) {
    formError.value = contextError
    return
  }

  if (!trimToUndefined(form.enrollmentNo)) {
    formError.value = '请填写报名编号'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await createEnrollment({
      institutionId: institutionId.value as string,
      classId: classId.value as string,
      studentId: studentId.value as string,
      enrollmentNo: form.enrollmentNo.trim(),
      status: form.status,
      channel: trimToUndefined(form.channel),
      enrolledAt: form.enrolledAt,
      remark: trimToUndefined(form.remark),
    })
    ElMessage.success('报名记录已新增')
    dialogVisible.value = false
    resetForm()
    await loadRecords()
  } catch (error) {
    formError.value = toMessage(error, '新增报名失败')
  } finally {
    submitting.value = false
  }
}

async function refreshAll() {
  await refreshScopeData()
  await loadRecords()
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadInstitutions()
    if (institutions.value.length > 0) {
      institutionId.value = institutions.value[0].id
      await loadScope(institutionId.value, campusId.value, termId.value)
    }
    await loadRecords()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
}

onMounted(() => {
  initializePage()
})
</script>
