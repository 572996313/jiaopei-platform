<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createFeeLedger,
  fetchClassOptions,
  fetchStudentOptions,
  fetchTerms,
  generateFeeLedgers,
  type TermRecord,
} from '@/api/enterprise'
import type { PickerOption } from '@/components/picker-types'
import PickerField from '@/components/PickerField.vue'

const mode = ref<'single' | 'generate'>('single')
const terms = ref<TermRecord[]>([])

const termId = ref('')
const termLabel = ref('')
const classId = ref('')
const classLabel = ref('')
const studentId = ref('')
const studentLabel = ref('')
const submitting = ref(false)

onLoad((options) => {
  mode.value = options?.mode === 'generate' ? 'generate' : 'single'
  fetchTerms()
    .then((items) => {
      terms.value = items
    })
    .catch(() => {
      terms.value = []
    })
})

function switchMode(value: 'single' | 'generate') {
  mode.value = value
  studentId.value = ''
  studentLabel.value = ''
}

async function loadTermOptions(keyword: string): Promise<PickerOption[]> {
  if (terms.value.length === 0) {
    terms.value = await fetchTerms()
  }
  const kw = keyword.trim()
  return terms.value
    .filter((term) => !kw || term.termName.includes(kw))
    .map((term) => ({
      value: term.id,
      label: term.termName,
      sub:
        term.startDate && term.endDate
          ? `${term.startDate} ~ ${term.endDate}`
          : '未维护起止日期，建账前需到管理后台补齐',
      meta: { termName: term.termName, termId: term.id },
    }))
}

async function loadClassOptions(keyword: string): Promise<PickerOption[]> {
  const classes = await fetchClassOptions({
    keyword: keyword.trim() || undefined,
    termId: termId.value || undefined,
  })
  return classes.map((clazz) => ({
    value: clazz.id,
    label: clazz.className,
    sub: clazz.classCode || undefined,
    meta: { termId: clazz.termId || '', campusId: clazz.campusId || '' },
  }))
}

async function loadStudentOptions(keyword: string): Promise<PickerOption[]> {
  const students = await fetchStudentOptions({ keyword: keyword.trim() || undefined })
  return students.map((student) => ({
    value: student.id,
    label: `${student.studentName}（${student.studentNo || '无学号'}）`,
  }))
}

function onTermChange(option: PickerOption | null) {
  termLabel.value = option?.label || ''
  if (!option) {
    termId.value = ''
  }
}

async function onClassChange(option: PickerOption | null) {
  classLabel.value = option?.label || ''
  if (!option) {
    classId.value = ''
    return
  }
  // 班级带学期时顺手把学期补上,少一步选择、也避免两份参数对不上
  const classTermId = String(option.meta?.termId || '')
  if (!termId.value && classTermId) {
    if (terms.value.length === 0) {
      // onLoad 的学期请求可能还没回来，补拉一次才能把学期名填到选择器上
      terms.value = await fetchTerms().catch(() => [])
    }
    termId.value = classTermId
    termLabel.value = terms.value.find((term) => term.id === classTermId)?.termName || ''
  }
}

function onStudentChange(option: PickerOption | null) {
  studentLabel.value = option?.label || ''
  if (!option) {
    studentId.value = ''
  }
}

function validate() {
  if (!termId.value) {
    uni.showToast({ title: '请选择学期', icon: 'none' })
    return false
  }
  if (!classId.value) {
    uni.showToast({ title: '请选择班级', icon: 'none' })
    return false
  }
  if (mode.value === 'single' && !studentId.value) {
    uni.showToast({ title: '请选择学员', icon: 'none' })
    return false
  }
  return true
}

async function handleSubmit() {
  if (submitting.value || !validate()) {
    return
  }
  submitting.value = true
  uni.showLoading({ title: mode.value === 'single' ? '建账中…' : '生成中…' })
  try {
    if (mode.value === 'single') {
      const created = await createFeeLedger({
        classId: classId.value,
        termId: termId.value,
        studentId: studentId.value,
      })
      uni.hideLoading()
      uni.showToast({ title: '台账已创建', icon: 'success' })
      setTimeout(() => {
        uni.redirectTo({ url: `/pages/enterprise/fee-ledger/detail?id=${created.id}` })
      }, 500)
      return
    }

    const result = await generateFeeLedgers({ termId: termId.value, classId: classId.value })
    uni.hideLoading()
    uni.showModal({
      title: '批量建账完成',
      content: `新建 ${result.createdCount} 份，跳过 ${result.skippedCount} 份（已有台账），每份默认 ${result.itemCount} 个收费项目。`,
      showCancel: false,
      success: () => uni.navigateBack(),
    })
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '建账失败', icon: 'none', duration: 2600 })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="ent-page">
    <view class="ent-tabs">
      <view class="ent-tab" :class="{ active: mode === 'single' }" @click="switchMode('single')">单个新增</view>
      <view class="ent-tab" :class="{ active: mode === 'generate' }" @click="switchMode('generate')">按班建账</view>
    </view>

    <view class="ent-card">
      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 学期</text>
        <PickerField
          v-model="termId"
          :label="termLabel"
          title="选择学期"
          placeholder="请选择学期"
          :loader="loadTermOptions"
          @change="onTermChange"
        />
      </view>
      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 班级</text>
        <PickerField
          v-model="classId"
          :label="classLabel"
          title="选择班级"
          placeholder="请选择班级"
          :loader="loadClassOptions"
          @change="onClassChange"
        />
      </view>
      <view v-if="mode === 'single'" class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 学员</text>
        <PickerField
          v-model="studentId"
          :label="studentLabel"
          title="选择学员"
          placeholder="输入姓名或学号搜索"
          :loader="loadStudentOptions"
          @change="onStudentChange"
        />
      </view>

      <view class="ent-tip">
        <text v-if="mode === 'generate'">
          只给该班在读名册里的学员建账；已有本学期台账的学员会自动跳过，不会覆盖已录数据。每个学员按当前启用的收费项目自动建行并取价。
        </text>
        <text v-else>
          单个学员建账后，会按当前启用的收费项目自动建行并取价；班级与学期建账后不可改。
        </text>
      </view>
      <view class="ent-tip">
        <text>学期必须维护起止日期，否则无法生成收费月表。</text>
      </view>
    </view>

    <view class="ent-footer">
      <view class="ent-btn primary" :class="{ disabled: submitting }" @click="handleSubmit">
        {{ submitting ? '提交中…' : (mode === 'single' ? '创建台账' : '生成台账') }}
      </view>
    </view>
  </view>
</template>

<style scoped>
.ent-footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
