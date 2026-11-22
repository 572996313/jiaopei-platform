<script setup lang="ts">
import { ref } from 'vue'
import { isApiError } from '@/utils/request'
import { bindStudent, type BindMethod } from '@/api/miniapp'

const method = ref<BindMethod>('STUDENT_NO')
const value = ref('')
const studentName = ref('')
const remark = ref('')
const submitting = ref(false)

const methodOptions: Array<{ value: BindMethod; title: string; desc: string }> = [
  { value: 'STUDENT_NO', title: '按学员编号', desc: '学员编号在报名时由机构分配' },
  { value: 'PARENT_PHONE', title: '按监护人手机号', desc: '用报名时留的监护人手机号' },
]

function selectMethod(m: BindMethod) {
  method.value = m
  value.value = ''
}

/** 孩子还没有档案(没有手机/账号)时,直接由家长代建 */
function goCreateChild() {
  uni.navigateTo({ url: '/pages/parent/create-student/index' })
}

async function handleBind() {
  if (!value.value.trim()) {
    uni.showToast({ title: method.value === 'STUDENT_NO' ? '请输入学员编号' : '请输入监护人手机号', icon: 'none' })
    return
  }
  if (!studentName.value.trim()) {
    uni.showToast({ title: '请输入学员姓名', icon: 'none' })
    return
  }
  if (submitting.value) {
    return
  }
  submitting.value = true
  uni.showLoading({ title: '绑定中…' })
  try {
    await bindStudent(method.value, value.value.trim(), studentName.value.trim(), remark.value)
    uni.hideLoading()
    uni.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
  } catch (error) {
    uni.hideLoading()
    const message = isApiError(error) ? error.message : (error as Error)?.message || '绑定失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="bind-page">
    <view class="method-grid">
      <view
        v-for="item in methodOptions"
        :key="item.value"
        class="method-card"
        :class="{ active: method === item.value }"
        @click="selectMethod(item.value)"
      >
        <text class="method-title">{{ item.title }}</text>
        <text class="method-desc">{{ item.desc }}</text>
      </view>
    </view>

    <view class="form-card">
      <view class="form-item">
        <text class="label">{{ method === 'STUDENT_NO' ? '学员编号' : '监护人手机号' }}</text>
        <input
          v-model="value"
          class="input"
          type="text"
          :placeholder="method === 'STUDENT_NO' ? '请输入学员编号' : '请输入监护人手机号'"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <text class="label">学员姓名</text>
        <input
          v-model="studentName"
          class="input"
          type="text"
          placeholder="请输入学员在机构登记的姓名"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <text class="label">备注(选填)</text>
        <input
          v-model="remark"
          class="input"
          type="text"
          placeholder="例:大儿子 / 老二"
          placeholder-class="placeholder"
        />
      </view>
      <button class="submit-btn" :disabled="submitting" @click="handleBind">绑定</button>
    </view>

    <view class="create-link" @click="goCreateChild">
      <text class="create-link-text">孩子还没有档案?直接创建一份 ›</text>
    </view>

    <text class="tip">为保护学员隐私,绑定需同时核对学员姓名。绑定后可在「我的」查看课表,一个账号可绑定多个学员。</text>
  </view>
</template>

<style scoped>
.bind-page {
  min-height: 100vh;
  padding: 40rpx 48rpx;
  box-sizing: border-box;
}

.method-grid {
  display: flex;
  margin-bottom: 40rpx;
}

.method-card {
  flex: 1;
  background: #ffffff;
  border: 3rpx solid #e5e7eb;
  border-radius: 20rpx;
  padding: 30rpx 24rpx;
  margin-right: 24rpx;
  display: flex;
  flex-direction: column;
}

.method-card:last-child {
  margin-right: 0;
}

.method-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.method-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.method-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
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

.create-link {
  display: flex;
  justify-content: center;
  padding: 28rpx 0 0;
}

.create-link-text {
  font-size: 26rpx;
  color: #2563eb;
}

.tip {
  display: block;
  margin-top: 28rpx;
  font-size: 24rpx;
  color: #9ca3af;
  line-height: 1.6;
}
</style>
