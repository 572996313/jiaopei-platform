<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:按钮靠左、当前范围回显靠右,筛选行在它下面 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openTimeSlotDialog('create')">新增节次</el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="refreshAll" />
      <el-button @click="goTemplates">时段模板</el-button>
      <div class="education-context-text">
        {{
          campusId
            ? `当前校区：${scopeStore.campusName(campusId)}，另含机构通用节次`
            : '全部校区：显示本机构全部节次'
        }}
      </div>
    </div>

    <!-- 统一筛选栏:校区范围在横幅那颗芯片上选,本页只按它取数;来源只筛已加载的这批,不发请求 -->
    <FilterBar :loading="loading" @query="loadTimeSlots" @reset="resetAndLoad">
      <el-select v-model="sourceFilter" placeholder="筛选来源" clearable filterable>
        <el-option v-if="hasManualSlots" label="手工新增" value="manual" />
        <el-option
          v-for="item in sourceOptions"
          :key="item.id"
          :label="item.label"
          :value="item.id"
        />
      </el-select>
    </FilterBar>

    <article class="panel-card education-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="filteredTimeSlots"
          border
          row-key="id"
          highlight-current-row
          :height="height"
          v-loading="loading"
          :empty-text="
            loading ? '' : sourceFilter ? '当前来源筛选下没有节次。' : '当前范围暂无节次数据。'
          "
        >
          <el-table-column prop="slotCode" label="节次编码" width="150" fixed="left" sortable />
          <el-table-column
            prop="slotName"
            label="节次名称"
            min-width="180"
            show-overflow-tooltip
            sortable
          />
          <el-table-column
            prop="templateId"
            label="来源"
            width="150"
            show-overflow-tooltip
            sortable
            :sort-by="sortBySource"
          >
            <template #default="{ row }">{{ sourceLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="星期" width="90" prop="weekday" sortable>
            <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
          </el-table-column>
          <el-table-column
            label="适用校区"
            width="150"
            prop="campusId"
            sortable
            show-overflow-tooltip
          >
            <template #default="{ row }">{{
              row.campusId ? scopeStore.campusName(row.campusId) : '机构通用'
            }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="180" prop="startTime" sortable>
            <template #default="{ row }"
              >{{ row.startTime || '-' }} - {{ row.endTime || '-' }}</template
            >
          </el-table-column>
          <el-table-column prop="durationMinutes" label="时长" width="100" align="right" sortable />
          <el-table-column label="类型" width="120" prop="slotType" sortable>
            <template #default="{ row }">{{ slotTypeLabel(row.slotType) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120" sortable>
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{
                row.status === 'active' ? '启用' : '停用'
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTimeSlotDialog('edit', row)"
                >编辑</el-button
              >
              <el-button link @click="openRefsDialog(row)"
                >查看引用</el-button
              >
              <el-button link type="danger" @click="confirmDeleteTimeSlot(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

    </article>

    <el-dialog
      v-model="timeSlotDialogVisible"
      :title="timeSlotDialogMode === 'create' ? '新增节次' : '编辑节次'"
      width="560px"
    >
      <el-alert
        v-if="timeSlotFormError"
        :title="timeSlotFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="timeSlotFormRef"
        :model="timeSlotForm"
        :rules="timeSlotRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="适用校区">
          <el-select v-model="timeSlotForm.campusId" placeholder="选择校区" clearable filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节次编码" prop="slotCode">
          <CodeInput
            v-model="timeSlotForm.slotCode"
            resource="time_slot"
            :mode="timeSlotDialogMode"
          />
        </el-form-item>
        <el-form-item label="节次名称" prop="slotName"
          ><el-input v-model="timeSlotForm.slotName"
        /></el-form-item>
        <el-form-item label="节次类型">
          <DictSelect
            v-model="timeSlotForm.slotType"
            type-code="slot_type"
            placeholder="选择类型"
          />
        </el-form-item>
        <el-form-item label="星期">
          <el-select v-model="timeSlotForm.weekday">
            <el-option
              v-for="day in WEEKDAY_OPTIONS"
              :key="day.value"
              :label="day.label"
              :value="day.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime"
          ><el-time-picker
            v-model="timeSlotForm.startTime"
            value-format="HH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="结束时间" prop="endTime"
          ><el-time-picker
            v-model="timeSlotForm.endTime"
            value-format="HH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="时长（分钟）">
          <el-input-number
            v-model="timeSlotForm.durationMinutes"
            :min="1"
            disabled
            style="width: 100%"
          />
          <div class="education-context-text">由开始和结束时间自动计算</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="timeSlotForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeSlotDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="timeSlotSubmitting" @click="submitTimeSlot">{{
          timeSlotDialogMode === 'create' ? '提交节次' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>

    <!-- 引用弹窗：节次删不掉时"是谁在挡"只有这里答得出来，尤其是课表页根本不显示的已删除课次 -->
    <el-dialog v-model="refsDialogVisible" :title="refsDialogTitle" width="760px">
      <el-alert
        v-if="refsError"
        :title="refsError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-table
        :data="refs"
        border
        max-height="330"
        v-loading="refsLoading"
        :empty-text="refsLoading ? '' : '没有课次引用这个节次，可以直接删除。'"
      >
        <el-table-column label="上课日期" width="120" prop="lessonDate" />
        <el-table-column label="星期" width="90">
          <template #default="{ row }">{{ weekdayLabel(row.lessonWeekday) }}</template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }"
            >{{ row.startTime || '-' }} - {{ row.endTime || '-' }}</template
          >
        </el-table-column>
        <el-table-column label="班级" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{
            row.className || `班级 #${row.classId ?? '-'}`
          }}</template>
        </el-table-column>
        <el-table-column label="课次状态" width="130">
          <template #default="{ row }">
            <el-tag :type="row.deleted ? 'info' : 'warning'">{{
              row.deleted ? '已删除课次' : '在排课中'
            }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!refsLoading && refs.length > 0" class="education-context-text">
        {{
          liveRefCount > 0
            ? `在排课中的 ${liveRefCount} 节要请到排课或课表与点名页处理${
                tombstoneCount > 0 ? `；另有 ${tombstoneCount} 节已删除的课次可以清理` : ''
              }。`
            : `全部 ${tombstoneCount} 节都是已删除的课次，清理后这个节次就能删除。`
        }}
      </div>
      <template #footer>
        <el-button @click="refsDialogVisible = false">关闭</el-button>
        <el-button
          v-if="canWriteSchedule && tombstoneCount > 0"
          type="warning"
          :loading="purging"
          @click="purgeTombstones"
          >清理已删除课次（{{ tombstoneCount }}）</el-button
        >
        <el-button
          v-if="canWriteOrganization && refs.length === 0 && !refsLoading"
          type="primary"
          @click="deleteReferencedSlot"
          >删除该节次</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import CodeInput from '@cornerstone/core/components/CodeInput.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  createTimeSlot,
  deleteTimeSlot,
  fetchTimeSlotLessonRefs,
  fetchTimeSlotTemplates,
  fetchTimeSlots,
  purgeTimeSlotLessonTombstones,
  updateTimeSlot,
  type TimeSlotLessonRef,
  type TimeSlotSummary,
  type TimeSlotTemplateSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { WEEKDAY_OPTIONS } from './components/time-slot-utils'

const router = useRouter()
const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const dictStore = useDictStore()
const auth = useAuthStore()

/** 清理墓碑归排课域，按写权限显隐；查看引用课次已改为登录即可，不再按读码隐藏 */
const canWriteSchedule = computed(() => auth.hasPermission('education:schedule:write'))
const canWriteOrganization = computed(() => auth.hasPermission('organization:write'))

const institutionId = computed(() => institutionStore.currentInstitutionId)
/** 校区范围全站只有一份：横幅那顆芯片改，本页只读；模板页跳过来时停在同一个范围 */
const { campusId, campuses, prefillCampusId } = storeToRefs(scopeStore)

const templates = ref<TimeSlotTemplateSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])
const loading = ref(false)
const pageError = ref('')
/** 来源筛选只作用于已加载的这批节次，'' = 全部，'manual' = 手工新增，其余为模板 id */
const sourceFilter = ref('')

