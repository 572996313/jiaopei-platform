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

    <!-- 硬冲突（红色）与软提示（黄色）分块展示：提示不拦保存，只有冲突禁保存 -->
    <el-alert
      v-if="conflictResult?.conflict"
      :title="
        isExecutionMode ? '执行变更冲突未通过，请调整后再保存' : '冲突检查未通过，请调整后再保存'
      "
      type="error"
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
      v-if="pendingNotices.length > 0"
      type="warning"
      show-icon
      :closable="false"
      title="以下提示不阻止保存："
      class="schedule-dialog-alert"
    >
      <div v-for="(notice, index) in pendingNotices" :key="`${notice}-${index}`" class="schedule-notice-line">
        {{ notice }}
      </div>
    </el-alert>

    <el-alert
      v-if="statusLockedHint"
      :title="statusLockedHint"
      type="info"
      show-icon
      :closable="false"
      class="schedule-dialog-alert"
    />

    <el-form
      ref="lessonFormRef"
      :model="form"
      :rules="lessonRules"
      v-loading="detailLoading"
      label-position="top"
      class="education-form"
      scroll-to-error
      @submit.prevent
    >
      <div class="schedule-form-grid">
        <el-form-item label="机构">
          <el-select v-model="form.institutionId" disabled>
            <el-option
              v-for="item in institutions"
              :key="item.id"
              :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="校区" prop="campusId">
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
        <el-form-item label="学期" prop="termId">
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
        <el-form-item label="班级" prop="classId">
          <ClassSelect
            v-model="form.classId"
            :institution-id="form.institutionId"
            :campus-id="form.campusId"
            :disabled="lockScopeFields"
          />
        </el-form-item>
        <el-form-item label="教室" prop="classroomId">
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
        <el-form-item label="上课日期" prop="lessonDate">
          <el-date-picker
            v-model="form.lessonDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择上课日期"
            style="width: 100%"
            :disabled="lockLessonFields"
          />
        </el-form-item>
        <el-form-item label="节次" prop="timeSlotId">
          <el-select
            v-model="form.timeSlotId"
            :placeholder="slotPlaceholder"
            filterable
            :disabled="lockLessonFields || !form.campusId"
            no-data-text="该日期所在星期在本校区没有节次，请调整日期或到「时间段管理」维护"
            style="width: 100%"
          >
            <el-option-group
              v-for="group in slotGroups"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="item in group.options"
                :key="item.slot.id"
                :label="slotOptionLabel(item)"
                :value="item.slot.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="排课状态">
          <el-select v-model="form.lessonStatus" disabled>
            <el-option :label="lessonStatusLabel(form.lessonStatus)" :value="form.lessonStatus" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'makeup'" label="关联停课原课" prop="relatedLessonId">
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

      <el-form-item label="授课教师（可多选）" prop="teacherIds">
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

      <el-form-item label="备注" prop="remark">
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
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="Boolean(conflictResult?.conflict)"
        @click="submit"
      >
        {{ submitButtonText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
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
} from '@cornerstone/module-education/api/education'
import {
  fetchClassrooms,
  type CampusSummary,
  type ClassroomSummary,
  type TermSummary,
  type TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import type { InstitutionSummary } from '@cornerstone/core/api/tenant'
import ClassSelect from '../components/ClassSelect.vue'
import {
  compactTimeRange,
  conflictTypeLabel,
  lessonStatusLabel,
  normalizeTeacherIds,
  TIME_SLOT_SECTION_ORDER,
  timeSlotSectionOf,
  timeSlotShortName,
  todayValue,
  weekdayFromDate,
  weekdayLabel,
} from '@cornerstone/module-education/utils/schedule'
import { toMessage } from '@cornerstone/core/utils/error'
import { trimToUndefined } from '@cornerstone/core/utils/text'

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
const lessonFormRef = ref<FormInstance | null>(null)
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

interface SlotOption {
  slot: TimeSlotSummary
  note: string
}

/**
 * 可排节次：后端要求「课次日期的星期 = 节次的星期」且节次属于同校区，
 * 所以候选只列本校区 + 上课日期那一天的节次（机构级 700+ 条收成一天 10 条）。
 */
const usableSlots = computed(() => {
  const weekday = weekdayFromDate(form.lessonDate)
  const campus = form.campusId
  return props.timeSlots.filter((slot) => {
    if (!slot.id || (slot.status && slot.status !== 'active')) {
      return false
    }
    if (campus && slot.campusId && slot.campusId !== campus) {
      return false
    }
    return !weekday || slot.weekday === weekday
  })
})

// 选中项落在候选外（改了日期、节次被停用）时仍列出来并写明原因，否则下拉会显示裸 id
const slotOptions = computed<SlotOption[]>(() => {
  const options: SlotOption[] = usableSlots.value.map((slot) => ({ slot, note: '' }))
  const selectedId = form.timeSlotId
  if (!selectedId || options.some((item) => item.slot.id === selectedId)) {
    return options
  }
  const selected = props.timeSlots.find((slot) => slot.id === selectedId)
  if (selected) {
    options.push({
      slot: selected,
      note: selected.status && selected.status !== 'active' ? '已停用' : '与上课日期的星期不同',
    })
  }
  return options
})

const slotGroups = computed(() => {
  const groups = new Map<string, SlotOption[]>()
  for (const option of slotOptions.value) {
    const label = timeSlotSectionOf(option.slot)
    const bucket = groups.get(label)
    if (bucket) {
      bucket.push(option)
    } else {
      groups.set(label, [option])
    }
  }
  const orderOf = (section: string) => {
    const index = TIME_SLOT_SECTION_ORDER.indexOf(section)
    return index === -1 ? TIME_SLOT_SECTION_ORDER.length : index
  }
  return [...groups.entries()]
    .sort((left, right) => orderOf(left[0]) - orderOf(right[0]))
    .map(([label, options]) => ({
      label,
      options: options.sort((a, b) =>
        (a.slot.startTime || '').localeCompare(b.slot.startTime || '')
      ),
    }))
})

function slotOptionLabel(option: SlotOption) {
  const name = timeSlotShortName(option.slot.slotName) || '未命名节次'
  const range = compactTimeRange(option.slot.startTime, option.slot.endTime)
  const base = [name, range].filter(Boolean).join(' ')
  return option.note ? `${base}（${option.note}）` : base
}

/** 节次候选跟着上课日期的星期走，placeholder 里点明白，否则操作员不知道列表为什么只剩一天的量 */
const slotPlaceholder = computed(() => {
  if (!form.campusId) {
    return '请先选择校区'
  }
  const week = weekdayLabel(weekdayFromDate(form.lessonDate))
  return week === '未知' ? '选择节次' : `选择${week}的节次`
})

// 日期或校区变了，旧节次多半已不适用：当场清掉，别把必然被后端拒的组合留到保存那一步
watch(
  () => [form.lessonDate, form.campusId],
  () => {
    if (form.timeSlotId && !usableSlots.value.some((slot) => slot.id === form.timeSlotId)) {
      form.timeSlotId = ''
    }
  }
)

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

// 软提示先展示、再放行：第一次提交只把 notices 摆出来，按钮不禁用，再点一次即保存
const noticesAcknowledged = ref(false)
const pendingNotices = computed(() => conflictResult.value?.notices || [])
const submitButtonText = computed(() => {
  // 提示已知晓：所有模式统一把按钮文案改成「继续保存」，明确第二下就落库
  if (noticesAcknowledged.value && pendingNotices.value.length > 0 && !conflictResult.value?.conflict) {
    return '已知晓提示，继续保存'
  }
  if (isExecutionMode.value) {
    return '保存变更'
  }
  return submittingText.value
})

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

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案。
    教室/节次/日期/教师在停课模式锁定后不再校验；备注只在执行变更（调课/停课/补课）时必填。 */
const lessonRules = computed<FormRules>(() => ({
  campusId: [{ required: true, message: '缺少校区信息', trigger: 'change' }],
  termId: [{ required: true, message: '缺少学期信息', trigger: 'change' }],
  classId: [{ required: true, message: '请选择班级', trigger: 'change' }],
  classroomId: lockLessonFields.value ? [] : [{ required: true, message: '请选择教室', trigger: 'change' }],
  lessonDate: lockLessonFields.value
    ? []
    : [{ required: true, message: '请选择上课日期', trigger: 'change' }],
  timeSlotId: lockLessonFields.value ? [] : [{ required: true, message: '请选择时间段', trigger: 'change' }],
  teacherIds: lockLessonFields.value
    ? []
    : [{ required: true, type: 'array', message: '请至少选择一位教师', trigger: 'change' }],
  remark: isExecutionMode.value
    ? [{ required: true, whitespace: true, message: '请填写执行变更备注', trigger: 'blur' }]
    : [],
  relatedLessonId:
    props.mode === 'makeup'
      ? [
          { required: true, message: '请选择关联的停课原课', trigger: 'change' },
          {
            // 补课改挂时不能选到当前课次本身
            validator: (_rule, value: string, callback) => {
              if (!makeupAsCreate.value && value && value === editingLessonId.value) {
                callback(new Error('关联的原课次不能是当前课次本身'))
                return
              }
              callback()
            },
            trigger: 'change',
          },
        ]
      : [],
}))

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
  noticesAcknowledged.value = false
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
  noticesAcknowledged.value = false
  formError.value = ''
}

// 改动参与冲突判定的任一字段后，上一次检查结果作废：提示块收起，需要重新点提交
watch(
  () =>
    [
      form.campusId,
      form.termId,
      form.classId,
      form.classroomId,
      form.timeSlotId,
      form.lessonDate,
      form.teacherIds.join(','),
    ].join('|'),
  () => {
    conflictResult.value = null
    noticesAcknowledged.value = false
  }
)

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
      // 上一次弹窗留下的红框不能带给新一次填写
      void nextTick(() => lessonFormRef.value?.clearValidate())
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
    // 上一次弹窗留下的红框不能带给新一次填写
    void nextTick(() => lessonFormRef.value?.clearValidate())
  }
)

function validate() {
  if (!form.institutionId) {
    return '缺少机构信息'
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
  // 软提示不拦保存，但要先让操作员看见：第一次提交只展示 notices，再点即继续
  if ((conflictResult.value.notices || []).length > 0 && !noticesAcknowledged.value) {
    noticesAcknowledged.value = true
    return false
  }
  return true
}

async function submit() {
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await lessonFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
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
  background: var(--soft-danger-bg);
  color: var(--soft-danger-ink);
}

.schedule-conflict-item + .schedule-conflict-item {
  margin-top: 6px;
}

.schedule-notice-line {
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .schedule-form-grid,
  .schedule-dialog-context {
    grid-template-columns: 1fr;
  }
}
</style>
