<template>
  <el-dialog
    :model-value="modelValue"
    title="排课规则"
    width="1080px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <!-- 校区日历不开新路由与新菜单节点，作为本弹窗的第二个页签承载（2026-09-25 口径） -->
    <el-tabs v-model="activeTab">
      <el-tab-pane label="禁排/必排规则" name="rules">
        <div class="rule-toolbar">
          <el-button v-if="canManage" type="primary" @click="emit('create')">新增规则</el-button>
          <el-select v-model="filterRuleType" placeholder="筛选类型" clearable @change="reload">
            <el-option label="禁排" value="forbidden" />
            <el-option label="必排" value="required" />
          </el-select>
          <el-select v-model="filterTargetType" placeholder="筛选对象" clearable @change="reload">
            <el-option label="班级" value="class" />
            <el-option label="教师" value="teacher" />
            <el-option label="教室" value="classroom" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="筛选状态" clearable @change="reload">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </div>

        <el-table
          :data="rules"
          border
          row-key="id"
          highlight-current-row
          v-loading="loading"
          max-height="440"
          :cell-class-name="sort.cellClass"
          @sort-change="sort.onSortChange"
        >
          <el-table-column label="类型" width="80" fixed="left" prop="ruleType" sortable="custom">
            <template #default="{ row }">
              <el-tag size="small" :type="row.ruleType === 'forbidden' ? 'danger' : 'warning'">
                {{ row.ruleType === 'forbidden' ? '禁排' : '必排' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="对象" min-width="170" prop="targetType" sortable="custom">
            <template #default="{ row }">{{ formatTarget(row) }}</template>
          </el-table-column>
          <el-table-column label="节次格子" min-width="240">
            <template #default="{ row }">{{ formatSlots(row.slots) }}</template>
          </el-table-column>
          <el-table-column label="生效区间" width="200" prop="startDate" sortable="custom">
            <template #default="{ row }">{{ formatRange(row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" prop="status" sortable="custom">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="批注" min-width="160" prop="remark" sortable="custom">
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column v-if="canManage" label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <div class="system-operation-group">
                <el-button link type="primary" @click="emit('edit', row)">编辑</el-button>
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
      </el-tab-pane>

      <el-tab-pane label="校区日历" name="calendar" :lazy="true">
        <SchoolCalendarPanel
          :institution-id="institutionId"
          :campuses="campuses"
          :active="modelValue && activeTab === 'calendar'"
        />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="rule-list-footer">
        <span v-if="activeTab === 'rules'" class="rule-list-hint">
          禁排格子会在排课时直接拦下；必排只在周期计划预览里提示覆盖缺口，不拦提交。改规则不会回改已生成的课次。
        </span>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
// 排课规则列表：按类型/对象/状态筛选，编辑走同一个编辑器，删除只影响后续校验。
// 「校区日历」页签共用这一个弹窗入口，避免再写一版 sys_menu 迁移。
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteScheduleRule,
  fetchScheduleRulePage,
  type SchedulePlanSlotItem,
  type ScheduleRuleRecord,
  type TeacherSummary,
} from '@cornerstone/module-education/api/education'
import type {
  CampusSummary,
  ClassroomSummary,
  TimeSlotSummary,
} from '@cornerstone/module-organization/api/organization'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { classLabelOf, ensureClassLabels } from '@cornerstone/module-education/composables/classLabels'
import SchoolCalendarPanel from './SchoolCalendarPanel.vue'
import { timeSlotShortName, toMessage, weekdayLabel } from './schedule-utils'

const props = defineProps<{
  modelValue: boolean
  institutionId: string | null
  teachers: TeacherSummary[]
  classrooms: ClassroomSummary[]
  campuses: CampusSummary[]
  timeSlots: TimeSlotSummary[]
  scopeCampusId?: string | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'create'): void
  (event: 'edit', rule: ScheduleRuleRecord): void
  (event: 'changed'): void
}>()

/** 写操作按排课维护码收口（菜单按钮节点 112「排课维护」），后端仍按权限串硬拦 */
const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('education:schedule:write'))

const activeTab = ref('rules')
const rules = ref<ScheduleRuleRecord[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const deletingId = ref<string | null>(null)
const filterRuleType = ref<string | null>(null)
const filterTargetType = ref<string | null>(null)
const filterStatus = ref<string | null>(null)

/** 表头排序(服务端):无序时回到后端的 createdAt 倒序 */
const sort = useTableSort({
  onSorted: () => {
    page.value = 1
    void reload()
  },
})

function formatTarget(rule: ScheduleRuleRecord) {
  const id = rule.targetId
  if (!id) {
    return '—'
  }
  if (rule.targetType === 'class') {
    return `班级 ${classLabelOf(id)}`
  }
  if (rule.targetType === 'classroom') {
    const classroom = props.classrooms.find((item) => item.id === id)
    return `教室「${classroom?.classroomName || classroom?.classroomCode || id}」`
  }
  const teacher = props.teachers.find((item) => item.id === id)
  return `教师 ${teacher?.teacherName || teacher?.teacherCode || id}`
}

function formatSlots(slots?: SchedulePlanSlotItem[] | null) {
  if (!slots || slots.length === 0) {
    return '—'
  }
  return slots
    .map((slot) => {
      const timeSlot = props.timeSlots.find((item) => item.id === slot.timeSlotId)
      // 节次名可能自带"周一"前缀,再拼 weekdayLabel 会读成"周一周一上1"
      const name = timeSlot
        ? timeSlotShortName(timeSlot.slotName) || '节次'
        : `时段#${slot.timeSlotId}`
      return `${weekdayLabel(slot.weekday)}${name}`
    })
    .join('、')
}

function formatRange(rule: ScheduleRuleRecord) {
  if (!rule.startDate && !rule.endDate) {
    return '不限日期'
  }
  return `${rule.startDate || '不限'} ~ ${rule.endDate || '不限'}`
}

function handleOpen() {
  activeTab.value = 'rules'
  page.value = 1
  filterRuleType.value = null
  filterTargetType.value = null
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
    rules.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await fetchScheduleRulePage({
      institutionId: props.institutionId,
      ruleType: filterRuleType.value,
      targetType: filterTargetType.value,
      status: filterStatus.value,
      page: page.value,
      pageSize: pageSize.value,
      ...sort.sortParams(),
    })
    rules.value = result.records
    total.value = result.total
    // 规则只有 targetType + targetId，班级对象的名字要按 id 回查
    void ensureClassLabels(
      props.institutionId,
      result.records.filter((item) => item.targetType === 'class').map((item) => item.targetId)
    )
  } catch (error) {
    rules.value = []
    total.value = 0
    ElMessage.error(toMessage(error, '排课规则加载失败'))
  } finally {
    loading.value = false
  }
}

async function handleDelete(rule: ScheduleRuleRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除${formatTarget(rule)}的这条${rule.ruleType === 'forbidden' ? '禁排' : '必排'}规则吗？`,
      '删除排课规则',
      { type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = rule.id
  try {
    await deleteScheduleRule(rule.id)
    ElMessage.success('排课规则已删除')
    await reload()
    emit('changed')
  } catch (error) {
    ElMessage.error(toMessage(error, '删除排课规则失败'))
  } finally {
    deletingId.value = null
  }
}

defineExpose({ reload })
</script>

<style scoped>
.rule-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.rule-toolbar .el-select {
  width: 150px;
}

.rule-list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.rule-list-hint {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
