<template>
  <el-popover
    v-model:visible="panelOpen"
    placement="bottom-end"
    trigger="click"
    :width="380"
    popper-class="announce-bell-popper"
  >
    <template #reference>
      <button
        type="button"
        class="banner-bell"
        :class="{ 'is-active': panelOpen }"
        aria-label="公告通知"
      >
        <el-badge :value="unread" :max="99" :hidden="unread === 0" type="danger">
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
            未读{{ unread > 0 ? `（${unread}）` : '' }}
          </button>
        </div>
      </div>

      <div class="bell-scroll">
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
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useDictStore } from '@cornerstone/core/stores/dict'
import {
  fetchMyAnnouncements,
  fetchMyUnreadCount,
  openMyAnnouncement,
  type MyAnnouncement,
} from '@cornerstone/module-education/api/announcement'

/**
 * 顶栏公告铃铛：收件箱只认登录态（/mine/** 无权限码），所以这里不做 hasPermission 判断——
 * 没有公告中心读权限的教师同样该看到发给自己的公告。
 * 默认落在「全部」这条时间线上：点开即已读，若只列未读，随手一点公告就从面板里消失了。
 */
const LIST_SIZE = 20
const POLL_MS = 60_000

const dictStore = useDictStore()

const panelOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<MyAnnouncement | null>(null)
const unread = ref(0)
const items = ref<MyAnnouncement[]>([])
const total = ref(0)
const page = ref(1)
const unreadOnly = ref(false)
const loading = ref(false)

let timer: number | undefined

onMounted(() => {
  void dictStore.ensureLoaded(['announcement_type'])
  void loadUnread()
  void loadPage(1)
  timer = window.setInterval(loadUnread, POLL_MS)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

// 打开面板即重拉：轮询只兜住角标，点开时要拿到当下的收件箱而不是上一次的状态
watch(panelOpen, (open) => {
  if (open) {
    void loadUnread()
    void loadPage(1)
  }
})

/** 按天分组：接口已按置顶+发布时间倒序返回，这里只把同一天并到一段，不再重排 */
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

async function loadUnread() {
  try {
    unread.value = await fetchMyUnreadCount()
  } catch {
    // 顶栏是背景信息，拉不到就保持上一次的结果，不弹错打扰
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
      // 点「加载更早」是要反馈的动作，静默失败看起来像按钮坏了
      ElMessage.warning('加载更早的公告失败，请重试')
    } else {
      items.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
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
    // 这个请求同时就是"打读"动作，服务端只记首次已读时间
    const opened = await openMyAnnouncement(received.id)
    detail.value = opened
    detailOpen.value = true
    if (!received.readAt) {
      // 已读状态回源数组改：模板里拿到的是 computed 解包后的只读投影，直接赋值过不了类型
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
    // 拉不到通常是面板停留期间公告被撤回：给结论并刷新，不把列表里的旧快照当详情展示
    ElMessage.warning('公告不存在或已撤回')
    void loadUnread()
    void loadPage(1)
  }
}

function typeLabel(value?: string | null) {
  return dictStore.displayLabel('announcement_type', value)
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
  /* 空态是一句要读的结论，不是占位符，用 muted 而不是 faint（亮档 faint 压白底只有 2.5） */
  color: var(--text-muted);
}

.bell-day {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 0;
  padding: 7px 14px 5px;
  /* 弹层自己的底色：--card-bg 在夜档比 EP 的 popper 更深，粘性表头会露出色差 */
  background: var(--el-popover-bg-color);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-muted);
}

/* 时间线：竖线画在分组上，节点压在竖线上并用卡片色描边挖出来 */
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

/* 未读不能只靠节点颜色表达，文字一并给出来 */
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
