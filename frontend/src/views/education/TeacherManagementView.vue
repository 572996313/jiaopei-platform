<template>
  <section class="education-page">
    <div class="education-hero">
      <div>
        <div class="page-kicker">ARCHIVE / TEACHER</div>
        <h2 class="panel-headline">教师档案</h2>
        <p class="panel-subtitle">
          当前页面承接教师档案列表与新增。真实联机构、校区与教师接口，先做基础档案，再为后续授课能力与成班规则差异预留空间。
        </p>
      </div>

      <div class="education-hero-actions">
        <el-button @click="refreshAll" :loading="lookupLoading || tableLoading">刷新数据</el-button>
        <el-button type="primary" @click="openDialog">新增教师</el-button>
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">机构数</div>
        <div class="stat-value">{{ institutionStore.institutions.length }}</div>
        <div class="stat-hint">来自 /api/organizations/institutions</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">校区数</div>
        <div class="stat-value">{{ campuses.length }}</div>
        <div class="stat-hint">按当前机构范围加载</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">教师数</div>
        <div class="stat-value">{{ teachers.length }}</div>
        <div class="stat-hint">来自 /api/education/teachers</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">当前机构</div>
        <div class="stat-value">{{ institutionId ? '1' : '0' }}</div>
        <div class="stat-hint">{{ formatInstitution(institutionId) }}</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="panel-title">当前范围</div>
      <div class="education-toolbar education-toolbar-compact" v-loading="lookupLoading">
        <el-select v-model="campusId" placeholder="选择校区" clearable filterable @change="loadTeachers">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">教师列表</div>
        <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}，当前校区：{{ formatCampus(campusId) }}</div>
      </div>

      <el-table :data="teachers" border v-loading="tableLoading">
        <el-table-column prop="teacherCode" label="教师编码" width="160" />
        <el-table-column prop="teacherName" label="教师姓名" min-width="160" />
        <el-table-column label="校区" min-width="160">
          <template #default="{ row }">
            {{ formatCampus(row.campusId) }}
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column label="用工类型" width="120">
          <template #default="{ row }">
            {{ employmentTypeLabel(row.employmentType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="teacherStatusTagType(row.status)">
              {{ teacherStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hiredAt" label="入职日期" width="140" />
        <el-table-column prop="remark" label="备注" min-width="200" />
      </el-table>

      <div v-if="!tableLoading && teachers.length === 0" class="education-empty">
        当前筛选范围下暂无教师数据。
      </div>
    </article>

    <el-dialog v-model="dialogVisible" title="新增教师" width="560px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>校区：</strong>{{ formatCampus(form.campusId || campusId) }}</div>
      </div>

      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <el-form-item label="教师编码">
          <el-input v-model="form.teacherCode" placeholder="例如 TCH-20260609-001" />
        </el-form-item>
        <el-form-item label="教师姓名">
          <el-input v-model="form.teacherName" placeholder="请输入教师姓名" />
        </el-form-item>
        <el-form-item label="归属校区">
          <el-select v-model="form.campusId" placeholder="选择校区" clearable filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="用工类型">
          <el-select v-model="form.employmentType" placeholder="选择用工类型">
            <el-option label="全职" value="full_time" />
            <el-option label="兼职" value="part_time" />
            <el-option label="外聘" value="contract" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="选择状态">
            <el-option label="在职" value="active" />
            <el-option label="暂停" value="suspended" />
            <el-option label="离职" value="left" />
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期">
          <el-date-picker
            v-model="form.hiredAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择入职日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充教师说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTeacher">提交教师</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { createTeacher, fetchTeachers, type TeacherSummary } from '@/api/education'
import { fetchCampuses, type CampusSummary } from '@/api/organization'
import { useInstitutionStore } from '@/stores/institution'

const institutionStore = useInstitutionStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)
const campuses = ref<CampusSummary[]>([])
const teachers = ref<TeacherSummary[]>([])

const campusId = ref<string | null>(null)

const dialogVisible = ref(false)
const pageError = ref('')
const formError = ref('')
const lookupLoading = ref(false)
const tableLoading = ref(false)
const submitting = ref(false)

const form = reactive({
  teacherCode: '',
  teacherName: '',
  campusId: '' as string | null,
  phone: '',
  employmentType: 'full_time',
  status: 'active',
  hiredAt: '',
  remark: '',
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function formatInstitution(id?: string | null) {
  return institutionStore.currentInstitutionName || (id ? `机构 #${id}` : '未选择')
}

function formatCampus(id?: string | null) {
  const target = campuses.value.find((item) => item.id === id)
  return target ? target.campusName || target.campusCode || `校区 #${id}` : id ? `校区 #${id}` : '未选择'
}

function employmentTypeLabel(value?: string | null) {
  switch (value) {
    case 'part_time':
      return '兼职'
    case 'contract':
      return '外聘'
    case 'full_time':
    default:
      return '全职'
  }
}

function teacherStatusLabel(value?: string | null) {
  switch (value) {
    case 'suspended':
      return '暂停'
    case 'left':
      return '离职'
    case 'active':
    default:
      return '在职'
  }
}

function teacherStatusTagType(value?: string | null) {
  switch (value) {
    case 'suspended':
      return 'warning'
    case 'left':
      return 'info'
    case 'active':
    default:
      return 'success'
  }
}

function resetForm() {
  form.teacherCode = ''
  form.teacherName = ''
  form.campusId = campusId.value
  form.phone = ''
  form.employmentType = 'full_time'
  form.status = 'active'
  form.hiredAt = ''
  form.remark = ''
  formError.value = ''
}

function validateContext() {
  if (!institutionId.value) {
    return '请先选择机构范围'
  }
  return ''
}

async function loadCampusesAndTeachers() {
  if (!institutionId.value) {
    campuses.value = []
    teachers.value = []
    return
  }

  lookupLoading.value = true
  try {
    campuses.value = await fetchCampuses(institutionId.value)
    if (campusId.value && !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '校区数据加载失败')
  } finally {
    lookupLoading.value = false
  }

  await loadTeachers()
}

async function loadTeachers() {
  if (!institutionId.value) {
    teachers.value = []
    return
  }

  tableLoading.value = true
  pageError.value = ''
  try {
    teachers.value = await fetchTeachers({
      institutionId: institutionId.value,
      campusId: campusId.value,
    })
  } catch (error) {
    teachers.value = []
    pageError.value = toMessage(error, '教师列表加载失败')
  } finally {
    tableLoading.value = false
  }
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

async function submitTeacher() {
  if (!institutionId.value) {
    formError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(form.teacherCode)) {
    formError.value = '请填写教师编码'
    return
  }
  if (!trimToUndefined(form.teacherName)) {
    formError.value = '请填写教师姓名'
    return
  }
  if (form.hiredAt && form.hiredAt > new Date().toISOString().slice(0, 10)) {
    formError.value = '入职日期不能晚于今天'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await createTeacher({
      institutionId: institutionId.value,
      campusId: form.campusId || undefined,
      teacherCode: form.teacherCode.trim(),
      teacherName: form.teacherName.trim(),
      phone: trimToUndefined(form.phone),
      employmentType: form.employmentType,
      status: form.status,
      hiredAt: form.hiredAt || null,
      remark: trimToUndefined(form.remark),
    })
    ElMessage.success('教师已新增')
    dialogVisible.value = false
    await loadTeachers()
  } catch (error) {
    formError.value = toMessage(error, '新增教师失败')
  } finally {
    submitting.value = false
  }
}

async function refreshAll() {
  await loadCampusesAndTeachers()
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
  await loadCampusesAndTeachers()
}

watch(() => institutionStore.currentInstitutionId, async () => {
  campusId.value = null
  await loadCampusesAndTeachers()
})

onMounted(() => {
  initializePage()
})
</script>
