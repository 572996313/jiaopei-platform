<template>
  <el-popover
    v-model:visible="panelOpen"
    placement="bottom-end"
    trigger="click"
    :width="400"
    popper-class="announce-bell-popper"
  >
    <template #reference>
      <button
        type="button"
        class="banner-bell"
        :class="{ 'is-active': panelOpen }"
        aria-label="通知"
      >
        <el-badge :value="totalUnread" :max="99" :hidden="totalUnread === 0" type="danger">
          <el-icon><Bell /></el-icon>
        </el-badge>
        <span>通知</span>
      </button>
    </template>

    <div class="bell-panel">
      <div class="bell-head">
        <div class="bell-tabs">
          <button
            type="button"
            :class="{ 'is-active': mode === 'announcement' }"
            :aria-pressed="mode === 'announcement'"
            @click="switchMode('announcement')"
          >
            公告{{ unread > 0 ? `（${unread}）` : '' }}
          </button>
          <button
            type="button"
            :class="{ 'is-active': mode === 'event' }"
            :aria-pressed="mode === 'event'"
            @click="switchMode('event')"
          >
            事件{{ eventUnread > 0 ? `（${eventUnread}）` : '' }}
          </button>
        </div>

        <div v-if="mode === 'announcement'" class="bell-tabs">
          <button
            type="button"
            :class="{ 'is-active': !unreadOnly }"
            :aria-pressed="!unreadOnly"
            @click="switchTab(false)"
          >
            全部
          </button>
          <button
            type="button"
            :class="{ 'is-active': unreadOnly }"
            :aria-pressed="unreadOnly"
            @click="switchTab(true)"
          >
            未读
          </button>
        </div>
        <el-checkbox v-else v-model="eventUnreadOnly" size="small" @change="loadEvents(1)">
          只看未读
        </el-checkbox>
      </div>

      <div class="bell-scroll">
        <template v-if="mode === 'announcement'">
          <div v-if="loading && items.length === 0" class="bell-tip">加载中…</div>
          <div v-else-if="items.length === 0" class="bell-tip">
            {{ unreadOnly ? '没有未读公告' : '暂时还没有收到公告' }}
          </div>
          <template v-else>
            <section v-for="group in groups" :key="group.day" class="bell-group">
              <h4 class="bell-day">{{ dayLabel(group.day) }}</h4>
              <div class="bell-track">
                <button
                  v-for="item in group.items"
                  :key="item.id"
                  type="button"
                  class="bell-item"
                  :class="{ 'is-read': !!item.readAt }"
                  @click="openItem(item)"
                >
                  <span class="bell-item-dot" aria-hidden="true"></span>
                  <span class="bell-item-body">
                    <span class="bell-item-title">{{ item.title }}</span>
                    <span class="bell-item-meta">
                      <span v-if="item.top" class="bell-tag is-top">置顶</span>
                      <span class="bell-tag">{{ typeLabel(item.typeCode) }}</span>
                      <span :class="item.readAt ? 'bell-read' : 'bell-unread'">
                        {{ item.readAt ? `已读 ${shortTime(item.readAt)}` : '未读' }}
                      </span>
                    </span>
                  </span>
                  <span class="bell-item-time">{{ clockTime(item.publishedAt) }}</span>
                </button>
              </div>
            </section>
            <el-button
              v-if="items.length < total"
              class="bell-more"
              link
              :loading="loading"
              @click="loadPage(page + 1)"
            >
              加载更早的公告（还有 {{ total - items.length }} 条）
            </el-button>
            <p v-else class="bell-foot">没有更多了</p>
          </template>
        </template>

        <template v-else>
          <div v-if="eventLoading && eventItems.length === 0" class="bell-tip">加载中…</div>
          <div v-else-if="eventItems.length === 0" class="bell-tip">
            {{ eventUnreadOnly ? '没有未读的未结束事件' : '当前没有未结束事件' }}
          </div>
          <template v-else>
            <section v-for="group in eventGroups" :key="group.day" class="bell-group">
              <h4 class="bell-day">{{ dayLabel(group.day) }}</h4>
              <div class="bell-track">
                <button
                  v-for="item in group.items"
                  :key="item.id"
                  type="button"
                  class="bell-item"
                  :class="{ 'is-read': item.read }"
                  @click="openEvent(item)"
                >
                  <span class="bell-item-dot" aria-hidden="true"></span>
                  <span class="bell-item-body">
                    <span class="bell-item-title">{{ item.title }}</span>
                    <span class="bell-item-meta">
                      <span class="bell-tag">{{ item.typeName || item.typeCode }}</span>
                      <span class="bell-tag" :class="levelClass(item.level)">
                        {{ levelLabel(item.level) }}
                      </span>
                      <span :class="item.read ? 'bell-read' : 'bell-unread'">
                        {{ item.read ? '已读' : '未读' }}
                      </span>
                    </span>
                  </span>
                  <span class="bell-item-time">{{ clockTime(item.occurredAt) }}</span>
                </button>
              </div>
            </section>
            <el-button
              v-if="eventItems.length < eventTotal"
              class="bell-more"
              link
              :loading="eventLoading"
              @click="loadEvents(eventPage + 1)"
            >
              加载更多事件（还有 {{ eventTotal - eventItems.length }} 条）
            </el-button>
            <p v-else class="bell-foot">没有更多了</p>
          </template>
          <div class="bell-foot-actions">
            <el-button link type="primary" @click="goMessageCenter">打开事件中心</el-button>
          </div>
        </template>
      </div>
    </div>
  </el-popover>

  <el-dialog v-model="detailOpen" width="620px" :title="detail?.title || '公告详情'" append-to-body>
    <div v-if="detail" class="bell-detail">
      <div class="bell-detail-meta">
        <span class="bell-tag">{{ typeLabel(detail.typeCode) }}</span>
        <span>发布于 {{ formatTime(detail.publishedAt) }}</span>
        <span v-if="detail.readAt">· 已读 {{ formatTime(detail.readAt) }}</span>
      </div>
      <!-- 正文是发布时冻结的纯文本快照，换行按原样呈现 -->
      <div class="bell-detail-content">{{ detail.content }}</div>
    </div>
    <template #footer>
      <el-button @click="detailOpen = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="eventDetailOpen"
    width="580px"
    :title="eventDetail?.title || '事件详情'"
    append-to-body
  >
    <div v-if="eventDetail" class="bell-detail">
      <div class="bell-detail-meta">
        <el-tag :type="levelTag(eventDetail.level)" size="small">
          {{ levelLabel(eventDetail.level) }}
        </el-tag>
        <span class="bell-tag">{{ eventDetail.typeName || eventDetail.typeCode }}</span>
        <span>{{ statusLabel(eventDetail.status) }}</span>
        <span>· {{ formatTime(eventDetail.occurredAt) }}</span>
      </div>
      <div class="bell-detail-content">{{ eventDetail.body }}</div>
      <div v-if="eventDetail.actionUrl">
        <el-button type="primary" plain size="small" @click="goAction(eventDetail.actionUrl)">
          打开关联页面
        </el-button>
      </div>
    </div>
    <template #footer>
      <el-button @click="eventDetailOpen = false">关闭</el-button>
      <el-button type="primary" @click="goMessageCenter">在事件中心查看</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useDictStore } from '@cornerstone/core/stores/dict'
