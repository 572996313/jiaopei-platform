<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <el-button :icon="Refresh" circle :loading="loading" @click="load" />
        <NoteHint label="台账口径">
          <b>事件总数</b>是该类型累计产生的事件；<b>未结束</b>是当前仍需要看的（系统 active / 人工
          open、in_progress）；<b>已读人数</b>是去过重账号数，不是阅读次数；<b>订阅覆盖</b>是明确订阅了该类型的人数。
          没有订阅的人也能在「全部事件」里看到，所以"订阅覆盖低"不等于"没人知道"，但可以作为漏订阅排查的起点。
        </NoteHint>
      </div>

      <el-table
        :data="rows"
        border
        row-key="typeCode"
        v-loading="loading"
        empty-text="还没有事件类型"
      >
        <el-table-column
          prop="typeName"
          label="类型"
          min-width="160"
          fixed="left"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="ml-name">{{ row.typeName }}</div>
            <div class="ml-code">{{ row.typeCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="产生方式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.sourceMode === 'scan' ? 'warning' : 'info'" size="small">
              {{ row.sourceMode === 'scan' ? '配置化巡检' : '人工事项' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="事件总数" width="110" align="right" />
        <el-table-column prop="activeCount" label="未结束" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'ml-active': row.activeCount > 0 }">{{ row.activeCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="readCount" label="已读人数" width="110" align="right" />
        <el-table-column prop="subscriberCount" label="订阅覆盖" width="110" align="right" />
        <el-table-column label="最近巡检" min-width="200">
          <template #default="{ row }">
            <div v-if="row.sourceMode !== 'scan'" class="ml-muted">不巡检</div>
            <template v-else>
              <div class="ml-muted">
                {{ row.lastRunAt ? formatTime(row.lastRunAt) : '尚未执行' }}
                <span v-if="row.lastRunAt">· 命中 {{ row.lastRunCount ?? 0 }}</span>
              </div>
              <el-tooltip v-if="row.lastError" :content="row.lastError" placement="top">
                <el-tag type="danger" size="small">失败：{{ row.lastError }}</el-tag>
              </el-tooltip>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import {
  fetchMessageReadLedger,
  type MessageReadLedgerSummary,
} from '@cornerstone/module-education/api/message'

const dictStore = useDictStore()
const rows = ref<MessageReadLedgerSummary[]>([])
const loading = ref(false)
const pageError = ref('')

onMounted(async () => {
  void dictStore.ensureLoaded(['message_category'])
  await load()
})

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    rows.value = await fetchMessageReadLedger()
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '阅读台账加载失败'
  } finally {
    loading.value = false
  }
}

function categoryLabel(value?: string | null) {
  return dictStore.displayLabel('message_category', value)
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<style scoped>
.ml-name {
  font-weight: 600;
  color: var(--text-primary);
}

.ml-code {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.ml-muted {
  font-size: 12px;
  color: var(--text-muted);
}

.ml-active {
  color: var(--soft-warn-ink);
  font-weight: 600;
}
</style>
