<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { bindSelfStudent, fetchMyStudentProfile, saveStudentProfile, type Gender } from '@/api/miniapp'
import { isApiError } from '@/utils/request'

// mode: bind=绑定已有档案;create=新建档案;edit=编辑已有档案
const mode = ref<'bind' | 'create' | 'edit'>('bind')
const submitting = ref(false)

const studentNo = ref('')
const studentName = ref('')
const gender = ref<Gender>('UNKNOWN')
const birthday = ref('')

const genderOptions: Array<{ value: Gender; label: string }> = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'UNKNOWN', label: '未知' },
]

const title = computed(() => {
  if (mode.value === 'bind') return '绑定我的档案'
  if (mode.value === 'create') return '创建我的档案'
  return '编辑我的档案'
})

onLoad((options) => {
  const m = options?.mode
  if (m === 'create' || m === 'edit') {
    mode.value = m
  }
  if (m === 'edit') {
    loadProfileForEdit()
  }
})

async function loadProfileForEdit() {
  try {
    const profile = await fetchMyStudentProfile()
    if (profile.bound) {
      studentName.value = profile.studentName || ''
      if (profile.gender) {
        gender.value = profile.gender
      }
      birthday.value = profile.birthday || ''
    }
  } catch {
    // 忽略,保持空白表单
  }
}

async function handleSubmit() {
  if (mode.value === 'bind') {
    if (!studentNo.value.trim()) {
      uni.showToast({ title: '请输入学员编号', icon: 'none' })
      return
    }
    if (!studentName.value.trim()) {
      uni.showToast({ title: '请输入学员姓名', icon: 'none' })
      return
    }
  } else if (!studentName.value.trim()) {
    uni.showToast({ title: '请输入学员姓名', icon: 'none' })
    return
  }

  if (submitting.value) {
    return
  }
  submitting.value = true
  uni.showLoading({ title: '保存中…' })

  try {
    if (mode.value === 'bind') {
      await bindSelfStudent(studentNo.value.trim(), studentName.value.trim())
    } else {
      await saveStudentProfile({
        studentName: studentName.value.trim(),
        gender: gender.value,
        birthday: birthday.value || undefined,
      })
    }
    uni.hideLoading()
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error) ? error.message : (error as Error)?.message || '保存失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="profile-page">
    <view class="header">
      <text class="title">{{ title }}</text>
      <text v-if="mode === 'bind'" class="desc">绑定后仅可查看和维护自己的档案,不可更换;若档案已被绑定请先联系机构</text>
      <text v-else class="desc">维护你的学员档案信息,机构登记的信息建议保持一致</text>
    </view>

    <view class="form-card">
      <view v-if="mode === 'bind'" class="form-item">
        <text class="label">学员编号</text>
        <input
          v-model="studentNo"
          class="input"
          type="text"
          placeholder="请输入机构分配的学员编号"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <text class="label">学员姓名</text>
        <input
          v-model="studentName"
          class="input"
          type="text"
          placeholder="请输入姓名"
          placeholder-class="placeholder"
        />
      </view>
      <view v-if="mode !== 'bind'" class="form-item">
        <text class="label">性别</text>
        <view class="gender-group">
          <view
            v-for="opt in genderOptions"
            :key="opt.value"
            class="gender-option"
            :class="{ active: gender === opt.value }"
            @click="gender = opt.value"
          >
            {{ opt.label }}
          </view>
        </view>
      </view>
      <view v-if="mode !== 'bind'" class="form-item">
        <text class="label">出生日期(选填)</text>
        <picker mode="date" :value="birthday" @change="(e: any) => (birthday = e.detail.value)">
          <view class="picker-input" :class="{ placeholder: !birthday }">
            {{ birthday || '请选择出生日期' }}
          </view>
        </picker>
      </view>
      <button class="submit-btn" :disabled="submitting" @click="handleSubmit">保存</button>
    </view>
  </view>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding: 60rpx 48rpx;
  box-sizing: border-box;
}

.header {
  margin-bottom: 44rpx;
}

.title {
  display: block;
  font-size: 44rpx;
  font-weight: 600;
  color: #111827;
}

.desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.6;
}

.form-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 36rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.form-item {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #374151;
  margin-bottom: 14rpx;
}

.input {
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 30rpx;
}

.placeholder {
  color: #9ca3af;
}

.gender-group {
  display: flex;
}

.gender-option {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 12rpx;
  background: #f5f6fa;
  color: #6b7280;
  font-size: 30rpx;
  margin-right: 20rpx;
}

.gender-option:last-child {
  margin-right: 0;
}

.gender-option.active {
  background: #eff6ff;
  color: #2563eb;
  border: 1rpx solid #2563eb;
}

.picker-input {
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 30rpx;
  color: #111827;
}

.picker-input.placeholder {
  color: #9ca3af;
}

.submit-btn {
  margin-top: 16rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: #2563eb;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 500;
}

.submit-btn[disabled] {
  opacity: 0.6;
  color: #ffffff;
  background: #2563eb;
}
</style>