import {
  fetchMyAnnouncements,
  fetchMyUnreadCount,
  openMyAnnouncement,
  type MyAnnouncement,
} from '@cornerstone/module-education/api/announcement'
import {
  MESSAGE_LEVEL_OPTIONS,
  fetchMessageDetail,
  fetchMessageUnreadCount,
  fetchMyMessagePage,
  type MessageEventRecord,
} from '@cornerstone/module-education/api/message'

/**
 * 顶栏通知铃铛：公告与事件两个时间线。
 * 公告走 /mine/**（只认登录态）；事件走 /messages/mine（订阅收窄注意力，不是权限）。
 * 角标合并两边的未读数，轮询只兜角标，点开面板时重拉当前列表。
 */
const LIST_SIZE = 20
const POLL_MS = 60_000

const dictStore = useDictStore()
const router = useRouter()

const panelOpen = ref(false)
const mode = ref<'announcement' | 'event'>('announcement')

const detailOpen = ref(false)
const detail = ref<MyAnnouncement | null>(null)
const unread = ref(0)
const items = ref<MyAnnouncement[]>([])
const total = ref(0)
const page = ref(1)
const unreadOnly = ref(false)
const loading = ref(false)

const eventDetailOpen = ref(false)
const eventDetail = ref<MessageEventRecord | null>(null)
const eventUnread = ref(0)
const eventItems = ref<MessageEventRecord[]>([])
const eventTotal = ref(0)
const eventPage = ref(1)
const eventUnreadOnly = ref(true)
const eventLoading = ref(false)

const totalUnread = computed(() => unread.value + eventUnread.value)

let timer: number | undefined

