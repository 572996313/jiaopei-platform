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
const filterOpen = ref(false)
const genderOptions = ref<{ itemCode: string; itemName: string; itemValue: string }[]>([])
const statusOptions = ref<{ value: string; label: string }[]>([{ value: '', label: '全部' }])
const statusIndex = ref(0)
const draftStatusIndex = ref(0)
let dictsLoaded = false

const selectedStatus = computed(() => statusOptions.value[statusIndex.value]?.value || '')
const statusText = computed(() => statusOptions.value[statusIndex.value]?.label || '全部')
const activeFilterCount = computed(() => (selectedStatus.value ? 1 : 0))

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
      { value: '', label: '全部' },
      ...statuses.map((item) => ({ value: item.itemValue, label: item.itemName })),
    ]
    const index = statusOptions.value.findIndex((item) => item.value === selectedStatus.value)
    statusIndex.value = index >= 0 ? index : 0
    draftStatusIndex.value = statusIndex.value
    dictsLoaded = true
  } catch {
    // 字典没拉到就保留“全部”，不硬编码状态中文
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

function clearKeyword() {
  keyword.value = ''
  void load(true)
}

function toggleFilterPanel() {
  if (filterOpen.value) {
    filterOpen.value = false
    return
  }
  draftStatusIndex.value = statusIndex.value
  filterOpen.value = true
}

function applyFilters() {
  statusIndex.value = draftStatusIndex.value
  filterOpen.value = false
  void load(true)
}

function resetFilters() {
  keyword.value = ''
  statusIndex.value = 0
  draftStatusIndex.value = 0
  filterOpen.value = false
  void load(true)
}

function clearStatus() {
  statusIndex.value = 0
  draftStatusIndex.value = 0
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
  <view class="ent-page list-page">
    <view class="list-toolbar">
      <view class="toolbar-search">
        <uni-icons type="search" size="18" color="#9ca3af" />
        <input
          v-model="keyword"
          class="toolbar-search-input"
          type="text"
          confirm-type="search"
          placeholder="搜索姓名 / 学号"
          placeholder-class="input-placeholder"
          @confirm="search"
        />
        <uni-icons
          v-if="keyword"
          type="clear"
          size="18"
          color="#c4c9d1"
          class="toolbar-clear"
          @click="clearKeyword"
        />
      </view>
      <view class="toolbar-btn" :class="{ active: activeFilterCount > 0 }" @click="toggleFilterPanel">
        <uni-icons type="tune" size="18" :color="activeFilterCount > 0 ? '#2563eb' : '#4b5563'" />
        <text>筛选</text>
        <text v-if="activeFilterCount > 0" class="toolbar-badge">{{ activeFilterCount }}</text>
      </view>
    </view>

    <view v-if="activeFilterCount > 0" class="chip-row">
      <view class="chip" @click="clearStatus">状态：{{ statusText }} ×</view>
      <text class="chip-clear" @click="resetFilters">清空</text>
    </view>

    <view v-if="filterOpen" class="filter-panel">
      <text class="filter-label">学员状态</text>
      <view class="status-chips">
        <view
          v-for="(option, index) in statusOptions"
          :key="option.value"
          class="status-chip"
          :class="{ active: draftStatusIndex === index }"
          @click="draftStatusIndex = index"
        >
          {{ option.label }}
        </view>
      </view>
      <view class="filter-actions">
        <view class="ent-btn small" @click="resetFilters">重置</view>
        <view class="ent-btn primary small" @click="applyFilters">查询</view>
      </view>
    </view>

    <view class="list-head">
      <text class="list-title">学员档案</text>
      <text class="list-count">共 {{ total }} 人</text>
    </view>

    <view v-if="!loaded && loading" class="ent-card ent-empty">加载中…</view>
    <view v-else-if="loadError" class="ent-card ent-empty">{{ loadError }}</view>
    <view v-else-if="records.length === 0" class="ent-card ent-empty">
      还没有符合条件的学员；新增学员后可入班、建收费台账。
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

    <view v-if="canWrite" class="fab" @click="openCreate">
      <uni-icons type="plusempty" size="30" color="#ffffff" />
    </view>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}
</style>
