<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? '新增收费台账' : '编辑收费台账'"
    width="1080px"
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

    <el-form
      ref="ledgerFormRef"
      :model="form"
      :rules="ledgerRules"
      label-position="top"
      class="education-form ledger-head"
      scroll-to-error
      @submit.prevent
    >
      <el-form-item label="班级" prop="classId">
        <ClassSelect
          v-model="form.classId"
          :institution-id="institutionId"
          :disabled="mode === 'edit'"
          @change="handleClassChange"
        />
      </el-form-item>
      <el-form-item label="学期" prop="termId">
        <el-select
          v-model="form.termId"
          :disabled="mode === 'edit'"
          placeholder="选择学期"
          style="width: 100%"
        >
          <el-option
            v-for="term in terms"
            :key="term.id"
            :label="term.termName || term.termCode || `学期 #${term.id}`"
            :value="String(term.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="学员" prop="studentId">
        <StudentSelect
          v-model="form.studentId"
          :institution-id="institutionId"
          :disabled="mode === 'edit'"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 100%">
          <el-option label="在收" value="active" />
          <el-option label="已结清" value="settled" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注" class="ledger-remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如：国兴QQ班转入" />
      </el-form-item>
    </el-form>

    <template v-if="mode === 'create'">
      <el-alert
        title="保存后按当前启用的收费项目和价目自动建行，建好后可在「编辑」里逐项调整月数与标准。"
        type="info"
        show-icon
        :closable="false"
      />
    </template>

    <template v-else>
      <div class="table-toolbar ledger-items-toolbar">
        <el-select
          v-model="addFeeItemId"
          placeholder="选择要加入的收费项目"
          filterable
          style="width: 240px"
        >
          <el-option
            v-for="item in addableFeeItems"
            :key="item.id"
            :label="item.itemName || item.itemCode || `项目 #${item.id}`"
            :value="String(item.id)"
          />
        </el-select>
        <el-button :disabled="!addFeeItemId" @click="addItemRow">加入项目</el-button>
        <span class="education-context-text">
          标准留空 = 按价目表取价；显式填 0 = 免费。上期结余正数表示预收。
        </span>
      </div>

      <el-table :data="form.items" border row-key="feeItemId" height="360" class="ledger-items-table">
        <el-table-column label="收费项目" width="140" fixed="left">
          <template #default="{ row }">
            {{ feeItemName(row.feeItemId) }}
          </template>
        </el-table-column>
        <el-table-column label="计费方式" width="90">
          <template #default="{ row }">{{ billingModeLabel(billingModeOf(row.feeItemId)) }}</template>
        </el-table-column>
        <el-table-column label="收费月数" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.billableMonths"
              :min="0"
              :precision="2"
              :step="0.5"
              controls-position="right"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="月标准" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.monthlyStandard"
              :min="0"
              :precision="2"
              :step="100"
              controls-position="right"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="日标准" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.dailyRate"
              :min="0"
              :precision="2"
              :step="5"
              controls-position="right"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="上期结余" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.openingBalance"
              :precision="2"
              :step="100"
              controls-position="right"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="其他抵扣" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.otherDeduction"
              :min="0"
              :precision="2"
              :step="100"
              controls-position="right"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="应收（预览）" width="120" align="right">
          <template #default="{ row }">{{ moneyText(previewReceivable(row)) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItemRow($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ mode === 'create' ? '提交建账' : '保存修改' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, nextTick, reactive, ref } from 'vue'
import ClassSelect from './ClassSelect.vue'
import StudentSelect from './StudentSelect.vue'
import {
  billingModeLabel,
  createFeeLedger,
  moneyText,
  updateFeeLedger,
  type FeeItemRecord,
  type FeeLedgerDetailView,
  type FeeLedgerItemPayload,
  type FeeLedgerItemView,
} from '@cornerstone/module-education/api/fee'
import type { ClassOption } from '@cornerstone/module-education/api/education'
import type { TermSummary } from '@cornerstone/module-organization/api/organization'

interface ItemRow {
  feeItemId: string
  billableMonths: number | null
  monthlyStandard: number | null
  dailyRate: number | null
  openingBalance: number | null
  otherDeduction: number | null
  remark: string
}

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  institutionId: string
  feeItems: FeeItemRecord[]
  terms: TermSummary[]
  ledger: FeeLedgerDetailView | null
  /** 从列表筛选栏进「新增台账」时，把当前学期/班级带进来，少选两次 */
  defaultTermId?: string | null
  defaultClassId?: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const submitting = ref(false)
const formError = ref('')
const addFeeItemId = ref('')
const ledgerFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案。
    编辑态下班级/学期/学员锁定只改状态与行项目，三个必填规则仍在但值不会变空。 */
const ledgerRules: FormRules = {
  classId: [{ required: true, message: '请选择班级', trigger: 'change' }],
  termId: [{ required: true, message: '请选择学期', trigger: 'change' }],
  studentId: [{ required: true, message: '请选择学员', trigger: 'change' }],
}

const form = reactive({
  classId: '' as string | null,
  termId: '' as string | null,
  studentId: '' as string | null,
  status: 'active',
  remark: '',
  items: [] as ItemRow[],
})

