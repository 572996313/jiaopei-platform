<template>
  <el-dialog
    :model-value="modelValue"
    :title="editPlan ? '调整周期排课计划' : '新建周期排课计划'"
    width="960px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <el-steps :active="step" align-center finish-status="success" class="plan-steps">
      <el-step title="计划设置" />
      <el-step title="预览与生成" />
    </el-steps>

    <div v-show="step === 0" class="plan-form" v-loading="lookupLoading">
      <el-form label-width="90px">
        <el-form-item label="班级" required>
          <el-select
            v-model="formClassId"
            placeholder="选择要批量排课的班级"
            filterable
            @change="handleClassChange"
          >
            <el-option
              v-for="item in classes"
              :key="item.id"
              :label="item.className || item.classCode || `班级 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="校区/学期">
          <span class="plan-context-text">
            {{ formatCampus(formCampusId) }} · {{ formatTerm(formTermId) }}
            （跟随所选班级，班级未设置学期时请先在班级管理中补充）
          </span>
        </el-form-item>
        <el-form-item label="授课教师" required>
          <el-select
            v-model="formTeacherIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可多选，课次将同时安排给这些教师"
          >
            <el-option
              v-for="item in teacherOptions"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教室" required>
          <el-select v-model="formClassroomId" filterable placeholder="选择固定使用的教室">
            <el-option
              v-for="item in classroomOptions"
              :key="item.id"
              :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排课频率">
          <el-radio-group v-model="formWeekFrequency">
            <el-radio-button label="every_week">每周</el-radio-button>
            <el-radio-button label="odd_week">单周</el-radio-button>
            <el-radio-button label="even_week">双周</el-radio-button>
          </el-radio-group>
          <span class="plan-context-text plan-frequency-hint">
            单双周以起始日期所在自然周为第 1 周
          </span>
        </el-form-item>
        <el-form-item label="起止日期" required>
          <el-date-picker
            v-model="formStartDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="起始日期"
          />
          <span class="plan-date-sep">至</span>
          <el-date-picker
            v-model="formEndDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item label="跳过日期">
          <el-date-picker
            v-model="formSkipDates"
            type="dates"
            value-format="YYYY-MM-DD"
            placeholder="停课日/节假日，可多选"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formRemark" maxlength="500" placeholder="选填" />
        </el-form-item>
        <el-form-item label="每周节次" required>
          <div class="plan-grid-wrap">
            <SchedulePatternGrid v-model="formSlots" :time-slots="scopedTimeSlots" />
          </div>
        </el-form-item>
      </el-form>
    </div>

    <div v-show="step === 1" class="plan-preview" v-loading="previewLoading">
      <div class="education-context-text">
        共展开 {{ previewItems.length }} 个课次，其中冲突 {{ conflictCount }} 个、跳过
        {{ skippedCount }} 个。冲突课次无法勾选生成，请调整计划或先处理占用；取消勾选即不生成该课次。
      </div>
      <el-alert
        v-if="ruleNotices.length > 0"
        type="warning"
        :closable="false"
        show-icon
        title="必排规则缺口"
      >
        <div v-for="notice in ruleNotices" :key="notice" class="plan-notice-line">{{ notice }}</div>
      </el-alert>
      <el-table :data="previewItems" border stripe row-key="key" max-height="420">
        <el-table-column width="46" align="center">
          <template #header>
            <el-checkbox
              :model-value="allChecked"
              :indeterminate="someChecked"
              :disabled="selectableItems.length === 0"
              @change="toggleAll"
            />
          </template>
          <template #default="{ row }">
            <el-checkbox
              :model-value="checkedKeys.has(row.key)"
              :disabled="row.conflict || row.skipped"
              @change="toggleRow(row.key, $event as boolean)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="weekNo" label="周次" width="64" align="center" />
        <el-table-column prop="lessonDate" label="日期" width="110" />
        <el-table-column label="星期" width="72">
          <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
        </el-table-column>
        <el-table-column prop="slotName" label="节次" min-width="110" />
        <el-table-column label="时间" width="120">
          <template #default="{ row }">{{
            compactTimeRange(row.startTime, row.endTime) || '—'
          }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.conflict" size="small" type="danger">
              {{ (row.conflictMessages || []).join('；') || '存在冲突' }}
            </el-tag>
            <el-tag v-else-if="row.skipped" size="small" type="warning">命中跳过日期</el-tag>
            <span v-else class="plan-ok-text">可生成</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="step === 1" @click="step = 0">上一步</el-button>
      <el-button v-if="step === 0" type="primary" :loading="previewLoading" @click="handlePreview">
        下一步：预览
      </el-button>
      <el-button
        v-else
        type="primary"
        :loading="committing"
        :disabled="checkedKeys.size === 0"
        @click="handleCommit"
      >
        生成课次（{{ checkedKeys.size }}）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
