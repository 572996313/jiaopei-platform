<template>
  <section class="education-page">
    <article v-if="dictSectionError" class="education-empty">
      {{ dictSectionError }}
    </article>
    <template v-else>
      <div class="education-table-head">
        <div class="panel-title">字典管理</div>
        <div class="education-context-text">
          维护全局字典类型与条目,业务表单的下拉选项与列表回显实时生效。
        </div>
      </div>

      <div class="system-dict-grid">
        <div class="panel-card">
          <div class="education-table-head">
            <div class="panel-title">字典类型</div>
            <el-button
              v-if="permissions.canManageDicts"
              type="primary"
              size="small"
              @click="openCreateDictTypeDialog"
              >新增类型</el-button
            >
          </div>
          <el-table
            :data="dictTypes"
            border
            highlight-current-row
            v-loading="dictTypesLoading"
            :height="TABLE_FILL_HEIGHT"
            @current-change="handleDictTypeChange"
          >
            <el-table-column
              prop="typeCode"
              label="类型编码"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="typeName"
              label="类型名称"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{
                  statusLabel(row.status, statusLabelMap)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="permissions.canManageDicts" label="操作" width="140">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click.stop="openEditDictTypeDialog(row)"
                  >编辑</el-button
                >
                <el-button link type="danger" size="small" @click.stop="removeDictType(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="panel-card">
          <div class="education-table-head">
            <div class="panel-title">字典条目</div>
            <el-button
              v-if="permissions.canManageDicts"
              type="primary"
              size="small"
              :disabled="!selectedDictTypeCode"
              @click="openCreateDictItemDialog"
              >新增条目</el-button
            >
          </div>
          <div class="education-context-text system-dict-current">
            当前类型:{{ selectedDictTypeCode || '未选择' }}
          </div>
          <el-table
            :data="selectedDictItems"
            border
            v-loading="dictItemsLoading"
            :height="TABLE_FILL_HEIGHT"
          >
            <el-table-column prop="itemCode" label="编码" width="140" />
            <el-table-column prop="itemName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="itemValue" label="值" min-width="140" show-overflow-tooltip />
            <el-table-column prop="sortNo" label="排序" width="80" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{
                  statusLabel(row.status, statusLabelMap)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="permissions.canManageDicts" label="操作" width="140">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditDictItemDialog(row)"
                  >编辑</el-button
                >
                <el-button link type="danger" size="small" @click="removeDictItem(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <el-dialog
      v-model="dictTypeDialogVisible"
      :title="dictTypeDialogMode === 'create' ? '新增字典类型' : '编辑字典类型'"
      width="520px"
    >
      <el-alert
        v-if="dictTypeFormError"
        :title="dictTypeFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="类型编码">
          <el-input
            v-model="dictTypeForm.typeCode"
            :disabled="dictTypeDialogMode === 'edit'"
            placeholder="如 order_status"
          />
        </el-form-item>
        <el-form-item label="类型名称">
          <el-input v-model="dictTypeForm.typeName" placeholder="请输入字典类型名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dictTypeForm.status">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="dictTypeForm.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictTypeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dictTypeSubmitting" @click="submitDictType">
          {{ dictTypeDialogMode === 'create' ? '创建类型' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dictItemDialogVisible"
      :title="dictItemDialogMode === 'create' ? '新增字典条目' : '编辑字典条目'"
      width="520px"
    >
      <el-alert
        v-if="dictItemFormError"
        :title="dictItemFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <div class="education-context-text">所属类型:{{ selectedDictTypeCode || '未选择' }}</div>
      <el-form label-position="top" class="education-form">
        <el-form-item label="条目编码">
          <el-input
            v-model="dictItemForm.itemCode"
            :disabled="dictItemDialogMode === 'edit'"
            placeholder="如 enabled"
          />
        </el-form-item>
        <el-form-item label="条目名称">
          <el-input v-model="dictItemForm.itemName" placeholder="展示名称,如 启用" />
        </el-form-item>
        <el-form-item label="条目值">
          <el-input v-model="dictItemForm.itemValue" placeholder="业务存储值,一般与编码一致" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dictItemForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dictItemForm.status">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="dictItemForm.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictItemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dictItemSubmitting" @click="submitDictItem">
          {{ dictItemDialogMode === 'create' ? '创建条目' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_FILL_HEIGHT } from '@cornerstone/core/utils/table-view'
import { computed, onMounted, reactive, ref } from 'vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import {
  createSystemDictItem,
  createSystemDictType,
  deleteSystemDictItem,
  deleteSystemDictType,
  fetchSystemDictItems,
  fetchSystemDictTypes,
  updateSystemDictItem,
  updateSystemDictType,
} from '@cornerstone/core/api/system'
import type { DictItemSummary, DictTypeSummary } from '@cornerstone/core/api/system'
import {
  ensureStatusDictLoaded,
  refreshStatusDictCache,
  statusLabel,
  statusTagType,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const dictStore = useDictStore()
const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const dictSectionError = ref('')
const dictTypesLoading = ref(false)
const dictItemsLoading = ref(false)
const dictTypes = ref<DictTypeSummary[]>([])
const dictItemsMap = ref<Record<string, DictItemSummary[]>>({})
const selectedDictTypeCode = ref('common_status')
const selectedDictType = ref<DictTypeSummary | null>(null)
const selectedDictItem = ref<DictItemSummary | null>(null)
const selectedDictItems = computed(() => dictItemsMap.value[selectedDictTypeCode.value] || [])

const dictTypeDialogVisible = ref(false)
const dictTypeDialogMode = ref<'create' | 'edit'>('create')
const dictTypeSubmitting = ref(false)
const dictTypeFormError = ref('')
const dictTypeForm = reactive({
  typeCode: '',
  typeName: '',
  status: 'active',
  remark: '',
})

const dictItemDialogVisible = ref(false)
const dictItemDialogMode = ref<'create' | 'edit'>('create')
const dictItemSubmitting = ref(false)
const dictItemFormError = ref('')
const dictItemForm = reactive({
  itemCode: '',
  itemName: '',
  itemValue: '',
  sortNo: 0,
  status: 'active',
  remark: '',
})

async function loadDictItems(typeCode: string) {
  const items = await fetchSystemDictItems(typeCode)
  dictItemsMap.value = {
    ...dictItemsMap.value,
    [typeCode]: items,
  }
  if (typeCode === 'common_status') {
    refreshStatusDictCache(items)
  }
}

async function reloadDictTypes(preferTypeCode?: string) {
  dictTypes.value = await fetchSystemDictTypes()
  const target = preferTypeCode || selectedDictTypeCode.value
  if (target && dictTypes.value.some((item) => item.typeCode === target)) {
    selectedDictTypeCode.value = target
    if (!dictItemsMap.value[target]) {
      await loadDictItems(target)
    }
  }
}

function openCreateDictTypeDialog() {
  dictTypeDialogMode.value = 'create'
  selectedDictType.value = null
  dictTypeForm.typeCode = ''
  dictTypeForm.typeName = ''
  dictTypeForm.status = statusOptions.value[0]?.value || 'active'
  dictTypeForm.remark = ''
  dictTypeFormError.value = ''
  dictTypeDialogVisible.value = true
}

function openEditDictTypeDialog(row: DictTypeSummary) {
  dictTypeDialogMode.value = 'edit'
  selectedDictType.value = row
  dictTypeForm.typeCode = row.typeCode
  dictTypeForm.typeName = row.typeName || ''
  dictTypeForm.status = row.status || 'active'
  dictTypeForm.remark = row.remark || ''
  dictTypeFormError.value = ''
  dictTypeDialogVisible.value = true
}

async function submitDictType() {
  const typeCode = trimToUndefined(dictTypeForm.typeCode)
  const typeName = trimToUndefined(dictTypeForm.typeName)
  if (dictTypeDialogMode.value === 'create' && !typeCode) {
    dictTypeFormError.value = '请填写类型编码'
    return
  }
  if (!typeName) {
    dictTypeFormError.value = '请填写类型名称'
    return
  }

  dictTypeSubmitting.value = true
  dictTypeFormError.value = ''
  try {
    if (dictTypeDialogMode.value === 'create') {
      const created = await createSystemDictType({
        typeCode: typeCode!,
        typeName,
        status: dictTypeForm.status || 'active',
        remark: trimToUndefined(dictTypeForm.remark),
      })
      await reloadDictTypes(created.typeCode)
      dictStore.invalidate(created.typeCode)
      ElMessage.success('字典类型已创建')
    } else if (selectedDictType.value) {
      const updated = await updateSystemDictType(selectedDictType.value.id, {
        typeName,
        status: dictTypeForm.status || 'active',
        remark: trimToUndefined(dictTypeForm.remark),
      })
      await reloadDictTypes(updated.typeCode)
      dictStore.invalidate(updated.typeCode)
      ElMessage.success('字典类型已保存')
    }
    dictTypeDialogVisible.value = false
  } catch (error) {
    dictTypeFormError.value = toMessage(error, '字典类型保存失败')
  } finally {
    dictTypeSubmitting.value = false
  }
}

async function removeDictType(row: DictTypeSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除字典类型「${row.typeName || row.typeCode}」吗?类型下存在条目时将无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteSystemDictType(row.id)
    const nextItems = { ...dictItemsMap.value }
    delete nextItems[row.typeCode]
    dictItemsMap.value = nextItems
    if (selectedDictTypeCode.value === row.typeCode) {
      selectedDictTypeCode.value =
        dictTypes.value.find((item) => item.typeCode !== row.typeCode)?.typeCode || ''
    }
    await reloadDictTypes(selectedDictTypeCode.value || undefined)
    dictStore.invalidate(row.typeCode)
    ElMessage.success('字典类型已删除')
  } catch (error) {
    ElMessage.error(toMessage(error, '字典类型删除失败'))
  }
}

function openCreateDictItemDialog() {
  if (!selectedDictTypeCode.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  dictItemDialogMode.value = 'create'
  selectedDictItem.value = null
  dictItemForm.itemCode = ''
  dictItemForm.itemName = ''
  dictItemForm.itemValue = ''
  dictItemForm.sortNo = 0
  dictItemForm.status = statusOptions.value[0]?.value || 'active'
  dictItemForm.remark = ''
  dictItemFormError.value = ''
  dictItemDialogVisible.value = true
}

function openEditDictItemDialog(row: DictItemSummary) {
  dictItemDialogMode.value = 'edit'
  selectedDictItem.value = row
  dictItemForm.itemCode = row.itemCode
  dictItemForm.itemName = row.itemName || ''
  dictItemForm.itemValue = row.itemValue || ''
  dictItemForm.sortNo = row.sortNo ?? 0
  dictItemForm.status = row.status || 'active'
  dictItemForm.remark = row.remark || ''
  dictItemFormError.value = ''
  dictItemDialogVisible.value = true
}

async function submitDictItem() {
  const itemCode = trimToUndefined(dictItemForm.itemCode)
  const itemName = trimToUndefined(dictItemForm.itemName)
  const itemValue = trimToUndefined(dictItemForm.itemValue)
  if (dictItemDialogMode.value === 'create' && !itemCode) {
    dictItemFormError.value = '请填写条目编码'
    return
  }
  if (!itemName) {
    dictItemFormError.value = '请填写条目名称'
    return
  }
  if (!itemValue) {
    dictItemFormError.value = '请填写条目值'
    return
  }

  const targetType = dictTypes.value.find((item) => item.typeCode === selectedDictTypeCode.value)
  if (!targetType) {
    dictItemFormError.value = '请先选择字典类型'
    return
  }

  dictItemSubmitting.value = true
  dictItemFormError.value = ''
  try {
    if (dictItemDialogMode.value === 'create') {
      await createSystemDictItem({
        typeId: targetType.id,
        itemCode: itemCode!,
        itemName,
        itemValue,
        sortNo: dictItemForm.sortNo ?? 0,
        status: dictItemForm.status || 'active',
        remark: trimToUndefined(dictItemForm.remark),
      })
      ElMessage.success('字典条目已创建')
    } else if (selectedDictItem.value) {
      await updateSystemDictItem(selectedDictItem.value.id, {
        itemName,
        itemValue,
        sortNo: dictItemForm.sortNo ?? 0,
        status: dictItemForm.status || 'active',
        remark: trimToUndefined(dictItemForm.remark),
      })
      ElMessage.success('字典条目已保存')
    }
    await loadDictItems(targetType.typeCode)
    dictStore.invalidate(targetType.typeCode)
    dictItemDialogVisible.value = false
  } catch (error) {
    dictItemFormError.value = toMessage(error, '字典条目保存失败')
  } finally {
    dictItemSubmitting.value = false
  }
}

async function removeDictItem(row: DictItemSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除字典条目「${row.itemName || row.itemCode}」吗?删除后业务表单将不再出现该选项。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteSystemDictItem(row.id)
    const typeCode =
      dictTypes.value.find((item) => item.id === row.typeId)?.typeCode || selectedDictTypeCode.value
    await loadDictItems(typeCode)
    dictStore.invalidate(typeCode)
    ElMessage.success('字典条目已删除')
  } catch (error) {
    ElMessage.error(toMessage(error, '字典条目删除失败'))
  }
}

async function handleDictTypeChange(row?: DictTypeSummary | null) {
  if (!row?.typeCode) {
    return
  }
  selectedDictTypeCode.value = row.typeCode
  if (!dictItemsMap.value[row.typeCode]) {
    try {
      dictItemsLoading.value = true
      await loadDictItems(row.typeCode)
    } catch (error) {
      dictSectionError.value = toMessage(error, '字典条目加载失败')
    } finally {
      dictItemsLoading.value = false
    }
  }
}

onMounted(async () => {
  await ensureStatusDictLoaded()
  try {
    dictTypesLoading.value = true
    dictTypes.value = await fetchSystemDictTypes()
    if (selectedDictTypeCode.value && !dictItemsMap.value[selectedDictTypeCode.value]) {
      dictItemsLoading.value = true
      try {
        await loadDictItems(selectedDictTypeCode.value)
      } finally {
        dictItemsLoading.value = false
      }
    }
  } catch (error) {
    dictSectionError.value = toMessage(error, '字典数据加载失败')
  } finally {
    dictTypesLoading.value = false
  }
})
</script>