/** 来源列与筛选下拉都只认这份映射，模板被删掉时回显 模板 #id，不把脏数据吞成空白 */
function templateNameOf(id: string) {
  return templates.value.find((item) => String(item.id) === id)?.templateName || `模板 #${id}`
}

function sourceLabel(row: TimeSlotSummary) {
  return row.templateId ? templateNameOf(String(row.templateId)) : '手工新增'
}

function sortBySource(row: TimeSlotSummary) {
  return sourceLabel(row)
}

/** 下拉只列当前这批节次里真实出现过的来源，模板被删空、全是模板生成时都不多给一个空选项 */
const hasManualSlots = computed(() => timeSlots.value.some((item) => !item.templateId))

const sourceOptions = computed(() => {
  const ids = new Set(
    timeSlots.value.map((item) => (item.templateId ? String(item.templateId) : '')).filter(Boolean)
  )
  return [...ids]
    .map((id) => ({ id, label: templateNameOf(id) }))
    .sort((a, b) => a.label.localeCompare(b.label))
})

const filteredTimeSlots = computed(() => {
  if (!sourceFilter.value) {
    return timeSlots.value
  }
  if (sourceFilter.value === 'manual') {
    return timeSlots.value.filter((item) => !item.templateId)
  }
  return timeSlots.value.filter((item) => String(item.templateId) === sourceFilter.value)
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimOrUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function weekdayLabel(value?: number | null) {
  return WEEKDAY_OPTIONS.find((item) => item.value === value)?.label || '-'
}

function slotTypeLabel(value?: string | null) {
  return dictStore.displayLabel('slot_type', value)
}

function goTemplates() {
  router.push('/time-slots')
}

/** 只重拉节次（换校区、保存后）:按当前校区取本校区 + 机构通用；模板名一并刷新，新建的模板当天就能在来源列认出来 */
async function loadTimeSlots() {
  if (!institutionId.value) {
    timeSlots.value = []
    return
  }
  loading.value = true
  pageError.value = ''
  try {
    const [slotList, templateList] = await Promise.all([
      fetchTimeSlots(institutionId.value, campusId.value),
      fetchTimeSlotTemplates(institutionId.value),
    ])
    timeSlots.value = slotList
    templates.value = templateList
  } catch (error) {
    timeSlots.value = []
    pageError.value = toMessage(error, '加载节次失败')
  } finally {
    loading.value = false
  }
}

/** 重置 = 取消来源筛选（校区范围是全站的，不归本页改） */
function resetAndLoad() {
  sourceFilter.value = ''
  void loadTimeSlots()
}

async function refreshAll() {
  if (!institutionId.value) {
    await institutionStore.loadInstitutions()
  }
  if (!institutionId.value) {
    pageError.value = '未获取到机构范围,请先在顶部选择机构'
    return
  }
  loading.value = true
  try {
    const [, templateList] = await Promise.all([
      scopeStore.loadCampuses(institutionId.value),
      fetchTimeSlotTemplates(institutionId.value),
    ])
    templates.value = templateList
    timeSlots.value = await fetchTimeSlots(institutionId.value, campusId.value)
  } catch (error) {
    timeSlots.value = []
    pageError.value = toMessage(error, '加载节次失败')
  } finally {
    loading.value = false
  }
}

// ---- 节次弹窗 ----
const timeSlotDialogVisible = ref(false)
const timeSlotDialogMode = ref<'create' | 'edit'>('create')
const timeSlotSubmitting = ref(false)
const timeSlotFormError = ref('')
const editingTimeSlotId = ref<string | null>(null)

const timeSlotFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
function requiredText(label: string): FormRules[string] {
  return [{ required: true, whitespace: true, message: `请填写${label}`, trigger: 'blur' }]
}

const timeSlotRules: FormRules = {
  slotCode: requiredText('节次编码'),
  slotName: requiredText('节次名称'),
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      // 起止时间是跨字段关系：改开始时间时也要重新校验结束时间那一格
      validator: (_rule, value: string, callback) => {
        if (
          value &&
          timeSlotForm.startTime &&
          timeSlotMinutes(value) <= timeSlotMinutes(timeSlotForm.startTime)
        ) {
          callback(new Error('结束时间必须晚于开始时间'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

const timeSlotForm = reactive({
  campusId: '' as string | null,
  slotCode: '',
  slotName: '',
  slotType: '',
  weekday: 1,
  startTime: '08:00:00',
  endTime: '09:30:00',
  durationMinutes: 90,
  status: 'active',
})

function timeSlotMinutes(value?: string | null) {
  if (!value) {
    return Number.NaN
  }
  const [hours, minutes] = value.split(':').map(Number)
  return (hours || 0) * 60 + (minutes || 0)
}

// 后端要求 durationMinutes 与起止时间差值完全一致，这里随时间选择自动重算
watch(
  () => [timeSlotForm.startTime, timeSlotForm.endTime] as const,
  ([start, end]) => {
    const diff = timeSlotMinutes(end) - timeSlotMinutes(start)
    if (Number.isFinite(diff) && diff > 0) {
      timeSlotForm.durationMinutes = diff
    }
    // 开始时间变化后结束时间那一格的红框要立刻跟上，不用等再次提交；
    // 弹窗没开（如 edit 回填）时不触发，避免和 clearValidate 抢时序闪出红框
    if (timeSlotDialogVisible.value && end) {
      void timeSlotFormRef.value?.validateField('endTime').catch(() => undefined)
    }
  }
)

function openTimeSlotDialog(mode: 'create' | 'edit', row?: TimeSlotSummary) {
  timeSlotDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingTimeSlotId.value = row.id
    timeSlotForm.campusId = row.campusId ?? null
    timeSlotForm.slotCode = row.slotCode ?? ''
    timeSlotForm.slotName = row.slotName ?? ''
    timeSlotForm.slotType = row.slotType ?? ''
    timeSlotForm.weekday = row.weekday ?? 1
    timeSlotForm.startTime = row.startTime ?? '08:00:00'
    timeSlotForm.endTime = row.endTime ?? '09:30:00'
    timeSlotForm.durationMinutes = row.durationMinutes ?? 90
    timeSlotForm.status = row.status ?? 'active'
  } else {
    editingTimeSlotId.value = null
    timeSlotForm.campusId = prefillCampusId.value
    timeSlotForm.slotCode = ''
    timeSlotForm.slotName = ''
    timeSlotForm.slotType = ''
    timeSlotForm.weekday = 1
    timeSlotForm.startTime = '08:00:00'
    timeSlotForm.endTime = '09:30:00'
    timeSlotForm.durationMinutes = 90
    timeSlotForm.status = 'active'
  }
  timeSlotFormError.value = ''
  timeSlotDialogVisible.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => timeSlotFormRef.value?.clearValidate())
}

async function submitTimeSlot() {
  if (!institutionId.value) {
    timeSlotFormError.value = '请先选择机构范围'
    return
  }
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await timeSlotFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }

  timeSlotSubmitting.value = true
  timeSlotFormError.value = ''
  try {
    if (timeSlotDialogMode.value === 'create') {
      await createTimeSlot({
        institutionId: institutionId.value,
        campusId: timeSlotForm.campusId || null,
        slotCode: timeSlotForm.slotCode.trim(),
        slotName: timeSlotForm.slotName.trim(),
        weekday: timeSlotForm.weekday,
        startTime: timeSlotForm.startTime,
        endTime: timeSlotForm.endTime,
        durationMinutes: timeSlotForm.durationMinutes,
        slotType: trimOrUndefined(timeSlotForm.slotType),
        status: timeSlotForm.status,
      })
      ElMessage.success('节次已新增')
    } else if (editingTimeSlotId.value) {
      await updateTimeSlot(editingTimeSlotId.value, {
        campusId: timeSlotForm.campusId || null,
        slotCode: timeSlotForm.slotCode.trim(),
        slotName: timeSlotForm.slotName.trim(),
        weekday: timeSlotForm.weekday,
        startTime: timeSlotForm.startTime,
        endTime: timeSlotForm.endTime,
        durationMinutes: timeSlotForm.durationMinutes,
        slotType: trimOrUndefined(timeSlotForm.slotType),
        status: timeSlotForm.status,
      })
      ElMessage.success('节次已更新')
    }
    timeSlotDialogVisible.value = false
    // 范围已收窄到某个校区、保存的却是另一个校区时把范围跟过去，否则刚保存的那条被挡在列表外
    // 改成「机构通用」不用切：后端读本校区时一并返回 campus_id 为空的节次
    if (campusId.value && timeSlotForm.campusId && campusId.value !== timeSlotForm.campusId) {
      scopeStore.setCampus(timeSlotForm.campusId)
    }
    await loadTimeSlots()
  } catch (error) {
    timeSlotFormError.value = toMessage(
      error,
      timeSlotDialogMode.value === 'create' ? '新增节次失败' : '更新节次失败'
    )
  } finally {
    timeSlotSubmitting.value = false
  }
}

async function confirmDeleteTimeSlot(row: TimeSlotSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除节次「${row.slotName || row.slotCode || row.id}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteTimeSlot(row.id)
    ElMessage.success('节次已删除')
    await loadTimeSlots()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除节次失败'))
  }
}

// ---- 查看引用 ----
const refsDialogVisible = ref(false)
const refsLoading = ref(false)
const refsError = ref('')
const refs = ref<TimeSlotLessonRef[]>([])
const refsSlot = ref<TimeSlotSummary | null>(null)
const purging = ref(false)

const liveRefCount = computed(() => refs.value.filter((item) => !item.deleted).length)
const tombstoneCount = computed(() => refs.value.length - liveRefCount.value)
const refsDialogTitle = computed(
  () => `引用「${refsSlot.value?.slotName || refsSlot.value?.slotCode || ''}」的课次`
)

async function openRefsDialog(row: TimeSlotSummary) {
  refsSlot.value = row
  refs.value = []
  refsError.value = ''
  refsDialogVisible.value = true
  await loadRefs()
}

async function loadRefs() {
  const slot = refsSlot.value
  if (!slot) {
    return
  }
  refsLoading.value = true
  refsError.value = ''
  try {
    refs.value = await fetchTimeSlotLessonRefs(String(slot.id))
  } catch (error) {
    refs.value = []
    refsError.value = toMessage(error, '加载引用课次失败')
  } finally {
    refsLoading.value = false
  }
}

async function purgeTombstones() {
  const slot = refsSlot.value
  if (!slot) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `会物理删除 ${tombstoneCount.value} 个已删除课次及其点名记录，删掉后无法恢复。`,
      '确认清理',
      { type: 'warning', confirmButtonText: '清理', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  purging.value = true
  refsError.value = ''
  try {
    const result = await purgeTimeSlotLessonTombstones(String(slot.id))
    ElMessage.success(
      `已清理 ${result.purgedCount} 个已删除课次` +
        (result.skippedCount > 0
          ? `，${result.skippedCount} 个仍有未删除的补课课次指着、已跳过`
          : '')
    )
    await loadRefs()
  } catch (error) {
    refsError.value = toMessage(error, '清理已删除课次失败')
  } finally {
    purging.value = false
  }
}

/** 清干净之后顺手把这节删掉，省得回列表再找一次 */
function deleteReferencedSlot() {
  const slot = refsSlot.value
  if (!slot) {
    return
  }
  refsDialogVisible.value = false
  void confirmDeleteTimeSlot(slot)
}

watch(institutionId, async () => {
  sourceFilter.value = ''
  await refreshAll()
})

onMounted(() => {
  // 节次类型列的回显靠字典（表单里的 DictSelect 自己会拉，表格列不会）
  void dictStore.ensureLoaded(['slot_type']).catch(() => undefined)
  refreshAll()
})
</script>
