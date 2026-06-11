<template>
  <section class="campus-page">
    <div class="campus-hero">
      <div>
        <div class="page-kicker">组织与校区</div>
        <h2 class="panel-headline">校区管理</h2>
        <p class="panel-subtitle">
          当前入口统一承接机构、校区、教室、学期和时间段。保持现有导航，不再拆复杂子路由。
        </p>
      </div>

      <div class="campus-hero-actions">
        <el-button @click="refreshAll" :loading="lookupLoading">刷新资源</el-button>
        <el-button type="primary" @click="openCampusDialog">新增校区</el-button>
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid campus-stat-grid">
      <article class="stat-card">
        <div class="stat-label">机构数量</div>
        <div class="stat-value">{{ institutions.length }}</div>
        <div class="stat-hint">当前机构：{{ formatInstitution(institutionId) }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">校区数量</div>
        <div class="stat-value">{{ campuses.length }}</div>
        <div class="stat-hint">来自 /api/organizations/campuses</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">教室数量</div>
        <div class="stat-value">{{ classrooms.length }}</div>
        <div class="stat-hint">当前校区：{{ formatCampus(campusId) }}</div>
      </article>
      <article class="panel-card">
        <div class="panel-title">当前迁移脚本</div>
        <div class="schema-file">{{ schemaMigration }}</div>
      </article>
    </div>

    <article class="panel-card campus-panel">
      <div class="panel-title">当前范围</div>
      <div class="education-toolbar education-toolbar-compact" v-loading="lookupLoading">
        <el-select v-model="institutionId" placeholder="选择机构" clearable filterable @change="handleInstitutionChange">
          <el-option
            v-for="item in institutions"
            :key="item.id"
            :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="campusId" placeholder="选择校区" clearable filterable @change="loadClassrooms">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="education-context-text">
        机构切换后会同步刷新校区、教室、学期和时间段。当前沿用真实后端的 stub token 与字符串 ID 方案。
      </div>
    </article>

    <div class="resource-grid">
      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">校区列表</div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openCampusDialog">新增校区</el-button>
        </div>
        <el-table :data="campuses" border>
          <el-table-column prop="campusCode" label="校区编码" width="150" />
          <el-table-column prop="campusName" label="校区名称" min-width="180" />
          <el-table-column prop="campusType" label="校区类型" width="120">
            <template #default="{ row }">{{ campusTypeLabel(row.campusType) }}</template>
          </el-table-column>
          <el-table-column prop="principalName" label="负责人" width="120" />
          <el-table-column prop="phone" label="联系电话" width="150" />
          <el-table-column prop="address" label="地址" min-width="220" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ campusStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">教室列表</div>
            <div class="education-context-text">当前校区：{{ formatCampus(campusId) }}</div>
          </div>
          <el-button type="primary" @click="openClassroomDialog">新增教室</el-button>
        </div>
        <el-table :data="classrooms" border>
          <el-table-column prop="classroomCode" label="教室编码" width="150" />
          <el-table-column prop="classroomName" label="教室名称" min-width="180" />
          <el-table-column prop="roomType" label="类型" width="120" />
          <el-table-column prop="floorNo" label="楼层" width="100" />
          <el-table-column prop="seatCapacity" label="容量" width="100" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ classroomStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可排课" width="100">
            <template #default="{ row }">{{ row.allowScheduling ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">学期列表</div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openTermDialog">新增学期</el-button>
        </div>
        <el-table :data="terms" border>
          <el-table-column prop="termCode" label="学期编码" width="150" />
          <el-table-column prop="termName" label="学期名称" min-width="180" />
          <el-table-column prop="academicYear" label="学年" width="120" />
          <el-table-column prop="season" label="学季" width="120" />
          <el-table-column label="起止日期" min-width="220">
            <template #default="{ row }">{{ row.startDate || '-' }} 至 {{ row.endDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ termStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">时间段列表</div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openTimeSlotDialog">新增时间段</el-button>
        </div>
        <el-table :data="timeSlots" border>
          <el-table-column prop="slotCode" label="时间段编码" width="150" />
          <el-table-column prop="slotName" label="时间段名称" min-width="180" />
          <el-table-column label="星期" width="90">
            <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="180">
            <template #default="{ row }">{{ row.startTime || '-' }} - {{ row.endTime || '-' }}</template>
          </el-table-column>
          <el-table-column prop="durationMinutes" label="时长" width="100" />
          <el-table-column prop="slotType" label="类型" width="120" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ timeSlotStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </div>

    <article class="panel-card campus-panel">
      <div class="panel-title">组织模块元数据</div>
      <div class="schema-grid">
        <div class="schema-block">
          <div class="schema-label">聚合根</div>
          <div class="schema-chips">
            <span v-for="item in schemaAggregates" :key="item" class="schema-chip">{{ item }}</span>
          </div>
        </div>
        <div class="schema-block">
          <div class="schema-label">表结构</div>
          <div class="schema-chips">
            <span v-for="item in schemaTables" :key="item" class="schema-chip is-secondary">{{ item }}</span>
          </div>
        </div>
      </div>
    </article>

    <el-dialog v-model="campusDialogVisible" title="新增校区" width="560px">
      <el-alert v-if="campusFormError" :title="campusFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="校区编码"><el-input v-model="campusForm.campusCode" /></el-form-item>
        <el-form-item label="校区名称"><el-input v-model="campusForm.campusName" /></el-form-item>
        <el-form-item label="校区类型">
          <el-select v-model="campusForm.campusType">
            <el-option label="直营" value="direct" />
            <el-option label="加盟" value="franchise" />
            <el-option label="合作" value="partner" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="campusForm.principalName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="campusForm.phone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="campusForm.address" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="容量上限"><el-input-number v-model="campusForm.capacityLimit" :min="0" :step="10" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="campusForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用中" value="active" />
            <el-option label="暂停中" value="suspended" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="campusSubmitting" @click="submitCampus">提交校区</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classroomDialogVisible" title="新增教室" width="560px">
      <el-alert v-if="classroomFormError" :title="classroomFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="归属校区">
          <el-select v-model="classroomForm.campusId" placeholder="选择校区" filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教室编码"><el-input v-model="classroomForm.classroomCode" /></el-form-item>
        <el-form-item label="教室名称"><el-input v-model="classroomForm.classroomName" /></el-form-item>
        <el-form-item label="教室类型"><el-input v-model="classroomForm.roomType" placeholder="例如 normal / piano / lab" /></el-form-item>
        <el-form-item label="楼层"><el-input v-model="classroomForm.floorNo" /></el-form-item>
        <el-form-item label="座位数"><el-input-number v-model="classroomForm.seatCapacity" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classroomForm.status">
            <el-option label="空闲" value="idle" />
            <el-option label="占用" value="occupied" />
            <el-option label="维护" value="maintenance" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classroomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classroomSubmitting" @click="submitClassroom">提交教室</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="termDialogVisible" title="新增学期" width="560px">
      <el-alert v-if="termFormError" :title="termFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="学期编码"><el-input v-model="termForm.termCode" /></el-form-item>
        <el-form-item label="学期名称"><el-input v-model="termForm.termName" /></el-form-item>
        <el-form-item label="学年"><el-input v-model="termForm.academicYear" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学季"><el-input v-model="termForm.season" placeholder="例如 summer / autumn" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="termForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="termForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="termForm.status">
            <el-option label="草稿" value="draft" />
            <el-option label="启用" value="active" />
            <el-option label="归档" value="archived" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="termDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="termSubmitting" @click="submitTerm">提交学期</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="timeSlotDialogVisible" title="新增时间段" width="560px">
      <el-alert v-if="timeSlotFormError" :title="timeSlotFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="适用校区">
          <el-select v-model="timeSlotForm.campusId" placeholder="选择校区" clearable filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段编码"><el-input v-model="timeSlotForm.slotCode" /></el-form-item>
        <el-form-item label="时间段名称"><el-input v-model="timeSlotForm.slotName" /></el-form-item>
        <el-form-item label="星期">
          <el-select v-model="timeSlotForm.weekday">
            <el-option v-for="day in weekdays" :key="day.value" :label="day.label" :value="day.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间"><el-time-picker v-model="timeSlotForm.startTime" value-format="HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="结束时间"><el-time-picker v-model="timeSlotForm.endTime" value-format="HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="时长（分钟）"><el-input-number v-model="timeSlotForm.durationMinutes" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="timeSlotForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeSlotDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="timeSlotSubmitting" @click="submitTimeSlot">提交时间段</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createCampus,
  createClassroom,
  createTerm,
  createTimeSlot,
  fetchCampuses,
  fetchClassrooms,
  fetchInstitutions,
  fetchTerms,
  fetchTimeSlots,
  getOrganizationSchema,
  type CampusSummary,
  type ClassroomSummary,
  type InstitutionSummary,
  type TermSummary,
  type TimeSlotSummary,
} from '@/api/organization'

