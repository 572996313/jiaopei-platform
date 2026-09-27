<template>
  <el-dialog
    :model-value="modelValue"
    :title="editRule ? '调整排课规则' : '新增排课规则'"
    width="960px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <div class="rule-form" v-loading="lookupLoading">
      <el-form label-width="90px">
        <el-form-item label="规则类型" required>
          <el-radio-group v-model="formRuleType">
            <el-radio-button label="forbidden">禁排</el-radio-button>
            <el-radio-button label="required">必排</el-radio-button>
          </el-radio-group>
          <span class="rule-context-text">{{ ruleTypeHint }}</span>
        </el-form-item>
        <el-form-item label="规则对象" required>
          <el-radio-group v-model="formTargetType" @change="handleTargetTypeChange">
            <el-radio-button label="class">班级</el-radio-button>
            <el-radio-button label="teacher">教师</el-radio-button>
            <el-radio-button label="classroom">教室</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="targetObjectLabel" required>
          <ClassSelect
            v-if="formTargetType === 'class'"
            v-model="formTargetId"
            :institution-id="institutionId"
            :campus-id="scopeCampusId"
            placeholder="选择要约束的班级"
            @change="handleTargetChange"
          />
          <el-select
            v-else-if="formTargetType === 'teacher'"
            v-model="formTargetId"
            placeholder="选择要约束的教师"
            filterable
            @change="handleTargetChange"
          >
            <el-option
              v-for="item in teacherOptions"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-else
            v-model="formTargetId"
            placeholder="选择要约束的教室"
            filterable
            @change="handleTargetChange"
          >
            <el-option
              v-for="item in scopedClassroomOptions"
              :key="item.id"
              :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="校区">
          <span class="rule-context-text">
            {{ formatCampus(targetCampusId) }}（跟随所选对象，节次网格只列出该校区的时段）
          </span>
        </el-form-item>
        <el-form-item label="生效区间">
          <el-date-picker
            v-model="formStartDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="起始日期(空=不限)"
          />
          <span class="rule-date-sep">至</span>
          <el-date-picker
            v-model="formEndDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="截止日期(空=不限)"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formActive" active-text="启用" inactive-text="停用" />
          <span class="rule-context-text">停用后的规则不参与排课校验</span>
        </el-form-item>
        <el-form-item label="批注">
          <el-input
            v-model="formRemark"
            maxlength="500"
            placeholder="选填，排课冲突提示里会带出这句话，例如：周一早班全校检修"
          />
        </el-form-item>
        <el-form-item label="节次格子" required>
          <div class="rule-grid-wrap">
            <SchedulePatternGrid
              v-if="targetCampusId"
              v-model="formSlots"
              :time-slots="scopedTimeSlots"
            />
            <!-- 没定校区就没法收窄节次：整机构的时段混在一格里比节次多更难选 -->
            <span v-else class="rule-context-text">
              先选上面的「约束对象」，节次网格只列出该校区的时间段。
            </span>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
// 排课规则编辑器：先定"约束谁、禁排还是必排"，再在节次×星期网格上勾格子。
// 禁排格子与教室/教师占用同层判定，排课时直接拦下；必排只在计划预览里提示覆盖缺口。
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createScheduleRule,
  updateScheduleRule,
  type SchedulePlanSlotItem,
  type ScheduleRulePayload,
  type ScheduleRuleRecord,
  type TeacherSummary,
} from '@cornerstone/module-education/api/education'
import type { ClassroomSummary, TimeSlotSummary } from '@cornerstone/module-organization/api/organization'
import ClassSelect from './ClassSelect.vue'
import SchedulePatternGrid from './SchedulePatternGrid.vue'
import { ensureClassKnown } from '@cornerstone/module-education/composables/classLabels'
import { toMessage } from './schedule-utils'

const props = defineProps<{
  modelValue: boolean
  institutionId: string | null
  editRule: ScheduleRuleRecord | null
  teachers: TeacherSummary[]
  classrooms: ClassroomSummary[]
  timeSlots: TimeSlotSummary[]
  scopeCampusId?: string | null
  lookupLoading?: boolean
  formatCampus: (id?: string | null) => string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', message: string): void
}>()

const saving = ref(false)
const formRuleType = ref('forbidden')
const formTargetType = ref('class')
const formTargetId = ref<string>('')
const formStartDate = ref<string>('')
const formEndDate = ref<string>('')
const formActive = ref(true)
const formRemark = ref('')
const formSlots = ref<SchedulePlanSlotItem[]>([])

const ruleTypeHint = computed(() =>
  formRuleType.value === 'forbidden'
    ? '勾出的节次一律不许排课'
    : '提示这份计划的每周模式漏掉了哪些必排节次，不拦提交'
)

