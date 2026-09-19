<template>
  <el-dialog
    :model-value="modelValue"
    title="课次点名"
    width="960px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <div v-if="page" class="attendance-shell">
        <div class="attendance-dialog-context">
          <div>
            <strong>班级：</strong>{{ page.lesson.className || formatClass(page.lesson.classId) }}
          </div>
          <div><strong>日期：</strong>{{ page.lesson.lessonDate || '未设置' }}</div>
          <div>
            <strong>时段：</strong>{{ compactTimeRange(page.lesson.startTime, page.lesson.endTime) || '未设置' }}
          </div>
          <div><strong>主题：</strong>{{ page.lesson.lessonTitle || '未设置' }}</div>
          <div v-if="relationSummary"><strong>停补关联：</strong>{{ relationSummary }}</div>
          <div v-if="relationJumpLabel">
            <el-button link type="primary" @click="emit('relation-jump')">
              {{ relationJumpLabel }}
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="loadError"
          :title="loadError"
          type="error"
          show-icon
          :closable="false"
          class="attendance-dialog-alert"
        />
        <el-alert
          v-else-if="page.message"
          :title="page.message"
          type="warning"
          show-icon
          :closable="false"
          class="attendance-dialog-alert"
        />

        <div class="dashboard-grid attendance-stat-grid">
          <article class="stat-card">
            <div class="stat-label">应到</div>
            <div class="stat-value">{{ summary.expectedCount }}</div>
            <div class="stat-hint">课次在班学员</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">已点</div>
            <div class="stat-value">{{ summary.markedCount }}</div>
            <div class="stat-hint">已选择点名状态</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">到课</div>
            <div class="stat-value">{{ summary.presentCount }}</div>
            <div class="stat-hint">出勤</div>
          </article>
          <article class="stat-card">
            <div class="stat-label">缺勤 / 请假 / 迟到</div>
            <div class="stat-value attendance-multi-stat">
              {{ summary.absentCount }} / {{ summary.leaveCount }} / {{ summary.lateCount }}
            </div>
            <div class="stat-hint">缺勤 / 请假 / 迟到</div>
          </article>
        </div>

        <div class="attendance-toolbar">
          <div class="education-context-text">
            支持再次进入回显；未设置状态的学员不会写入记录。
          </div>
          <div class="attendance-toolbar-actions">
            <el-button @click="applyStatusToAll('present')">全员到课</el-button>
            <el-button @click="clearStatuses">清空状态</el-button>
          </div>
        </div>

        <el-table :data="rows" border max-height="420">
          <el-table-column label="学号" min-width="140">
            <template #default="{ row }">
              {{ row.studentNo || `学员 #${row.studentId}` }}
            </template>
          </el-table-column>
          <el-table-column label="姓名" min-width="140">
            <template #default="{ row }">
              {{ row.studentName || `学员 #${row.studentId}` }}
            </template>
          </el-table-column>
          <el-table-column label="当前状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.attendanceStatus" :type="attendanceStatusTagType(row.attendanceStatus)">
                {{ attendanceStatusLabel(row.attendanceStatus) }}
              </el-tag>
              <span v-else class="attendance-empty-text">未点名</span>
            </template>
          </el-table-column>
          <el-table-column label="点名状态" min-width="240">
            <template #default="{ row }">
              <el-radio-group v-model="row.draftStatus" class="attendance-status-group">
                <el-radio-button label="present">到课</el-radio-button>
                <el-radio-button label="absent">缺勤</el-radio-button>
                <el-radio-button label="leave">请假</el-radio-button>
                <el-radio-button label="late">迟到</el-radio-button>
              </el-radio-group>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="220">
            <template #default="{ row }">
              <el-input v-model="row.draftRemark" maxlength="100" placeholder="补充点名说明" />
            </template>
          </el-table-column>
        </el-table>

        <div v-if="rows.length === 0" class="education-empty">当前课次未找到可点名学员。</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        type="warning"
        :loading="submitting || completing"
        @click="saveAndComplete"
      >
        保存并结课
      </el-button>
      <el-button type="primary" :loading="submitting" @click="save">保存点名</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  fetchLessonAttendancePage,
  saveLessonAttendanceBatch,
  type AttendanceLessonPage,
  type AttendanceLessonStudent,
} from '@/api/education'
import {
  attendanceStatusLabel,
  attendanceStatusTagType,
  compactTimeRange,
  toMessage,
  trimToUndefined,
  updateLessonCompletion,
} from './schedule-utils'

