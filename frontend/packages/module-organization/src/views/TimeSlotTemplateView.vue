<template>
  <section class="ts-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:模板的新建/删除与刷新靠左,会跳走的入口放刷新右边 -->
    <div class="table-toolbar">
      <el-button v-if="canWrite" type="primary" @click="openCreateDialog">新建模板</el-button>
      <el-button v-if="canWrite && selectedTemplate" @click="confirmDeleteTemplate"
        >删除模板</el-button
      >
      <el-button :icon="Refresh" circle :loading="loading" @click="refreshAll" />
      <el-button @click="goTimeSlotList">节次明细</el-button>
    </div>

    <!-- 统一筛选栏:校区/模板都不再自动触发,点「查询」才切范围并载入所选模板 -->
    <FilterBar :loading="loading" @query="applyFilters" @reset="resetFilters">
      <el-select v-model="campusId" placeholder="筛选校区" clearable filterable>
        <el-option
          v-for="item in campuses"
          :key="item.id"
          :label="item.campusName || item.campusCode || `校区 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <el-select
        v-model="selectedTemplateId"
        placeholder="选择时段模板"
        filterable
        :no-data-text="filteredTemplates.length ? '暂无模板' : '当前范围暂无模板'"
        style="width: 240px"
      >
        <el-option
          v-for="item in filteredTemplates"
          :key="item.id"
          :label="item.templateName"
          :value="item.id"
        >
          <span>{{ item.templateName }}</span>
          <span class="ts-option-campus">{{ campusText(item.campusId) }}</span>
        </el-option>
      </el-select>
    </FilterBar>

    <div v-if="!selectedTemplate" class="panel-card ts-empty-panel">
      <el-empty description="请选择时段模板,或新建一个时段模板开始编辑" />
    </div>

    <article v-else class="panel-card">
      <div class="table-toolbar">
        <el-button v-if="canWrite" @click="regenerateRows">按参数生成</el-button>
        <el-button v-if="canWrite" @click="addRow">添加节次</el-button>
        <el-button
          v-if="canWrite"
          type="primary"
          :loading="saving"
          :disabled="conflicts.size > 0"
          @click="saveGrid"
        >
          保存
        </el-button>
        <div class="education-context-text">
          当前模板:{{ selectedTemplate.templateName }};网格 {{ rows.length }} 个节次 ×
          {{ config.weekdays.length }} 天
        </div>
      </div>

      <!-- 参数表自己的列头（时段/节数/时长…）已经说明它是什么，不再单独占一行标题 -->
      <TimeSlotConfigForm v-model="config" :disabled="!canWrite" />
      <div class="ts-section-label">节次时间</div>
      <el-alert
        v-if="conflicts.size > 0"
        :title="`存在 ${conflicts.size / 2} 组时间重叠的节次,请调整后再保存(红色单元格)`"
        type="error"
        show-icon
        :closable="false"
        class="ts-conflict-alert"
      />
      <TimeSlotGridEditor
        :rows="rows"
        :weekdays="config.weekdays"
        :conflicts="conflicts"
        :editable="canWrite"
        @remove-row="removeRow"
      />
    </article>

    <el-dialog v-model="createDialogVisible" title="新建时段模板" width="480px">
      <el-alert
        v-if="createError"
        :title="createError"
        type="error"
        show-icon
        :closable="false"
        class="ts-dialog-alert"
      />
      <el-form label-position="top">
        <el-form-item label="模板名称" required>
          <el-input
            v-model="createForm.templateName"
            placeholder="如:标准工作日课表"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="适用校区">
          <el-select
            v-model="createForm.campusId"
            placeholder="不选则为机构级模板"
            clearable
            filterable
          >
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并编辑</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  applyTimeSlotTemplate,
  createTimeSlotTemplate,
  deleteTimeSlotTemplate,
  fetchCampuses,
  fetchTimeSlotTemplates,
  fetchTimeSlots,
  type CampusSummary,
  type TimeSlotConfig,
  type TimeSlotSummary,
  type TimeSlotTemplateSummary,
} from '@cornerstone/module-organization/api/organization'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import TimeSlotConfigForm from './components/TimeSlotConfigForm.vue'
import TimeSlotGridEditor from './components/TimeSlotGridEditor.vue'
import {
  asTimeSlotConfig,
  buildRowsFromSlots,
  defaultTimeSlotConfig,
  findCellConflicts,
  generateRowsFromConfig,
  rowsToApplySlots,
  type SectionRow,
} from './components/time-slot-utils'