// 批量排课向导：第一步选班级/教师/教室并在节次×星期网格上勾每周模式，
// 第二步调后端 preview 展开课次草案、按冲突标记勾选，commit 一次性落库。
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  commitSchedulePlan,
  previewSchedulePlan,
  type SchedulePlanPayload,
  type SchedulePlanPreviewItem,
  type SchedulePlanRecord,
  type SchedulePlanSlotItem,
  type TeacherSummary,
  type TeachingClassSummary,
} from '@cornerstone/module-education/api/education'
import type { TermSummary, ClassroomSummary, TimeSlotSummary } from '@cornerstone/module-organization/api/organization'
import SchedulePatternGrid from './SchedulePatternGrid.vue'
import { compactTimeRange, normalizeTeacherIds, toMessage, weekdayLabel } from './schedule-utils'

const props = defineProps<{
  modelValue: boolean
  institutionId: string | null
  editPlan: SchedulePlanRecord | null
  terms: TermSummary[]
  classes: TeachingClassSummary[]
  teachers: TeacherSummary[]
  classrooms: ClassroomSummary[]
  timeSlots: TimeSlotSummary[]
  scopeCampusId?: string | null
  scopeClassId?: string | null
  lookupLoading?: boolean
  formatCampus: (id?: string | null) => string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', message: string): void
}>()

interface PreviewRow extends SchedulePlanPreviewItem {
  key: string
}

const step = ref(0)
const previewLoading = ref(false)
const committing = ref(false)

const formClassId = ref<string>('')
const formCampusId = ref<string>('')
const formTermId = ref<string>('')
const formTeacherIds = ref<string[]>([])
const formClassroomId = ref<string>('')
const formWeekFrequency = ref('every_week')
const formStartDate = ref<string>('')
const formEndDate = ref<string>('')
const formSkipDates = ref<string[]>([])
const formRemark = ref('')
const formSlots = ref<SchedulePlanSlotItem[]>([])

const previewItems = ref<PreviewRow[]>([])
const checkedKeys = ref(new Set<string>())
const ruleNotices = ref<string[]>([])

function formatTerm(id?: string | null) {
  if (!id) {
    return '未设置学期'
  }
  const term = props.terms.find((item) => item.id === id)
  return term?.termName || term?.termCode || `学期 #${id}`
}

// 教师/教室/时段候选跟随所选班级校区；未选班级时展示全量
const scopedCampusId = computed(() => formCampusId.value || null)

const teacherOptions = computed(() =>
  scopedCampusId.value
    ? props.teachers.filter((item) => !item.campusId || item.campusId === scopedCampusId.value)
    : props.teachers
)

const classroomOptions = computed(() =>
  scopedCampusId.value
    ? props.classrooms.filter((item) => !item.campusId || item.campusId === scopedCampusId.value)
    : props.classrooms
)

const scopedTimeSlots = computed(() => {
  const campus = scopedCampusId.value
  return props.timeSlots.filter((slot) => {
    if (slot.status && slot.status !== 'active') {
      return false
    }
    return !campus || !slot.campusId || slot.campusId === campus
  })
})

const conflictCount = computed(() => previewItems.value.filter((item) => item.conflict).length)
const skippedCount = computed(() => previewItems.value.filter((item) => item.skipped).length)
const selectableItems = computed(() =>
  previewItems.value.filter((item) => !item.conflict && !item.skipped)
)
const allChecked = computed(
  () =>
    selectableItems.value.length > 0 &&
    selectableItems.value.every((item) => checkedKeys.value.has(item.key))
)
const someChecked = computed(
  () => checkedKeys.value.size > 0 && !allChecked.value && selectableItems.value.length > 0
)

function handleOpen() {
  step.value = 0
  previewItems.value = []
  checkedKeys.value = new Set()
  ruleNotices.value = []
  const plan = props.editPlan
  if (plan) {
    formClassId.value = plan.classId || ''
    formCampusId.value = plan.campusId || ''
    formTermId.value = plan.termId || ''
    formTeacherIds.value = normalizeTeacherIds(plan.teacherIds)
    formClassroomId.value = plan.classroomId || ''
    formWeekFrequency.value = plan.weekFrequency || 'every_week'
    formStartDate.value = plan.startDate || ''
    formEndDate.value = plan.endDate || ''
    formSkipDates.value = [...(plan.skipDates || [])]
    formRemark.value = plan.remark || ''
    formSlots.value = [...(plan.slots || [])]
    return
  }
  formClassId.value = props.scopeClassId || ''
  formCampusId.value = props.scopeCampusId || ''
  formTermId.value = ''
  formTeacherIds.value = []
  formClassroomId.value = ''
  formWeekFrequency.value = 'every_week'
  formStartDate.value = ''
  formEndDate.value = ''
  formSkipDates.value = []
  formRemark.value = ''
  formSlots.value = []
  const clazz = props.classes.find((item) => item.id === props.scopeClassId)
  if (clazz) {
    formCampusId.value = clazz.campusId || formCampusId.value
    formTermId.value = clazz.termId || ''
  }
}

