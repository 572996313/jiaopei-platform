<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { fetchDictOptions } from '@/api/enterprise'
import {
  fetchStudentPage,
  stageStudentRecord,
  studentStatusLabel,
  studentStatusTone,
  textOrUndefined,
  type StudentRecord,
} from '@/api/archive'

const authStore = useAuthStore()
const institutionId = computed(
  () => authStore.userInfo?.institutionId || authStore.userInfo?.orgId || '',
)
// 权限信息还没拉到时先按“可用”渲染，真正的拦截在后端 @RequiresPermission
const canWrite = computed(
  () => !authStore.userInfo || authStore.hasPermission('education:profile:write'),
)

const records = ref<StudentRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')

const keyword = ref('')
const genderOptions = ref<{ itemCode: string; itemName: string; itemValue: string }[]>([])
const statusOptions = ref<{ value: string; label: string }[]>([{ value: '', label: '全部状态' }])
const statusIndex = ref(0)
let dictsLoaded = false

const selectedStatus = computed(() => statusOptions.value[statusIndex.value]?.value || '')

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
    loadError.value = '当前账号没有所属机构，请先在管理后台确认账号归属'
    loaded.value = true
    return
  }
  loadError.value = ''
  if (!dictsLoaded) {
    await loadDicts()
  }
  await load(true)
}

/** 字典只决定显示名与筛选项；接口失败不影响学员列表本身 */
async function loadDicts() {
  try {
    const [genders, statuses] = await Promise.all([
      fetchDictOptions('gender'),
      fetchDictOptions('student_status'),
    ])
    genderOptions.value = genders
    statusOptions.value = [
      { value: '', label: '全部状态' },
      ...statuses.map((item) => ({ value: item.itemValue, label: item.itemName })),
    ]
    dictsLoaded = true
  } catch {
    // 字典没拉到就保留“全部状态”，不硬编码状态中文
  }
}

async function load(reset: boolean) {
  if (loading.value) {
    return
  }
  loading.value = true
  if (reset) {
    page.value = 1
  }
  try {
    const result = await fetchStudentPage({
      page: page.value,
      pageSize,
      institutionId: institutionId.value,
      keyword: textOrUndefined(keyword.value),
      status: selectedStatus.value || undefined,
    })
    records.value = reset ? result.records : records.value.concat(result.records)
    total.value = result.total
    loadError.value = ''
  } catch (error) {
    if (reset) {
      records.value = []
      total.value = 0
    }
    loadError.value = (error as Error)?.message || '学员列表加载失败'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function search() {
  void load(true)
}

function onStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  void load(true)
}

function genderText(value?: string | null) {
  if (!value) {
    return '-'
  }
  return genderOptions.value.find((item) => item.itemValue === value)?.itemName || value
}

function openCreate() {
  uni.navigateTo({ url: '/pages/enterprise/student/form?mode=create' })
}

function openDetail(item: StudentRecord) {
  // 学员没有按 id 取详情的接口，列表把整行档案交接给详情页
  stageStudentRecord(item)
  uni.navigateTo({ url: `/pages/enterprise/student/detail?id=${item.id}` })
}

function openEdit(item: StudentRecord) {
  stageStudentRecord(item)
  uni.navigateTo({ url: `/pages/enterprise/student/form?id=${item.id}` })
}

onReachBottom(() => {
  if (!loading.value && records.value.length < total.value) {
    page.value += 1
    void load(false)
  }
})

onPullDownRefresh(async () => {
  try {
    await load(true)
  } finally {
    uni.stopPullDownRefresh()
  }
})
</script>

<template>
  <view class="ent-page">
    <view class="ent-card">
      <view class="ent-card-title">
        <text>学员档案</text>
        <text class="ent-link">共 {{ total }} 人</text>
      </view>

      <view class="ent-search-row">
        <input
          v-model="keyword"
          class="ent-search-input"
          type="text"
          confirm-type="search"
          placeholder="按姓名 / 学号搜索"
          placeholder-class="input-placeholder"
          @confirm="search"
        />
        <view class="ent-btn small primary" @click="search">搜索</view>
      </view>

      <view class="ent-filter-row">
        <picker
          mode="selector"
          :range="statusOptions"
          range-key="label"
          :value="statusIndex"
          @change="onStatusChange"
        >
          <view class="ent-picker">{{ statusOptions[statusIndex]?.label || '全部状态' }}</view>
        </picker>
        <view class="ent-btn small" @click="load(true)">刷新</view>
      </view>

      <view class="ent-btn-row">
        <view v-if="canWrite" class="ent-btn primary small" @click="openCreate">新增学员</view>
      </view>
      <view class="ent-tip">
        学员编号留空会按编号规则自动生成；家长联系人进入学员详情维护。
      </view>
    </view>

    <view v-if="!loaded && loading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="loadError" class="ent-card ent-empty">{{ loadError }}</view>
    <view v-else-if="records.length === 0" class="ent-card ent-empty">
      还没有学员档案，点「新增学员」建档后即可入班、建收费台账。
    </view>

    <view
      v-for="item in records"
      :key="item.id"
      class="ent-card"
      hover-class="ent-card-hover"
      @click="openDetail(item)"
    >
      <view class="ent-item-title">
        <text class="ent-item-name">{{ item.studentName || '未命名学员' }}</text>
        <text class="ent-tag" :class="studentStatusTone(item.status)">
          {{ studentStatusLabel(item.status) }}
        </text>
      </view>
      <view class="ent-row">
        <text class="ent-label">学号</text>
        <text class="ent-value">{{ item.studentNo || '-' }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">性别 / 入学</text>
        <text class="ent-value">{{ genderText(item.gender) }} · {{ item.enrolledAt || '-' }}</text>
      </view>
      <view class="ent-row">
        <text class="ent-label">监护人</text>
        <text class="ent-value">
          {{ item.guardianName || '-' }}
          <text v-if="item.guardianPhone" class="ent-muted">（{{ item.guardianPhone }}）</text>
        </text>
      </view>
      <view v-if="canWrite" class="ent-btn-row right">
        <view class="ent-btn small" @click.stop="openEdit(item)">编辑</view>
        <view class="ent-btn ghost small" @click.stop="openDetail(item)">详情 ›</view>
      </view>
    </view>

    <view v-if="records.length > 0 && records.length >= total" class="ent-empty">
      已显示全部 {{ total }} 名学员
    </view>
    <view v-else-if="records.length > 0 && loading" class="ent-empty">加载中…</view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}
</style>
