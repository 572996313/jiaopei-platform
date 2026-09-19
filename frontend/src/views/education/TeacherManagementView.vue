<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="page-toolbar">
      <div class="page-toolbar-filters" v-loading="lookupLoading">
        <el-select
          v-model="campusId"
          placeholder="筛选校区"
          clearable
          filterable
          @change="loadTeachers"
        >
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="page-toolbar-actions">
        <el-button @click="statsVisible = !statsVisible">
          统计
          <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"
            ><ArrowDown
          /></el-icon>
        </el-button>
        <el-button
          :icon="Refresh"
          circle
          :loading="lookupLoading || tableLoading"
          @click="refreshAll"
        />
        <el-button type="primary" @click="openTeacherDialog('create')">新增教师</el-button>
      </div>
    </div>

    <div v-show="statsVisible" class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">教师数</div>
        <div class="stat-value">{{ teachers.length }}</div>
        <div class="stat-hint">
          {{ campusId ? `当前校区：${formatCampus(campusId)}` : '全部校区' }}
        </div>
      </article>
      <article class="stat-card">
        <div class="stat-label">校区数</div>
        <div class="stat-value">{{ campuses.length }}</div>
        <div class="stat-hint">当前机构：{{ formatInstitution(institutionId) }}</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">
          教师列表<span class="panel-count">共 {{ teachers.length }} 条</span>
        </div>
        <div class="education-context-text">
          当前机构：{{ formatInstitution(institutionId) }}，当前校区：{{ formatCampus(campusId) }}
        </div>
      </div>

      <el-table :data="teachers" border v-loading="tableLoading" :height="TABLE_FILL_HEIGHT">
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTeacherDialog('edit', row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDeleteTeacher(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!tableLoading && teachers.length === 0" class="education-empty">
        当前筛选范围下暂无教师数据。
      </div>
    </article>

    <el-dialog
      v-model="dialogVisible"
      :title="teacherDialogMode === 'create' ? '新增教师' : '编辑教师'"
      width="560px"
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

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
        <el-button type="primary" :loading="submitting" @click="submitTeacher">{{
          teacherDialogMode === 'create' ? '提交教师' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_FILL_HEIGHT } from '@/utils/table-view'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createTeacher,
  deleteTeacher,
  fetchTeachers,
  updateTeacher,
  type TeacherSummary,
} from '@/api/education'
import { fetchCampuses, type CampusSummary } from '@/api/organization'
import { useEducationScopeStore } from '@/stores/educationScope'
import { useInstitutionStore } from '@/stores/institution'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId } = storeToRefs(scopeStore)
const institutionId = computed(() => institutionStore.currentInstitutionId)
const campuses = ref<CampusSummary[]>([])
const teachers = ref<TeacherSummary[]>([])

const statsVisible = ref(false)

const dialogVisible = ref(false)
const teacherDialogMode = ref<'create' | 'edit'>('create')
const editingTeacherId = ref<string | null>(null)
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
  return target
    ? target.campusName || target.campusCode || `校区 #${id}`
    : id
      ? `校区 #${id}`
      : '未选择'
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

function openTeacherDialog(mode: 'create' | 'edit', row?: TeacherSummary) {
  teacherDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingTeacherId.value = row.id
    form.teacherCode = row.teacherCode ?? ''
    form.teacherName = row.teacherName ?? ''
    form.campusId = row.campusId ?? null
    form.phone = row.phone ?? ''
    form.employmentType = row.employmentType ?? 'full_time'
    form.status = row.status ?? 'active'
    form.hiredAt = row.hiredAt ?? ''
    form.remark = row.remark ?? ''
  } else {
    editingTeacherId.value = null
    resetForm()
  }
  formError.value = ''
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
    if (teacherDialogMode.value === 'create') {
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
    } else if (editingTeacherId.value) {
      await updateTeacher(editingTeacherId.value, {
        campusId: form.campusId || undefined,
        teacherCode: form.teacherCode.trim(),
        teacherName: form.teacherName.trim(),
        phone: trimToUndefined(form.phone),
        employmentType: form.employmentType,
        status: form.status,
        hiredAt: form.hiredAt || null,
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('教师已更新')
    }
    dialogVisible.value = false
    resetForm()
    await loadTeachers()
  } catch (error) {
    formError.value = toMessage(
      error,
      teacherDialogMode.value === 'create' ? '新增教师失败' : '更新教师失败'
    )
  } finally {
    submitting.value = false
  }
}

async function confirmDeleteTeacher(row: TeacherSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除教师「${row.teacherName || row.teacherCode || row.id}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteTeacher(row.id)
    ElMessage.success('教师已删除')
    await loadTeachers()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除教师失败'))
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

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    campusId.value = null
    await loadCampusesAndTeachers()
  }
)

onMounted(() => {
  initializePage()
})
</script>