const institutions = ref<InstitutionSummary[]>([])
const campuses = ref<CampusSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const terms = ref<TermSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

const institutionId = ref<string | null>(null)
const campusId = ref<string | null>(null)

const pageError = ref('')
const lookupLoading = ref(false)
const schemaTables = ref<string[]>([])
const schemaAggregates = ref<string[]>([])
const schemaMigration = ref('V2__create_organization_core_tables.sql')

const campusDialogVisible = ref(false)
const classroomDialogVisible = ref(false)
const termDialogVisible = ref(false)
const timeSlotDialogVisible = ref(false)

const campusSubmitting = ref(false)
const classroomSubmitting = ref(false)
const termSubmitting = ref(false)
const timeSlotSubmitting = ref(false)

const campusFormError = ref('')
const classroomFormError = ref('')
const termFormError = ref('')
const timeSlotFormError = ref('')

const campusForm = reactive({
  campusCode: '',
  campusName: '',
  campusType: 'direct',
  principalName: '',
  phone: '',
  address: '',
  capacityLimit: 100,
  status: 'planning',
})

const classroomForm = reactive({
  campusId: '' as string | null,
  classroomCode: '',
  classroomName: '',
  roomType: 'normal',
  floorNo: '',
  seatCapacity: 20,
  status: 'idle',
})

