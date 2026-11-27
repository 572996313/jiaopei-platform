<template>
  <el-dialog
    :model-value="modelValue"
    title="按手机号匹配教师账号"
    width="1040px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
  >
    <el-alert
      v-if="errorText"
      :title="errorText"
      type="error"
      show-icon
      :closable="false"
      class="link-alert"
    />

    <p class="education-context-text">
      只处理本机构「未绑定账号的教师档案 × 已存在的登录账号」，匹配键是手机号完全相同。
      这里不创建账号：没有命中说明本人还没开号，请到「系统设置 → 用户管理 → 批量导入账号」开通后再回来。
      一位教师只绑一个账号，同一手机号命中多个账号时请对照显示名称勾选其中一行。
    </p>

    <div class="dashboard-grid link-stat-grid">
      <article class="stat-card">
        <div class="stat-label">未绑定档案</div>
        <div class="stat-value">{{ candidates?.unlinkedTotal ?? 0 }}</div>
        <div class="stat-hint">本机构已建档、尚无账号</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">可比对</div>
        <div class="stat-value">{{ candidates?.matchableTotal ?? 0 }}</div>
        <div class="stat-hint">其中档案填了手机号的</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">已勾选</div>
        <div class="stat-value">{{ pickedCount }}</div>
        <div class="stat-hint">姓名一致的已默认勾上</div>
      </article>
    </div>

    <div class="table-toolbar">
      <el-button :icon="Refresh" :loading="loading" @click="load">重新匹配</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      border
      row-key="rowKey"
      max-height="420"
      :row-class-name="rowClassName"
      :empty-text="loading ? '' : '本机构暂无可比对的教师档案：要么都已绑定账号，要么档案里没填手机号。'"
    >
      <el-table-column label="绑定" width="70">
        <template #default="{ row }">
          <el-checkbox
            :disabled="!isPickable(row)"
            :model-value="isPicked(row)"
            @change="toggle(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="teacherCode" label="教师编码" width="150" fixed="left" />
      <el-table-column prop="teacherName" label="教师姓名" width="120" fixed="left" />
      <el-table-column prop="phone" label="档案手机号" width="130" />
      <el-table-column prop="username" label="候选账号" width="160" />
      <el-table-column prop="displayName" label="账号显示名称" width="140" />
      <el-table-column label="姓名" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.nameMatched === true" type="success">一致</el-tag>
          <el-tag v-else-if="row.nameMatched === false" type="warning">不同</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="280">
        <template #default="{ row }">
          <span v-if="row.notice" class="link-notice-text">{{ row.notice }}</span>
          <span v-else>可绑定</span>
        </template>
      </el-table-column>
    </el-table>


    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="pickedCount === 0"
        @click="submit"
      >
        绑定选中（{{ pickedCount }}）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { isApiError } from '@cornerstone/core/api/request'
import {
  fetchTeacherLinkCandidates,
  linkTeacherAccounts,
  type TeacherLinkCandidateResult,
  type TeacherLinkCandidateView,
} from '@cornerstone/module-education/api/teacherImport'

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 绑定成功后通知宿主刷新教师列表（列表要看「关联账号」列） */
  linked: []
}>()

defineProps<{
  modelValue: boolean
}>()

/** 表格里的一行：候选行 + 用于 row-key 的定位串（一位教师可能命中多个账号，teacherId 不唯一） */
type CandidateRow = TeacherLinkCandidateView & { rowKey: string }

const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const candidates = ref<TeacherLinkCandidateResult | null>(null)
/** teacherId -> 勾选的那一行；一位教师只留一行，改勾即覆盖 */
const picked = ref<Record<string, CandidateRow>>({})

const rows = computed<CandidateRow[]>(() =>
  (candidates.value?.rows ?? []).map((row) => ({
    ...row,
    rowKey: `${row.teacherId ?? ''}#${row.userId ?? ''}`,
  }))
)

const pickedCount = computed(() => Object.keys(picked.value).length)

function messageOf(error: unknown, fallback: string) {
  return isApiError(error) ? error.message : fallback
}

function isPickable(row: CandidateRow) {
  return row.actionable && !!row.userId
}

function isPicked(row: CandidateRow) {
  return !!row.teacherId && picked.value[row.teacherId]?.rowKey === row.rowKey
}

function rowClassName({ row }: { row: CandidateRow }) {
  return isPickable(row) ? '' : 'link-row-muted'
}

function toggle(row: CandidateRow) {
  if (!row.teacherId || !isPickable(row)) {
    return
  }
  const current = picked.value[row.teacherId]
  const next = { ...picked.value }
  if (current?.rowKey === row.rowKey) {
    delete next[row.teacherId]
  } else {
    next[row.teacherId] = row
  }
  picked.value = next
}

async function load() {
  loading.value = true
  errorText.value = ''
  picked.value = {}
  try {
    candidates.value = await fetchTeacherLinkCandidates()
    // 姓名一致是强信号，默认勾上；只勾"可绑定"的行，多账号命中时不会同时勾两行
    for (const row of rows.value) {
      if (row.teacherId && row.nameMatched === true && isPickable(row) && !picked.value[row.teacherId]) {
        picked.value = { ...picked.value, [row.teacherId]: row }
      }
    }
  } catch (error) {
    candidates.value = null
    errorText.value = messageOf(error, '匹配候选加载失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  const links = Object.values(picked.value)
    .filter((row) => row.teacherId && row.userId)
    .map((row) => ({ teacherId: row.teacherId as string, userId: row.userId as string }))
  if (links.length === 0) {
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    const result = await linkTeacherAccounts(links)
    emit('update:modelValue', false)
    emit('linked')
    ElMessage.success(`已绑定 ${result?.linkedCount ?? links.length} 位教师账号`)
  } catch (error) {
    // 任一无效配对后端整批拒绝，留在弹窗里改勾，别让人以为绑上了一半
    errorText.value = messageOf(error, '绑定失败，请检查勾选项')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.link-alert {
  margin-bottom: 12px;
}

.link-stat-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 12px;
}

.link-notice-text {
  color: var(--el-color-warning, #e6a23c);
}

:deep(.link-row-muted) {
  color: var(--el-text-color-secondary, #909399);
}
</style>
