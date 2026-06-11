<template>
  <section class="education-page">
    <div class="education-hero">
      <div>
        <div class="page-kicker">CLASS / CLASS_STUDENT</div>
        <h2 class="panel-headline">班级管理</h2>
        <p class="panel-subtitle">
          当前页面先承接班级学员关系。围绕正式 class_student 接口完成查询与新增，不扩课程模块。
        </p>
      </div>

      <div class="education-hero-actions">
        <el-button @click="refreshAll" :loading="lookupLoading || tableLoading">刷新数据</el-button>
        <el-button type="primary" @click="openDialog">新增入班</el-button>
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
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classes.length }}</div>
        <div class="stat-hint">当前机构/校区范围</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">学员数</div>
        <div class="stat-value">{{ students.length }}</div>
        <div class="stat-hint">来自 /api/education/students</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">入班关系</div>
        <div class="stat-value">{{ records.length }}</div>
        <div class="stat-hint">来自 /api/education/class-students</div>
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
        <el-select v-model="classId" placeholder="选择班级" clearable filterable @change="loadRecords">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="studentId" placeholder="选择学员" clearable filterable @change="loadRecords">
          <el-option
            v-for="item in students"
            :key="item.id"
            :label="item.studentName || item.studentNo || `学员 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="education-context-text">
        新增入班会使用当前已选机构、班级和学员，重复入班、容量不足和关系错误直接在弹窗内提示。
      </div>
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">班级学员关系</div>
        <div class="education-context-text">
          当前班级：{{ formatClass(classId) }}，当前学员：{{ formatStudent(studentId) }}
        </div>
      </div>

      <el-table :data="records" border v-loading="tableLoading">
        <el-table-column label="班级" min-width="180">
          <template #default="{ row }">
            {{ formatClass(row.classId) }}
          </template>
        </el-table-column>
        <el-table-column label="学员" min-width="180">
          <template #default="{ row }">
            {{ formatStudent(row.studentId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="classStudentTagType(row.status)">
              {{ classStudentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="joinedAt" label="入班日期" width="140" />
        <el-table-column prop="leftAt" label="离班日期" width="140" />
        <el-table-column prop="remark" label="备注" min-width="220" />
      </el-table>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无班级学员关系。
      </div>
    </article>

    <el-dialog v-model="dialogVisible" title="新增入班关系" width="520px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>班级：</strong>{{ formatClass(classId) }}</div>
        <div><strong>学员：</strong>{{ formatStudent(studentId) }}</div>
      </div>

      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="选择状态">
            <el-option label="在班" value="active" />
            <el-option label="停班" value="suspended" />
            <el-option label="已离班" value="left" />
          </el-select>
        </el-form-item>
        <el-form-item label="入班日期">
          <el-date-picker
            v-model="form.joinedAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择入班日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="离班日期">
          <el-date-picker
            v-model="form.leftAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择离班日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充关系说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClassStudent">提交入班</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { createClassStudent, fetchClassStudents, type ClassStudentRecord } from '@/api/education'
import { useEducationScope } from '@/composables/useEducationScope'

const { institutions, campuses, classes, students, loadInstitutions, loadScope } = useEducationScope()

const institutionId = ref<string | null>(null)
const campusId = ref<string | null>(null)
const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)

const records = ref<ClassStudentRecord[]>([])
const pageError = ref('')
const formError = ref('')
const dialogVisible = ref(false)
const lookupLoading = ref(false)
const tableLoading = ref(false)
const submitting = ref(false)

const form = reactive({
  status: 'active',
  joinedAt: todayValue(),
  leftAt: '',
  remark: '',
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
  const target = institutions.value.find((item) => item.id === id)
  return target ? target.orgName || target.shortName || target.orgCode || `机构 #${id}` : id ? `机构 #${id}` : '未选择'
}

function formatClass(id?: string | null) {
  const target = classes.value.find((item) => item.id === id)
  return target ? target.className || target.classCode || `班级 #${id}` : id ? `班级 #${id}` : '未选择'
}

function formatStudent(id?: string | null) {
  const target = students.value.find((item) => item.id === id)
  return target ? target.studentName || target.studentNo || `学员 #${id}` : id ? `学员 #${id}` : '未选择'
}

function classStudentStatusLabel(status?: string | null) {
  switch (status) {
    case 'suspended':
      return '停班'
    case 'left':
      return '已离班'
    case 'active':
    default:
      return '在班'
  }
}

function classStudentTagType(status?: string | null) {
  switch (status) {
    case 'suspended':
      return 'warning'
    case 'left':
      return 'info'
    case 'active':
    default:
      return 'success'
  }
}

function validateContext() {
  if (!institutionId.value) {
    return '请先选择机构范围'
  }
  if (!classId.value) {
    return '请先选择班级'
  }
  if (!studentId.value) {
    return '请先选择学员'
  }
  if (form.leftAt && form.joinedAt && form.leftAt < form.joinedAt) {
    return '离班日期不能早于入班日期'
  }
  return ''
}

function resetForm() {
  form.status = 'active'
  form.joinedAt = todayValue()
  form.leftAt = ''
  form.remark = ''
  formError.value = ''
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    classes.value = []
    students.value = []
    return
  }

  lookupLoading.value = true
  try {
    await loadScope(institutionId.value, campusId.value, null)
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
    records.value = await fetchClassStudents({
      classId: classId.value,
      studentId: studentId.value,
    })
  } catch (error) {
    records.value = []
    pageError.value = toMessage(error, '班级学员关系加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function handleInstitutionChange() {
  campusId.value = null
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

function openDialog() {
  const contextError = validateContext()
  if (contextError) {
    ElMessage.warning(contextError)
    return
  }
  resetForm()
  dialogVisible.value = true
}

async function submitClassStudent() {
  const contextError = validateContext()
  if (contextError) {
    formError.value = contextError
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await createClassStudent({
      institutionId: institutionId.value as string,
      classId: classId.value as string,
      studentId: studentId.value as string,
      status: form.status,
      joinedAt: form.joinedAt,
      leftAt: form.leftAt || null,
      remark: trimToUndefined(form.remark),
    })
    ElMessage.success('入班关系已新增')
    dialogVisible.value = false
    resetForm()
    await loadRecords()
  } catch (error) {
    formError.value = toMessage(error, '新增入班失败')
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
      await loadScope(institutionId.value, campusId.value, null)
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