const termForm = reactive({
  termCode: '',
  termName: '',
  academicYear: '2026-2027',
  season: 'autumn',
  startDate: '',
  endDate: '',
  status: 'draft',
})

const timeSlotForm = reactive({
  campusId: '' as string | null,
  slotCode: '',
  slotName: '',
  weekday: 1,
  startTime: '08:00:00',
  endTime: '09:30:00',
  durationMinutes: 90,
  status: 'active',
})

const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
]

const campusMap = computed(() => {
  return new Map(campuses.value.map((item) => [item.id, item.campusName || item.campusCode || `校区 #${item.id}`]))
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function formatInstitution(id?: string | null) {
  const target = institutions.value.find((item) => item.id === id)
  return target ? target.orgName || target.shortName || target.orgCode || `机构 #${id}` : id ? `机构 #${id}` : '未选择'
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function campusTypeLabel(value?: string | null) {
  switch (value) {
    case 'franchise':
      return '加盟'
    case 'partner':
      return '合作'
    case 'direct':
    default:
      return '直营'
  }
}

function campusStatusLabel(value?: string | null) {
  switch (value) {
    case 'active':
      return '启用中'
    case 'suspended':
      return '暂停中'
    case 'planning':
    default:
      return '规划中'
  }
}

function classroomStatusLabel(value?: string | null) {
  switch (value) {
    case 'occupied':
      return '占用'
    case 'maintenance':
      return '维护'
    case 'idle':
    default:
      return '空闲'
  }
}

function termStatusLabel(value?: string | null) {
  switch (value) {
    case 'active':
      return '启用'
    case 'archived':
      return '归档'
    case 'draft':
    default:
      return '草稿'
  }
}

function timeSlotStatusLabel(value?: string | null) {
  switch (value) {
    case 'inactive':
      return '停用'
    case 'active':
    default:
      return '启用'
  }
}

function statusTagType(value?: string | null) {
  switch (value) {
    case 'active':
      return 'success'
    case 'suspended':
    case 'maintenance':
      return 'warning'
    case 'archived':
    case 'inactive':
      return 'info'
    default:
      return ''
  }
}

function weekdayLabel(value?: number | null) {
  return weekdays.find((item) => item.value === value)?.label || '-'
}

function resetCampusForm() {
  campusForm.campusCode = ''
  campusForm.campusName = ''
  campusForm.campusType = 'direct'
  campusForm.principalName = ''
  campusForm.phone = ''
  campusForm.address = ''
  campusForm.capacityLimit = 100
  campusForm.status = 'planning'
  campusFormError.value = ''
}

function resetClassroomForm() {
  classroomForm.campusId = campusId.value
  classroomForm.classroomCode = ''
  classroomForm.classroomName = ''
  classroomForm.roomType = 'normal'
  classroomForm.floorNo = ''
  classroomForm.seatCapacity = 20
  classroomForm.status = 'idle'
  classroomFormError.value = ''
}

function resetTermForm() {
  termForm.termCode = ''
  termForm.termName = ''
  termForm.academicYear = '2026-2027'
  termForm.season = 'autumn'
  termForm.startDate = ''
  termForm.endDate = ''
  termForm.status = 'draft'
  termFormError.value = ''
}

function resetTimeSlotForm() {
  timeSlotForm.campusId = campusId.value
  timeSlotForm.slotCode = ''
  timeSlotForm.slotName = ''
  timeSlotForm.weekday = 1
  timeSlotForm.startTime = '08:00:00'
  timeSlotForm.endTime = '09:30:00'
  timeSlotForm.durationMinutes = 90
  timeSlotForm.status = 'active'
  timeSlotFormError.value = ''
}

async function reloadSchema() {
  try {
    const { data } = await getOrganizationSchema()
    schemaTables.value = data.data.tables
    schemaAggregates.value = data.data.aggregates
    schemaMigration.value = data.data.migration
  } catch {
    schemaTables.value = ['jp_institution', 'jp_campus', 'jp_classroom', 'jp_term', 'jp_time_slot']
    schemaAggregates.value = ['institution', 'campus', 'classroom', 'term', 'timeSlot']
  }
}

async function loadClassrooms() {
  if (!campusId.value) {
    classrooms.value = []
    return
  }

  try {
    classrooms.value = await fetchClassrooms(campusId.value)
  } catch (error) {
    classrooms.value = []
    pageError.value = toMessage(error, '教室列表加载失败')
  }
}

async function loadResources() {
  if (!institutionId.value) {
    campuses.value = []
    classrooms.value = []
    terms.value = []
    timeSlots.value = []
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    const [campusList, termList, timeSlotList] = await Promise.all([
      fetchCampuses(institutionId.value),
      fetchTerms(institutionId.value),
      fetchTimeSlots(institutionId.value),
    ])

    campuses.value = campusList
    terms.value = termList
    timeSlots.value = timeSlotList

    if (!campusId.value || !campusList.some((item) => item.id === campusId.value)) {
      campusId.value = campusList[0]?.id || null
    }

    if (campusId.value) {
      classrooms.value = await fetchClassrooms(campusId.value)
    } else {
      classrooms.value = []
    }
  } catch (error) {
    pageError.value = toMessage(error, '组织资源加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function handleInstitutionChange() {
  campusId.value = null
  await loadResources()
}

function openCampusDialog() {
  if (!institutionId.value) {
    ElMessage.warning('请先选择机构范围')
    return
  }
  resetCampusForm()
  campusDialogVisible.value = true
}

function openClassroomDialog() {
  if (!campusId.value) {
    ElMessage.warning('请先选择校区')
    return
  }
  resetClassroomForm()
  classroomDialogVisible.value = true
}

function openTermDialog() {
  if (!institutionId.value) {
    ElMessage.warning('请先选择机构范围')
    return
  }
  resetTermForm()
  termDialogVisible.value = true
}

function openTimeSlotDialog() {
  if (!institutionId.value) {
    ElMessage.warning('请先选择机构范围')
    return
  }
  resetTimeSlotForm()
  timeSlotDialogVisible.value = true
}

async function submitCampus() {
  if (!institutionId.value) {
    campusFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(campusForm.campusCode) || !trimToUndefined(campusForm.campusName) || !trimToUndefined(campusForm.address)) {
    campusFormError.value = '请完整填写校区编码、名称和地址'
    return
  }

  campusSubmitting.value = true
  campusFormError.value = ''
  try {
    await createCampus({
      institutionId: institutionId.value,
      campusCode: campusForm.campusCode.trim(),
      campusName: campusForm.campusName.trim(),
      campusType: campusForm.campusType,
      principalName: trimToUndefined(campusForm.principalName),
      phone: trimToUndefined(campusForm.phone),
      address: campusForm.address.trim(),
      capacityLimit: campusForm.capacityLimit,
      status: campusForm.status,
    })
    ElMessage.success('校区已新增')
    campusDialogVisible.value = false
    await loadResources()
  } catch (error) {
    campusFormError.value = toMessage(error, '新增校区失败')
  } finally {
    campusSubmitting.value = false
  }
}

async function submitClassroom() {
  if (!classroomForm.campusId) {
    classroomFormError.value = '请选择归属校区'
    return
  }
  if (!trimToUndefined(classroomForm.classroomCode) || !trimToUndefined(classroomForm.classroomName)) {
    classroomFormError.value = '请完整填写教室编码和名称'
    return
  }

  classroomSubmitting.value = true
  classroomFormError.value = ''
  try {
    await createClassroom({
      campusId: classroomForm.campusId,
      classroomCode: classroomForm.classroomCode.trim(),
      classroomName: classroomForm.classroomName.trim(),
      roomType: trimToUndefined(classroomForm.roomType),
      floorNo: trimToUndefined(classroomForm.floorNo),
      seatCapacity: classroomForm.seatCapacity,
      status: classroomForm.status,
    })
    ElMessage.success('教室已新增')
    classroomDialogVisible.value = false
    if (campusId.value !== classroomForm.campusId) {
      campusId.value = classroomForm.campusId
    }
    await loadClassrooms()
  } catch (error) {
    classroomFormError.value = toMessage(error, '新增教室失败')
  } finally {
    classroomSubmitting.value = false
  }
}

async function submitTerm() {
  if (!institutionId.value) {
    termFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(termForm.termCode) || !trimToUndefined(termForm.termName) || !trimToUndefined(termForm.academicYear)) {
    termFormError.value = '请完整填写学期编码、名称和学年'
    return
  }
  if (!termForm.startDate || !termForm.endDate) {
    termFormError.value = '请选择学期开始和结束日期'
    return
  }
  if (termForm.startDate > termForm.endDate) {
    termFormError.value = '开始日期不能晚于结束日期'
    return
  }

  termSubmitting.value = true
  termFormError.value = ''
  try {
    await createTerm({
      institutionId: institutionId.value,
      termCode: termForm.termCode.trim(),
      termName: termForm.termName.trim(),
      academicYear: termForm.academicYear.trim(),
      season: trimToUndefined(termForm.season),
      startDate: termForm.startDate,
      endDate: termForm.endDate,
      status: termForm.status,
    })
    ElMessage.success('学期已新增')
    termDialogVisible.value = false
    terms.value = await fetchTerms(institutionId.value)
  } catch (error) {
    termFormError.value = toMessage(error, '新增学期失败')
  } finally {
    termSubmitting.value = false
  }
}

async function submitTimeSlot() {
  if (!institutionId.value) {
    timeSlotFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(timeSlotForm.slotCode) || !trimToUndefined(timeSlotForm.slotName)) {
    timeSlotFormError.value = '请完整填写时间段编码和名称'
    return
  }
  if (!timeSlotForm.startTime || !timeSlotForm.endTime) {
    timeSlotFormError.value = '请选择开始和结束时间'
    return
  }

  timeSlotSubmitting.value = true
  timeSlotFormError.value = ''
  try {
    await createTimeSlot({
      institutionId: institutionId.value,
      campusId: timeSlotForm.campusId || null,
      slotCode: timeSlotForm.slotCode.trim(),
      slotName: timeSlotForm.slotName.trim(),
      weekday: timeSlotForm.weekday,
      startTime: timeSlotForm.startTime,
      endTime: timeSlotForm.endTime,
      durationMinutes: timeSlotForm.durationMinutes,
      status: timeSlotForm.status,
    })
    ElMessage.success('时间段已新增')
    timeSlotDialogVisible.value = false
    timeSlots.value = await fetchTimeSlots(institutionId.value)
  } catch (error) {
    timeSlotFormError.value = toMessage(error, '新增时间段失败')
  } finally {
    timeSlotSubmitting.value = false
  }
}

async function refreshAll() {
  await Promise.all([reloadSchema(), loadResources()])
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    institutions.value = await fetchInstitutions()
    if (institutions.value.length > 0) {
      institutionId.value = institutions.value[0].id
    }
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
  await refreshAll()
}

onMounted(() => {
  initializePage()
})
</script>
