<template>
  <el-dialog
    v-model="visible"
    :title="`价目 - ${item?.itemName || item?.itemCode || ''}`"
    class="fee-price-dialog"
    width="min(1280px, 94vw)"
    top="6vh"
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

    <div class="table-toolbar">
      <el-button v-if="canConfig && !formVisible" type="primary" @click="openForm()">
        新增价目
      </el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="loadPrices" />
      <span class="education-context-text">
        取价按「学员 &gt; 班级 &gt; 校区 &gt; 学期」取最具体的一档；月标准与日标准各自独立回落。
      </span>
    </div>

    <el-table
      :data="prices"
      border
      row-key="id"
      highlight-current-row
      v-loading="loading"
      height="min(460px, 46vh)"
      empty-text="该项目还没有价目，建台账时标准会记 0 并提示补充。"
    >
      <el-table-column label="适用维度" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ dimensionText(row) }}</template>
      </el-table-column>
      <el-table-column label="月标准" width="120" align="right">
        <template #default="{ row }">{{ moneyText(row.monthlyPrice) }}</template>
      </el-table-column>
      <el-table-column label="日标准" width="120" align="right">
        <template #default="{ row }">{{ moneyText(row.dailyPrice) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="canConfig" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="formVisible" class="price-form">
      <div class="education-table-head">
        <span class="panel-title">{{ editingId ? '编辑价目' : '新增价目' }}</span>
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
      <div class="price-form-actions">
        <el-button @click="closeForm">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存价目</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, reactive, ref } from 'vue'
import ClassSelect from './ClassSelect.vue'
import StudentSelect from './StudentSelect.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { classLabelOf, ensureClassLabels } from '@cornerstone/module-education/composables/classLabels'
import { studentLabelOf } from '@cornerstone/module-education/composables/studentLabels'
import {
  createFeePrice,
  deleteFeePrice,
  fetchFeePrices,
  moneyText,
  updateFeePrice,
  type FeeItemRecord,
  type FeePriceRecord,
} from '@cornerstone/module-education/api/fee'
import type {
  CampusSummary,
  TermSummary,
} from '@cornerstone/module-organization/api/organization'

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

function dimensionText(row: FeePriceRecord) {
  const parts: string[] = []
  if (activeDimension(row.campusId)) {
    parts.push(`校区：${campusMap.value.get(String(row.campusId)) ?? `校区 #${row.campusId}`}`)
  }
  if (activeDimension(row.classId)) {
    parts.push(`班级：${classLabelOf(String(row.classId))}`)
  }
  if (activeDimension(row.termId)) {
    parts.push(`学期：${termMap.value.get(String(row.termId)) ?? `学期 #${row.termId}`}`)
  }
  if (activeDimension(row.studentId)) {
    parts.push(`学员：${studentLabelOf(String(row.studentId))}`)
  }
  return parts.length ? parts.join(' / ') : '全局（所有学员）'
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
    void ensureClassLabels(
      props.institutionId,
      prices.value.map((price) => price.classId)
    )
  } catch (error) {
    prices.value = []
    formError.value = toMessage(error, '价目加载失败')
  } finally {
    loading.value = false
  }
}

function handleOpen() {
  closeForm()
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
}

function openForm(row?: FeePriceRecord) {
  resetForm()
  if (row) {
    editingId.value = row.id
    form.campusId = activeDimension(row.campusId) ? String(row.campusId) : ''
    form.classId = activeDimension(row.classId) ? String(row.classId) : ''
    form.termId = activeDimension(row.termId) ? String(row.termId) : ''
    form.studentId = activeDimension(row.studentId) ? String(row.studentId) : ''
    form.monthlyPrice = row.monthlyPrice ?? null
    form.dailyPrice = row.dailyPrice ?? null
    form.status = row.status ?? 'active'
    form.remark = row.remark ?? ''
  } else {
    editingId.value = null
  }
  formVisible.value = true
}

function closeForm() {
  formVisible.value = false
  editingId.value = null
  formError.value = ''
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
  submitting.value = true
  formError.value = ''
  const payload = {
    monthlyPrice: form.monthlyPrice,
    dailyPrice: form.dailyPrice,
    status: form.status,
    remark: trimToUndefined(form.remark),
  }
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
</script>

<style scoped>
.price-form {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.price-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
}

.price-form-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

:global(.fee-price-dialog .el-dialog__body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}
</style>
