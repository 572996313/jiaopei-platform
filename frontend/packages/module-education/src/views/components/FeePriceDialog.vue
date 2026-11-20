<template>
  <el-dialog
    v-model="visible"
    :title="`价目 - ${item?.itemName || item?.itemCode || ''}`"
    class="fee-price-dialog"
    width="min(1440px, 96vw)"
    top="4vh"
    draggable
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <div class="table-toolbar price-toolbar">
      <el-button v-if="canConfig && !formVisible" type="primary" @click="openForm()">
        新增价目
      </el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="loadPrices" />
      <span class="education-context-text">
        取价按「学员 8 &gt; 班级 4 &gt; 校区 2 &gt; 学期 1」累计权重取最高；月标准与日标准各自独立回落。
      </span>
    </div>

    <div class="price-filters">
      <div class="price-filter-field">
        <el-select v-model="filters.campusId" clearable placeholder="全部校区">
          <el-option label="全部校区" value="" />
          <el-option
            v-for="campus in campuses"
            :key="campus.id"
            :label="campus.campusName || campus.campusCode || `校区 #${campus.id}`"
            :value="String(campus.id)"
          />
        </el-select>
      </div>
      <div class="price-filter-field">
        <ClassSelect
          v-model="filters.classId"
          :institution-id="institutionId"
          :campus-id="filters.campusId || null"
          placeholder="全部班级"
        />
      </div>
      <div class="price-filter-field">
        <el-select v-model="filters.termId" clearable placeholder="全部学期">
          <el-option label="全部学期" value="" />
          <el-option
            v-for="term in terms"
            :key="term.id"
            :label="term.termName || term.termCode || `学期 #${term.id}`"
            :value="String(term.id)"
          />
        </el-select>
      </div>
      <div class="price-filter-field">
        <StudentSelect
          v-model="filters.studentId"
          :institution-id="institutionId"
          placeholder="全部学员"
        />
      </div>
      <div class="price-filter-field is-status">
        <el-select v-model="filters.status" clearable placeholder="全部状态">
          <el-option label="全部状态" value="" />
          <el-option label="启用" value="active" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </div>
      <el-button :icon="RefreshLeft" @click="resetFilters">重置</el-button>
      <span class="education-context-text price-count">
        显示 {{ filteredPrices.length }} / 共 {{ prices.length }} 条（启用 {{ activeCount }} 条）
      </span>
    </div>

    <el-table
      :data="filteredPrices"
      border
      row-key="id"
      highlight-current-row
      v-loading="loading"
      :height="tableHeight"
      :empty-text="emptyText"
    >
      <el-table-column label="级别" width="92" align="center">
        <template #default="{ row }">
          <el-tooltip :content="`命中权重 ${specificityScore(row)} 分`" placement="top">
            <el-tag :type="scopeLevel(row).type" effect="light">{{ scopeLevel(row).label }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="校区" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'unlimited-text': !activeDimension(row.campusId) }">{{ campusText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="班级" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'unlimited-text': !activeDimension(row.classId) }">{{ classText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="学期" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'unlimited-text': !activeDimension(row.termId) }">{{ termText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="学员" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'unlimited-text': !activeDimension(row.studentId) }">{{ studentText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="月标准" width="110" align="right">
        <template #default="{ row }">
          <span :class="{ 'unlimited-text': row.monthlyPrice === null || row.monthlyPrice === undefined }">
            {{ moneyText(row.monthlyPrice) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="日标准" width="110" align="right">
        <template #default="{ row }">
          <span :class="{ 'unlimited-text': row.dailyPrice === null || row.dailyPrice === undefined }">
            {{ moneyText(row.dailyPrice) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="86">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      <el-table-column v-if="canConfig" label="操作" width="176" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="success" @click="copyPrice(row)">复制</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="formVisible" ref="formSection" class="price-form">
      <div class="education-table-head price-form-head">
        <span class="panel-title">{{ formTitle }}</span>
        <el-tag :type="formScopeLevel.type" effect="plain">
          当前范围：{{ formScopeLevel.label }}
        </el-tag>
      </div>
      <el-form label-position="top" class="education-form price-form-grid">
        <el-form-item label="校区">
          <el-select v-model="form.campusId" :disabled="Boolean(editingId)" clearable placeholder="不限（所有校区）">
            <el-option label="不限（所有校区）" value="" />
            <el-option
              v-for="campus in campuses"
              :key="campus.id"
              :label="campus.campusName || campus.campusCode || `校区 #${campus.id}`"
              :value="String(campus.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <ClassSelect
            v-model="form.classId"
            :institution-id="institutionId"
            :campus-id="form.campusId || null"
            :disabled="Boolean(editingId)"
            placeholder="不限（所有班级）"
            @change="handleClassChange"
          />
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.termId" :disabled="Boolean(editingId)" clearable placeholder="不限（所有学期）">
            <el-option label="不限（所有学期）" value="" />
            <el-option
              v-for="term in terms"
              :key="term.id"
              :label="term.termName || term.termCode || `学期 #${term.id}`"
              :value="String(term.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学员（学员级覆盖）">
          <StudentSelect
            v-model="form.studentId"
            :institution-id="institutionId"
            :disabled="Boolean(editingId)"
            placeholder="不限（所有学员）"
          />
        </el-form-item>
        <el-form-item label="月标准">
          <el-input-number
            v-model="form.monthlyPrice"
            :min="0"
            :precision="2"
            :step="100"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="日标准">
          <el-input-number
            v-model="form.dailyPrice"
            :min="0"
            :precision="2"
            :step="5"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <div class="price-form-hint">
        四个维度都可留空 = 不限；同一组维度只能有一条价目。月标准与日标准至少填一个，留空的那一档会继续往更宽的范围回落。
      </div>
      <div class="price-form-actions">
        <el-button @click="closeForm">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存价目</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import ClassSelect from './ClassSelect.vue'
import StudentSelect from './StudentSelect.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { classLabelOf, ensureClassLabels } from '@cornerstone/module-education/composables/classLabels'
import { ensureStudentLabels, studentLabelOf } from '@cornerstone/module-education/composables/studentLabels'
import {
  createFeePrice,
  deleteFeePrice,
  fetchFeePrices,
  moneyText,
  updateFeePrice,
  type FeeItemRecord,
  type FeePriceRecord,
} from '@cornerstone/module-education/api/fee'
import type { ClassOption } from '@cornerstone/module-education/api/education'
import type {
  CampusSummary,
  TermSummary,
} from '@cornerstone/module-organization/api/organization'

type ScopeFields = Pick<FeePriceRecord, 'campusId' | 'classId' | 'termId' | 'studentId'>
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    institutionId: string
    item: FeeItemRecord | null
    campuses?: CampusSummary[]
    terms?: TermSummary[]
  }>(),
  { campuses: () => [], terms: () => [] }
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const auth = useAuthStore()
const canConfig = computed(() => auth.hasPermission('education:fee:config:write'))

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const prices = ref<FeePriceRecord[]>([])
const loading = ref(false)
const formError = ref('')
const formVisible = ref(false)
const editingId = ref<string | null>(null)
const submitting = ref(false)
const copyFromText = ref('')
const formSection = ref<HTMLElement | null>(null)
const tableHeight = 'clamp(300px, calc(100vh - 560px), 520px)'

const filters = reactive({
  campusId: '',
  classId: '',
  termId: '',
  studentId: '' as string | null,
  status: '',
})

const form = reactive({
  campusId: '' as string | null,
  classId: '' as string | null,
  termId: '' as string | null,
  studentId: '' as string | null,
  monthlyPrice: null as number | null,
  dailyPrice: null as number | null,
  status: 'active',
  remark: '',
})

const campusMap = computed(
  () =>
    new Map(
      props.campuses.map((campus) => [
        String(campus.id),
        campus.campusName || campus.campusCode || `校区 #${campus.id}`,
      ])
    )
)
const termMap = computed(
  () =>
    new Map(
      props.terms.map((term) => [
        String(term.id),
        term.termName || term.termCode || `学期 #${term.id}`,
      ])
    )
)

const activeCount = computed(
  () => prices.value.filter((price) => price.status === 'active').length
)

const filteredPrices = computed(() => {
  const campusId = filters.campusId
  const classId = filters.classId
  const termId = filters.termId
  const studentId = filters.studentId
  const status = filters.status
  return prices.value
    .filter((row) => {
      // 价目该维度留空 = 不限，任何筛选值都应命中；非空则必须与筛选取值一致
      if (campusId && activeDimension(row.campusId) && String(row.campusId) !== campusId) {
        return false
      }
      if (classId && activeDimension(row.classId) && String(row.classId) !== classId) {
        return false
      }
      if (termId && activeDimension(row.termId) && String(row.termId) !== termId) {
        return false
      }
      if (studentId && activeDimension(row.studentId) && String(row.studentId) !== studentId) {
        return false
      }
      if (status && row.status !== status) {
        return false
      }
      return true
    })
    .sort(
      (left, right) =>
        specificityScore(right) - specificityScore(left) || String(left.id).localeCompare(String(right.id))
    )
})

const emptyText = computed(() => {
  if (prices.value.length > 0) {
    return '没有符合筛选条件的价目，点「重置」看全部。'
  }
  return '该项目还没有价目，建台账时标准会记 0 并提示补充。'
})

const formTitle = computed(() => {
  if (editingId.value) {
    return '编辑价目'
  }
  return copyFromText.value ? `新增价目（复制自：${copyFromText.value}）` : '新增价目'
})

const formScopeLevel = computed(() => scopeLevel(form))

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

/** 0 与空同义：都是「不限」，所以只认非 0 的维度 */
function activeDimension(value?: string | number | null) {
  return value !== null && value !== undefined && String(value) !== '' && String(value) !== '0'
}

/** 与后端取价权重一致：学员 8、班级 4、校区 2、学期 1 */
function specificityScore(scope: ScopeFields) {
  return (
    (activeDimension(scope.studentId) ? 8 : 0) +
    (activeDimension(scope.classId) ? 4 : 0) +
    (activeDimension(scope.campusId) ? 2 : 0) +
    (activeDimension(scope.termId) ? 1 : 0)
  )
}

/** 行的最高档位：列表里一眼看出这条价目是给谁配的 */
function scopeLevel(scope: ScopeFields): { label: string; type: TagType } {
  if (activeDimension(scope.studentId)) {
    return { label: '学员级', type: 'danger' }
  }
  if (activeDimension(scope.classId)) {
    return { label: '班级级', type: 'warning' }
  }
  if (activeDimension(scope.campusId)) {
    return { label: '校区级', type: 'primary' }
  }
  if (activeDimension(scope.termId)) {
    return { label: '学期级', type: 'success' }
  }
  return { label: '全局', type: 'info' }
}

function campusText(row: FeePriceRecord) {
  if (!activeDimension(row.campusId)) {
    return '不限'
  }
  return campusMap.value.get(String(row.campusId)) ?? `校区 #${row.campusId}`
}

function classText(row: FeePriceRecord) {
  return activeDimension(row.classId) ? classLabelOf(String(row.classId)) : '不限'
}

function termText(row: FeePriceRecord) {
  if (!activeDimension(row.termId)) {
    return '不限'
  }
  return termMap.value.get(String(row.termId)) ?? `学期 #${row.termId}`
}

function studentText(row: FeePriceRecord) {
  return activeDimension(row.studentId) ? studentLabelOf(String(row.studentId)) : '不限'
}

function dimensionText(row: FeePriceRecord) {
  const parts: string[] = []
  if (activeDimension(row.campusId)) {
    parts.push(`校区：${campusText(row)}`)
  }
  if (activeDimension(row.classId)) {
    parts.push(`班级：${classText(row)}`)
  }
  if (activeDimension(row.termId)) {
    parts.push(`学期：${termText(row)}`)
  }
  if (activeDimension(row.studentId)) {
    parts.push(`学员：${studentText(row)}`)
  }
  return parts.length ? parts.join(' / ') : '全局（所有学员）'
}

/** 唯一键口径：四个维度任一为空都折算成 0（=不限） */
function scopeKey(scope: ScopeFields) {
  return [scope.campusId, scope.classId, scope.termId, scope.studentId]
    .map((value) => (activeDimension(value) ? String(value) : '0'))
    .join('|')
}

async function loadPrices() {
  if (!props.item) {
    prices.value = []
    return
  }
  loading.value = true
  formError.value = ''
  try {
    prices.value = await fetchFeePrices({
      institutionId: props.institutionId,
      feeItemId: props.item.id,
    })
    // 行上只有 id：班级/学员名称要按 id 补取，否则大 ID 会直接铺在表格里
    void ensureClassLabels(
      props.institutionId,
      prices.value.map((price) => price.classId)
    )
    void ensureStudentLabels(
      props.institutionId,
      prices.value.map((price) => price.studentId)
    )
  } catch (error) {
    prices.value = []
    formError.value = toMessage(error, '价目加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.campusId = ''
  filters.classId = ''
  filters.termId = ''
  filters.studentId = ''
  filters.status = ''
}

function handleOpen() {
  closeForm()
  resetFilters()
  void loadPrices()
}

function resetForm() {
  form.campusId = ''
  form.classId = ''
  form.termId = ''
  form.studentId = ''
  form.monthlyPrice = null
  form.dailyPrice = null
  form.status = 'active'
  form.remark = ''
  formError.value = ''
  copyFromText.value = ''
}

function fillFormFrom(row: FeePriceRecord) {
  form.campusId = activeDimension(row.campusId) ? String(row.campusId) : ''
  form.classId = activeDimension(row.classId) ? String(row.classId) : ''
  form.termId = activeDimension(row.termId) ? String(row.termId) : ''
  form.studentId = activeDimension(row.studentId) ? String(row.studentId) : ''
  form.monthlyPrice = row.monthlyPrice ?? null
  form.dailyPrice = row.dailyPrice ?? null
  form.status = row.status ?? 'active'
  form.remark = row.remark ?? ''
}

function openForm(row?: FeePriceRecord) {
  resetForm()
  editingId.value = row?.id ?? null
  if (row) {
    fillFormFrom(row)
  }
  formVisible.value = true
  scrollToForm()
}

/** 复制成新增：维度可改，方便照着学员级价目给另一个学员配一条 */
function copyPrice(row: FeePriceRecord) {
  resetForm()
  editingId.value = null
  fillFormFrom(row)
  copyFromText.value = dimensionText(row)
  formVisible.value = true
  scrollToForm()
}

/** 表单在表格下方，点行内「编辑/复制」后滚进来，不然要自己找 */
function scrollToForm() {
  void nextTick(() => formSection.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
}

function closeForm() {
  formVisible.value = false
  editingId.value = null
  formError.value = ''
  copyFromText.value = ''
}

/** 选了班级就把它的校区带出来（仅在没手选时），少维护一个维度 */
function handleClassChange(option: ClassOption | null) {
  if (option?.campusId && !form.campusId) {
    form.campusId = String(option.campusId)
  }
}

async function submitForm() {
  if (!props.item) {
    formError.value = '请先选择收费项目'
    return
  }
  if (form.monthlyPrice === null && form.dailyPrice === null) {
    formError.value = '月标准与日标准至少填一个'
    return
  }
  const payload = {
    monthlyPrice: form.monthlyPrice,
    dailyPrice: form.dailyPrice,
    status: form.status,
    remark: trimToUndefined(form.remark),
  }
  if (!editingId.value) {
    const duplicate = prices.value.find((price) => scopeKey(price) === scopeKey(form))
    if (duplicate) {
      formError.value = `已存在相同维度的价目（${dimensionText(duplicate)}），请直接编辑那一条，或调整维度。`
      return
    }
  }
  submitting.value = true
  formError.value = ''
  try {
    if (editingId.value) {
      await updateFeePrice(editingId.value, payload)
      ElMessage.success('价目已更新')
    } else {
      await createFeePrice({
        institutionId: props.institutionId,
        feeItemId: props.item.id,
        campusId: form.campusId || null,
        classId: form.classId || null,
        termId: form.termId || null,
        studentId: form.studentId || null,
        ...payload,
      })
      ElMessage.success('价目已新增')
    }
    closeForm()
    await loadPrices()
  } catch (error) {
    formError.value = toMessage(error, editingId.value ? '更新价目失败' : '新增价目失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: FeePriceRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除价目「${dimensionText(row)}」吗？已建台账的标准是快照，不会回改。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteFeePrice(row.id)
    ElMessage.success('价目已删除')
    await loadPrices()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除价目失败'))
  }
}

// 换校区筛选时把班级清掉，否则会留下一个不属于该校区的班，筛出来永远是 0 条
watch(
  () => filters.campusId,
  () => {
    filters.classId = ''
  }
)
</script>

<style scoped>
.price-toolbar {
  align-items: center;
}

.price-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.price-filter-field {
  width: 176px;
}

.price-filter-field.is-status {
  width: 124px;
}

.price-filter-field :deep(.archive-select) {
  width: 100%;
}

.price-count {
  margin-left: auto;
  white-space: nowrap;
}

.unlimited-text {
  color: var(--el-text-color-placeholder);
}

.price-form {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.price-form-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.price-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
}

.price-form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.price-form-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

:global(.fee-price-dialog .el-dialog__body) {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
</style>
