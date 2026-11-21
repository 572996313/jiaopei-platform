<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { fetchCampuses, fetchTerms, type CampusRecord, type TermRecord } from '@/api/enterprise'
import {
  classStatusLabel,
  createTeachingClass,
  fetchClassDetail,
  fetchClassTypes,
  fetchCourses,
  fetchNextCode,
  fetchTeachers,
  numberOrNull,
  textOrUndefined,
  updateTeachingClass,
  type ClassTypeOptionRecord,
  type CourseOptionRecord,
  type TeacherOptionRecord,
} from '@/api/archive'

const authStore = useAuthStore()
const institutionId = computed(
  () => authStore.userInfo?.institutionId || authStore.userInfo?.orgId || '',
)

function idText(value?: string | number | null) {
  return value === null || value === undefined ? '' : String(value)
}

const classId = ref('')
const isEdit = computed(() => Boolean(classId.value))
const initialized = ref(false)
const submitting = ref(false)
const loadError = ref('')

const campuses = ref<CampusRecord[]>([])
const terms = ref<TermRecord[]>([])
const courses = ref<CourseOptionRecord[]>([])
const classTypes = ref<ClassTypeOptionRecord[]>([])
const teachers = ref<TeacherOptionRecord[]>([])

const form = ref({
  campusId: '',
  classCode: '',
  className: '',
  termId: '',
  courseId: '',
  classTypeId: '',
  teacherId: '',
  capacityLimit: '0',
  weeklyLessonsMode: 'weekly',
  weeklyLessonsInput: '',
  totalLessons: '',
  status: 'planning',
  openedAt: '',
  remark: '',
})

const statusOptions = [
  { value: 'planning', label: '规划中' },
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
  { value: 'closed', label: '已结课' },
]
const modeOptions = [
  { value: 'weekly', label: '按周填写' },
  { value: 'monthly', label: '按月填写（自动换算每周）' },
]

const campusOptions = computed(() => [
  { value: '', label: '选择校区' },
  ...campuses.value.map((item) => ({
    value: idText(item.id),
    label: item.campusName || item.campusCode || '未命名校区',
  })),
])
const termOptions = computed(() => [
  { value: '', label: '不指定学期' },
  ...terms.value.map((item) => ({
    value: idText(item.id),
    label: item.termName || item.termCode || '未命名学期',
  })),
])
const courseOptions = computed(() => [
  { value: '', label: '不指定课程' },
  ...courses.value.map((item) => ({
    value: idText(item.id),
    label: item.courseName || item.courseCode || '未命名课程',
  })),
])
const classTypeOptions = computed(() => [
  { value: '', label: '不指定班型' },
  ...classTypes.value.map((item) => ({
    value: idText(item.id),
    label: item.typeName || item.typeCode || '未命名班型',
  })),
])
const teacherOptions = computed(() => [
  { value: '', label: '不指定教师' },
  ...teachers.value.map((item) => ({
    value: idText(item.id),
    label: item.teacherName || item.teacherCode || '未命名教师',
  })),
])

/** 详情里的教师可能不在当前校区下拉里，但提交仍会原样带上，显示上不能假装成“不指定” */
const teacherDisplayLabel = computed(() => {
  const found = teacherOptions.value.find((item) => item.value === form.value.teacherId)
  if (found) {
    return found.label
  }
  return form.value.teacherId ? `教师 #${form.value.teacherId}` : '不指定教师'
})

const campusIndex = ref(0)
const termIndex = ref(0)
const courseIndex = ref(0)
const classTypeIndex = ref(0)
const teacherIndex = ref(0)
const statusIndex = ref(0)
const modeIndex = ref(0)

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
  if (!institutionId.value) {
    loadError.value = '当前账号没有所属机构，无法保存班级'
    initialized.value = true
    return
  }
  if (initialized.value) {
    return
  }
  await loadLookups()
  if (isEdit.value) {
    await loadDetail()
  } else {
    form.value.campusId = ''
    fetchNextCode('class')
      .then((code) => {
        if (!form.value.classCode) {
          form.value.classCode = code || ''
        }
      })
      .catch(() => undefined)
  }
  syncIndexes()
  initialized.value = true
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
  try {
    const detail = await fetchClassDetail(institutionId.value, classId.value)
    form.value = {
      campusId: idText(detail.campusId),
      classCode: detail.classCode || '',
      className: detail.className || '',
      termId: idText(detail.termId),
      courseId: idText(detail.courseId),
      classTypeId: idText(detail.classTypeId),
      teacherId: idText(detail.teacherId),
      capacityLimit: String(detail.capacityLimit ?? 0),
      weeklyLessonsMode: detail.weeklyLessonsMode || 'weekly',
      weeklyLessonsInput: detail.weeklyLessonsInput === null || detail.weeklyLessonsInput === undefined
        ? (detail.weeklyLessons ? String(detail.weeklyLessons) : '')
        : String(detail.weeklyLessonsInput),
      totalLessons: detail.totalLessons === null || detail.totalLessons === undefined
        ? ''
        : String(detail.totalLessons),
      status: detail.status || 'planning',
      openedAt: detail.openedAt || '',
      remark: detail.remark || '',
    }
    // 教师按校区收窄；详情里的教师可能已不在当前校区列表，index 会回落到「不指定」，
    // 但 teacherId 仍原样提交，不会因为显示回落而误清主讲教师
    if (form.value.campusId) {
      await loadTeachers(form.value.campusId)
    }
    syncIndexes()
  } catch (error) {
    loadError.value = (error as Error)?.message || '班级详情加载失败'
  }
}

