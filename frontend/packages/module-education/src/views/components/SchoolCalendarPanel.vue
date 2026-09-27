<template>
  <div class="calendar-panel">
    <!-- 主操作按钮行：按钮左排、筛选与说明落右侧同一行 -->
    <div class="table-toolbar">
      <el-button v-if="canManage" type="primary" @click="openDialog(null)">新增停排日</el-button>
      <el-date-picker
        v-model="year"
        type="year"
        value-format="YYYY"
        :clearable="false"
        :editable="false"
        placeholder="筛选年份"
        class="calendar-year-picker"
        @change="loadDays"
      />
      <div class="education-context-text">
        校区为「全校区」的停排日对本机构所有校区生效；排课时命中停排日只提示、不拦保存。
      </div>
    </div>

    <el-table
      :data="sortedDays"
      border
      row-key="id"
      highlight-current-row
      v-loading="loading"
      max-height="440"
    >
      <el-table-column prop="dayDate" label="日期" width="120" fixed="left" sortable />
      <el-table-column prop="dayName" label="名称" min-width="160" show-overflow-tooltip sortable />
      <el-table-column label="校区" min-width="160">
        <template #default="{ row }">{{ formatDayCampus(row.campusId) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip sortable>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <div class="system-operation-group">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              :loading="deletingId === row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && sortedDays.length === 0" class="education-empty">
      {{ year }} 年暂无停排日。
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑停排日' : '新增停排日'"
      width="520px"
      append-to-body
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="校区" required>
          <el-select v-model="form.campusId" filterable>
            <el-option label="全校区（机构级停排）" :value="ALL_CAMPUS_ID" />
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker
            v-model="form.dayDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择停排日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input
            v-model="form.dayName"
            maxlength="64"
            placeholder="例如 国庆假期 / 校区检修日"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="选填，例如停排范围与依据"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// 校区日历（停排日）：嵌在「排课规则」弹窗的页签里，不另开路数与菜单。
// campusId=0 表示机构级（全校区停排），后端契约里不是 null。
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSchoolCalendarDay,
  deleteSchoolCalendarDay,
  fetchSchoolCalendar,
  updateSchoolCalendarDay,
  type SchoolCalendarDayRecord,
} from '@cornerstone/module-education/api/education'
import type { CampusSummary } from '@cornerstone/module-organization/api/organization'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { toMessage } from './schedule-utils'

const ALL_CAMPUS_ID = '0'

const props = defineProps<{
  institutionId: string | null
  campuses: CampusSummary[]
  /** 页签是否处于激活态：首次切进「校区日历」才拉数 */
  active: boolean
}>()

const auth = useAuthStore()
/** 写操作按排课维护码收口（与排课规则同口径），后端仍按权限串硬拦 */
const canManage = computed(() => auth.hasPermission('education:schedule:write'))

const year = ref(String(new Date().getFullYear()))
const days = ref<SchoolCalendarDayRecord[]>([])
const loading = ref(false)
const deletingId = ref<string | null>(null)
/** 已完成首次加载的机构ID：切机构要重拉，切回页签不重复拉 */
const loadedKey = ref<string | null>(null)

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const editingId = ref<string | null>(null)

const form = reactive({
  campusId: ALL_CAMPUS_ID,
  dayDate: '',
  dayName: '',
  remark: '',
})

const sortedDays = computed(() =>
  [...days.value].sort((left, right) =>
    String(left.dayDate || '').localeCompare(String(right.dayDate || ''))
  )
)

function formatDayCampus(campusId?: string | number | null) {
  const id = String(campusId ?? '')
  if (id === ALL_CAMPUS_ID) {
    return '全校区'
  }
  const campus = props.campuses.find((item) => item.id === id)
  return campus?.campusName || campus?.campusCode || (id ? `校区 #${id}` : '未选择')
}

async function loadDays() {
  if (!props.institutionId) {
    days.value = []
    return
  }
  loading.value = true
  try {
    days.value = await fetchSchoolCalendar({
      institutionId: props.institutionId,
      year: Number(year.value),
    })
  } catch (error) {
    days.value = []
    ElMessage.error(toMessage(error, '校区日历加载失败'))
  } finally {
    loading.value = false
  }
}

function openDialog(row: SchoolCalendarDayRecord | null) {
  editingId.value = row?.id || null
  form.campusId = row ? String(row.campusId ?? ALL_CAMPUS_ID) : ALL_CAMPUS_ID
  form.dayDate = row?.dayDate || ''
  form.dayName = row?.dayName || ''
  form.remark = row?.remark || ''
  formError.value = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!props.institutionId) {
    ElMessage.warning('请先选择机构')
    return
  }
  if (!form.dayDate) {
    formError.value = '请选择停排日期'
    return
  }
  if (!form.dayName.trim()) {
    formError.value = '请填写名称'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const payload = {
      institutionId: props.institutionId,
      campusId: form.campusId,
      dayDate: form.dayDate,
      dayName: form.dayName.trim(),
      remark: form.remark.trim() || undefined,
    }
    if (editingId.value) {
      await updateSchoolCalendarDay(editingId.value, payload)
      ElMessage.success('停排日已更新')
    } else {
      await createSchoolCalendarDay(payload)
      ElMessage.success('停排日已新增')
    }
    dialogVisible.value = false
    await loadDays()
  } catch (error) {
    formError.value = toMessage(error, editingId.value ? '更新停排日失败' : '新增停排日失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SchoolCalendarDayRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${row.dayDate}「${row.dayName || '未命名'}」这条停排日吗？`,
      '删除停排日',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deleteSchoolCalendarDay(row.id)
    ElMessage.success('停排日已删除')
    await loadDays()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除停排日失败'))
  } finally {
    deletingId.value = null
  }
}

// 页签首次激活时才拉数；el-tab-pane 是 lazy 的，挂载时 active 可能已经是 true，
// watcher 不会有「变化」触发，所以用 immediate 并自己记录已加载的机构。
watch(
  () => [props.active, props.institutionId] as const,
  ([isActive, institutionId]) => {
    if (!isActive || !institutionId) {
      return
    }
    if (loadedKey.value === institutionId) {
      return
    }
    loadedKey.value = institutionId
    void loadDays()
  },
  { immediate: true }
)
</script>

<style scoped>
/* 说明文案靠右由全局 .table-toolbar > .education-context-text 负责，这里只补弹窗内的下间距 */
.calendar-panel .table-toolbar {
  margin-bottom: 12px;
}

.calendar-year-picker {
  width: 130px;
}
</style>