function handleClassChange() {
  const clazz = props.classes.find((item) => item.id === formClassId.value)
  formCampusId.value = clazz?.campusId || props.scopeCampusId || ''
  formTermId.value = clazz?.termId || ''
  // 换班级后原勾选的时段可能不属于新校区，清空重选最稳妥
  formSlots.value = []
  formClassroomId.value = ''
  formTeacherIds.value = []
}

function buildPayload(): SchedulePlanPayload | null {
  if (!props.institutionId) {
    ElMessage.warning('请先选择机构')
    return null
  }
  if (!formClassId.value) {
    ElMessage.warning('请选择班级')
    return null
  }
  if (!formTermId.value) {
    ElMessage.warning('班级未设置学期，请先在班级管理中补充学期')
    return null
  }
  if (formTeacherIds.value.length === 0) {
    ElMessage.warning('请至少选择一名授课教师')
    return null
  }
  if (!formClassroomId.value) {
    ElMessage.warning('请选择教室')
    return null
  }
  if (formSlots.value.length === 0) {
    ElMessage.warning('请在节次×星期网格上勾选每周模式')
    return null
  }
  if (!formStartDate.value || !formEndDate.value) {
    ElMessage.warning('请选择起止日期')
    return null
  }
  if (formStartDate.value > formEndDate.value) {
    ElMessage.warning('起始日期不能晚于截止日期')
    return null
  }
  return {
    institutionId: props.institutionId,
    campusId: formCampusId.value,
    termId: formTermId.value,
    classId: formClassId.value,
    classroomId: formClassroomId.value,
    teacherIds: [...formTeacherIds.value],
    slots: [...formSlots.value],
    weekFrequency: formWeekFrequency.value,
    startDate: formStartDate.value,
    endDate: formEndDate.value,
    skipDates: formSkipDates.value.length > 0 ? [...formSkipDates.value] : undefined,
    remark: formRemark.value || undefined,
  }
}

async function handlePreview() {
  const payload = buildPayload()
  if (!payload) {
    return
  }
  previewLoading.value = true
  try {
    const result = await previewSchedulePlan(payload)
    ruleNotices.value = result.ruleNotices || []
    previewItems.value = result.items.map((item) => ({
      ...item,
      key: `${item.lessonDate}#${item.timeSlotId}`,
    }))
    checkedKeys.value = new Set(
      previewItems.value.filter((item) => item.suggestedChecked).map((item) => item.key)
    )
    step.value = 1
  } catch (error) {
    ElMessage.error(toMessage(error, '展开预览失败'))
  } finally {
    previewLoading.value = false
  }
}

function toggleRow(key: string, value: boolean) {
  const next = new Set(checkedKeys.value)
  if (value) {
    next.add(key)
  } else {
    next.delete(key)
  }
  checkedKeys.value = next
}

function toggleAll(value: boolean | string | number | undefined) {
  // 冲突/跳过行不可勾选，全选只覆盖可生成行
  checkedKeys.value = value
    ? new Set(selectableItems.value.map((item) => item.key))
    : new Set()
}

async function handleCommit() {
  const payload = buildPayload()
  if (!payload) {
    return
  }
  const lessons = previewItems.value
    .filter((item) => checkedKeys.value.has(item.key))
    .map((item) => ({ lessonDate: item.lessonDate, timeSlotId: item.timeSlotId }))
  if (lessons.length === 0) {
    ElMessage.warning('请至少勾选一个课次')
    return
  }
  committing.value = true
  try {
    const result = await commitSchedulePlan({ planId: props.editPlan?.id || null, plan: payload, lessons })
    const parts = [`已生成 ${result.createdCount} 个课次`]
    if (result.skippedCount > 0) {
      parts.push(`跳过已存在 ${result.skippedCount} 个`)
    }
    emit('saved', parts.join('，'))
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(toMessage(error, '生成课次失败'))
  } finally {
    committing.value = false
  }
}
</script>

<style scoped>
.plan-steps {
  margin-bottom: 18px;
}

.plan-form :deep(.el-select),
.plan-form :deep(.el-input) {
  width: 100%;
}

.plan-form :deep(.el-date-editor.el-input) {
  width: 100%;
}

.plan-grid-wrap {
  width: 100%;
}

.plan-context-text {
  color: var(--text-secondary);
  font-size: 12px;
}

.plan-frequency-hint {
  margin-left: 12px;
}

.plan-date-sep {
  margin: 0 8px;
  color: var(--text-secondary);
}

.plan-preview {
  display: grid;
  gap: 10px;
}

.plan-ok-text {
  color: var(--el-color-success);
  font-size: 12px;
}

.plan-notice-line {
  font-size: 12px;
  line-height: 1.7;
}
</style>
