<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { fetchDictOptions, todayText, type DictOptionItem } from '@/api/enterprise'
import {
  createStudent,
  fetchNextCode,
  stageStudentRecord,
  studentStatusLabel,
  takeStudentRecord,
  textOrUndefined,
  updateStudent,
  type StudentRecord,
} from '@/api/archive'

const authStore = useAuthStore()
const institutionId = computed(
  () => authStore.userInfo?.institutionId || authStore.userInfo?.orgId || '',
)

const editingId = ref('')
const initialized = ref(false)
const submitting = ref(false)
const loadError = ref('')

const genderOptions = ref<DictOptionItem[]>([])
const statusOptions = ref<DictOptionItem[]>([])
const genderIndex = ref(0)
const statusIndex = ref(0)

const today = todayText()
const form = ref({
  studentNo: '',
  studentName: '',
  gender: '',
  birthday: '',
  guardianName: '',
  guardianPhone: '',
  status: 'studying',
  enrolledAt: today,
  remark: '',
})

const isEdit = computed(() => Boolean(editingId.value))

onLoad((options) => {
  editingId.value = String(options?.id || '')
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
    loadError.value = '当前账号没有所属机构，无法保存学员档案'
    initialized.value = true
    return
  }
  if (!initialized.value) {
    await loadDicts()
    if (isEdit.value) {
      const record = takeStudentRecord()
      if (!record) {
        loadError.value = '档案信息已过期，请返回学员列表重新进入'
        initialized.value = true
        return
      }
      fillForm(record)
    } else {
      // 编号只是“让客户看得见”的建议值；规则没启用时接口会报错，留空由后端补号
      fetchNextCode('student')
        .then((code) => {
          if (!form.value.studentNo) {
            form.value.studentNo = code || ''
          }
        })
        .catch(() => undefined)
    }
    // 新建时状态默认 studying，字典回来后要把选择器高亮同步到默认值，
    // 否则直接确认选择器会把第一条字典当成用户选择
    syncIndexes()
    initialized.value = true
  }
}

async function loadDicts() {
  try {
    const [genders, statuses] = await Promise.all([
      fetchDictOptions('gender'),
      fetchDictOptions('student_status'),
    ])
    genderOptions.value = genders
    statusOptions.value = statuses
    syncIndexes()
  } catch {
    // 字典不可用时仍可保存，状态保留后端默认值
  }
}

function fillForm(record: StudentRecord) {
  form.value = {
    studentNo: record.studentNo || '',
    studentName: record.studentName || '',
    gender: record.gender || '',
    birthday: record.birthday || '',
    guardianName: record.guardianName || '',
    guardianPhone: record.guardianPhone || '',
    status: record.status || 'studying',
    enrolledAt: record.enrolledAt || '',
    remark: record.remark || '',
  }
  syncIndexes()
}

function syncIndexes() {
  const genderAt = genderOptions.value.findIndex((item) => item.itemValue === form.value.gender)
  genderIndex.value = genderAt >= 0 ? genderAt : 0
  const statusAt = statusOptions.value.findIndex((item) => item.itemValue === form.value.status)
  statusIndex.value = statusAt >= 0 ? statusAt : 0
}

function genderLabel(value?: string | null) {
  if (!value) {
    return '未填写'
  }
  return genderOptions.value.find((item) => item.itemValue === value)?.itemName || value
}

function statusLabel(value?: string | null) {
  if (!value) {
    return '选择状态'
  }
  return statusOptions.value.find((item) => item.itemValue === value)?.itemName
    || studentStatusLabel(value)
}

function onGenderChange(event: { detail: { value: string | number } }) {
  genderIndex.value = Number(event.detail.value)
  form.value.gender = genderOptions.value[genderIndex.value]?.itemValue || ''
}

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  form.value.status = statusOptions.value[statusIndex.value]?.itemValue || 'studying'
}

function onBirthdayChange(event: { detail: { value: string } }) {
  form.value.birthday = event.detail.value
}

function onEnrolledAtChange(event: { detail: { value: string } }) {
  form.value.enrolledAt = event.detail.value
}

