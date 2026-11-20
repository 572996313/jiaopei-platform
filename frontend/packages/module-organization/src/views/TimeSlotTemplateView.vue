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

    <!-- 这颗下拉不是筛选条件,是"选要编辑的那一个模板",选中即把参数与节次载入下方编辑器,
         所以不放进 FilterBar(那里的口径是点「查询」才生效,会让人以为模板下面是空的)。
         本页唯一的筛选条件是校区范围,它在横幅那颗芯片上改,这里只按它过滤模板清单并回显。 -->
    <div class="table-toolbar">
      <span class="filter-bar-group">
        <span class="filter-bar-group-label">时段模板</span>
        <el-select
          v-model="selectedTemplateId"
          placeholder="选择时段模板"
          filterable
          clearable
          :no-data-text="filteredTemplates.length ? '暂无模板' : '当前范围暂无模板'"
          style="width: 240px"
          @change="onTemplateChange"
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
      </span>
      <span class="education-context-text">
        当前范围：{{
          campusId ? educationScope.campusName(campusId) : '全部校区'
        }}；模板按校区分开维护，范围在顶部横幅的校区芯片上改。
      </span>
    </div>

    <article v-if="!selectedTemplate" class="panel-card ts-need-pick">
      在上方选一个时段模板即可编辑它的节次时间；没有合适的模板请点「新建模板」。
    </article>

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
        <!-- 模板名上面的下拉已经显示过,这里只说网格:已存的是还原出来的,没存过的是草稿,不保存不入库 -->
        <div class="education-context-text" v-if="savedSlotCount > 0">
          网格 {{ rows.length }} 个节次 ×
          {{ config.weekdays.length }} 天（该模板已存过节次，改动点「保存」生效）
        </div>
        <div class="education-context-text" v-else>
          该模板还没存过节次，下面是按参数生成的节次表（{{ rows.length }} 个 ×
          {{ config.weekdays.length }} 天），点「保存」才会写入
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
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="模板名称" prop="templateName">
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
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import {
  applyTimeSlotTemplate,
  createTimeSlotTemplate,
  deleteTimeSlotTemplate,
  fetchTimeSlotTemplates,
  fetchTimeSlots,
  type TimeSlotConfig,
  type TimeSlotSummary,
  type TimeSlotTemplateSummary,
} from '@cornerstone/module-organization/api/organization'
import { useAuthStore } from '@cornerstone/core/stores/auth'
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

const templates = ref<TimeSlotTemplateSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

/** 校区范围与校区清单全站只有一份：范围在横幅那颗芯片上改，本页只读 */
const { campusId, campuses, prefillCampusId } = storeToRefs(educationScope)
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
  return id ? educationScope.campusName(id) : '机构级'
}

/** 节次明细是另一张页面,跳过去即可——两页共用 educationScope 那份范围 */
function goTimeSlotList() {
  router.push('/time-slot-list')
}

/** 当前所选模板已保存的节次条数（按星期拆开计数），删除/清空确认与网格说明都用它 */
const savedSlotCount = computed(() => {
  const id = selectedTemplateId.value
  if (!id) {
    return 0
  }
  return timeSlots.value.filter((slot) => slot.templateId && String(slot.templateId) === String(id))
    .length
})

function onTemplateChange(id: string | null) {
  if (!id) {
    rows.value = []
    return
  }
  loadTemplateIntoEditor(selectedTemplate.value)
}

/** 选中模板后:回填参数;已有展开结果则还原网格,否则按参数生成 */
function loadTemplateIntoEditor(template: TimeSlotTemplateSummary | null) {
  if (!template) {
    return
  }
  config.value = asTimeSlotConfig(template.config)
  const owned = timeSlots.value.filter(
    (slot) => slot.templateId && String(slot.templateId) === String(template.id)
  )
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
    const templateList = await fetchTimeSlotTemplates(institutionId.value)
    templates.value = templateList
    await educationScope.loadCampuses(institutionId.value)
    timeSlots.value = await fetchTimeSlots(institutionId.value, campusId.value)
    if (
      selectedTemplateId.value &&
      !templateList.some((item) => item.id === selectedTemplateId.value)
    ) {
      selectedTemplateId.value = null
      rows.value = []
    } else if (selectedTemplateId.value) {
      // 选中模板还在:按刚拉到的节次重载编辑器,否则点刷新只是清单变了、网格还是旧的
      loadTemplateIntoEditor(selectedTemplate.value)
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
  if (conflicts.value.size > 0) {
    ElMessage.error('存在时间重叠的节次,请先调整')
    return
  }
  // 网格空着保存 = 清空本模板的全部节次,是不可逆动作,先确认;本来就没节次时只是在存参数,不用拦
  if (slots.length === 0 && !(await confirmClearSlots())) {
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

/** 清空确认:没有存量节次时直接放行(否则这句提示没有内容可对) */
async function confirmClearSlots() {
  if (savedSlotCount.value === 0) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      `网格中一个时间都没有,保存会删除该模板下的 ${savedSlotCount.value} 个节次；已被排课引用的节次只停用并转为手工维护,不会消失。`,
      '确认清空节次',
      { type: 'warning', confirmButtonText: '清空并保存', cancelButtonText: '取消' }
    )
  } catch {
    return false
  }
  return true
}

// ---- 新建模板 ----
const createDialogVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createFormRef = ref<FormInstance | null>(null)
const createForm = reactive({ templateName: '', campusId: '' as string | null, remark: '' })

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
const createRules: FormRules = {
  templateName: [
    { required: true, whitespace: true, message: '请填写模板名称', trigger: 'blur' },
  ],
}

function openCreateDialog() {
  createForm.templateName = ''
  createForm.campusId = prefillCampusId.value
  createForm.remark = ''
  createError.value = ''
  createDialogVisible.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => createFormRef.value?.clearValidate())
}

async function submitCreate() {
  if (!institutionId.value) {
    return
  }
  // 校验失败时 el-form 已经在模板名称上标红并给出提示，这里不再补一条顶部总文案
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (valid === false) {
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
      savedSlotCount.value > 0
        ? `确定删除时段模板「${template.templateName}」吗？模板下 ${savedSlotCount.value} 个节次会一并删除；` +
            '已被排课引用的节次只停用并转为手工维护，不会消失。'
        : `确定删除时段模板「${template.templateName}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteTimeSlotTemplate(template.id)
    ElMessage.success('时段模板及其节次已删除')
    selectedTemplateId.value = null
    rows.value = []
    templates.value = await fetchTimeSlotTemplates(institutionId.value)
    await reloadTimeSlots()
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

/* 未选模板时只给一行指引,不铺空卡（el-empty 那一块白占 130px 高却没任何信息） */
.ts-need-pick {
  padding: 18px 20px;
  color: var(--text-secondary);
  font-size: 14px;
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
