<template>
  <el-dialog
    :model-value="modelValue"
    title="周期排课计划"
    width="1080px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <div class="plan-list-toolbar">
      <el-button v-if="canManage" type="primary" @click="emit('create')">新建计划</el-button>
      <el-select
        v-model="filterClassId"
        placeholder="筛选班级"
        clearable
        filterable
        @change="reload"
      >
        <el-option
          v-for="item in classes"
          :key="item.id"
          :label="item.className || item.classCode || `班级 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <el-select v-model="filterStatus" placeholder="筛选状态" clearable @change="reload">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="inactive" />
      </el-select>
    </div>

    <el-table
      :data="plans"
      border
      row-key="id"
      highlight-current-row
      v-loading="loading"
      max-height="440"
      :cell-class-name="sort.cellClass"
      @sort-change="sort.onSortChange"
    >
      <el-table-column label="班级" min-width="150" fixed="left">
        <template #default="{ row }">{{ formatClass(row.classId) }}</template>
      </el-table-column>
      <el-table-column label="频率" width="70" prop="weekFrequency" sortable="custom">
        <template #default="{ row }">{{ weekFrequencyLabel(row.weekFrequency) }}</template>
      </el-table-column>
      <el-table-column label="起止日期" width="200" prop="startDate" sortable="custom">
        <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
      </el-table-column>
      <el-table-column label="每周节次" min-width="220">
        <template #default="{ row }">{{ formatSlots(row.slots) }}</template>
      </el-table-column>
      <el-table-column label="教师" min-width="140">
        <template #default="{ row }">{{ formatTeachers(row.teacherIds) }}</template>
      </el-table-column>
      <el-table-column label="教室" min-width="120">
        <template #default="{ row }">{{ formatClassroom(row.classroomId) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" prop="status" sortable="custom">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <div class="system-operation-group">
            <el-button link type="primary" @click="emit('edit', row)">补排/调整</el-button>
            <el-button
              link
              type="warning"
              :loading="cancellingId === row.id"
              @click="handleCancelLessons(row)"
            >
              撤销未上课次
            </el-button>
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

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      class="panel-pagination"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="reload"
      @size-change="handleSizeChange"
    />

    <template #footer>
      <div class="plan-list-footer">
        <span class="plan-list-hint">
          删除计划只移除计划本身，不撤销已生成的课次；撤销未上课次只处理今天及以后的占位课次。
        </span>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
// 周期排课计划列表：新建/查看/筛选计划，支持进向导补排调整、整批撤销未上课次、删除计划。
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelSchedulePlanLessons,
  deleteSchedulePlan,
  fetchSchedulePlanPage,
  type SchedulePlanRecord,
  type SchedulePlanSlotItem,
  type TeacherSummary,
  type TeachingClassSummary,
} from '@cornerstone/module-education/api/education'
import type {
  ClassroomSummary,
  TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { formatTeacherIdsAs, toMessage, weekdayLabel } from './schedule-utils'

const props = defineProps<{
  modelValue: boolean
  institutionId: string | null
  classes: TeachingClassSummary[]
  teachers: TeacherSummary[]
  classrooms: ClassroomSummary[]
  timeSlots: TimeSlotSummary[]
  scopeCampusId?: string | null
  scopeClassId?: string | null
  formatClass: (id?: string | null) => string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'create'): void
  (event: 'edit', plan: SchedulePlanRecord): void
  (event: 'changed'): void
}>()

/** 写操作按 education:write 收口（菜单按钮节点 112「排课维护」），后端仍按权限串硬拦 */
const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('education:write'))

const plans = ref<SchedulePlanRecord[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const cancellingId = ref<string | null>(null)
const deletingId = ref<string | null>(null)
const filterClassId = ref<string | null>(null)
const filterStatus = ref<string | null>(null)

/** 表头排序(服务端):无序时回到后端的 createdAt 倒序 */
const sort = useTableSort({
  onSorted: () => {
    page.value = 1
    void reload()
  },
})

function weekFrequencyLabel(value?: string | null) {
  switch (value) {
    case 'odd_week':
      return '单周'
    case 'even_week':
      return '双周'
    default:
      return '每周'
  }
}

function formatSlots(slots?: SchedulePlanSlotItem[] | null) {
  if (!slots || slots.length === 0) {
    return '—'
  }
  return slots
    .map((slot) => {
      const timeSlot = props.timeSlots.find((item) => item.id === slot.timeSlotId)
      const name = timeSlot?.slotName || `时段#${slot.timeSlotId}`
      return `${weekdayLabel(slot.weekday)}${name}`
    })
    .join('、')
}

function formatTeachers(values?: Array<string | number> | null) {
  const map = new Map(
    props.teachers.map((item) => [
      item.id,
      item.teacherName || item.teacherCode || `教师 #${item.id}`,
    ])
  )
  return formatTeacherIdsAs(values, map)
}

function formatClassroom(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  const room = props.classrooms.find((item) => item.id === id)
  return room?.classroomName || room?.classroomCode || `教室 #${id}`
}

function handleOpen() {
  page.value = 1
  filterClassId.value = props.scopeClassId || null
  filterStatus.value = null
  // 弹窗是 destroy-on-close,表头箭头每次重开都会消失,排序状态要一起回到默认
  sort.resetSort()
  void reload()
}

function handleSizeChange() {
  page.value = 1
  void reload()
}

async function reload() {
  if (!props.institutionId) {
    plans.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await fetchSchedulePlanPage({
      institutionId: props.institutionId,
      campusId: props.scopeCampusId || null,
      classId: filterClassId.value,
      status: filterStatus.value,
      page: page.value,
      pageSize: pageSize.value,
      ...sort.sortParams(),
    })
    plans.value = result.records
    total.value = result.total
  } catch (error) {
    plans.value = []
    total.value = 0
    ElMessage.error(toMessage(error, '周期排课计划加载失败'))
  } finally {
    loading.value = false
  }
}

async function handleCancelLessons(plan: SchedulePlanRecord) {
  try {
    await ElMessageBox.confirm(
      `确认撤销「${props.formatClass(plan.classId)}」计划下今天及以后未开的课次吗？已上课程不受影响。`,
      '撤销未上课次',
      { type: 'warning' }
    )
  } catch {
    return
  }
  cancellingId.value = plan.id
  try {
    const count = await cancelSchedulePlanLessons(plan.id)
    ElMessage.success(`已撤销 ${count} 个未上课次`)
    emit('changed')
  } catch (error) {
    ElMessage.error(toMessage(error, '撤销未上课次失败'))
  } finally {
    cancellingId.value = null
  }
}

async function handleDelete(plan: SchedulePlanRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${props.formatClass(plan.classId)}」的这条周期排课计划吗？已生成的课次不会被删除。`,
      '删除周期排课计划',
      { type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = plan.id
  try {
    await deleteSchedulePlan(plan.id)
    ElMessage.success('周期排课计划已删除')
    await reload()
    emit('changed')
  } catch (error) {
    ElMessage.error(toMessage(error, '删除周期排课计划失败'))
  } finally {
    deletingId.value = null
  }
}

defineExpose({ reload })
</script>

<style scoped>
.plan-list-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.plan-list-toolbar .el-select {
  width: 200px;
}

.plan-list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.plan-list-hint {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
