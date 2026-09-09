<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">系统参数<span class="panel-count">共 {{ configTotal }} 条</span></div>
        <div class="table-actions">
          <span class="education-context-text">维护全局键值参数(token 有效期等),支持新增、编辑与删除。</span>
          <el-button v-if="permissions.canWriteConfigs" type="primary" @click="openCreateConfigDialog">新增参数</el-button>
        </div>
      </div>

      <ConfigurableList
        ref="configListRef"
        resource-code="system-configs"
        :actions-width="180"
        @loaded="onConfigsLoaded"
      >
        <template #actions="{ row }">
          <el-button v-if="permissions.canWriteConfigs" text type="primary" @click="openEditConfigDialog(row)">编辑</el-button>
          <el-button
            v-if="permissions.canWriteConfigs"
            text
            type="danger"
            :disabled="row.isSystem === true"
            @click="handleDeleteConfig(row)"
          >删除</el-button>
        </template>
      </ConfigurableList>
    </article>

    <el-dialog v-model="configDialogVisible" :title="configDialogMode === 'create' ? '新增系统参数' : '编辑系统参数'" width="620px">
      <el-alert v-if="configFormError" :title="configFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="配置键">
            <el-input v-model="configForm.configKey" :disabled="configDialogMode === 'edit'" placeholder="如 token.expire-minutes" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="configForm.displayName" placeholder="请输入显示名称" />
          </el-form-item>
          <el-form-item label="配置值">
            <el-input v-model="configForm.configValue" placeholder="请输入配置值" />
          </el-form-item>
          <el-form-item label="值类型">
            <el-select v-model="configForm.valueType" :disabled="configDialogMode === 'edit'">
              <el-option v-for="item in configValueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置分组">
            <el-input v-model="configForm.configGroup" :disabled="configDialogMode === 'edit'" placeholder="如 general / auth" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="configForm.sortOrder" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item v-if="configDialogMode === 'create'" label="是否系统内置">
            <el-switch v-model="configForm.isSystem" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="configForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="默认值">
          <el-input v-model="configForm.defaultValue" placeholder="选填" />
        </el-form-item>
        <el-form-item label="配置说明">
          <el-input v-model="configForm.description" type="textarea" :rows="3" maxlength="500" placeholder="补充参数用途说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSubmitting" @click="submitConfig">
          {{ configDialogMode === 'create' ? '创建参数' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  createSystemConfig,
  deleteSystemConfig,
  updateSystemConfig,
} from '@/api/system'
import type { SystemConfigSummary } from '@/api/system'
import ConfigurableList from '@/components/ConfigurableList/index.vue'
import {
  ensureStatusDictLoaded,
  toMessage,
  trimToUndefined,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()

const configListRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
const configTotal = ref(0)

function onConfigsLoaded(payload: { total: number }) {
  configTotal.value = payload.total
}

const configDialogVisible = ref(false)
const configDialogMode = ref<'create' | 'edit'>('create')
const configSubmitting = ref(false)
const configFormError = ref('')
const selectedConfig = ref<SystemConfigSummary | null>(null)

const configValueTypeOptions = [
  { value: 'string', label: 'string' },
  { value: 'int', label: 'int' },
  { value: 'boolean', label: 'boolean' },
  { value: 'json', label: 'json' },
]

const configForm = reactive({
  configKey: '',
  configValue: '',
  valueType: 'string',
  configGroup: 'general',
  displayName: '',
  description: '',
  defaultValue: '',
  isSystem: false,
  sortOrder: 0,
  status: 'active',
})

function openCreateConfigDialog() {
  configDialogMode.value = 'create'
  selectedConfig.value = null
  configForm.configKey = ''
  configForm.configValue = ''
  configForm.valueType = 'string'
  configForm.configGroup = 'general'
  configForm.displayName = ''
  configForm.description = ''
  configForm.defaultValue = ''
  configForm.isSystem = false
  configForm.sortOrder = 0
  configForm.status = statusOptions.value[0]?.value || 'active'
  configFormError.value = ''
  configDialogVisible.value = true
}

function openEditConfigDialog(config: SystemConfigSummary) {
  configDialogMode.value = 'edit'
  selectedConfig.value = config
  configForm.configKey = config.configKey
  configForm.configValue = config.configValue
  configForm.valueType = config.valueType || 'string'
  configForm.configGroup = config.configGroup || ''
  configForm.displayName = config.displayName || ''
  configForm.description = config.description || ''
  configForm.defaultValue = config.defaultValue || ''
  configForm.isSystem = config.isSystem === true
  configForm.sortOrder = config.sortOrder ?? 0
  configForm.status = config.status || 'active'
  configFormError.value = ''
  configDialogVisible.value = true
}

async function submitConfig() {
  if (!trimToUndefined(configForm.configKey) && configDialogMode.value === 'create') {
    configFormError.value = '请填写配置键'
    return
  }
  if (!trimToUndefined(configForm.configValue)) {
    configFormError.value = '请填写配置值'
    return
  }
  if (!trimToUndefined(configForm.displayName)) {
    configFormError.value = '请填写显示名称'
    return
  }
  if (!trimToUndefined(configForm.configGroup) && configDialogMode.value === 'create') {
    configFormError.value = '请填写配置分组'
    return
  }

  configSubmitting.value = true
  configFormError.value = ''
  try {
    if (configDialogMode.value === 'create') {
      await createSystemConfig({
        configKey: configForm.configKey.trim(),
        configValue: configForm.configValue,
        valueType: configForm.valueType,
        configGroup: configForm.configGroup.trim(),
        displayName: configForm.displayName.trim(),
        description: trimToUndefined(configForm.description),
        defaultValue: trimToUndefined(configForm.defaultValue),
        isSystem: configForm.isSystem,
        sortOrder: configForm.sortOrder ?? 0,
        status: configForm.status,
      })
      ElMessage.success('系统参数已创建')
    } else if (selectedConfig.value) {
      await updateSystemConfig(selectedConfig.value.id, {
        configValue: configForm.configValue,
        displayName: configForm.displayName.trim(),
        description: trimToUndefined(configForm.description),
        sortOrder: configForm.sortOrder ?? 0,
        status: configForm.status,
      })
      ElMessage.success('系统参数已更新')
    }
    configDialogVisible.value = false
    await configListRef.value?.reload()
  } catch (error) {
    configFormError.value = toMessage(error, configDialogMode.value === 'create' ? '创建系统参数失败' : '编辑系统参数失败')
  } finally {
    configSubmitting.value = false
  }
}

async function handleDeleteConfig(config: SystemConfigSummary) {
  try {
    await ElMessageBox.confirm(`确定删除系统参数「${config.displayName || config.configKey}」吗?`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await deleteSystemConfig(config.id)
    ElMessage.success('系统参数已删除')
    await configListRef.value?.reload()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除系统参数失败'))
  }
}

onMounted(() => {
  void ensureStatusDictLoaded()
})
</script>