const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()
const educationScope = useEducationScopeStore()

const institutionId = computed(() => institutionStore.currentInstitutionId)
const canWrite = computed(() => auth.hasPermission('organization:write'))

const loading = ref(false)
const saving = ref(false)
const pageError = ref('')

const campuses = ref<CampusSummary[]>([])
const templates = ref<TimeSlotTemplateSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

const campusId = ref<string | null>(educationScope.campusId)
const selectedTemplateId = ref<string | null>(null)
const config = ref<TimeSlotConfig>(defaultTimeSlotConfig())
const rows = ref<SectionRow[]>([])

const conflicts = computed(() => findCellConflicts(rows.value, config.value.weekdays))

const filteredTemplates = computed(() => {
  if (!campusId.value) {
    return templates.value
  }
  return templates.value.filter((item) => !item.campusId || item.campusId === campusId.value)
})

const selectedTemplate = computed(
  () => templates.value.find((item) => item.id === selectedTemplateId.value) || null
)

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function campusText(id?: string | null) {
  if (!id) {
    return '机构级'
  }
  const campus = campuses.value.find((item) => item.id === id)
  return campus?.campusName || campus?.campusCode || `校区 #${id}`
}

/** 节次明细是另一张页面,跳过去时把当前校区带过去（两页共用 educationScope 那份范围） */
function goTimeSlotList() {
  educationScope.setCampus(campusId.value)
  router.push('/time-slot-list')
}

async function onCampusChange() {
  educationScope.setCampus(campusId.value)
  // 节次按校区取；网格里的行不动，避免换校区把未保存的编辑清掉
  await reloadTimeSlots()
}

function onTemplateChange(id: string | null) {
  if (!id) {
    rows.value = []
    return
  }
  loadTemplateIntoEditor(selectedTemplate.value)
}

/** 点「查询」才应用:先切校区范围重拉节次,再把选中的模板载入编辑器 */
async function applyFilters() {
  await onCampusChange()
  onTemplateChange(selectedTemplateId.value)
}

/** 重置 = 取消校区筛选(看机构全部模板)并退出当前模板 */
async function resetFilters() {
  campusId.value = null
  selectedTemplateId.value = null
  await onCampusChange()
  rows.value = []
}

/** 选中模板后:回填参数;已有展开结果则还原网格,否则按参数生成 */
function loadTemplateIntoEditor(template: TimeSlotTemplateSummary | null) {
  if (!template) {
    return
  }
  config.value = asTimeSlotConfig(template.config)
  const owned = timeSlots.value.filter((slot) => slot.templateId === template.id)
  rows.value = owned.length > 0 ? buildRowsFromSlots(owned) : generateRowsFromConfig(config.value)
}

function regenerateRows() {
  rows.value = generateRowsFromConfig(config.value)
  ElMessage.success('节次表已按参数生成,可继续微调后保存')
}

function addRow() {
  const index = rows.value.length + 1
  const cells: Record<number, SectionRow['cells'][number]> = {}
  for (const weekday of config.value.weekdays) {
    cells[weekday] = null
  }
  rows.value.push({
    slotCode: `EX${index}`,
    slotName: `附加${index}`,
    slotType: 'teaching',
    sortNo: 900 + index,
    cells,
  })
}

function removeRow(row: SectionRow) {
  rows.value = rows.value.filter((item) => item !== row)
}

