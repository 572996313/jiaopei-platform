<script setup lang="ts">
import { ref } from 'vue'
import { createChildStudent, fetchGenderOptions, type DictOptionItem } from '@/api/miniapp'
import { isApiError } from '@/utils/request'

const submitting = ref(false)
const studentName = ref('')
const gender = ref('')
const birthday = ref('')
const guardianName = ref('')
const guardianPhone = ref('')

// 性别选项来自系统字典 gender,与 PC 端同一套值(male/female/unknown)
const genderOptions = ref<DictOptionItem[]>([])
fetchGenderOptions()
  .then((items) => {
    genderOptions.value = items
  })
  .catch(() => {
    // fetchGenderOptions 失败时已回落静态选项
  })

async function handleSubmit() {
  if (!studentName.value.trim()) {
    uni.showToast({ title: '请输入学员姓名', icon: 'none' })
    return
  }
  if (!gender.value) {
    uni.showToast({ title: '请选择性别', icon: 'none' })
    return
  }
  if (submitting.value) {
    return
  }
  submitting.value = true
  uni.showLoading({ title: '创建中…' })
  try {
    await createChildStudent({
      studentName: studentName.value.trim(),
      gender: gender.value,
      birthday: birthday.value || undefined,
      guardianName: guardianName.value.trim() || undefined,
      guardianPhone: guardianPhone.value.trim() || undefined,
    })
    uni.hideLoading()
    uni.showToast({ title: '学员档案已创建', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error) ? error.message : (error as Error)?.message || '创建失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="create-page">
    <view class="header">
      <text class="title">创建学员档案</text>
      <text class="desc">
        孩子还没有手机或账号时,由家长代为创建。建档后会自动绑定到你的账号,学号由系统生成,
        之后机构排班、收费都认这份档案。
      </text>
    </view>

    <view class="form-card">
      <view class="form-item">
        <text class="label">学员姓名</text>
        <input
          v-model="studentName"
          class="input"
          type="text"
          maxlength="32"
          placeholder="请输入孩子姓名"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <text class="label">性别</text>
        <view class="gender-group">
          <view
            v-for="opt in genderOptions"
            :key="opt.itemValue"
            class="gender-option"
            :class="{ active: gender === opt.itemValue }"
            @click="gender = opt.itemValue"
          >
            {{ opt.itemName }}
          </view>
        </view>
      </view>
      <view class="form-item">
        <text class="label">出生日期(选填)</text>
        <picker mode="date" :value="birthday" @change="(e: any) => (birthday = e.detail.value)">
          <view class="picker-input" :class="{ placeholder: !birthday }">
            {{ birthday || '请选择出生日期' }}
          </view>
        </picker>
      </view>
      <view class="form-item">
        <text class="label">监护人姓名(选填)</text>
        <input
          v-model="guardianName"
          class="input"
          type="text"
          maxlength="32"
          placeholder="请输入监护人姓名"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <text class="label">监护人电话(选填)</text>
        <input
          v-model="guardianPhone"
          class="input"
          type="text"
          maxlength="20"
          placeholder="填写后可用该手机号再次绑定"
          placeholder-class="placeholder"
        />
      </view>
      <button class="submit-btn" :disabled="submitting" @click="handleSubmit">创建并绑定</button>
    </view>
  </view>
</template>

<style scoped>
.create-page {
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
  border: 1rpx solid transparent;
  box-sizing: border-box;
}

.gender-option:last-child {
  margin-right: 0;
}

.gender-option.active {
  background: #eff6ff;
  color: #2563eb;
  border-color: #2563eb;
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
