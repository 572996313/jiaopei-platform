<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  createFeePrice,
  deleteFeePrice,
  fetchCampuses,
  fetchClassOptions,
  fetchFeeItems,
  fetchFeePrices,
  fetchStudentOptions,
  fetchTerms,
  optionalNumber,
  optionalText,
  priceLevelLabel,
  updateFeePrice,
  type CampusRecord,
  type FeeItemRecord,
  type FeePriceRecord,
  type TermRecord,
} from '@/api/enterprise'
import type { PickerOption } from '@/components/picker-types'
import PickerField from '@/components/PickerField.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const prices = ref<FeePriceRecord[]>([])
const feeItems = ref<FeeItemRecord[]>([])
const campuses = ref<CampusRecord[]>([])
const terms = ref<TermRecord[]>([])
const classMap = ref<Record<string, string>>({})
const studentMap = ref<Record<string, string>>({})
const loading = ref(false)

const filterItemId = ref('')
const filterItemLabel = ref('')

const popupRef = ref<any>(null)
const editingId = ref('')
const submitting = ref(false)

const statusOptions = [
  { value: 'active', label: '启用' },
  { value: 'disabled', label: '停用' },
]
const statusIndex = ref(0)

const form = ref({
  feeItemId: '',
  feeItemLabel: '',
  campusId: '',
  campusLabel: '',
  classId: '',
  classLabel: '',
  termId: '',
  termLabel: '',
  studentId: '',
  studentLabel: '',
  monthlyPrice: '',
  dailyPrice: '',
  status: 'active',
  remark: '',
})

const canWrite = computed(
  () => !authStore.userInfo || authStore.hasPermission('education:fee:config:write'),
)