/** 网格要把已保存的节次还原成行：这份清单只用于回填网格，逐条维护去节次明细页 */
async function reloadTimeSlots() {
  if (!institutionId.value) {
    timeSlots.value = []
    return
  }
  try {
    timeSlots.value = await fetchTimeSlots(institutionId.value, campusId.value)
  } catch (error) {
    timeSlots.value = []
    pageError.value = toMessage(error, '加载节次时间失败')
  }
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
  pageError.value = ''
  try {
    const [campusList, templateList, slotList] = await Promise.all([
      fetchCampuses(institutionId.value),
      fetchTimeSlotTemplates(institutionId.value),
      fetchTimeSlots(institutionId.value, campusId.value),
    ])
    campuses.value = campusList
    templates.value = templateList
    timeSlots.value = slotList
    if (
      selectedTemplateId.value &&
      !templateList.some((item) => item.id === selectedTemplateId.value)
    ) {
      selectedTemplateId.value = null
      rows.value = []
    }
  } catch (error) {
    pageError.value = toMessage(error, '加载时段模板失败')
  } finally {
    loading.value = false
  }
}

async function saveGrid() {
  if (!selectedTemplateId.value) {
    ElMessage.warning('请先选择时段模板')
    return
  }
  const slots = rowsToApplySlots(rows.value)
  if (slots.length === 0) {
    ElMessage.warning('网格中没有任何已排时间,无法保存')
    return
  }
  if (conflicts.value.size > 0) {
    ElMessage.error('存在时间重叠的节次,请先调整')
    return
  }
  saving.value = true
  pageError.value = ''
  try {
    const result = await applyTimeSlotTemplate(selectedTemplateId.value, {
      config: config.value,
      slots,
    })
    ElMessage.success(
      `已保存:新增 ${result.createdCount}、更新 ${result.updatedCount}、移除 ${result.removedCount}` +
        (result.deactivatedCount > 0 ? `、停用(被排课引用) ${result.deactivatedCount}` : '')
    )
    await reloadTimeSlots()
  } catch (error) {
    pageError.value = toMessage(error, '保存节次时间失败')
  } finally {
    saving.value = false
  }
}

// ---- 新建模板 ----
const createDialogVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({ templateName: '', campusId: '' as string | null, remark: '' })

function openCreateDialog() {
  createForm.templateName = ''
  createForm.campusId = campusId.value || null
  createForm.remark = ''
  createError.value = ''
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!institutionId.value) {
    return
  }
  if (!createForm.templateName.trim()) {
    createError.value = '请填写模板名称'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const created = await createTimeSlotTemplate({
      institutionId: institutionId.value,
      campusId: createForm.campusId || null,
      templateName: createForm.templateName.trim(),
      config: config.value,
      remark: createForm.remark.trim() || undefined,
    })
    templates.value = await fetchTimeSlotTemplates(institutionId.value)
    selectedTemplateId.value = created.id
    loadTemplateIntoEditor(created)
    createDialogVisible.value = false
    ElMessage.success('时段模板已创建,请确认节次时间后保存')
  } catch (error) {
    createError.value = toMessage(error, '创建时段模板失败')
  } finally {
    creating.value = false
  }
}

async function confirmDeleteTemplate() {
  const template = selectedTemplate.value
  if (!template) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除时段模板「${template.templateName}」吗?模板下仍有时间段时需先在网格中移除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteTimeSlotTemplate(template.id)
    ElMessage.success('时段模板已删除')
    selectedTemplateId.value = null
    rows.value = []
    templates.value = await fetchTimeSlotTemplates(institutionId.value)
  } catch (error) {
    ElMessage.error(toMessage(error, '删除时段模板失败'))
  }
}

watch(institutionId, () => {
  selectedTemplateId.value = null
  rows.value = []
  refreshAll()
})

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.ts-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ts-empty-panel {
  padding: 40px 0;
}

/* 一张卡里两个区块的分隔标题:参数与网格是先后两步,不写清容易以为网格是独立设置 */
.ts-section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 10px 0 4px;
  padding-bottom: 3px;
  border-bottom: 1px solid var(--line);
}

/* 冲突提示默认 40px 高，压到 28px：这一页纵向是按像素省的，一行提示就挤掉一整节次 */
.ts-conflict-alert {
  padding: 4px 10px;
  margin-bottom: 8px;
}

.ts-option-campus {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 16px;
}

.ts-dialog-alert {
  margin-bottom: 12px;
}
</style>
