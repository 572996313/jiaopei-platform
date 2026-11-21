<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  billingModeLabel,
  createFeeItem,
  deleteFeeItem,
  fetchFeeItems,
  optionalText,
  updateFeeItem,
  type FeeItemRecord,
} from '@/api/enterprise'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const items = ref<FeeItemRecord[]>([])
const loading = ref(false)
const popupRef = ref<any>(null)
const editingId = ref('')
const submitting = ref(false)

const billingModeOptions = [
  { value: 'monthly', label: '按月' },
  { value: 'daily', label: '按天' },
]
const statusOptions = [
  { value: 'active', label: '启用' },
  { value: 'disabled', label: '停用' },
]
const billingModeIndex = ref(0)
const statusIndex = ref(0)

const form = ref({
  itemCode: '',
  itemName: '',
  billingMode: 'monthly',
  sortNo: '',
  status: 'active',
  remark: '',
})

// 权限信息还没拉到时先按"可用"渲染，真正的拦截在后端 @RequiresPermission
const canWrite = computed(
  () => !authStore.userInfo || authStore.hasPermission('education:fee:config:write'),
)

onShow(() => {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  load()
})

async function load() {
  loading.value = true
  try {
    items.value = await fetchFeeItems()
  } catch (error) {
    items.value = []
    uni.showToast({ title: (error as Error)?.message || '收费项目加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = ''
  form.value = {
    itemCode: '',
    itemName: '',
    billingMode: 'monthly',
    sortNo: '',
    status: 'active',
    remark: '',
  }
  billingModeIndex.value = 0
  statusIndex.value = 0
  popupRef.value?.open()
}

function openEdit(item: FeeItemRecord) {
  editingId.value = item.id
  form.value = {
    itemCode: item.itemCode || '',
    itemName: item.itemName || '',
    billingMode: item.billingMode || 'monthly',
    sortNo: item.sortNo === null || item.sortNo === undefined ? '' : String(item.sortNo),
    status: item.status || 'active',
    remark: item.remark || '',
  }
  billingModeIndex.value = billingModeOptions.findIndex((option) => option.value === form.value.billingMode)
  if (billingModeIndex.value < 0) {
    billingModeIndex.value = 0
  }
  statusIndex.value = statusOptions.findIndex((option) => option.value === form.value.status)
  if (statusIndex.value < 0) {
    statusIndex.value = 0
  }
  popupRef.value?.open()
}

function closeDialog() {
  popupRef.value?.close()
}

function onBillingModeChange(event: { detail: { value: string | number } }) {
  billingModeIndex.value = Number(event.detail.value)
  form.value.billingMode = billingModeOptions[billingModeIndex.value]?.value ?? 'monthly'
}

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  form.value.status = statusOptions[statusIndex.value]?.value ?? 'active'
}

async function submit() {
  if (submitting.value) {
    return
  }
  if (!form.value.itemName.trim()) {
    uni.showToast({ title: '请填写项目名称', icon: 'none' })
    return
  }
  const sortNoText = form.value.sortNo.trim()
  const sortNo = sortNoText === '' ? null : Number(sortNoText)
  if (sortNo !== null && Number.isNaN(sortNo)) {
    uni.showToast({ title: '排序号格式不正确', icon: 'none' })
    return
  }
  const payload = {
    itemCode: optionalText(form.value.itemCode),
    itemName: form.value.itemName.trim(),
    billingMode: form.value.billingMode,
    sortNo,
    status: form.value.status,
    remark: optionalText(form.value.remark),
  }
  submitting.value = true
  uni.showLoading({ title: '保存中…' })
  try {
    if (editingId.value) {
      await updateFeeItem(editingId.value, payload)
    } else {
      await createFeeItem(payload)
    }
    uni.hideLoading()
    closeDialog()
    uni.showToast({ title: '已保存', icon: 'success' })
    load()
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: (error as Error)?.message || '保存失败', icon: 'none', duration: 2600 })
  } finally {
    submitting.value = false
  }
}

function confirmDelete(item: FeeItemRecord) {
  uni.showModal({
    title: '删除收费项目',
    content: `确定删除「${item.itemName || ''}」吗？已被价目或台账使用的项目不能删除，不再收费请改为停用。`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteFeeItem(item.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (error) {
        uni.showToast({ title: (error as Error)?.message || '删除失败', icon: 'none', duration: 2600 })
      }
    },
  })
}
</script>

