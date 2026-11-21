<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import PickerField from '@/components/PickerField.vue'
import type { PickerOption } from '@/components/picker-types'
import {
  fetchCampuses,
  fetchStudentOptions,
  fetchTerms,
  todayText,
  type CampusRecord,
  type TermRecord,
} from '@/api/enterprise'
import {
  classStatusLabel,
  classStatusTone,
  classStudentStatusLabel,
  createClassStudent,
  deleteClassStudent,
  deleteTeachingClass,
  fetchClassDetail,
  fetchClassStudentPage,
  fetchClassTypes,
  fetchCourses,
  fetchTeachers,
  type ClassStudentRecord,
  type ClassTypeOptionRecord,
  type CourseOptionRecord,
  type TeacherOptionRecord,
  type TeachingClassRecord,
} from '@/api/archive'

const authStore = useAuthStore()
const canWrite = computed(
  () => !authStore.userInfo || authStore.hasPermission('education:class:write'),
)
const institutionId = computed(
  () => authStore.userInfo?.institutionId || authStore.userInfo?.orgId || '',
)

function idText(value?: string | number | null) {
  return value === null || value === undefined ? '' : String(value)
}

const classId = ref('')
const detail = ref<TeachingClassRecord | null>(null)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')

const campuses = ref<CampusRecord[]>([])
const terms = ref<TermRecord[]>([])
const courses = ref<CourseOptionRecord[]>([])
const classTypes = ref<ClassTypeOptionRecord[]>([])
const teachers = ref<TeacherOptionRecord[]>([])
let lookupsLoaded = false

const roster = ref<ClassStudentRecord[]>([])
const rosterTotal = ref(0)
const rosterPage = ref(1)
const rosterPageSize = 10
const rosterLoading = ref(false)
const rosterLoaded = ref(false)
const rosterError = ref('')

const popupRef = ref<any>(null)
const joinSubmitting = ref(false)
const joinError = ref('')
const selectedStudentId = ref('')
const selectedStudentLabel = ref('')
const joinForm = ref({
  joinedAt: todayText(),
  remark: '',
})

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

onLoad((options) => {
  classId.value = String(options?.id || '')
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
  if (!classId.value) {
    loadError.value = '缺少班级编号，请返回班级列表重新进入'
    loaded.value = true
    return
  }
  if (!institutionId.value) {
    loadError.value = '当前账号没有所属机构，无法维护班级'
    loaded.value = true
    return
  }
  if (!lookupsLoaded) {
    await loadLookups()
    lookupsLoaded = true
  }
  await Promise.all([loadDetail(), loadRoster(true)])
}

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

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await fetchClassDetail(institutionId.value, classId.value)
  } catch (error) {
    detail.value = null
    loadError.value = (error as Error)?.message || '班级详情加载失败'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function loadRoster(reset: boolean) {
  if (rosterLoading.value) {
    return
  }
  rosterLoading.value = true
  if (reset) {
    rosterPage.value = 1
  }
  rosterError.value = ''
  try {
    const result = await fetchClassStudentPage({
      page: rosterPage.value,
      pageSize: rosterPageSize,
      institutionId: institutionId.value,
      classId: classId.value,
    })
    roster.value = reset ? result.records : roster.value.concat(result.records)
    rosterTotal.value = result.total
  } catch (error) {
    if (reset) {
      roster.value = []
      rosterTotal.value = 0
    }
    rosterError.value = (error as Error)?.message || '名册加载失败'
  } finally {
    rosterLoading.value = false
    rosterLoaded.value = true
  }
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

/** 入班选择器：按姓名/学号搜索，最多 30 条，不在这里下载整库学员 */
async function loadStudentOptions(keyword: string): Promise<PickerOption[]> {
  const options = await fetchStudentOptions({ keyword })
  return options.map((item) => ({
    value: idText(item.id),
    label: item.studentName || '未命名学员',
    sub: item.studentNo || '',
  }))
}

function onStudentChange(option: PickerOption | null) {
  selectedStudentLabel.value = option
    ? `${option.label}${option.sub ? `（${option.sub}）` : ''}`
    : ''
}

function openJoin() {
  selectedStudentId.value = ''
  selectedStudentLabel.value = ''
  joinForm.value = { joinedAt: todayText(), remark: '' }
  joinError.value = ''
  popupRef.value?.open()
}

function closeJoin() {
  popupRef.value?.close()
}

function onJoinedAtChange(event: { detail: { value: string } }) {
  joinForm.value.joinedAt = event.detail.value
}

async function submitJoin() {
  if (joinSubmitting.value) {
    return
  }
  if (!selectedStudentId.value) {
    joinError.value = '请先选择学员'
    return
  }
  joinSubmitting.value = true
  joinError.value = ''
  uni.showLoading({ title: '入班中…' })
  try {
    await createClassStudent({
      classId: classId.value,
      studentId: selectedStudentId.value,
      institutionId: institutionId.value,
      status: 'active',
      joinedAt: joinForm.value.joinedAt || null,
      remark: joinForm.value.remark.trim() || undefined,
    })
    uni.hideLoading()
    closeJoin()
    uni.showToast({ title: '已入班', icon: 'success' })
    await Promise.all([loadDetail(), loadRoster(true)])
  } catch (error) {
    uni.hideLoading()
    joinError.value = (error as Error)?.message || '入班失败'
  } finally {
    joinSubmitting.value = false
  }
}

function confirmRemove(row: ClassStudentRecord) {
  uni.showModal({
    title: '移出入班',
    content: `确定将「${row.studentName || '该学员'}」移出本班吗？移出后原入班记录不再保留，必要时可重新入班。`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteClassStudent(row.id)
        uni.showToast({ title: '已移出', icon: 'success' })
        await Promise.all([loadDetail(), loadRoster(true)])
      } catch (error) {
        uni.showToast({
          title: (error as Error)?.message || '移出失败',
          icon: 'none',
          duration: 2800,
        })
      }
    },
  })
}

