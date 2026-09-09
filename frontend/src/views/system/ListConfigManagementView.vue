<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="listConfigSectionError" class="education-empty">
        {{ listConfigSectionError }}
      </article>
      <template v-else>
        <div class="education-table-head">
          <div class="panel-title">列表配置<span class="panel-count">共 {{ listConfigRecords.length }} 条</span></div>
          <div class="table-actions">
            <span class="education-context-text">维护列表的展示列与查询条件,保存后立即生效,无需发版。</span>
            <el-button v-if="permissions.canManageListConfigs" type="primary" @click="openCreateListConfigDialog">新增配置</el-button>
          </div>
        </div>

        <el-table :data="listConfigRecords" border v-loading="listConfigLoading">
          <el-table-column prop="resourceCode" label="资源编码" min-width="150" />
          <el-table-column prop="resourceName" label="资源名称" min-width="120" />
          <el-table-column prop="endpoint" label="分页接口" min-width="190" />
          <el-table-column prop="pageSize" label="每页条数" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status, statusLabelMap) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column v-if="permissions.canManageListConfigs" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="system-operation-group">
                <el-button text type="primary" @click="openEditListConfigDialog(row)">编辑</el-button>
                <el-button text type="danger" :disabled="seedListConfigIds.has(row.id)" @click="handleDeleteListConfig(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </article>

    <el-dialog v-model="listConfigDialogVisible" :title="listConfigDialogMode === 'create' ? '新增列表配置' : '编辑列表配置'" width="720px">
      <el-alert v-if="listConfigFormError" :title="listConfigFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="资源编码">
            <el-input v-model="listConfigForm.resourceCode" :disabled="listConfigDialogMode === 'edit'" placeholder="如 system-users,创建后不可修改" />
          </el-form-item>
          <el-form-item label="资源名称">
            <el-input v-model="listConfigForm.resourceName" maxlength="64" placeholder="如 用户管理" />
          </el-form-item>
          <el-form-item label="分页接口路径">
            <el-input v-model="listConfigForm.endpoint" maxlength="128" placeholder="如 /system/users/page" />
          </el-form-item>
          <el-form-item label="默认每页条数">
            <el-input-number v-model="listConfigForm.pageSize" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="listConfigForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="listConfigForm.remark" maxlength="500" placeholder="选填" />
          </el-form-item>
        </div>

        <el-form-item label="列配置 columnsJson">
          <el-input v-model="listConfigForm.columnsJson" type="textarea" :rows="9" placeholder='[{"prop":"username","label":"账号","width":150}]' />
          <div class="education-context-text">
            每列 prop/label 必填;type 可选 text/datetime/dict/boolean/tags/map;type=dict 需 dictType,可配 tagTypeMap;type=map 需 valueMap;
            renderer 可选 institutionName/campusName/permissionTags/passwordStatus。
          </div>
        </el-form-item>
        <el-form-item label="查询条件 queryJson">
          <el-input v-model="listConfigForm.queryJson" type="textarea" :rows="6" placeholder='[{"field":"keyword","label":"关键字","type":"input"}]' />
          <div class="education-context-text">
            每项 field/label 必填;type 可选 input/select/dict-select/date;type=dict-select 需 dictType;type=select 可配 options 或 optionsLoader=institutions;
            field 必须是后端分页接口支持的查询参数,否则查询不生效。
          </div>
        </el-form-item>
        <el-form-item label="按钮配置 actionsJson">
          <el-input v-model="listConfigForm.actionsJson" type="textarea" :rows="7" placeholder='[{"action":"xxx-create","label":"新增","position":"toolbar","buttonType":"primary"}]' />
          <div class="education-context-text">
            每项 action/label/position 必填;position= toolbar(表格上方)或 row(行内);buttonType 可选 primary/default/text/danger;
            permission 为权限码,无权限自动隐藏;action 必须是对应列表页已注册的动作名,写错会导致按钮不渲染。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="listConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="listConfigSubmitting" @click="submitListConfig">
          {{ listConfigDialogMode === 'create' ? '创建配置' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useListConfigStore } from '@/stores/listConfig'
import {
  createListConfig,
  deleteListConfig,
  fetchListConfigs,
  updateListConfig,
} from '@/api/system'
import type { ListConfigSummary } from '@/api/system'
import {
  ensureStatusDictLoaded,
  statusLabel,
  statusTagType,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const listConfigStore = useListConfigStore()
const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const listConfigSectionError = ref('')
const listConfigLoading = ref(false)
const listConfigRecords = ref<ListConfigSummary[]>([])
const listConfigDialogVisible = ref(false)
const listConfigDialogMode = ref<'create' | 'edit'>('create')
const listConfigSubmitting = ref(false)
const listConfigFormError = ref('')
const selectedListConfig = ref<ListConfigSummary | null>(null)
// V20 种子的三条内置配置(id 1-3)支撑系统设置三个列表页,后端禁删,前端同步禁用删除按钮
const seedListConfigIds = new Set(['1', '2', '3'])

const listConfigForm = reactive({
  resourceCode: '',
  resourceName: '',
  endpoint: '',
  pageSize: 10,
  status: 'active',
  remark: '',
  columnsJson: '',
  queryJson: '',
  actionsJson: '',
})

async function loadListConfigs() {
  listConfigLoading.value = true
  listConfigSectionError.value = ''
  try {
    listConfigRecords.value = await fetchListConfigs()
  } catch (error) {
    listConfigRecords.value = []
    listConfigSectionError.value = toMessage(error, '列表配置加载失败')
  } finally {
    listConfigLoading.value = false
  }
}

function prettyJson(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function validateJsonArray(raw: string, name: string) {
  const trimmed = raw.trim()
  try {
    const parsed: unknown = JSON.parse(trimmed)
    if (!Array.isArray(parsed)) {
      throw new Error(`${name} 必须是 JSON 数组`)
    }
  } catch (error) {
    throw new Error(toMessage(error, `${name} 不是合法的 JSON`))
  }
  return trimmed
}

function openCreateListConfigDialog() {
  listConfigDialogMode.value = 'create'
  selectedListConfig.value = null
  listConfigForm.resourceCode = ''
  listConfigForm.resourceName = ''
  listConfigForm.endpoint = ''
  listConfigForm.pageSize = 10
  listConfigForm.status = 'active'
  listConfigForm.remark = ''
  listConfigForm.columnsJson = '[\n  { "prop": "id", "label": "ID" }\n]'
  listConfigForm.queryJson = '[]'
  listConfigForm.actionsJson = ''
  listConfigFormError.value = ''
  listConfigDialogVisible.value = true
}

function openEditListConfigDialog(row: ListConfigSummary) {
  listConfigDialogMode.value = 'edit'
  selectedListConfig.value = row
  listConfigForm.resourceCode = row.resourceCode
  listConfigForm.resourceName = row.resourceName
  listConfigForm.endpoint = row.endpoint
  listConfigForm.pageSize = row.pageSize ?? 10
  listConfigForm.status = row.status || 'active'
  listConfigForm.remark = row.remark || ''
  listConfigForm.columnsJson = prettyJson(row.columnsJson)
  listConfigForm.queryJson = prettyJson(row.queryJson)
  listConfigForm.actionsJson = row.actionsJson ? prettyJson(row.actionsJson) : ''
  listConfigFormError.value = ''
  listConfigDialogVisible.value = true
}

async function submitListConfig() {
  if (listConfigDialogMode.value === 'create' && !trimToUndefined(listConfigForm.resourceCode)) {
    listConfigFormError.value = '请填写资源编码'
    return
  }
  if (!trimToUndefined(listConfigForm.resourceName)) {
    listConfigFormError.value = '请填写资源名称'
    return
  }
  if (!trimToUndefined(listConfigForm.endpoint)) {
    listConfigFormError.value = '请填写分页接口路径'
    return
  }

  let columnsJson: string
  let queryJson: string
  let actionsJson: string | null
  try {
    columnsJson = validateJsonArray(listConfigForm.columnsJson, '列配置 columnsJson')
    queryJson = validateJsonArray(listConfigForm.queryJson, '查询条件 queryJson')
    // actionsJson 允许为空字符串(null=未配置按钮,回退插槽模式),非空时必须合法
    actionsJson = listConfigForm.actionsJson.trim() || null
    if (actionsJson) {
      actionsJson = validateJsonArray(actionsJson, '按钮配置 actionsJson')
    }
  } catch (error) {
    listConfigFormError.value = toMessage(error, 'JSON 校验失败')
    return
  }

  listConfigSubmitting.value = true
  listConfigFormError.value = ''
  try {
    const payload = {
      resourceName: listConfigForm.resourceName.trim(),
      endpoint: listConfigForm.endpoint.trim(),
      pageSize: listConfigForm.pageSize,
      columnsJson,
      queryJson,
      actionsJson,
      status: listConfigForm.status,
      remark: trimToUndefined(listConfigForm.remark),
    }
    if (listConfigDialogMode.value === 'create') {
      await createListConfig({ ...payload, resourceCode: listConfigForm.resourceCode.trim() })
      ElMessage.success('列表配置已创建')
    } else if (selectedListConfig.value) {
      await updateListConfig(selectedListConfig.value.id, payload)
      ElMessage.success('列表配置已更新')
    }
    listConfigDialogVisible.value = false
    listConfigStore.invalidate()
    await loadListConfigs()
  } catch (error) {
    listConfigFormError.value = toMessage(error, listConfigDialogMode.value === 'create' ? '创建列表配置失败' : '保存列表配置失败')
  } finally {
    listConfigSubmitting.value = false
  }
}

async function handleDeleteListConfig(row: ListConfigSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除列表配置「${row.resourceName || row.resourceCode}」吗?使用该配置的列表页将无法展示数据。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteListConfig(row.id)
    ElMessage.success('列表配置已删除')
    listConfigStore.invalidate()
    await loadListConfigs()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除列表配置失败'))
  }
}

onMounted(async () => {
  await Promise.allSettled([ensureStatusDictLoaded(), loadListConfigs()])
})
</script>
