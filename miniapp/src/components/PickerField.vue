<script setup lang="ts">
import { ref, watch } from 'vue'
import type { PickerOption } from './picker-types'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    /** 已选项的显示名(编辑场景由详情带入) */
    label?: string
    placeholder?: string
    title?: string
    /** 是否提供「不限」选项(价目的四个维度用) */
    clearable?: boolean
    disabled?: boolean
    loader: (keyword: string) => Promise<PickerOption[]>
  }>(),
  {
    modelValue: '',
    label: '',
    placeholder: '请选择',
    title: '请选择',
    clearable: false,
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [string]
  change: [PickerOption | null]
}>()

const popupRef = ref<any>(null)
const options = ref<PickerOption[]>([])
const loading = ref(false)
const keyword = ref('')
const displayLabel = ref(props.label || '')
let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.label,
  (value) => {
    if (value) {
      displayLabel.value = value
    }
  },
)

watch(
  () => props.modelValue,
  (value) => {
    if (!value) {
      displayLabel.value = ''
    }
  },
)

function open() {
  if (props.disabled) {
    return
  }
  keyword.value = ''
  options.value = []
  popupRef.value?.open()
  load('')
}

function close() {
  popupRef.value?.close()
}

async function load(text: string) {
  loading.value = true
  try {
    options.value = await props.loader(text.trim())
  } catch (error) {
    options.value = []
    uni.showToast({
      title: (error as Error)?.message || '加载选项失败',
      icon: 'none',
    })
  } finally {
    loading.value = false
  }
}

function onInput() {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => load(keyword.value), 300)
}

function choose(option: PickerOption | null) {
  displayLabel.value = option?.label || ''
  emit('update:modelValue', option?.value || '')
  emit('change', option)
  close()
}
</script>

<template>
  <view class="picker-field" :class="{ 'is-disabled': disabled }" @click="open">
    <text class="picker-value" :class="{ 'is-placeholder': !displayLabel }">
      {{ displayLabel || placeholder }}
    </text>
    <text class="picker-arrow">›</text>
  </view>

  <uni-popup ref="popupRef" type="bottom" background-color="#ffffff">
    <view class="picker-sheet">
      <view class="sheet-head">
        <text class="sheet-title">{{ title }}</text>
        <text class="sheet-close" @click="close">关闭</text>
      </view>
      <view class="sheet-search">
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          confirm-type="search"
          placeholder="输入关键字搜索"
          placeholder-class="search-placeholder"
          @input="onInput"
          @confirm="load(keyword)"
        />
      </view>
      <scroll-view scroll-y class="sheet-list">
        <view v-if="clearable" class="option-row" @click="choose(null)">
          <text class="option-label">不限</text>
        </view>
        <view
          v-for="option in options"
          :key="option.value"
          class="option-row"
          @click="choose(option)"
        >
          <text class="option-label">{{ option.label }}</text>
          <text v-if="option.sub" class="option-sub">{{ option.sub }}</text>
        </view>
        <view v-if="loading" class="sheet-tip">加载中…</view>
        <view v-else-if="options.length === 0" class="sheet-tip">没有匹配项</view>
      </scroll-view>
    </view>
  </uni-popup>
</template>

<style scoped>
.picker-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 80rpx;
  padding: 0 20rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
}

.picker-field.is-disabled {
  opacity: 0.55;
}

.picker-value {
  flex: 1;
  font-size: 28rpx;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.picker-value.is-placeholder {
  color: #9ca3af;
}

.picker-arrow {
  margin-left: 12rpx;
  font-size: 34rpx;
  color: #c4c9d1;
  line-height: 1;
}

.picker-sheet {
  display: flex;
  flex-direction: column;
  max-height: 70vh;
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.sheet-close {
  font-size: 26rpx;
  color: #6b7280;
}

.sheet-search {
  padding: 12rpx 32rpx 20rpx;
}

.search-input {
  height: 76rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 28rpx;
}

.search-placeholder {
  color: #9ca3af;
}

.sheet-list {
  flex: 1;
  min-height: 200rpx;
  max-height: 52vh;
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 32rpx;
  border-top: 1rpx solid #f3f4f6;
}

.option-label {
  font-size: 30rpx;
  color: #111827;
}

.option-sub {
  font-size: 24rpx;
  color: #9ca3af;
}

.sheet-tip {
  padding: 48rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9ca3af;
}
</style>