function openEdit() {
  if (!detail.value) {
    return
  }
  uni.navigateTo({ url: `/pages/enterprise/class/form?id=${detail.value.id}` })
}

function confirmDeleteClass() {
  const current = detail.value
  if (!current) {
    return
  }
  uni.showModal({
    title: '删除班级',
    content: `确定删除「${current.className || '该班级'}」吗？班内仍有在班学员时无法删除，请先移出学员。`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteTeachingClass(current.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch (error) {
        uni.showToast({
          title: (error as Error)?.message || '删除失败',
          icon: 'none',
          duration: 2800,
        })
      }
    },
  })
}

onReachBottom(() => {
  if (!rosterLoading.value && roster.value.length < rosterTotal.value) {
    rosterPage.value += 1
    void loadRoster(false)
  }
})

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
    <view v-if="!loaded && loading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="loadError" class="ent-card ent-empty">{{ loadError }}</view>

    <template v-else-if="detail">
      <view class="ent-card">
        <view class="ent-item-title">
          <text class="ent-item-name">{{ detail.className || '未命名班级' }}</text>
          <text class="ent-tag" :class="classStatusTone(detail.status)">
            {{ classStatusLabel(detail.status) }}
          </text>
        </view>
        <view class="ent-row">
          <text class="ent-label">班级编码</text>
          <text class="ent-value">{{ detail.classCode || '-' }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">校区</text>
          <text class="ent-value">{{ campusName(detail.campusId) }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">学期</text>
          <text class="ent-value">{{ termName(detail.termId) }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">课程 / 班型</text>
          <text class="ent-value">{{ courseName(detail.courseId) }} · {{ classTypeName(detail.classTypeId) }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">主讲教师</text>
          <text class="ent-value">{{ teacherName(detail.teacherId) }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">在班 / 容量</text>
          <text class="ent-value">
            {{ detail.currentStudents ?? 0 }} /
            {{ (detail.capacityLimit || 0) > 0 ? detail.capacityLimit : '不限' }}
          </text>
        </view>
        <view class="ent-row">
          <text class="ent-label">课时目标</text>
          <text class="ent-value">{{ lessonTargetText(detail) }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">开班日期</text>
          <text class="ent-value">{{ detail.openedAt || '-' }}</text>
        </view>
        <view v-if="detail.remark" class="ent-row">
          <text class="ent-label">备注</text>
          <text class="ent-value">{{ detail.remark }}</text>
        </view>
        <view v-if="canWrite" class="ent-btn-row right">
          <view class="ent-btn small" @click="openEdit">编辑班级</view>
          <view class="ent-btn danger small" @click="confirmDeleteClass">删除班级</view>
        </view>
      </view>

      <view class="ent-card">
        <view class="ent-card-title">
          <text>班级名册</text>
          <text class="ent-link">共 {{ rosterTotal }} 条</text>
        </view>
        <view v-if="canWrite" class="ent-btn-row">
          <view class="ent-btn primary small" @click="openJoin">新增入班</view>
          <view class="ent-btn small" @click="loadRoster(true)">刷新</view>
        </view>

        <view v-if="rosterLoading && !rosterLoaded" class="ent-empty">加载中…</view>
        <view v-else-if="rosterError" class="ent-empty">{{ rosterError }}</view>
        <view v-else-if="roster.length === 0" class="ent-empty">
          本班还没有学员，点「新增入班」把学员加进来。
        </view>
        <template v-else>
          <view v-for="row in roster" :key="row.id" class="roster-row">
            <view class="ent-item-title">
              <text class="ent-item-name">{{ row.studentName || '未命名学员' }}</text>
              <text class="ent-tag" :class="row.status === 'active' ? 'green' : ''">
                {{ classStudentStatusLabel(row.status) }}
              </text>
            </view>
            <view class="ent-row">
              <text class="ent-label">学号</text>
              <text class="ent-value">{{ row.studentNo || '-' }}</text>
            </view>
            <view class="ent-row">
              <text class="ent-label">入班日期</text>
              <text class="ent-value">{{ row.joinedAt || '-' }}</text>
            </view>
            <view v-if="row.remark" class="ent-row">
              <text class="ent-label">备注</text>
              <text class="ent-value">{{ row.remark }}</text>
            </view>
            <view v-if="canWrite" class="ent-btn-row right">
              <view class="ent-btn danger small" @click="confirmRemove(row)">移出</view>
            </view>
          </view>
        </template>
        <view v-if="roster.length > 0 && roster.length >= rosterTotal" class="ent-empty">
          已显示全部 {{ rosterTotal }} 条入班记录
        </view>
        <view v-else-if="roster.length > 0 && rosterLoading" class="ent-empty">加载中…</view>
        <view class="ent-tip">
          退班/移出走删除入班关系，删除后该学员可重新入班；需要保留离班历史请在管理后台处理。
        </view>
      </view>
    </template>

    <uni-popup ref="popupRef" type="bottom" background-color="#ffffff">
      <view class="ent-sheet">
        <view class="ent-sheet-head">
          <text class="ent-sheet-title">新增入班</text>
          <text class="ent-sheet-close" @click="closeJoin">关闭</text>
        </view>
        <view class="ent-sheet-body">
          <view v-if="joinError" class="sheet-error">{{ joinError }}</view>

          <view class="ent-form-item">
            <text class="ent-form-label"><text class="ent-required">*</text> 学员</text>
            <PickerField
              v-model="selectedStudentId"
              :label="selectedStudentLabel"
              placeholder="按姓名 / 学号搜索"
              title="选择学员"
              :loader="loadStudentOptions"
              @change="onStudentChange"
            />
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">入班日期</text>
            <picker mode="date" :value="joinForm.joinedAt" @change="onJoinedAtChange">
              <view class="ent-picker" :class="{ 'is-placeholder': !joinForm.joinedAt }">
                {{ joinForm.joinedAt || '选择入班日期' }}
              </view>
            </picker>
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">备注</text>
            <textarea
              v-model="joinForm.remark"
              class="ent-textarea"
              maxlength="500"
              placeholder="选填"
              placeholder-class="input-placeholder"
            />
          </view>
        </view>
        <view class="ent-sheet-footer">
          <view class="ent-btn primary" :class="{ disabled: joinSubmitting }" @click="submitJoin">
            {{ joinSubmitting ? '入班中…' : '确认入班' }}
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.roster-row {
  padding: 18rpx 0;
  border-top: 1rpx solid #f3f4f6;
}

.sheet-error {
  margin-bottom: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 10rpx;
  background: #fef2f2;
  font-size: 24rpx;
  line-height: 1.5;
  color: #dc2626;
}
</style>