const itemMap = computed(() => {
  const map: Record<string, string> = {}
  feeItems.value.forEach((item) => {
    map[item.id] = item.itemName || item.itemCode || item.id
  })
  return map
})
const campusMap = computed(() => {
  const map: Record<string, string> = {}
  campuses.value.forEach((campus) => {
    map[campus.id] = campus.campusName
  })
  return map
})
const termMap = computed(() => {
  const map: Record<string, string> = {}
  terms.value.forEach((term) => {
    map[term.id] = term.termName
  })
  return map
})

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
    const [priceList, items, campusList, termList] = await Promise.all([
      fetchFeePrices({ feeItemId: filterItemId.value || undefined }),
      fetchFeeItems(),
      fetchCampuses().catch(() => [] as CampusRecord[]),
      fetchTerms().catch(() => [] as TermRecord[]),
    ])
    prices.value = priceList
    feeItems.value = items
    campuses.value = campusList
    terms.value = termList
    resolveDimensionNames(priceList)
  } catch (error) {
    prices.value = []
    uni.showToast({ title: (error as Error)?.message || '价目加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 价目里的班级/学员只带 id，用 options 的 ids 回查一次名称 */
function resolveDimensionNames(list: FeePriceRecord[]) {
  const classIds = Array.from(
    new Set(list.map((price) => price.classId).filter((id): id is string => Boolean(id) && id !== '0')),
  )
  const studentIds = Array.from(
    new Set(list.map((price) => price.studentId).filter((id): id is string => Boolean(id) && id !== '0')),
  )
  if (classIds.length > 0) {
    fetchClassOptions({ ids: classIds.join(',') })
      .then((options) => {
        const map = { ...classMap.value }
        options.forEach((option) => {
          map[option.id] = option.className
        })
        classMap.value = map
      })
      .catch(() => undefined)
  }
  if (studentIds.length > 0) {
    fetchStudentOptions({ ids: studentIds.join(',') })
      .then((options) => {
        const map = { ...studentMap.value }
        options.forEach((option) => {
          map[option.id] = option.studentName
        })
        studentMap.value = map
      })
      .catch(() => undefined)
  }
}

function itemNameOf(feeItemId?: string | null) {
  return feeItemId ? itemMap.value[feeItemId] || `项目 #${feeItemId}` : '-'
}

function dimensionTexts(price: FeePriceRecord) {
  const parts: string[] = []
  if (price.studentId && price.studentId !== '0') {
    parts.push(`学员：${studentMap.value[price.studentId] || `#${price.studentId}`}`)
  }
  if (price.classId && price.classId !== '0') {
    parts.push(`班级：${classMap.value[price.classId] || `#${price.classId}`}`)
  }
  if (price.campusId && price.campusId !== '0') {
    parts.push(`校区：${campusMap.value[price.campusId] || `#${price.campusId}`}`)
  }
  if (price.termId && price.termId !== '0') {
    parts.push(`学期：${termMap.value[price.termId] || `#${price.termId}`}`)
  }
  return parts.length > 0 ? parts : ['不限（全局价目）']
}

async function loadItemOptions(keyword: string): Promise<PickerOption[]> {
  const kw = keyword.trim()
  return feeItems.value
    .filter((item) => !kw || (item.itemName || '').includes(kw) || (item.itemCode || '').includes(kw))
    .map((item) => ({
      value: item.id,
      label: `${item.itemName || '未命名项目'}（${item.billingMode === 'daily' ? '按天' : '按月'}）`,
      sub: item.itemCode || undefined,
    }))
}

async function loadCampusOptions(keyword: string): Promise<PickerOption[]> {
  const kw = keyword.trim()
  return campuses.value
    .filter((campus) => !kw || campus.campusName.includes(kw))
    .map((campus) => ({ value: campus.id, label: campus.campusName }))
}

async function loadTermOptions(keyword: string): Promise<PickerOption[]> {
  const kw = keyword.trim()
  return terms.value
    .filter((term) => !kw || term.termName.includes(kw))
    .map((term) => ({ value: term.id, label: term.termName }))
}

async function loadClassOptionsLocal(keyword: string): Promise<PickerOption[]> {
  const classes = await fetchClassOptions({ keyword: keyword.trim() || undefined })
  return classes.map((clazz) => ({ value: clazz.id, label: clazz.className, sub: clazz.classCode || undefined }))
}

async function loadStudentOptionsLocal(keyword: string): Promise<PickerOption[]> {
  const students = await fetchStudentOptions({ keyword: keyword.trim() || undefined })
  return students.map((student) => ({
    value: student.id,
    label: `${student.studentName}（${student.studentNo || '无学号'}）`,
  }))
}

function onFilterItemChange(option: PickerOption | null) {
  filterItemLabel.value = option?.label || ''
  load()
}

function openCreate() {
  editingId.value = ''
  form.value = {
    feeItemId: '',
    feeItemLabel: '',
    campusId: '',
    campusLabel: '',
    classId: '',
    classLabel: '',
    termId: '',
    termLabel: '',
    studentId: '',
    studentLabel: '',
    monthlyPrice: '',
    dailyPrice: '',
    status: 'active',
    remark: '',
  }
  statusIndex.value = 0
  popupRef.value?.open()
}

function openEdit(price: FeePriceRecord) {
  editingId.value = price.id
  form.value = {
    feeItemId: price.feeItemId || '',
    feeItemLabel: itemNameOf(price.feeItemId),
    campusId: price.campusId && price.campusId !== '0' ? price.campusId : '',
    campusLabel: '',
    classId: price.classId && price.classId !== '0' ? price.classId : '',
    classLabel: '',
    termId: price.termId && price.termId !== '0' ? price.termId : '',
    termLabel: '',
    studentId: price.studentId && price.studentId !== '0' ? price.studentId : '',
    studentLabel: '',
    monthlyPrice:
      price.monthlyPrice === null || price.monthlyPrice === undefined ? '' : String(price.monthlyPrice),
    dailyPrice: price.dailyPrice === null || price.dailyPrice === undefined ? '' : String(price.dailyPrice),
    status: price.status || 'active',
    remark: price.remark || '',
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

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  form.value.status = statusOptions[statusIndex.value]?.value ?? 'active'
}

async function submit() {
  if (submitting.value) {
    return
  }
  if (!editingId.value && !form.value.feeItemId) {
    uni.showToast({ title: '请选择收费项目', icon: 'none' })
    return
  }
  const monthlyPrice = optionalNumber(form.value.monthlyPrice)
  const dailyPrice = optionalNumber(form.value.dailyPrice)
  // 新增必须给全标准；编辑时留空=该项不改，两项都留空只改状态/备注
  if (!editingId.value && monthlyPrice === null && dailyPrice === null) {
    uni.showToast({ title: '月标准与日标准至少填一个', icon: 'none' })
    return
  }
  if (
    (form.value.monthlyPrice.trim() !== '' && monthlyPrice === null) ||
    (form.value.dailyPrice.trim() !== '' && dailyPrice === null)
  ) {
    uni.showToast({ title: '标准格式不正确', icon: 'none' })
    return
  }

  submitting.value = true
  uni.showLoading({ title: '保存中…' })
  try {
    if (editingId.value) {
      // 更新接口只改标准/状态/备注，维度不可改；留空表示不修改该项标准
      await updateFeePrice(editingId.value, {
        monthlyPrice,
        dailyPrice,
        status: form.value.status,
        // 更新接口 null/缺省=不改，留空传空串才能清掉备注
        remark: form.value.remark.trim(),
      })
    } else {
      await createFeePrice({
        feeItemId: form.value.feeItemId,
        campusId: form.value.campusId || null,
        classId: form.value.classId || null,
        termId: form.value.termId || null,
        studentId: form.value.studentId || null,
        monthlyPrice,
        dailyPrice,
        status: form.value.status,
        remark: optionalText(form.value.remark),
      })
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

function confirmDelete(price: FeePriceRecord) {
  uni.showModal({
    title: '删除价目',
    content: '删除后不影响已建台账的标准快照，确定删除这行价目吗？',
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteFeePrice(price.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (error) {
        uni.showToast({ title: (error as Error)?.message || '删除失败', icon: 'none' })
      }
    },
  })
}

function priceText(value?: number | null) {
  return value === null || value === undefined ? '-' : Number(value).toFixed(2)
}
</script>

<template>
  <view class="ent-page">
    <view class="ent-card">
      <view class="ent-card-title">
        <text>收费价目</text>
        <text class="ent-link">{{ prices.length }} 条</text>
      </view>
      <view class="ent-form-item">
        <text class="ent-form-label">按收费项目筛选</text>
        <PickerField
          v-model="filterItemId"
          :label="filterItemLabel"
          title="选择收费项目"
          placeholder="全部项目"
          :clearable="true"
          :loader="loadItemOptions"
          @change="onFilterItemChange"
        />
      </view>
      <view class="ent-btn-row">
        <view v-if="canWrite" class="ent-btn primary small" @click="openCreate">新增价目</view>
        <view class="ent-btn small" @click="load">刷新</view>
      </view>
      <view class="ent-tip">
        四个维度留空 = 不限；特异度 学员 &gt; 班级 &gt; 校区 &gt; 学期 &gt; 全局。改价目不会回改已建台账的标准快照。
      </view>
    </view>

    <view v-if="loading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="prices.length === 0" class="ent-card ent-empty">还没有价目，建账时标准会是 0。</view>
    <view v-for="price in prices" :key="price.id" class="ent-card">
      <view class="ent-item-title">
        <text class="ent-item-name">{{ itemNameOf(price.feeItemId) }}</text>
        <view class="head-right">
          <text class="ent-tag blue">{{ priceLevelLabel(price) }}</text>
          <text class="ent-tag" :class="price.status === 'active' ? 'green' : 'gray'">
            {{ price.status === 'active' ? '启用' : '停用' }}
          </text>
        </view>
      </view>
      <view v-for="(text, index) in dimensionTexts(price)" :key="index" class="ent-row">
        <text class="ent-label">范围</text>
        <text class="ent-value">{{ text }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">月标准 / 日标准</text>
        <text class="ent-value ent-amount">{{ priceText(price.monthlyPrice) }} / {{ priceText(price.dailyPrice) }}</text>
      </view>
      <view v-if="price.remark" class="ent-row">
        <text class="ent-label">备注</text>
        <text class="ent-value">{{ price.remark }}</text>
      </view>
      <view v-if="canWrite" class="ent-btn-row right">
        <view class="ent-btn small" @click="openEdit(price)">编辑</view>
        <view class="ent-btn danger small" @click="confirmDelete(price)">删除</view>
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom" background-color="#ffffff">
      <view class="dialog">
        <view class="dialog-head">
          <text class="dialog-title">{{ editingId ? '编辑价目' : '新增价目' }}</text>
          <text class="dialog-close" @click="closeDialog">关闭</text>
        </view>
        <view class="dialog-body">
          <view v-if="editingId" class="ent-tip">
            价目的校区/班级/学期/学员维度建好后不可改；需要换范围请删除后重新新增。
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label"><text class="ent-required">*</text> 收费项目</text>
            <view v-if="editingId" class="ent-picker">{{ form.feeItemLabel }}</view>
            <PickerField
              v-else
              v-model="form.feeItemId"
              :label="form.feeItemLabel"
              title="选择收费项目"
              placeholder="请选择收费项目"
              :loader="loadItemOptions"
              @change="(option) => { form.feeItemLabel = option?.label || '' }"
            />
          </view>

          <template v-if="!editingId">
            <view class="ent-form-item">
              <text class="ent-form-label">校区（留空=不限）</text>
              <PickerField
                v-model="form.campusId"
                :label="form.campusLabel"
                title="选择校区"
                placeholder="不限校区"
                :clearable="true"
                :loader="loadCampusOptions"
                @change="(option) => { form.campusLabel = option?.label || '' }"
              />
            </view>
            <view class="ent-form-item">
              <text class="ent-form-label">班级（留空=不限）</text>
              <PickerField
                v-model="form.classId"
                :label="form.classLabel"
                title="选择班级"
                placeholder="不限班级"
                :clearable="true"
                :loader="loadClassOptionsLocal"
                @change="(option) => { form.classLabel = option?.label || '' }"
              />
            </view>
            <view class="ent-form-item">
              <text class="ent-form-label">学期（留空=不限）</text>
              <PickerField
                v-model="form.termId"
                :label="form.termLabel"
                title="选择学期"
                placeholder="不限学期"
                :clearable="true"
                :loader="loadTermOptions"
                @change="(option) => { form.termLabel = option?.label || '' }"
              />
            </view>
            <view class="ent-form-item">
              <text class="ent-form-label">学员（留空=不限，非空=学员级覆盖）</text>
              <PickerField
                v-model="form.studentId"
                :label="form.studentLabel"
                title="选择学员"
                placeholder="不限学员"
                :clearable="true"
                :loader="loadStudentOptionsLocal"
                @change="(option) => { form.studentLabel = option?.label || '' }"
              />
            </view>
          </template>

          <view class="ent-form-item">
            <text class="ent-form-label">月标准</text>
            <input
              v-model="form.monthlyPrice"
              class="ent-input"
              type="digit"
              placeholder="按月项目用，留空=不提供"
              placeholder-class="input-placeholder"
            />
          </view>
          <view class="ent-form-item">
            <text class="ent-form-label">日标准</text>
            <input
              v-model="form.dailyPrice"
              class="ent-input"
              type="digit"
              placeholder="按天项目用，留空=不提供"
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
          <view v-if="editingId" class="ent-tip">编辑时留空的标准表示不修改该项。</view>
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
  max-height: 86vh;
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
