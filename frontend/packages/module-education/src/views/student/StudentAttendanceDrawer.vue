<template>
  <el-drawer
    :model-value="modelValue"
    size="680px"
    :title="`学员考勤：${studentLabel}`"
    @update:model-value="emit('update:modelValue', Boolean($event))"
  >
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <!-- 区间默认本月 1 号到今天：未来的课次还没到点名的时候，不算进去 -->
    <div class="attendance-range-bar">
      <span class="attendance-range-label">统计区间</span>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="截止日期"
        :clearable="false"
        @change="loadDetail"
      />
      <el-button :icon="Refresh" circle :loading="loading" @click="loadDetail" />
    </div>

    <div class="dashboard-grid attendance-drawer-stat-grid">
      <article class="stat-card">
        <div class="stat-label">应到课次</div>
        <div class="stat-value">{{ detail?.summary.lessonCount ?? 0 }}</div>
        <div class="stat-hint">当天在班或有点名记录</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">已点到</div>
        <div class="stat-value">{{ detail?.summary.fullyMarkedLessons ?? 0 }}</div>
        <div class="stat-hint">有考勤记录的课次</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">未点名</div>
        <div class="stat-value">{{ detail?.summary.pendingLessons ?? 0 }}</div>
        <div class="stat-hint">还没点到这位学员的课次</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">出勤率</div>
        <div class="stat-value">{{ attendanceRateText }}</div>
        <div class="stat-hint">
          出勤 {{ detail?.summary.presentCount ?? 0 }} · 迟到 {{ detail?.summary.lateCount ?? 0 }} ·
          请假 {{ detail?.summary.leaveCount ?? 0 }} · 缺勤 {{ detail?.summary.absentCount ?? 0 }}
        </div>
      </article>
    </div>

    <el-table
      :data="detail?.lessons ?? []"
      border
      row-key="lessonId"
      v-loading="loading"
      :max-height="TABLE_STACK_MAX_HEIGHT"
      :empty-text="loading ? '' : '统计区间内没有这位学员的应到课次。'"
    >
      <el-table-column prop="lessonDate" label="日期" width="110" sortable />
      <el-table-column prop="className" label="班级" min-width="130" show-overflow-tooltip />
      <el-table-column label="时段" width="120">
        <template #default="{ row }">
          {{ compactTimeRange(row.startTime, row.endTime) || '待定时段' }}
        </template>
      </el-table-column>
      <el-table-column prop="lessonTitle" label="主题" min-width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="attendanceStatusTagType(row.attendanceStatus)">
            {{ attendanceStatusLabel(row.attendanceStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
    </el-table>

  </el-drawer>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, ref, watch } from 'vue'
import { TABLE_STACK_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import {
  fetchAttendanceStudentDetail,
  type AttendanceStudentDetailResponse,
} from '@cornerstone/module-education/api/education'
import { studentLabelOf } from '@cornerstone/module-education/composables/studentLabels'
import {
  attendanceStatusLabel,
  attendanceStatusTagType,
  compactTimeRange,
  todayValue,
} from '@cornerstone/module-education/utils/schedule'
import { toMessage } from '@cornerstone/core/utils/error'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 机构 id：小号机构运行期可能是数字 */
    institutionId?: string | number | null
    /** 抽屉查看的是哪一个学员；由宿主在点行时定下来 */
    studentId?: string | null
  }>(),
  { institutionId: null, studentId: null }
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const detail = ref<AttendanceStudentDetailResponse | null>(null)
const loading = ref(false)
const loadError = ref('')

/** 默认区间 = 本月 1 号到今天 */
const dateRange = ref<[string, string]>(defaultRange())

const studentLabel = computed(() =>
  props.studentId ? studentLabelOf(props.studentId) : '未选择学员'
)

const attendanceRateText = computed(() => {
  const summary = detail.value?.summary
  if (!summary || summary.expectedCount === 0) {
    return '—'
  }
  return `${Math.round((summary.presentCount / summary.expectedCount) * 100)}%`
})

function defaultRange(): [string, string] {
  const today = todayValue()
  return [`${today.slice(0, 7)}-01`, today]
}

async function loadDetail() {
  if (!props.studentId || !dateRange.value) {
    detail.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await fetchAttendanceStudentDetail({
      studentId: props.studentId,
      dateFrom: dateRange.value[0],
      dateTo: dateRange.value[1],
    })
  } catch (error) {
    detail.value = null
    loadError.value = toMessage(error, '学员考勤加载失败')
  } finally {
    loading.value = false
  }
}

// 抽屉是常驻挂载的（el-drawer 只隐藏内容），所以打开与换学员都要重拉并回到默认区间，
// 否则会看到上一个学员的考勤
watch(
  () => [props.modelValue, props.studentId],
  ([visible]) => {
    if (!visible) {
      return
    }
    detail.value = null
    dateRange.value = defaultRange()
    void loadDetail()
  }
)
</script>

<style scoped>
.attendance-range-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.attendance-range-label {
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

/* 抽屉宽度有限，四张统计卡收成两列 */
.attendance-drawer-stat-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 14px;
}
</style>