async function loadTeachers(campusId: string) {
  try {
    teachers.value = await fetchTeachers({
      institutionId: institutionId.value,
      campusId: campusId || undefined,
    })
  } catch {
    teachers.value = []
  }
}

function indexOf(options: { value: string }[], value: string) {
  const index = options.findIndex((item) => item.value === value)
  return index >= 0 ? index : 0
}

function syncIndexes() {
  campusIndex.value = indexOf(campusOptions.value, form.value.campusId)
  termIndex.value = indexOf(termOptions.value, form.value.termId)
  courseIndex.value = indexOf(courseOptions.value, form.value.courseId)
  classTypeIndex.value = indexOf(classTypeOptions.value, form.value.classTypeId)
  teacherIndex.value = indexOf(teacherOptions.value, form.value.teacherId)
  statusIndex.value = indexOf(statusOptions, form.value.status)
  modeIndex.value = indexOf(modeOptions, form.value.weeklyLessonsMode)
}

function onCampusChange(event: { detail: { value: string | number } }) {
  campusIndex.value = Number(event.detail.value)
  form.value.campusId = campusOptions.value[campusIndex.value]?.value || ''
  // 主讲教师按校区收窄下拉，但不主动清空已选教师：后端只校验教师属于本机构，
  // 换了校区就把教师悄悄撤掉会变成一次意料之外的写操作
  void loadTeachers(form.value.campusId).then(() => {
    teacherIndex.value = indexOf(teacherOptions.value, form.value.teacherId)
  })
}

function onTermChange(event: { detail: { value: string | number } }) {
  termIndex.value = Number(event.detail.value)
  form.value.termId = termOptions.value[termIndex.value]?.value || ''
}

function onCourseChange(event: { detail: { value: string | number } }) {
  courseIndex.value = Number(event.detail.value)
  form.value.courseId = courseOptions.value[courseIndex.value]?.value || ''
}

function onClassTypeChange(event: { detail: { value: string | number } }) {
  classTypeIndex.value = Number(event.detail.value)
  form.value.classTypeId = classTypeOptions.value[classTypeIndex.value]?.value || ''
}

function onTeacherChange(event: { detail: { value: string | number } }) {
  teacherIndex.value = Number(event.detail.value)
  form.value.teacherId = teacherOptions.value[teacherIndex.value]?.value || ''
}

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  form.value.status = statusOptions[statusIndex.value]?.value || 'planning'
}

function onModeChange(event: { detail: { value: string | number } }) {
  modeIndex.value = Number(event.detail.value)
  form.value.weeklyLessonsMode = modeOptions[modeIndex.value]?.value || 'weekly'
}

function onOpenedAtChange(event: { detail: { value: string } }) {
  form.value.openedAt = event.detail.value
}

function campusLabel(value: string) {
  return campusOptions.value.find((item) => item.value === value)?.label || '选择校区'
}

