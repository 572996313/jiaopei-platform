<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="table-toolbar">
      <el-button
        v-if="canConfig"
        v-permission="'education:fee:config:write'"
        type="primary"
        @click="openDialog('create')"
        >新增收费项目</el-button
      >
      <el-button :icon="Refresh" circle :loading="loading" @click="refreshAll" />
      <span class="education-context-text">
        排序号决定导出矩阵块内的项目行序；停用后生成台账不再自动建这一行，已建台账不受影响。
      </span>
    </div>

    <article class="panel-card education-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="items"
          border
          row-key="id"
          highlight-current-row
          v-loading="loading"
          :height="height"
          :empty-text="loading ? '' : '当前机构还没有收费项目，点击「新增收费项目」录入。'"
        >
          <el-table-column prop="itemCode" label="项目编码" width="160" fixed="left" sortable />
          <el-table-column
            prop="itemName"
            label="项目名称"
            min-width="180"
            fixed="left"
            show-overflow-tooltip
            sortable
          />
          <el-table-column label="计费方式" width="120" prop="billingMode" sortable>
            <template #default="{ row }">{{ billingModeLabel(row.billingMode) }}</template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序号" width="100" align="right" sortable />
          <el-table-column label="状态" width="100" prop="status" sortable>
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column v-if="canConfig" label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="openDialog('edit', row)">编辑</el-button>
                <el-button link type="primary" @click="openPriceDialog(row)">价目</el-button>
                <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>
    </article>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增收费项目' : '编辑收费项目'"
      width="560px"
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
        ref="feeItemFormRef"
        :model="form"
        :rules="feeItemRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="项目编码">
          <CodeInput v-model="form.itemCode" resource="fee_item" :mode="dialogMode" />
        </el-form-item>
        <el-form-item label="项目名称" prop="itemName">
          <el-input v-model="form.itemName" placeholder="如：保教费、伙食费" />
        </el-form-item>
        <el-form-item label="计费方式" prop="billingMode">
          <el-select v-model="form.billingMode" style="width: 100%">
            <el-option label="按月（月标准 × 月份系数）" value="monthly" />
            <el-option label="按天（出勤天数 × 日标准）" value="daily" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ dialogMode === 'create' ? '提交项目' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <FeePriceDialog
      v-model="priceDialogVisible"
      :institution-id="institutionId || ''"
      :item="priceItem"
      :campuses="campuses"
      :terms="terms"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import CodeInput from '@cornerstone/core/components/CodeInput.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { toMessage } from '@cornerstone/core/utils/error'
import { trimToUndefined } from '@cornerstone/core/utils/text'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import {
  billingModeLabel,
  createFeeItem,
  deleteFeeItem,
  fetchFeeItems,
  updateFeeItem,
  type FeeItemRecord,
} from '@cornerstone/module-education/api/fee'
import FeePriceDialog from './FeePriceDialog.vue'

const institutionStore = useInstitutionStore()
const auth = useAuthStore()
const { institutionId, campuses, terms, loadScope } = useEducationScope()

const canConfig = computed(() => auth.hasPermission('education:fee:config:write'))

const items = ref<FeeItemRecord[]>([])
const loading = ref(false)
const pageError = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | null>(null)
const submitting = ref(false)
const formError = ref('')
const feeItemFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
const feeItemRules: FormRules = {
  itemName: [{ required: true, whitespace: true, message: '请填写收费项目名称', trigger: 'blur' }],
  billingMode: [{ required: true, message: '请选择计费方式', trigger: 'change' }],
}

const priceDialogVisible = ref(false)
const priceItem = ref<FeeItemRecord | null>(null)

const form = reactive({
  itemCode: '',
  itemName: '',
  billingMode: 'monthly',
  sortNo: 10,
  status: 'active',
  remark: '',
})

function resetForm() {
  form.itemCode = ''
  form.itemName = ''
  form.billingMode = 'monthly'
  form.sortNo = 10
  form.status = 'active'
  form.remark = ''
  formError.value = ''
}

async function loadLookups() {
  try {
    await loadScope()
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  }
}

async function loadItems() {
  if (!institutionId.value) {
    items.value = []
    return
  }
  loading.value = true
  pageError.value = ''
  try {
    items.value = await fetchFeeItems({ institutionId: institutionId.value })
  } catch (error) {
    items.value = []
    pageError.value = toMessage(error, '收费项目加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await loadLookups()
    await loadItems()
  } finally {
    loading.value = false
  }
}

function openDialog(mode: 'create' | 'edit', row?: FeeItemRecord) {
  dialogMode.value = mode
  if (mode === 'edit' && row) {
    editingId.value = row.id
    form.itemCode = row.itemCode ?? ''
    form.itemName = row.itemName ?? ''
    form.billingMode = row.billingMode ?? 'monthly'
    form.sortNo = row.sortNo ?? 10
    form.status = row.status ?? 'active'
    form.remark = row.remark ?? ''
    formError.value = ''
  } else {
    editingId.value = null
    resetForm()
  }
  dialogVisible.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => feeItemFormRef.value?.clearValidate())
}

async function submit() {
  if (!institutionId.value) {
    formError.value = '请先选择机构范围'
    return
  }
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await feeItemFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    if (dialogMode.value === 'create') {
      await createFeeItem({
        institutionId: institutionId.value,
        itemCode: trimToUndefined(form.itemCode),
        itemName: form.itemName.trim(),
        billingMode: form.billingMode,
        sortNo: form.sortNo,
        status: form.status,
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('收费项目已新增')
    } else if (editingId.value) {
      await updateFeeItem(editingId.value, {
        itemCode: trimToUndefined(form.itemCode),
        itemName: form.itemName.trim(),
        billingMode: form.billingMode,
        sortNo: form.sortNo,
        status: form.status,
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('收费项目已更新')
    }
    dialogVisible.value = false
    await loadItems()
  } catch (error) {
    formError.value = toMessage(
      error,
      dialogMode.value === 'create' ? '新增收费项目失败' : '更新收费项目失败'
    )
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: FeeItemRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除收费项目「${row.itemName || row.itemCode || row.id}」吗？已被价目或台账引用的项目会被后端拒绝，改停用即可。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteFeeItem(row.id)
    ElMessage.success('收费项目已删除')
    await loadItems()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除收费项目失败'))
  }
}

function openPriceDialog(row: FeeItemRecord) {
  priceItem.value = row
  priceDialogVisible.value = true
}

watch(institutionId, () => {
  void refreshAll()
})

onMounted(async () => {
  await institutionStore.loadInstitutions()
  await refreshAll()
})
</script>