<template>
  <view class="ent-page">
    <view class="ent-card">
      <view class="ent-card-title">
        <text>收费项目</text>
        <text class="ent-link">{{ items.length }} 项</text>
      </view>
      <view class="ent-btn-row">
        <view v-if="canWrite" class="ent-btn primary small" @click="openCreate">新增收费项目</view>
        <view class="ent-btn small" @click="load">刷新</view>
      </view>
      <view class="ent-tip">
        只有「启用」的项目会在新建台账时自动建行；停用不影响已建台账。编码留空时按 FE-001 这类规则自动生成。
      </view>
    </view>

    <view v-if="loading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="items.length === 0" class="ent-card ent-empty">还没有收费项目，先新增保教费、伙食费等。</view>
    <view v-for="item in items" :key="item.id" class="ent-card">
      <view class="ent-item-title">
        <text class="ent-item-name">{{ item.itemName || '未命名项目' }}</text>
        <view class="head-right">
          <text class="ent-tag blue">{{ billingModeLabel(item.billingMode) }}</text>
          <text class="ent-tag" :class="item.status === 'active' ? 'green' : 'gray'">
            {{ item.status === 'active' ? '启用' : '停用' }}
          </text>
        </view>
      </view>
      <view class="ent-row">
        <text class="ent-label">项目编码</text>
        <text class="ent-value">{{ item.itemCode || '-' }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">排序号</text>
        <text class="ent-value">{{ item.sortNo ?? 0 }}</text>
      </view>
      <view v-if="item.remark" class="ent-row">
        <text class="ent-label">备注</text>
        <text class="ent-value">{{ item.remark }}</text>
      </view>
      <view v-if="canWrite" class="ent-btn-row right">
        <view class="ent-btn small" @click="openEdit(item)">编辑</view>
        <view class="ent-btn danger small" @click="confirmDelete(item)">删除</view>
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom" background-color="#ffffff">
      <view class="dialog">
        <view class="dialog-head">
          <text class="dialog-title">{{ editingId ? '编辑收费项目' : '新增收费项目' }}</text>
          <text class="dialog-close" @click="closeDialog">关闭</text>
        </view>
        <view class="dialog-body">
          <view class="ent-form-item">
            <text class="ent-form-label">项目编码（留空自动生成）</text>
            <input
              v-model="form.itemCode"
              class="ent-input"
              type="text"
              maxlength="64"
              placeholder="如 FE-001"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label"><text class="ent-required">*</text> 项目名称</text>
            <input
              v-model="form.itemName"
              class="ent-input"
              type="text"
              maxlength="64"
              placeholder="如 保教费 / 伙食费 / 延时费"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">计费方式</text>
            <picker
              mode="selector"
              :range="billingModeOptions"
              range-key="label"
              :value="billingModeIndex"
              @change="onBillingModeChange"
            >
              <view class="ent-picker">{{ billingModeOptions[billingModeIndex]?.label }}</view>
            </picker>
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">排序号</text>
            <input
              v-model="form.sortNo"
              class="ent-input"
              type="number"
              placeholder="越小越靠前，建议 10、20、30"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">状态</text>
            <picker
              mode="selector"
              :range="statusOptions"
              range-key="label"
              :value="statusIndex"
              @change="onStatusChange"
            >
              <view class="ent-picker">{{ statusOptions[statusIndex]?.label }}</view>
            </picker>
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">备注</text>
            <input
              v-model="form.remark"
              class="ent-input"
              type="text"
              maxlength="500"
              placeholder="选填"
              placeholder-class="input-placeholder"
            />
          </view>
        </view>
        <view class="dialog-footer">
          <view class="ent-btn primary" :class="{ disabled: submitting }" @click="submit">
            {{ submitting ? '保存中…' : '保存' }}
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

.head-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.dialog {
  display: flex;
  flex-direction: column;
  max-height: 82vh;
  padding-bottom: env(safe-area-inset-bottom);
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.dialog-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.dialog-close {
  font-size: 26rpx;
  color: #6b7280;
}

.dialog-body {
  padding: 12rpx 32rpx 4rpx;
  overflow-y: auto;
}

.dialog-footer {
  padding: 16rpx 32rpx 24rpx;
}

.dialog-footer .ent-btn {
  width: 100%;
}
</style>
