<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { fetchCampuses, fetchTerms, type CampusRecord, type TermRecord } from '@/api/enterprise'
import {
  classStatusLabel,
  classStatusTone,
  fetchClassTypes,
  fetchClasses,
  fetchCourses,
  fetchTeachers,
  type ClassTypeOptionRecord,
  type CourseOptionRecord,
  type TeacherOptionRecord,
  type TeachingClassRecord,
} from '@/api/archive'

const authStore = useAuthStore()
const institutionId = computed(
  () => authStore.userInfo?.institutionId || authStore.userInfo?.orgId || '',
)
const canWrite = computed(
  () => !authStore.userInfo || authStore.hasPermission('education:class:write'),
)

/** 小号机构的主数据 id 运行期可能是数字，比较与展示统一转成字符串 */
function idText(value?: string | number | null) {
  return value === null || value === undefined ? '' : String(value)
}

const classes = ref<TeachingClassRecord[]>([])
const campuses = ref<CampusRecord[]>([])
const terms = ref<TermRecord[]>([])
const courses = ref<CourseOptionRecord[]>([])
const classTypes = ref<ClassTypeOptionRecord[]>([])
const teachers = ref<TeacherOptionRecord[]>([])
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')

const keyword = ref('')
const filterOpen = ref(false)
const campusFilter = ref('')
const termFilter = ref('')
const statusFilter = ref('')

const campusOptions = computed(() => [
  { value: '', label: '全部校区' },
  ...campuses.value.map((item) => ({
    value: idText(item.id),
    label: item.campusName || item.campusCode || '未命名校区',
  })),
])
const termOptions = computed(() => [
  { value: '', label: '全部学期' },
  ...terms.value.map((item) => ({
    value: idText(item.id),
    label: item.termName || item.termCode || '未命名学期',
  })),
])
const statusOptions = [
  { value: '', label: '全部' },
  { value: 'planning', label: '规划中' },
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
  { value: 'closed', label: '已结课' },
]
// 已生效的筛选与面板里的草稿分开，点「查询」才改动列表
const campusIndex = ref(0)
const termIndex = ref(0)
const statusIndex = ref(0)
const draftCampusIndex = ref(0)
const draftTermIndex = ref(0)
const draftStatusIndex = ref(0)

const activeFilterCount = computed(
  () =>
    (campusFilter.value ? 1 : 0)
    + (termFilter.value ? 1 : 0)
    + (statusFilter.value ? 1 : 0),
)
const campusText = computed(() => campusOptions.value[campusIndex.value]?.label || '')
const termText = computed(() => termOptions.value[termIndex.value]?.label || '')
const statusText = computed(() => statusOptions[statusIndex.value]?.label || '全部')

const campusMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  campuses.value.forEach((item) => {
    map[idText(item.id)] = item.campusName || item.campusCode || '未知校区'
  })
  return map
})
const termMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  terms.value.forEach((item) => {
    map[idText(item.id)] = item.termName || item.termCode || '未知学期'
  })
  return map
})
const courseMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  courses.value.forEach((item) => {
    map[idText(item.id)] = item.courseName || item.courseCode || '未知课程'
  })
  return map
})
const classTypeMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  classTypes.value.forEach((item) => {
    map[idText(item.id)] = item.typeName || item.typeCode || '未知班型'
  })
  return map
})
const teacherMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  teachers.value.forEach((item) => {
    map[idText(item.id)] = item.teacherName || item.teacherCode || '未知教师'
  })
  return map
})

/** 班级列表接口不支持关键字，按名称/编码在本机收窄；校区、学期、状态也一样 */
const visibleClasses = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return classes.value.filter((item) => {
    if (campusFilter.value && idText(item.campusId) !== campusFilter.value) {
      return false
    }
    if (termFilter.value && idText(item.termId) !== termFilter.value) {
      return false
    }
    if (statusFilter.value && item.status !== statusFilter.value) {
      return false
    }
    if (text) {
      const haystack = `${item.className || ''} ${item.classCode || ''}`.toLowerCase()
      if (!haystack.includes(text)) {
        return false
      }
    }
    return true
  })
})

onShow(() => {
  void init()
})

async function init() {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!authStore.userInfo) {
    try {
      await authStore.refreshUserInfo()
    } catch {
      return
    }
  }
  if (!institutionId.value) {
    loadError.value = '当前账号没有所属机构，无法维护班级'
    loaded.value = true
    return
  }
  await loadLookups()
  await loadClasses()
}