onMounted(() => {
  void dictStore.ensureLoaded(['announcement_type'])
  void loadUnread()
  void loadEventUnread()
  void loadPage(1)
  timer = window.setInterval(() => {
    void loadUnread()
    void loadEventUnread()
  }, POLL_MS)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

watch(panelOpen, (open) => {
  if (open) {
    void loadUnread()
    void loadEventUnread()
    if (mode.value === 'announcement') {
      void loadPage(1)
    } else {
      void loadEvents(1)
    }
  }
})

/** 公告按天分组：接口已按置顶+发布时间倒序返回，这里只把同一天并到一段，不再重排 */
const groups = computed(() => {
  const buckets = new Map<string, MyAnnouncement[]>()
  for (const item of items.value) {
    const day = dayKey(item.publishedAt)
    const bucket = buckets.get(day)
    if (bucket) {
      bucket.push(item)
    } else {
      buckets.set(day, [item])
    }
  }
  return [...buckets].map(([day, list]) => ({ day, items: list }))
})

const eventGroups = computed(() => {
  const buckets = new Map<string, MessageEventRecord[]>()
  for (const item of eventItems.value) {
    const day = dayKey(item.occurredAt)
    const bucket = buckets.get(day)
    if (bucket) {
      bucket.push(item)
    } else {
      buckets.set(day, [item])
    }
  }
  return [...buckets].map(([day, list]) => ({ day, items: list }))
})

async function loadUnread() {
  try {
    unread.value = await fetchMyUnreadCount()
  } catch {
    // 顶栏是背景信息，拉不到就保持上一次的结果，不弹错打扰
  }
}

async function loadEventUnread() {
  try {
    eventUnread.value = await fetchMessageUnreadCount()
  } catch {
    // 同上
  }
}

async function loadPage(next: number) {
  loading.value = true
  try {
    const result = await fetchMyAnnouncements(next, LIST_SIZE, unreadOnly.value)
    items.value = next === 1 ? result.records : items.value.concat(result.records)
    total.value = result.total
    page.value = next
  } catch {
    if (next > 1) {
      ElMessage.warning('加载更早的公告失败，请重试')
    } else {
      items.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

async function loadEvents(next: number) {
  eventLoading.value = true
  try {
    const result = await fetchMyMessagePage({
      page: next,
      pageSize: LIST_SIZE,
      openOnly: true,
      unreadOnly: eventUnreadOnly.value ? true : undefined,
    })
    eventItems.value = next === 1 ? result.records : eventItems.value.concat(result.records)
    eventTotal.value = result.total
    eventPage.value = next
  } catch {
    if (next > 1) {
      ElMessage.warning('加载更多事件失败，请重试')
    } else {
      eventItems.value = []
      eventTotal.value = 0
    }
  } finally {
    eventLoading.value = false
  }
}

function switchMode(next: 'announcement' | 'event') {
  if (mode.value === next) {
    return
  }
  mode.value = next
  if (next === 'event' && eventItems.value.length === 0) {
    void loadEvents(1)
  }
}

function switchTab(only: boolean) {
  if (unreadOnly.value === only) {
    return
  }
  unreadOnly.value = only
  void loadPage(1)
}

async function openItem(received: MyAnnouncement) {
  panelOpen.value = false
  try {
    const opened = await openMyAnnouncement(received.id)
    detail.value = opened
    detailOpen.value = true
    if (!received.readAt) {
      const row = items.value.find((item) => item.id === received.id)
      if (row) {
        row.readAt = opened.readAt
        if (unreadOnly.value) {
          items.value = items.value.filter((item) => item.id !== received.id)
          total.value = Math.max(0, total.value - 1)
        }
      }
      unread.value = Math.max(0, unread.value - 1)
    }
  } catch {
    ElMessage.warning('公告不存在或已撤回')
    void loadUnread()
    void loadPage(1)
  }
}

async function openEvent(received: MessageEventRecord) {
  panelOpen.value = false
  try {
    const opened = await fetchMessageDetail(received.id)
    eventDetail.value = opened.event
    eventDetailOpen.value = true
    if (!received.read) {
      received.read = true
      eventUnread.value = Math.max(0, eventUnread.value - 1)
      if (eventUnreadOnly.value) {
        eventItems.value = eventItems.value.filter((item) => item.id !== received.id)
        eventTotal.value = Math.max(0, eventTotal.value - 1)
      }
    }
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '事件不存在或已不可见')
    void loadEventUnread()
    void loadEvents(1)
  }
}

function goMessageCenter() {
  panelOpen.value = false
  eventDetailOpen.value = false
  void router.push('/messages')
}

function goAction(url?: string | null) {
  if (!url) {
    return
  }
  panelOpen.value = false
  eventDetailOpen.value = false
  void router.push(url)
}

function typeLabel(value?: string | null) {
  return dictStore.displayLabel('announcement_type', value)
}

function levelLabel(value?: string | null) {
  return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === value)?.label || value || '提示'
}

function levelTag(value?: string | null) {
  return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === value)?.tag || 'info'
}

function levelClass(value?: string | null) {
  if (value === 'critical') {
    return 'is-critical'
  }
  if (value === 'warning') {
    return 'is-warning'
  }
  return ''
}

function statusLabel(value?: string | null) {
  const map: Record<string, string> = {
    active: '待处理',
    open: '待处理',
    in_progress: '处理中',
    done: '已完成',
    closed: '已关闭',
    resolved: '已消解',
  }
  return map[value || ''] || value || '—'
}

/** 后端给的是 LocalDateTime 的 'YYYY-MM-DDTHH:mm:ss'，全部按本地日历切片处理，不做时区换算 */
function dayKey(value?: string | null) {
  return value ? value.slice(0, 10) : '未知日期'
}

function dayLabel(key: string) {
  if (key === '未知日期') {
    return key
  }
  const now = new Date()
  const today = formatDate(now)
  const yesterday = formatDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1))
  if (key === today) {
    return `今天 ${key.slice(5)}`
  }
  if (key === yesterday) {
    return `昨天 ${key.slice(5)}`
  }
  return key.slice(0, 4) === String(now.getFullYear()) ? key.slice(5) : key
}

function formatDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function clockTime(value?: string | null) {
  return value ? value.slice(11, 16) : '—'
}

function shortTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(5, 16) : '—'
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<style scoped>
/* 顶栏控件：与 .banner-search 同一套深色带上的浅色玻璃质感 */
.banner-bell {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--banner-line);
  border-radius: 9px;
  background: var(--banner-glass);
  color: var(--banner-ink-dim);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s,
    color 0.15s;
}

.banner-bell:hover,
.banner-bell:focus-visible,
.banner-bell.is-active {
  border-color: rgba(249, 199, 79, 0.6);
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}

.banner-bell .el-icon {
  font-size: 16px;
}

/* 角标压在图标右上角，避免顶到胶囊边框 */
.banner-bell :deep(.el-badge__content) {
  top: 4px;
  right: calc(100% - 14px);
  border: none;
}

.bell-panel {
  display: flex;
  flex-direction: column;
}

.bell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
}

.bell-tabs {
  display: flex;
  gap: 4px;
}

.bell-tabs button {
  padding: 4px 12px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}

.bell-tabs button:hover,
.bell-tabs button:focus-visible {
  background: var(--hover-bg);
}

.bell-tabs button.is-active {
  background: var(--tint-bg);
  color: var(--tint-ink);
  font-weight: 600;
}

.bell-scroll {
  max-height: 46vh;
  overflow-y: auto;
}

.bell-tip {
  padding: 28px 14px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-muted);
}

.bell-day {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 0;
  padding: 7px 14px 5px;
  background: var(--el-popover-bg-color);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-muted);
}

.bell-track {
  position: relative;
}

.bell-track::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 17px;
  width: 2px;
  background: var(--line);
}

.bell-item {
  position: relative;
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 9px 14px 9px 32px;
  border: none;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.bell-item:hover,
.bell-item:focus-visible {
  background: var(--hover-bg);
}

.bell-item-dot {
  position: absolute;
  top: 15px;
  left: 14px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 2px var(--el-popover-bg-color);
}

.bell-item.is-read .bell-item-dot {
  background: var(--text-faint);
}

.bell-item-body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.bell-item-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-primary);
}

.bell-item.is-read .bell-item-title {
  font-weight: 500;
  color: var(--text-secondary);
}

.bell-item-time {
  flex: none;
  padding-top: 2px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.bell-item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.bell-unread {
  color: var(--accent);
}

.bell-read {
  color: var(--text-muted);
}

.bell-tag {
  padding: 0 6px;
  border-radius: 4px;
  background: var(--tint-bg);
  color: var(--tint-ink);
  font-size: 11px;
  line-height: 18px;
}

.bell-tag.is-top {
  background: var(--soft-warn-bg);
  color: var(--soft-warn-ink);
}

.bell-tag.is-warning {
  background: var(--soft-warn-bg);
  color: var(--soft-warn-ink);
}

.bell-tag.is-critical {
  background: var(--soft-danger-bg, rgba(245, 108, 108, 0.14));
  color: var(--soft-danger-ink, #c45656);
}

.bell-more {
  display: flex;
  width: 100%;
  justify-content: center;
  padding: 8px 0;
  font-size: 12px;
}

.bell-foot {
  margin: 0;
  padding: 8px 14px;
  text-align: center;
  font-size: 11.5px;
  color: var(--text-muted);
}

.bell-foot-actions {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
  border-top: 1px solid var(--line);
}

.bell-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bell-detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.bell-detail-content {
  max-height: 52vh;
  overflow-y: auto;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--subtle-bg);
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-secondary);
  white-space: pre-wrap;
}
</style>

<style>
/* 弹层挂在 body 上，作用样式只能走全局；把 EP 默认内边距交给面板自己控制 */
.el-popover.announce-bell-popper {
  padding: 0;
}
</style>
