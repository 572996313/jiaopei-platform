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
  { value: '', label: '全部状态' },
  { value: 'planning', label: '规划中' },
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
  { value: 'closed', label: '已结课' },
]
const campusIndex = ref(0)
const termIndex = ref(0)
const statusIndex = ref(0)

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

function onCampusChange(event: { detail: { value: string | number } }) {
  campusIndex.value = Number(event.detail.value)
  campusFilter.value = campusOptions.value[campusIndex.value]?.value || ''
}

function onTermChange(event: { detail: { value: string | number } }) {
  termIndex.value = Number(event.detail.value)
  termFilter.value = termOptions.value[termIndex.value]?.value || ''
}

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  statusFilter.value = statusOptions[statusIndex.value]?.value || ''
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
  <view class="ent-page">
    <view class="ent-card">
      <view class="ent-card-title">
        <text>班级与入班</text>
        <text class="ent-link">共 {{ visibleClasses.length }} 个班</text>
      </view>

      <view class="ent-search-row">
        <input
          v-model="keyword"
          class="ent-search-input"
          type="text"
          confirm-type="search"
          placeholder="按班级名称 / 编码筛选"
          placeholder-class="input-placeholder"
        />
        <view class="ent-btn small" @click="loadClasses">刷新</view>
      </view>

      <view class="ent-filter-row">
        <picker
          mode="selector"
          :range="campusOptions"
          range-key="label"
          :value="campusIndex"
          @change="onCampusChange"
        >
          <view class="ent-picker">{{ campusOptions[campusIndex]?.label || '全部校区' }}</view>
        </picker>
        <picker
          mode="selector"
          :range="termOptions"
          range-key="label"
          :value="termIndex"
          @change="onTermChange"
        >
          <view class="ent-picker">{{ termOptions[termIndex]?.label || '全部学期' }}</view>
        </picker>
      </view>
      <view class="ent-filter-row">
        <picker
          mode="selector"
          :range="statusOptions"
          range-key="label"
          :value="statusIndex"
          @change="onStatusChange"
        >
          <view class="ent-picker">{{ statusOptions[statusIndex]?.label || '全部状态' }}</view>
        </picker>
        <view v-if="canWrite" class="ent-btn primary small" @click="openCreate">新增班级</view>
      </view>

      <view class="ent-tip">
        入班即把学员加入班级名册；收费台账按学期、班级与在读名册建账，所以学员和班级要先维护好。
      </view>
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
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}
</style>