/** 主数据任何一项失败都不该拖住班级列表，名称回显退化成 #id 即可 */
async function loadLookups() {
  const [campusRes, termRes, courseRes, classTypeRes, teacherRes] = await Promise.allSettled([
    fetchCampuses(),
    fetchTerms(),
    fetchCourses({ institutionId: institutionId.value }),
    fetchClassTypes({ institutionId: institutionId.value }),
    fetchTeachers({ institutionId: institutionId.value }),
  ])
  if (campusRes.status === 'fulfilled') {
    campuses.value = campusRes.value
  }
  if (termRes.status === 'fulfilled') {
    terms.value = termRes.value
  }
  if (courseRes.status === 'fulfilled') {
    courses.value = courseRes.value
  }
  if (classTypeRes.status === 'fulfilled') {
    classTypes.value = classTypeRes.value
  }
  if (teacherRes.status === 'fulfilled') {
    teachers.value = teacherRes.value
  }
}

async function loadClasses() {
  loading.value = true
  loadError.value = ''
  try {
    classes.value = await fetchClasses({ institutionId: institutionId.value })
  } catch (error) {
    classes.value = []
    loadError.value = (error as Error)?.message || '班级列表加载失败'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function toggleFilterPanel() {
  if (filterOpen.value) {
    filterOpen.value = false
    return
  }
  draftCampusIndex.value = campusIndex.value
  draftTermIndex.value = termIndex.value
  draftStatusIndex.value = statusIndex.value
  filterOpen.value = true
}

function onDraftCampusChange(event: { detail: { value: string | number } }) {
  draftCampusIndex.value = Number(event.detail.value)
}

function onDraftTermChange(event: { detail: { value: string | number } }) {
  draftTermIndex.value = Number(event.detail.value)
}

function applyFilters() {
  campusIndex.value = draftCampusIndex.value
  termIndex.value = draftTermIndex.value
  statusIndex.value = draftStatusIndex.value
  campusFilter.value = campusOptions.value[campusIndex.value]?.value || ''
  termFilter.value = termOptions.value[termIndex.value]?.value || ''
  statusFilter.value = statusOptions[statusIndex.value]?.value || ''
  filterOpen.value = false
}

function resetFilters() {
  keyword.value = ''
  campusIndex.value = 0
  termIndex.value = 0
  statusIndex.value = 0
  draftCampusIndex.value = 0
  draftTermIndex.value = 0
  draftStatusIndex.value = 0
  campusFilter.value = ''
  termFilter.value = ''
  statusFilter.value = ''
  filterOpen.value = false
}

function clearCampus() {
  campusIndex.value = 0
  draftCampusIndex.value = 0
  campusFilter.value = ''
}

function clearTerm() {
  termIndex.value = 0
  draftTermIndex.value = 0
  termFilter.value = ''
}

function clearStatus() {
  statusIndex.value = 0
  draftStatusIndex.value = 0
  statusFilter.value = ''
}

function campusName(value?: string | null) {
  return campusMap.value[idText(value)] || '-'
}

function termName(value?: string | null) {
  return termMap.value[idText(value)] || '-'
}

function courseName(value?: string | null) {
  return courseMap.value[idText(value)] || '-'
}

function classTypeName(value?: string | null) {
  return classTypeMap.value[idText(value)] || '-'
}

function teacherName(value?: string | null) {
  return teacherMap.value[idText(value)] || '-'
}

function lessonTargetText(item: TeachingClassRecord) {
  const parts: string[] = []
  if (item.weeklyLessons) {
    if (item.weeklyLessonsMode === 'monthly' && item.weeklyLessonsInput) {
      parts.push(`${item.weeklyLessonsInput} 节/月（≈每周 ${item.weeklyLessons} 节）`)
    } else {
      parts.push(`每周 ${item.weeklyLessons} 节`)
    }
  }
  if (item.totalLessons) {
    parts.push(`总 ${item.totalLessons} 节`)
  }
  return parts.length > 0 ? parts.join(' · ') : '未设定'
}

function openCreate() {
  uni.navigateTo({ url: '/pages/enterprise/class/form?mode=create' })
}

function openDetail(item: TeachingClassRecord) {
  uni.navigateTo({ url: `/pages/enterprise/class/detail?id=${item.id}` })
}

onPullDownRefresh(async () => {
  try {
    await init()
  } finally {
    uni.stopPullDownRefresh()
  }
})
</script>

<template>
  <view class="ent-page list-page">
    <view class="list-toolbar">
      <view class="toolbar-search">
        <uni-icons type="search" size="18" color="#9ca3af" />
        <input
          v-model="keyword"
          class="toolbar-search-input"
          type="text"
          confirm-type="search"
          placeholder="搜索班级名称 / 编码"
          placeholder-class="input-placeholder"
        />
        <uni-icons
          v-if="keyword"
          type="clear"
          size="18"
          color="#c4c9d1"
          class="toolbar-clear"
          @click="keyword = ''"
        />
      </view>
      <view class="toolbar-btn" :class="{ active: activeFilterCount > 0 }" @click="toggleFilterPanel">
        <uni-icons type="tune" size="18" :color="activeFilterCount > 0 ? '#2563eb' : '#4b5563'" />
        <text>筛选</text>
        <text v-if="activeFilterCount > 0" class="toolbar-badge">{{ activeFilterCount }}</text>
      </view>
    </view>

    <view v-if="activeFilterCount > 0" class="chip-row">
      <view v-if="campusFilter" class="chip" @click="clearCampus">校区：{{ campusText }} ×</view>
      <view v-if="termFilter" class="chip" @click="clearTerm">学期：{{ termText }} ×</view>
      <view v-if="statusFilter" class="chip" @click="clearStatus">状态：{{ statusText }} ×</view>
      <text class="chip-clear" @click="resetFilters">清空</text>
    </view>

    <view v-if="filterOpen" class="filter-panel">
      <view class="filter-grid">
        <view class="filter-field">
          <text class="filter-label">校区</text>
          <picker
            mode="selector"
            :range="campusOptions"
            range-key="label"
            :value="draftCampusIndex"
            @change="onDraftCampusChange"
          >
            <view class="ent-picker" :class="{ 'is-placeholder': !campusOptions[draftCampusIndex]?.value }">
              {{ campusOptions[draftCampusIndex]?.label || '全部校区' }}
            </view>
          </picker>
        </view>
        <view class="filter-field">
          <text class="filter-label">学期</text>
          <picker
            mode="selector"
            :range="termOptions"
            range-key="label"
            :value="draftTermIndex"
            @change="onDraftTermChange"
          >
            <view class="ent-picker" :class="{ 'is-placeholder': !termOptions[draftTermIndex]?.value }">
              {{ termOptions[draftTermIndex]?.label || '全部学期' }}
            </view>
          </picker>
        </view>
      </view>

      <view class="filter-field filter-field-block">
        <text class="filter-label">班级状态</text>
        <view class="status-chips">
          <view
            v-for="(option, index) in statusOptions"
            :key="option.value"
            class="status-chip"
            :class="{ active: draftStatusIndex === index }"
            @click="draftStatusIndex = index"
          >
            {{ option.label }}
          </view>
        </view>
      </view>

      <view class="filter-actions">
        <view class="ent-btn small" @click="resetFilters">重置</view>
        <view class="ent-btn primary small" @click="applyFilters">查询</view>
      </view>
    </view>

    <view class="list-head">
      <text class="list-title">班级与入班</text>
      <text class="list-count">共 {{ visibleClasses.length }} 个班</text>
    </view>

    <view v-if="!loaded && loading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="loadError" class="ent-card ent-empty">{{ loadError }}</view>
    <view v-else-if="visibleClasses.length === 0" class="ent-card ent-empty">
      没有符合条件的班级，先新增班级并在班级详情里入班。
    </view>

    <view
      v-for="item in visibleClasses"
      :key="item.id"
      class="ent-card"
      hover-class="ent-card-hover"
      @click="openDetail(item)"
    >
      <view class="ent-item-title">
        <text class="ent-item-name">{{ item.className || '未命名班级' }}</text>
        <text class="ent-tag" :class="classStatusTone(item.status)">
          {{ classStatusLabel(item.status) }}
        </text>
      </view>
      <view class="ent-row">
        <text class="ent-label">班级编码</text>
        <text class="ent-value">{{ item.classCode || '-' }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">校区 / 学期</text>
        <text class="ent-value">{{ campusName(item.campusId) }} · {{ termName(item.termId) }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">课程 / 班型</text>
        <text class="ent-value">{{ courseName(item.courseId) }} · {{ classTypeName(item.classTypeId) }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">主讲教师</text>
        <text class="ent-value">{{ teacherName(item.teacherId) }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">在班 / 容量</text>
        <text class="ent-value">
          {{ item.currentStudents ?? 0 }} /
          {{ (item.capacityLimit || 0) > 0 ? item.capacityLimit : '不限' }}
        </text>
      </view>
      <view class="ent-row">
        <text class="ent-label">课时目标</text>
        <text class="ent-value">{{ lessonTargetText(item) }}</text>
      </view>
      <view class="ent-btn-row right">
        <view class="ent-btn ghost small" @click.stop="openDetail(item)">名册与入班 ›</view>
      </view>
    </view>

    <view v-if="canWrite" class="fab" @click="openCreate">
      <uni-icons type="plusempty" size="30" color="#ffffff" />
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

/* 筛选面板里的原生 picker 沿用表单控件样式，但高度压一档 */
.filter-field .ent-picker {
  min-height: 68rpx;
  font-size: 25rpx;
}
</style>