interface AttendanceEditorRow extends AttendanceLessonStudent {
  draftStatus: string
  draftRemark: string
}

const props = defineProps<{
  modelValue: boolean
  lessonId: string | null
  formatClass: (id?: string | null) => string
  relationSummary?: string
  relationJumpLabel?: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
  (event: 'completed'): void
  (event: 'relation-jump'): void
}>()

const loading = ref(false)
const submitting = ref(false)
const completing = ref(false)
const loadError = ref('')
const page = ref<AttendanceLessonPage | null>(null)
const rows = ref<AttendanceEditorRow[]>([])

const summary = computed(() => {
  let markedCount = 0
  let presentCount = 0
  let absentCount = 0
  let leaveCount = 0
  let lateCount = 0

  for (const row of rows.value) {
    if (!row.draftStatus) {
      continue
    }
    markedCount++
    if (row.draftStatus === 'present') presentCount++
    if (row.draftStatus === 'absent') absentCount++
    if (row.draftStatus === 'leave') leaveCount++
    if (row.draftStatus === 'late') lateCount++
  }

  return {
    expectedCount: rows.value.length,
    markedCount,
    presentCount,
    absentCount,
    leaveCount,
    lateCount,
  }
})

function populate(nextPage: AttendanceLessonPage) {
  page.value = nextPage
  rows.value = nextPage.students.map((item) => ({
    ...item,
    draftStatus: item.attendanceStatus || '',
    draftRemark: item.remark || '',
  }))
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible || !props.lessonId) {
      return
    }
    loading.value = true
    loadError.value = ''
    page.value = null
    rows.value = []
    try {
      populate(await fetchLessonAttendancePage(props.lessonId))
    } catch (error) {
      loadError.value = toMessage(error, '课次点名页加载失败')
    } finally {
      loading.value = false
    }
  }
)

function applyStatusToAll(status: string) {
  rows.value.forEach((row) => {
    row.draftStatus = status
  })
}

function clearStatuses() {
  rows.value.forEach((row) => {
    row.draftStatus = ''
  })
}

// 结课走全量 PUT（后端暂无状态端点），与页面直接结课共用同一实现
async function completeLessonById(id: string) {
  await updateLessonCompletion(id, 'completed')
}

async function save(): Promise<boolean> {
  if (!page.value?.lesson.lessonId) {
    loadError.value = '缺少课次信息，无法保存点名'
    return false
  }

  const items = rows.value
    .filter((row) => row.draftStatus)
    .map((row) => ({
      studentId: row.studentId,
      attendanceStatus: row.draftStatus,
      source: row.source || 'manual',
      remark: trimToUndefined(row.draftRemark),
    }))

  if (items.length === 0) {
    loadError.value = '请至少为一位学员选择点名状态'
    return false
  }

  submitting.value = true
  loadError.value = ''
  try {
    populate(await saveLessonAttendanceBatch({ lessonId: page.value.lesson.lessonId, items }))
    emit('saved')
    return true
  } catch (error) {
    loadError.value = toMessage(error, '课次点名保存失败')
    return false
  } finally {
    submitting.value = false
  }
}

async function saveAndComplete() {
  if (!page.value?.lesson.lessonId) {
    loadError.value = '当前未加载课次点名页'
    return
  }

  const saved = await save()
  if (!saved) {
    return
  }

  completing.value = true
  try {
    await completeLessonById(page.value.lesson.lessonId)
    emit('update:modelValue', false)
    emit('completed')
  } catch (error) {
    loadError.value = toMessage(error, '点名已保存，但结课失败')
  } finally {
    completing.value = false
  }
}
</script>

<style scoped>
.attendance-dialog-context {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 16px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.92);
  color: #234c77;
}

.attendance-dialog-alert {
  margin-bottom: 12px;
}

.attendance-shell {
  display: grid;
  gap: 16px;
}

.attendance-stat-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.attendance-multi-stat {
  font-size: 24px;
}

.attendance-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.attendance-toolbar-actions {
  display: flex;
  gap: 8px;
}

.attendance-status-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.attendance-empty-text {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .attendance-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .attendance-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .attendance-dialog-context {
    grid-template-columns: 1fr;
  }
}
</style>