async function submit() {
  if (submitting.value || loadError.value) {
    return
  }
  if (!institutionId.value) {
    uni.showToast({ title: '当前账号没有所属机构', icon: 'none' })
    return
  }
  if (!form.value.campusId) {
    uni.showToast({ title: '请选择所属校区', icon: 'none' })
    return
  }
  if (!form.value.className.trim()) {
    uni.showToast({ title: '请填写班级名称', icon: 'none' })
    return
  }
  if (isEdit.value && !form.value.classCode.trim()) {
    uni.showToast({ title: '编辑时班级编码不能为空', icon: 'none' })
    return
  }

  const capacityText = form.value.capacityLimit.trim()
  const capacity = capacityText === '' ? 0 : Number(capacityText)
  if (!Number.isInteger(capacity) || capacity < 0) {
    uni.showToast({ title: '容量上限必须是不小于 0 的整数', icon: 'none' })
    return
  }
  const weeklyInput = numberOrNull(form.value.weeklyLessonsInput)
  if (weeklyInput !== null && (!Number.isInteger(weeklyInput) || weeklyInput < 1)) {
    uni.showToast({ title: '课时目标必须是正整数', icon: 'none' })
    return
  }
  const totalLessons = numberOrNull(form.value.totalLessons)
  if (totalLessons !== null && (!Number.isInteger(totalLessons) || totalLessons < 1)) {
    uni.showToast({ title: '整班总课时必须是正整数', icon: 'none' })
    return
  }

  const payload = {
    institutionId: institutionId.value,
    campusId: form.value.campusId,
    termId: form.value.termId || null,
    courseId: form.value.courseId || null,
    classTypeId: form.value.classTypeId || null,
    classCode: textOrUndefined(form.value.classCode),
    className: form.value.className.trim(),
    teacherId: form.value.teacherId || null,
    capacityLimit: capacity,
    weeklyLessonsMode: form.value.weeklyLessonsMode,
    weeklyLessonsInput: weeklyInput,
    totalLessons,
    status: form.value.status || 'planning',
    openedAt: form.value.openedAt || null,
    remark: form.value.remark.trim(),
  }

  submitting.value = true
  uni.showLoading({ title: '保存中…' })
  try {
    if (isEdit.value) {
      await updateTeachingClass(classId.value, payload)
    } else {
      await createTeachingClass(payload)
    }
    uni.hideLoading()
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (error) {
    uni.hideLoading()
    uni.showToast({
      title: (error as Error)?.message || '保存失败',
      icon: 'none',
      duration: 2800,
    })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="ent-page">
    <view class="ent-card">
      <view class="ent-card-title">
        <text>{{ isEdit ? '编辑班级档案' : '新增班级' }}</text>
        <text class="ent-tag blue">{{ classStatusLabel(form.status) }}</text>
      </view>
      <view class="ent-tip">
        班级编码留空时按编号规则自动生成；课时目标留空表示未设定，编辑时清空会撤销原目标。
      </view>
    </view>

    <view v-if="loadError" class="ent-card ent-empty">{{ loadError }}</view>

    <view v-else class="ent-card">
      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 所属校区</text>
        <picker
          mode="selector"
          :range="campusOptions"
          range-key="label"
          :value="campusIndex"
          @change="onCampusChange"
        >
          <view class="ent-picker" :class="{ 'is-placeholder': !form.campusId }">
            {{ campusLabel(form.campusId) }}
          </view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">班级编码</text>
        <input
          v-model="form.classCode"
          class="ent-input"
          type="text"
          maxlength="64"
          :placeholder="isEdit ? '编辑时必填' : '可留空自动生成'"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 班级名称</text>
        <input
          v-model="form.className"
          class="ent-input"
          type="text"
          maxlength="128"
          placeholder="如 周六上午数学A1班"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">学期</text>
        <picker
          mode="selector"
          :range="termOptions"
          range-key="label"
          :value="termIndex"
          @change="onTermChange"
        >
          <view class="ent-picker">{{ termOptions[termIndex]?.label || '不指定学期' }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">课程</text>
        <picker
          mode="selector"
          :range="courseOptions"
          range-key="label"
          :value="courseIndex"
          @change="onCourseChange"
        >
          <view class="ent-picker">{{ courseOptions[courseIndex]?.label || '不指定课程' }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">班型</text>
        <picker
          mode="selector"
          :range="classTypeOptions"
          range-key="label"
          :value="classTypeIndex"
          @change="onClassTypeChange"
        >
          <view class="ent-picker">{{ classTypeOptions[classTypeIndex]?.label || '不指定班型' }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">主讲教师</text>
        <picker
          mode="selector"
          :range="teacherOptions"
          range-key="label"
          :value="teacherIndex"
          @change="onTeacherChange"
        >
          <view class="ent-picker">{{ teacherDisplayLabel }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">容量上限</text>
        <input
          v-model="form.capacityLimit"
          class="ent-input"
          type="number"
          placeholder="0 表示不限制"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">每周应上节数口径</text>
        <picker
          mode="selector"
          :range="modeOptions"
          range-key="label"
          :value="modeIndex"
          @change="onModeChange"
        >
          <view class="ent-picker">{{ modeOptions[modeIndex]?.label || '按周填写' }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">
          {{ form.weeklyLessonsMode === 'monthly' ? '每月节数' : '每周节数' }}
        </text>
        <input
          v-model="form.weeklyLessonsInput"
          class="ent-input"
          type="number"
          placeholder="留空表示未设定"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">整班总课时</text>
        <input
          v-model="form.totalLessons"
          class="ent-input"
          type="number"
          placeholder="留空表示未设定"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">班级状态</text>
        <picker
          mode="selector"
          :range="statusOptions"
          range-key="label"
          :value="statusIndex"
          @change="onStatusChange"
        >
          <view class="ent-picker">{{ statusOptions[statusIndex]?.label || '规划中' }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">开班日期</text>
        <view class="ent-date-row">
          <picker mode="date" :value="form.openedAt" @change="onOpenedAtChange">
            <view class="ent-picker" :class="{ 'is-placeholder': !form.openedAt }">
              {{ form.openedAt || '选择开班日期' }}
            </view>
          </picker>
          <view v-if="form.openedAt" class="ent-btn small" @click="form.openedAt = ''">清空</view>
        </view>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">备注</text>
        <textarea
          v-model="form.remark"
          class="ent-textarea"
          maxlength="500"
          placeholder="选填"
          placeholder-class="input-placeholder"
        />
      </view>
    </view>

    <view v-if="!loadError" class="ent-footer">
      <view class="ent-btn primary" :class="{ disabled: submitting }" @click="submit">
        {{ submitting ? '保存中…' : isEdit ? '保存修改' : '创建班级' }}
      </view>
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}
</style>