async function submit() {
  if (submitting.value || loadError.value) {
    return
  }
  if (!institutionId.value) {
    uni.showToast({ title: '当前账号没有所属机构', icon: 'none' })
    return
  }
  if (!form.value.studentName.trim()) {
    uni.showToast({ title: '请填写学员姓名', icon: 'none' })
    return
  }

  const payload = {
    studentNo: textOrUndefined(form.value.studentNo),
    studentName: form.value.studentName.trim(),
    gender: form.value.gender || undefined,
    birthday: form.value.birthday || null,
    guardianName: form.value.guardianName.trim(),
    guardianPhone: form.value.guardianPhone.trim(),
    status: form.value.status || 'studying',
    enrolledAt: form.value.enrolledAt || null,
    remark: form.value.remark.trim(),
  }

  submitting.value = true
  uni.showLoading({ title: '保存中…' })
  try {
    if (isEdit.value) {
      const updated = await updateStudent(editingId.value, payload)
      // 详情页没有按 id 回查的接口，编辑结果原样交接回去
      stageStudentRecord(updated || {
        id: editingId.value,
        studentName: payload.studentName,
        institutionId: institutionId.value,
      })
    } else {
      await createStudent({ institutionId: institutionId.value, ...payload })
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
        <text>{{ isEdit ? '编辑学员档案' : '新增学员档案' }}</text>
      </view>
      <view class="ent-tip">
        学员编号留空时按编号规则自动生成；出生日期、入学日期不能晚于今天。
      </view>
    </view>

    <view v-if="loadError" class="ent-card ent-empty">{{ loadError }}</view>

    <view v-else class="ent-card">
      <view class="ent-form-item">
        <text class="ent-form-label">学员编号</text>
        <input
          v-model="form.studentNo"
          class="ent-input"
          type="text"
          maxlength="64"
          placeholder="可留空自动生成"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label"><text class="ent-required">*</text> 学员姓名</text>
        <input
          v-model="form.studentName"
          class="ent-input"
          type="text"
          maxlength="64"
          placeholder="填写学员姓名"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">性别</text>
        <picker
          mode="selector"
          :range="genderOptions"
          range-key="itemName"
          :value="genderIndex"
          @change="onGenderChange"
        >
          <view class="ent-picker" :class="{ 'is-placeholder': !form.gender }">
            {{ genderLabel(form.gender) }}
          </view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">出生日期</text>
        <view class="ent-date-row">
          <picker
            mode="date"
            :value="form.birthday || today"
            :end="today"
            @change="onBirthdayChange"
          >
            <view class="ent-picker" :class="{ 'is-placeholder': !form.birthday }">
              {{ form.birthday || '选择出生日期' }}
            </view>
          </picker>
          <view v-if="form.birthday" class="ent-btn small" @click="form.birthday = ''">清空</view>
        </view>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">监护人姓名</text>
        <input
          v-model="form.guardianName"
          class="ent-input"
          type="text"
          maxlength="64"
          placeholder="选填"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">监护人电话</text>
        <input
          v-model="form.guardianPhone"
          class="ent-input"
          type="number"
          maxlength="20"
          placeholder="选填"
          placeholder-class="input-placeholder"
        />
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">学员状态</text>
        <picker
          mode="selector"
          :range="statusOptions"
          range-key="itemName"
          :value="statusIndex"
          @change="onStatusChange"
        >
          <view class="ent-picker">{{ statusLabel(form.status) }}</view>
        </picker>
      </view>

      <view class="ent-form-item">
        <text class="ent-form-label">入学日期</text>
        <view class="ent-date-row">
          <picker
            mode="date"
            :value="form.enrolledAt || today"
            :end="today"
            @change="onEnrolledAtChange"
          >
            <view class="ent-picker" :class="{ 'is-placeholder': !form.enrolledAt }">
              {{ form.enrolledAt || '选择入学日期' }}
            </view>
          </picker>
          <view v-if="form.enrolledAt" class="ent-btn small" @click="form.enrolledAt = ''">清空</view>
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
        {{ submitting ? '保存中…' : isEdit ? '保存修改' : '创建学员' }}
      </view>
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}
</style>
