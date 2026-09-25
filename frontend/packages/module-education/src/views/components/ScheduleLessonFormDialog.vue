<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="680px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="targetLesson" class="schedule-dialog-context">
      <div><strong>班级：</strong>{{ formatClass(targetLesson.classId) }}</div>
      <div>
        <strong>原课次：</strong>{{ targetLesson.lessonDate || '未设置日期' }}
        {{ compactTimeRange(targetLesson.startTime, targetLesson.endTime) || '待定时段' }}
      </div>
      <div><strong>当前状态：</strong>{{ lessonStatusLabel(targetLesson.lessonStatus) }}</div>
    </div>

    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      class="schedule-dialog-alert"
    />

    <el-alert
      v-if="conflictResult?.conflict"
      :title="
        isExecutionMode ? '执行变更冲突未通过，请调整后再保存' : '冲突检查未通过，请调整后再保存'
      "
      type="warning"
      show-icon
      :closable="false"
      class="schedule-dialog-alert"
    />

    <div v-if="conflictResult?.conflict" class="schedule-conflict-list">
      <div
        v-for="(message, index) in normalizedConflictMessages"
        :key="`${message}-${index}`"
        class="schedule-conflict-item"
      >
        {{ message }}
      </div>
    </div>

    <el-alert
      v-if="statusLockedHint"
      :title="statusLockedHint"
      type="info"
      show-icon
      :closable="false"
      class="schedule-dialog-alert"
    />

    <el-form v-loading="detailLoading" label-position="top" class="education-form">
      <div class="schedule-form-grid">
        <el-form-item label="机构">
          <el-select v-model="form.institutionId" disabled>
            <el-option
              v-for="item in institutions"
              :key="item.id"
              :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="校区">
          <el-select
            v-model="form.campusId"
            placeholder="选择校区"
            filterable
            :disabled="lockScopeFields"
            @change="handleFormCampusChange"
          >
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select
            v-model="form.termId"
            placeholder="选择学期"
            filterable
            :disabled="lockScopeFields"
          >
            <el-option
              v-for="item in terms"
              :key="item.id"
              :label="item.termName || item.termCode || `学期 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select
            v-model="form.classId"
            placeholder="选择班级"
            filterable
            :disabled="lockScopeFields"
          >
            <el-option
              v-for="item in classes"
              :key="item.id"
              :label="item.className || item.classCode || `班级 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教室">
          <el-select
            v-model="form.classroomId"
            placeholder="选择教室"
            filterable
            :disabled="lockLessonFields"
          >
            <el-option
              v-for="item in classrooms"
              :key="item.id"
              :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段">
          <el-select
            v-model="form.timeSlotId"
            placeholder="选择时间段"
            filterable
            :disabled="lockLessonFields"
          >
            <el-option
              v-for="item in timeSlots"
              :key="item.id"
              :label="
                timeSlotOptionLabel(item.slotName, item.startTime, item.endTime, item.weekday)
              "
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上课日期">
          <el-date-picker
            v-model="form.lessonDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择上课日期"
            style="width: 100%"
            :disabled="lockLessonFields"
          />
        </el-form-item>
        <el-form-item label="排课状态">
          <el-select v-model="form.lessonStatus" disabled>
            <el-option :label="lessonStatusLabel(form.lessonStatus)" :value="form.lessonStatus" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'makeup'" label="关联停课原课" required>
          <el-select
            v-model="form.relatedLessonId"
            filterable
            clearable
            placeholder="选择被补的停课原课"
          >
            <el-option
              v-for="item in cancelledOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="授课教师（可多选）">
        <el-select
          v-model="form.teacherIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          filterable
          placeholder="选择教师"
          :disabled="lockLessonFields"
        >
          <el-option
            v-for="item in teachers"
            :key="item.id"
            :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="课程主题">
        <el-input
          v-model="form.lessonTitle"
          :placeholder="mode === 'makeup' ? '例如 周末补课 / 节假日调课' : '例如 周三数学强化课'"
          maxlength="128"
        />
      </el-form-item>

      <el-form-item label="备注" :required="isExecutionMode">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          :placeholder="remarkPlaceholder"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ isExecutionMode ? '保存变更' : submittingText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  checkScheduleConflicts,
  createScheduleLesson,
  fetchScheduleLesson,
  fetchTeachers,
  updateScheduleLesson,
  type ScheduleConflictCheckResult,
  type ScheduleLessonPayload,
  type ScheduleLessonRecord,
  type TeacherSummary,
  type TeachingClassSummary,
} from '@cornerstone/module-education/api/education'
import {
  fetchClassrooms,
  type CampusSummary,
  type ClassroomSummary,
  type TermSummary,
  type TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import type { InstitutionSummary } from '@cornerstone/core/api/tenant'
import {
  compactTimeRange,
  conflictTypeLabel,
  lessonStatusLabel,
  normalizeTeacherIds,
  timeSlotOptionLabel,
  toMessage,
  todayValue,
  trimToUndefined,
} from './schedule-utils'

/**
 * 排课弹窗统一入口：
 * - create/edit：排课管理页的新建与基本信息编辑（编辑不改状态）；
 * - reschedule/cancel/makeup：执行页的课次变更（调课/停课必改原课次，补课按原课次状态走新增或改挂）。
 */
type DialogMode = 'create' | 'edit' | 'reschedule' | 'cancel' | 'makeup'

const props = defineProps<{
  modelValue: boolean
  mode: DialogMode
  lesson?: ScheduleLessonRecord | null
  /** create 模式的预填（周课表空格点击等） */
  prefill?: Partial<{
    campusId: string
    termId: string
    classId: string
    classroomId: string
    timeSlotId: string
    lessonDate: string
  }> | null
  institutionId: string
  scopeCampusId?: string | null
  scopeTermId?: string | null
  scopeClassId?: string | null
  institutions: InstitutionSummary[]
  campuses: CampusSummary[]
  terms: TermSummary[]
  classes: TeachingClassSummary[]
  timeSlots: TimeSlotSummary[]
  /** makeup 模式可选择的停课原课（id + 展示文案，排除自身） */
  cancelledOptions?: Array<{ id: string; label: string }>
  formatClass: (id?: string | null) => string
  defaultDate?: string | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', message: string): void
}>()

// 弹窗顶部的原课次上下文（仅关联课次的模式有值）
const targetLesson = computed(() => props.lesson ?? null)

const formError = ref('')
const submitting = ref(false)
const detailLoading = ref(false)
const conflictResult = ref<ScheduleConflictCheckResult | null>(null)
const teachers = ref<TeacherSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const editingLessonId = ref<string | null>(null)
/** makeup 且原课次已停课：走“新增补课课次”承接；否则视为把当前课次改挂为补课 */
const makeupAsCreate = ref(false)

const form = reactive({
  institutionId: '',
  campusId: '',
  termId: '',
  classId: '',
  classroomId: '',
  timeSlotId: '',
  lessonDate: todayValue(),
  lessonTitle: '',
  lessonStatus: 'scheduled',
  teacherIds: [] as string[],
  relatedLessonId: '',
  remark: '',
})

const isExecutionMode = computed(
  () => props.mode === 'reschedule' || props.mode === 'cancel' || props.mode === 'makeup'
)

const dialogTitle = computed(() => {
  switch (props.mode) {
    case 'edit':
      return '编辑排课'
    case 'reschedule':
      return '课次调课'
    case 'cancel':
      return '课次停课'
    case 'makeup':
      return makeupAsCreate.value ? '安排补课（新增补课课次）' : '课次补课'
    case 'create':
    default:
      return '新增排课'
  }
})

const submittingText = computed(() =>
  props.mode === 'create' ? '检查冲突并创建' : '检查冲突并保存'
)

const remarkPlaceholder = computed(() => {
  switch (props.mode) {
    case 'cancel':
      return '填写停课原因和后续安排'
    case 'makeup':
      return '填写补课原因、补课来源或执行说明'
    case 'reschedule':
      return '填写调课原因和同步说明'
    default:
      return '补充排课说明'
  }
})

// 执行变更不改班级归属关系；停课不改上课时间要素
const lockScopeFields = computed(() => isExecutionMode.value)
const lockLessonFields = computed(() => props.mode === 'cancel')

const statusLockedHint = computed(() => {
  if (props.mode === 'edit' && form.lessonStatus !== 'scheduled') {
    return `该课次当前状态为「${lessonStatusLabel(form.lessonStatus)}」，编辑仅调整基本信息，保存不会改变状态`
  }
  if (props.mode === 'cancel') {
    return '停课仅登记原因与备注，上课时间要素保持不变'
  }
  if (props.mode === 'makeup' && makeupAsCreate.value) {
    return '将基于该停课课次新增一节补课课次，原停课记录保留并可回查关联'
  }
  return ''
})

const normalizedConflictMessages = computed(() => {
  if (!conflictResult.value?.conflict) {
    return []
  }
  if (conflictResult.value.messages && conflictResult.value.messages.length > 0) {
    return conflictResult.value.messages
  }
  return (conflictResult.value.conflictTypes || []).map((item) => `${conflictTypeLabel(item)}冲突`)
})

async function loadCampusOptions() {
  if (!form.institutionId) {
    teachers.value = []
    classrooms.value = []
    return
  }
  try {
    const [teacherList, classroomList] = await Promise.all([
      fetchTeachers({ institutionId: form.institutionId, campusId: form.campusId || null }),
      fetchClassrooms(form.campusId || null),
    ])
    teachers.value = teacherList
    classrooms.value = classroomList
  } catch (error) {
    ElMessage.warning(toMessage(error, '按校区刷新教师与教室选项失败'))
  }
}

async function handleFormCampusChange() {
  form.classroomId = ''
  await loadCampusOptions()
}

function resetForm() {
  form.institutionId = props.institutionId || ''
  form.campusId = props.prefill?.campusId || props.scopeCampusId || ''
  form.termId = props.prefill?.termId || props.scopeTermId || ''
  form.classId = props.prefill?.classId || props.scopeClassId || ''
  form.classroomId = props.prefill?.classroomId || ''
  form.timeSlotId = props.prefill?.timeSlotId || ''
  form.lessonDate = props.prefill?.lessonDate || props.defaultDate || todayValue()
  form.lessonTitle = ''
  form.lessonStatus = 'scheduled'
  form.teacherIds = []
  form.relatedLessonId = ''
  form.remark = ''
  conflictResult.value = null
  formError.value = ''
}

function fillFormFromLesson(lesson: ScheduleLessonRecord, status: string) {
  form.institutionId = lesson.institutionId || props.institutionId || ''
  form.campusId = lesson.campusId || ''
  form.termId = lesson.termId || ''
  form.classId = lesson.classId || ''
  form.classroomId = lesson.classroomId || ''
  form.timeSlotId = lesson.timeSlotId || ''
  form.lessonDate = lesson.lessonDate || props.defaultDate || todayValue()
  form.lessonTitle = lesson.lessonTitle || ''
  form.lessonStatus = status
  form.teacherIds = normalizeTeacherIds(lesson.teacherIds)
  form.relatedLessonId = lesson.relatedLessonId || ''
  form.remark = lesson.remark || ''
  conflictResult.value = null
  formError.value = ''
}

watch(
  () => props.modelValue,
  async (visible) => {
    makeupAsCreate.value = false
    if (!visible) {
      return
    }
    editingLessonId.value = props.lesson?.id || null
    const target = props.lesson
    if (!target?.id || props.mode === 'create') {
      resetForm()
      await loadCampusOptions()
      return
    }

    detailLoading.value = true
    try {
      const detail = await fetchScheduleLesson(target.id)
      if (props.mode === 'edit') {
        // 编辑保留原状态，保存时不悄悄改写
        fillFormFromLesson(detail, detail.lessonStatus || 'scheduled')
      } else if (props.mode === 'reschedule') {
        fillFormFromLesson(detail, 'rescheduled')
      } else if (props.mode === 'cancel') {
        fillFormFromLesson(detail, 'cancelled')
      } else if (props.mode === 'makeup') {
        if (detail.lessonStatus === 'cancelled') {
          // 停课原课不能直接变成补课，新增一节补课课次承接，原停课记录保留
          makeupAsCreate.value = true
          fillFormFromLesson(detail, 'makeup')
          form.relatedLessonId = detail.id
          form.lessonDate = props.defaultDate || todayValue()
          form.timeSlotId = detail.timeSlotId || ''
          form.remark = ''
        } else {
          makeupAsCreate.value = false
          fillFormFromLesson(detail, 'makeup')
          form.relatedLessonId = detail.relatedLessonId || ''
        }
      }
      await loadCampusOptions()
    } catch (error) {
      formError.value = toMessage(error, '排课详情加载失败')
    } finally {
      detailLoading.value = false
    }
  }
)

function validate() {
  if (!form.institutionId) {
    return '缺少机构信息'
  }
  if (!form.campusId) {
    return '缺少校区信息'
  }
  if (!form.termId) {
    return '缺少学期信息'
  }
  if (!form.classId) {
    return '请选择班级'
  }
  if (isExecutionMode.value && !form.remark.trim()) {
    return '请填写执行变更备注'
  }
  if (props.mode === 'cancel') {
    return ''
  }
  if (!form.classroomId) {
    return '请选择教室'
  }
  if (!form.timeSlotId) {
    return '请选择时间段'
  }
  if (!form.lessonDate) {
    return '请选择上课日期'
  }
  if (form.teacherIds.length === 0) {
    return '请至少选择一位教师'
  }
  if (props.mode === 'makeup') {
    if (!form.relatedLessonId) {
      return '请选择关联的停课原课'
    }
    if (!makeupAsCreate.value && form.relatedLessonId === editingLessonId.value) {
      return '关联的原课次不能是当前课次本身'
    }
  }
  return ''
}

function buildPayload(): ScheduleLessonPayload {
  return {
    institutionId: form.institutionId,
    campusId: form.campusId,
    termId: form.termId,
    classId: form.classId,
    classroomId: form.classroomId,
    timeSlotId: form.timeSlotId,
    lessonDate: form.lessonDate,
    lessonTitle: trimToUndefined(form.lessonTitle),
    lessonStatus: form.lessonStatus,
    changeType: props.mode === 'makeup' ? 'cancelled_makeup' : null,
    relatedLessonId: props.mode === 'makeup' ? form.relatedLessonId : null,
    teacherIds: normalizeTeacherIds(form.teacherIds),
    remark: trimToUndefined(form.remark),
  }
}

async function runConflictCheck() {
  const payload = buildPayload()
  conflictResult.value = await checkScheduleConflicts({
    excludeLessonId: editingLessonId.value || undefined,
    institutionId: payload.institutionId,
    campusId: payload.campusId,
    termId: payload.termId,
    classId: payload.classId,
    classroomId: payload.classroomId,
    timeSlotId: payload.timeSlotId,
    lessonDate: payload.lessonDate,
    teacherIds: payload.teacherIds,
  })

  if (conflictResult.value.conflict) {
    formError.value = isExecutionMode.value
      ? '存在执行冲突，请调整后重试'
      : '存在排课冲突，请调整后重试'
    return false
  }
  return true
}

async function submit() {
  const validationError = validate()
  if (validationError) {
    formError.value = validationError
    return
  }
  if (isExecutionMode.value && props.mode !== 'makeup' && !editingLessonId.value) {
    formError.value = '缺少课次信息'
    return
  }

  submitting.value = true
  formError.value = ''
  conflictResult.value = null
  try {
    if (props.mode === 'cancel') {
      // 停课不改时间要素，无需冲突检查
      await updateScheduleLesson(editingLessonId.value!, buildPayload())
      emit('saved', '课次停课已保存')
    } else {
      const passed = await runConflictCheck()
      if (!passed) {
        return
      }
      const payload = buildPayload()
      const isCreate = props.mode === 'create' || (props.mode === 'makeup' && makeupAsCreate.value)
      if (isCreate) {
        await createScheduleLesson(payload)
        emit('saved', props.mode === 'create' ? '排课已创建' : '补课课次已创建')
      } else if (editingLessonId.value) {
        await updateScheduleLesson(editingLessonId.value, payload)
        emit('saved', props.mode === 'edit' ? '排课已更新' : `${dialogTitle.value}已保存`)
      }
    }
    emit('update:modelValue', false)
  } catch (error) {
    formError.value = toMessage(error, `${dialogTitle.value}失败`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.schedule-dialog-context {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 16px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--subtle-bg);
  color: var(--tint-ink);
}

.schedule-dialog-alert {
  margin-bottom: 12px;
}

.schedule-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.schedule-conflict-list {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--soft-warn-bg);
  color: var(--soft-warn-ink);
}

.schedule-conflict-item + .schedule-conflict-item {
  margin-top: 6px;
}

@media (max-width: 900px) {
  .schedule-form-grid,
  .schedule-dialog-context {
    grid-template-columns: 1fr;
  }
}
</style>