// 教师候选跟随当前校区收敛；编辑已有规则时所选教师可能不在该校区，
// 直接把它补进下拉，否则回显只剩一串 ID
const teacherOptions = computed(() => {
  const campus = props.scopeCampusId || null
  const scoped = campus
    ? props.teachers.filter((item) => !item.campusId || item.campusId === campus)
    : props.teachers
  const selected = props.teachers.find((item) => item.id === formTargetId.value)
  return selected && !scoped.includes(selected) ? [...scoped, selected] : scoped
})

// 教室候选同样按当前校区收敛（本机构本校区的教室），编辑回显补选逻辑同上
const scopedClassroomOptions = computed(() => {
  const campus = props.scopeCampusId || null
  const scoped = campus
    ? props.classrooms.filter((item) => !item.campusId || item.campusId === campus)
    : props.classrooms
  const selected = props.classrooms.find((item) => item.id === formTargetId.value)
  return selected && !scoped.includes(selected) ? [...scoped, selected] : scoped
})

const targetObjectLabel = computed(() => {
  if (formTargetType.value === 'class') {
    return '班级'
  }
  return formTargetType.value === 'teacher' ? '教师' : '教室'
})

/** 规则对象是班级时，它所属校区要按 id 取（下拉已改成按需搜索，本机没有全量班级） */
const classCampusId = ref<string | null>(null)

watch(
  () => (formTargetType.value === 'class' ? formTargetId.value : ''),
  async (id) => {
    classCampusId.value = null
    const known = await ensureClassKnown(props.institutionId, id || null)
    if (formTargetId.value === id) {
      classCampusId.value = known?.campusId || null
    }
  },
  { immediate: true }
)

const targetCampusId = computed(() => {
  if (formTargetType.value === 'class') {
    return classCampusId.value
  }
  if (formTargetType.value === 'teacher') {
    return props.teachers.find((item) => item.id === formTargetId.value)?.campusId || null
  }
  return props.classrooms.find((item) => item.id === formTargetId.value)?.campusId || null
})

const scopedTimeSlots = computed(() => {
  const campus = targetCampusId.value
  return props.timeSlots.filter((slot) => {
    if (slot.status && slot.status !== 'active') {
      return false
    }
    return !campus || !slot.campusId || slot.campusId === campus
  })
})

function handleOpen() {
  const rule = props.editRule
  if (rule) {
    formRuleType.value = rule.ruleType || 'forbidden'
    formTargetType.value = rule.targetType || 'class'
    formTargetId.value = rule.targetId || ''
    formStartDate.value = rule.startDate || ''
    formEndDate.value = rule.endDate || ''
    formActive.value = rule.status !== 'inactive'
    formRemark.value = rule.remark || ''
    formSlots.value = [...(rule.slots || [])]
    return
  }
  formRuleType.value = 'forbidden'
  formTargetType.value = 'class'
  formTargetId.value = ''
  formStartDate.value = ''
  formEndDate.value = ''
  formActive.value = true
  formRemark.value = ''
  formSlots.value = []
}

function clearTarget() {
  formTargetId.value = ''
  formSlots.value = []
}

function handleTargetTypeChange() {
  clearTarget()
}

function handleTargetChange() {
  // 换对象即换校区，原勾选的节次可能已不在该校区范围内
  formSlots.value = []
}

function buildPayload(): ScheduleRulePayload | null {
  if (!props.institutionId) {
    ElMessage.warning('请先选择机构')
    return null
  }
  if (!formTargetId.value) {
    ElMessage.warning(`请选择${targetObjectLabel.value}`)
    return null
  }
  if (formSlots.value.length === 0) {
    ElMessage.warning('请在节次×星期网格上勾选格子')
    return null
  }
  if (formStartDate.value && formEndDate.value && formStartDate.value > formEndDate.value) {
    ElMessage.warning('起始日期不能晚于截止日期')
    return null
  }
  return {
    institutionId: props.institutionId,
    ruleType: formRuleType.value,
    targetType: formTargetType.value,
    targetId: formTargetId.value,
    slots: [...formSlots.value],
    startDate: formStartDate.value || undefined,
    endDate: formEndDate.value || undefined,
    status: formActive.value ? 'active' : 'inactive',
    remark: formRemark.value || undefined,
  }
}

async function handleSave() {
  const payload = buildPayload()
  if (!payload) {
    return
  }
  saving.value = true
  try {
    const id = props.editRule?.id
    if (id) {
      await updateScheduleRule(id, payload)
    } else {
      await createScheduleRule(payload)
    }
    emit('saved', id ? '排课规则已更新' : '排课规则已新增')
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(toMessage(error, '保存排课规则失败'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.rule-form :deep(.el-select),
.rule-form :deep(.el-input) {
  width: 100%;
}

.rule-form :deep(.el-date-editor.el-input) {
  width: 100%;
}

.rule-grid-wrap {
  width: 100%;
}

.rule-context-text {
  margin-left: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

.rule-date-sep {
  margin: 0 8px;
  color: var(--text-secondary);
}
</style>
