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
            ? `当前校区：${formatCampus(campusId)}，另含机构通用节次`
            : '未筛选校区：显示本机构全部节次'
        }}
      </div>
    </div>

    <!-- 统一筛选栏:校区选择不再自动触发,点「查询」才按新校区拉节次 -->
    <FilterBar :loading="loading" @query="loadTimeSlots" @reset="resetAndLoad">
      <!-- 不选校区 = 本机构全部节次;选了 = 本校区 + 机构通用（服务端按 campusId 收窄） -->
      <el-select v-model="campusId" placeholder="筛选校区" clearable filterable>
        <el-option
          v-for="item in campuses"
          :key="item.id"
          :label="item.campusName || item.campusCode || `校区 #${item.id}`"
          :value="item.id"
        />
      </el-select>
    </FilterBar>

    <article class="panel-card education-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="timeSlots"
          border
          row-key="id"
          highlight-current-row
          :height="height"
          v-loading="loading"
        >
          <el-table-column prop="slotCode" label="节次编码" width="150" fixed="left" sortable />
          <el-table-column
            prop="slotName"
            label="节次名称"
            min-width="180"
            show-overflow-tooltip
            sortable
          />
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
              row.campusId ? formatCampus(row.campusId) : '机构通用'
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
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTimeSlotDialog('edit', row)"
                >编辑</el-button
              >
              <el-button link type="danger" @click="confirmDeleteTimeSlot(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!loading && timeSlots.length === 0" class="education-empty">
        当前范围暂无节次数据。
      </div>
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
      <el-form label-position="top" class="education-form">
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
        <el-form-item label="节次编码"><el-input v-model="timeSlotForm.slotCode" /></el-form-item>
        <el-form-item label="节次名称"><el-input v-model="timeSlotForm.slotName" /></el-form-item>
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
        <el-form-item label="开始时间"
          ><el-time-picker
            v-model="timeSlotForm.startTime"
            value-format="HH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="结束时间"
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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  createTimeSlot,
  deleteTimeSlot,
  fetchCampuses,
  fetchTimeSlots,
  updateTimeSlot,
  type CampusSummary,
  type TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { WEEKDAY_OPTIONS } from './components/time-slot-utils'

const router = useRouter()
const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const dictStore = useDictStore()

const institutionId = computed(() => institutionStore.currentInstitutionId)
/** 校区与模板页共用同一份范围,从模板页跳过来时停在同一个校区 */
const { campusId } = storeToRefs(scopeStore)

const campuses = ref<CampusSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])
const loading = ref(false)
const pageError = ref('')

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimOrUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  const campus = campuses.value.find((item) => item.id === id)
  return campus?.campusName || campus?.campusCode || `校区 #${id}`
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

/** 只重拉节次（换校区、保存后）:按当前校区取本校区 + 机构通用 */
async function loadTimeSlots() {
  if (!institutionId.value) {
    timeSlots.value = []
    return
  }
  loading.value = true
  pageError.value = ''
  try {
    timeSlots.value = await fetchTimeSlots(institutionId.value, campusId.value)
  } catch (error) {
    timeSlots.value = []
    pageError.value = toMessage(error, '加载节次失败')
  } finally {
    loading.value = false
  }
}

/** 重置 = 取消校区筛选看本机构全部节次 */
function resetAndLoad() {
  campusId.value = null
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
    campuses.value = await fetchCampuses(institutionId.value)
    // 与校区/教室页同一口径：范围里带着死 id 或空值时兜到第一个校区，别一进来就铺上千行
    if (!campusId.value || !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = campuses.value[0]?.id || null
    }
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
    timeSlotForm.campusId = campusId.value
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
}

async function submitTimeSlot() {
  if (!institutionId.value) {
    timeSlotFormError.value = '请先选择机构范围'
    return
  }
  if (!trimOrUndefined(timeSlotForm.slotCode) || !trimOrUndefined(timeSlotForm.slotName)) {
    timeSlotFormError.value = '请完整填写节次编码和名称'
    return
  }
  if (!timeSlotForm.startTime || !timeSlotForm.endTime) {
    timeSlotFormError.value = '请选择开始和结束时间'
    return
  }
  if (timeSlotMinutes(timeSlotForm.endTime) <= timeSlotMinutes(timeSlotForm.startTime)) {
    timeSlotFormError.value = '结束时间必须晚于开始时间'
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
    // 存到别的校区（或改成机构通用）时跟着切过去，否则刚保存的那条会被当前校区筛选挡掉
    if (campusId.value !== (timeSlotForm.campusId || null)) {
      campusId.value = timeSlotForm.campusId || null
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

watch(institutionId, async () => {
  campusId.value = null
  await refreshAll()
})

onMounted(() => {
  // 节次类型列的回显靠字典（表单里的 DictSelect 自己会拉，表格列不会）
  void dictStore.ensureLoaded(['slot_type']).catch(() => undefined)
  refreshAll()
})
</script>