const feeItemMap = computed(
  () => new Map(props.feeItems.map((item) => [String(item.id), item]))
)

const addableFeeItems = computed(() => {
  const used = new Set(form.items.map((row) => row.feeItemId))
  return props.feeItems.filter(
    (item) => item.status !== 'disabled' && !used.has(String(item.id))
  )
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function feeItemName(id: string) {
  const item = feeItemMap.value.get(String(id))
  return item?.itemName || item?.itemCode || `项目 #${id}`
}

function billingModeOf(id: string) {
  return feeItemMap.value.get(String(id))?.billingMode ?? null
}

/** 录入时的预览口径：月数 × 月标准 − 上期结余 − 其他抵扣；最终金额仍以服务端为准 */
function previewReceivable(row: ItemRow) {
  const months = Number(row.billableMonths ?? 0)
  const standard = Number(row.monthlyStandard ?? 0)
  const opening = Number(row.openingBalance ?? 0)
  const deduction = Number(row.otherDeduction ?? 0)
  return months * standard - opening - deduction
}

function resetForm() {
  form.classId = ''
  form.termId = ''
  form.studentId = ''
  form.status = 'active'
  form.remark = ''
  form.items = []
  addFeeItemId.value = ''
  formError.value = ''
}

function rowOf(item: FeeLedgerItemView): ItemRow {
  return {
    feeItemId: String(item.feeItemId),
    billableMonths: item.billableMonths ?? null,
    monthlyStandard: item.monthlyStandard ?? null,
    dailyRate: item.dailyRate ?? null,
    openingBalance: item.openingBalance ?? null,
    otherDeduction: item.otherDeduction ?? null,
    remark: item.remark ?? '',
  }
}

function handleOpen() {
  resetForm()
  if (props.mode === 'create') {
    form.termId = props.defaultTermId ? String(props.defaultTermId) : ''
    form.classId = props.defaultClassId ? String(props.defaultClassId) : ''
  }
  if (props.mode === 'edit' && props.ledger) {
    form.classId = props.ledger.classId ? String(props.ledger.classId) : ''
    form.termId = props.ledger.termId ? String(props.ledger.termId) : ''
    form.studentId = props.ledger.studentId ? String(props.ledger.studentId) : ''
    form.status = props.ledger.status ?? 'active'
    form.remark = props.ledger.remark ?? ''
    form.items = props.ledger.items.map(rowOf)
  }
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => ledgerFormRef.value?.clearValidate())
}

function handleClassChange(option: ClassOption | null) {
  // 班级自带学期，建账时带出来，减少一次手选；编辑态不改归属
  if (props.mode === 'create' && option?.termId) {
    form.termId = String(option.termId)
  }
}

function addItemRow() {
  const id = addFeeItemId.value
  if (!id || form.items.some((row) => row.feeItemId === id)) {
    return
  }
  form.items.push({
    feeItemId: id,
    billableMonths: null,
    monthlyStandard: null,
    dailyRate: null,
    openingBalance: null,
    otherDeduction: null,
    remark: '',
  })
  addFeeItemId.value = ''
}

function removeItemRow(index: number) {
  form.items.splice(index, 1)
}

function itemsPayload(): FeeLedgerItemPayload[] {
  return form.items.map((row) => ({
    feeItemId: row.feeItemId,
    billableMonths: row.billableMonths,
    monthlyStandard: row.monthlyStandard,
    dailyRate: row.dailyRate,
    openingBalance: row.openingBalance,
    otherDeduction: row.otherDeduction,
    remark: trimToUndefined(row.remark),
  }))
}

async function submit() {
  if (!props.institutionId) {
    formError.value = '请先选择机构范围'
    return
  }
  if (props.mode === 'create') {
    // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案。
    // 编辑态班级/学期/学员锁定只改状态与行项目，与原口径一致不再校验这三个归属字段
    const valid = await ledgerFormRef.value?.validate().catch(() => false)
    if (valid === false) {
      return
    }
  }
  submitting.value = true
  formError.value = ''
  try {
    if (props.mode === 'create') {
      await createFeeLedger({
        institutionId: props.institutionId,
        classId: form.classId || '',
        termId: form.termId || '',
        studentId: form.studentId || '',
        status: form.status,
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('收费台账已创建，收费项目行已按价目自动生成')
    } else if (props.ledger) {
      await updateFeeLedger(props.ledger.id, {
        status: form.status,
        remark: trimToUndefined(form.remark),
        items: itemsPayload(),
      })
      ElMessage.success('收费台账已更新')
    }
    visible.value = false
    emit('saved')
  } catch (error) {
    formError.value = toMessage(
      error,
      props.mode === 'create' ? '新建收费台账失败' : '更新收费台账失败'
    )
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.ledger-head {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
}

.ledger-head .ledger-remark {
  grid-column: 1 / -1;
}

.ledger-items-toolbar {
  margin-bottom: 8px;
}

.ledger-items-table :deep(.el-table__empty-block) {
  min-height: calc(360px - 38px);
}
</style>
