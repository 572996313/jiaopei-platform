<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">系统参数<span class="panel-count">共 {{ configTotal }} 条</span></div>
        <div class="table-actions">
          <span class="education-context-text">维护全局键值参数(token 有效期等),支持新增、编辑与删除。</span>
        </div>
      </div>

      <ConfigurableList
        ref="configListRef"
        resource-code="system-configs"
        :actions="configActionHandlers"
        @loaded="onConfigsLoaded"
      />
    </article>

    <el-dialog v-model="configDialogVisible" :title="configDialogMode === 'create' ? '新增系统参数' : '编辑系统参数'" width="640px">
      <el-alert v-if="configFormError" :title="configFormError" type="error" show-icon :closable="false" class="education-alert" />

      <ConfigurableForm
        ref="configFormRef"
        resource-code="system-configs"
        :mode="configDialogMode"
        :initial="configInitial"
        :context="configFormContext"
      />

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
import { computed, onMounted, ref } from 'vue'
import {
  createSystemConfig,
  deleteSystemConfig,
  updateSystemConfig,
} from '@/api/system'
import type { SystemConfigSummary } from '@/api/system'
import ConfigurableList from '@/components/ConfigurableList/index.vue'
import ConfigurableForm from '@/components/ConfigurableForm/index.vue'
import type { ActionHandler } from '@/components/ConfigurableList/types'
import type { RenderContext } from '@/components/ConfigurableList/renderers'
import {
  ensureStatusDictLoaded,
  toMessage,
  trimToUndefined,
  useStatusOptions,
} from './shared'

const statusOptions = useStatusOptions()

const configListRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
const configFormRef = ref<InstanceType<typeof ConfigurableForm> | null>(null)
const configTotal = ref(0)

function onConfigsLoaded(payload: { total: number }) {
  configTotal.value = payload.total
}

const configDialogVisible = ref(false)
const configDialogMode = ref<'create' | 'edit'>('create')
const configSubmitting = ref(false)
const configFormError = ref('')
const selectedConfig = ref<SystemConfigSummary | null>(null)

/** 弹窗回填初值(仅编辑时使用;新增由字段 defaultValue/默认初值承担) */
const configInitial = computed<Record<string, any>>(() => {
  if (configDialogMode.value === 'edit' && selectedConfig.value) {
    const config = selectedConfig.value
    return {
      configKey: config.configKey,
      configValue: config.configValue,
      valueType: config.valueType || 'string',
      configGroup: config.configGroup || '',
      displayName: config.displayName || '',
      description: config.description || '',
      defaultValue: config.defaultValue || '',
      isSystem: config.isSystem === true,
      sortOrder: config.sortOrder ?? 0,
      status: config.status || 'active',
    }
  }
  return {}
})

/** optionsLoader 数据源(状态下拉) */
const configFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
}))

function openCreateConfigDialog() {
  configDialogMode.value = 'create'
  selectedConfig.value = null
  configFormError.value = ''
  configDialogVisible.value = true
  void configFormRef.value?.reset()
}

function openEditConfigDialog(config: SystemConfigSummary) {
  configDialogMode.value = 'edit'
  selectedConfig.value = config
  configFormError.value = ''
  configDialogVisible.value = true
}

async function submitConfig() {
  if (!configFormRef.value) {
    return
  }
  const valid = await configFormRef.value.validate()
  if (!valid) {
    configFormError.value = '请检查表单填写是否完整'
    return
  }
  const form = configFormRef.value.values()

  configSubmitting.value = true
  configFormError.value = ''
  try {
    if (configDialogMode.value === 'create') {
      await createSystemConfig({
        configKey: String(form.configKey ?? '').trim(),
        configValue: form.configValue ?? '',
        valueType: form.valueType || 'string',
        configGroup: String(form.configGroup ?? '').trim(),
        displayName: String(form.displayName ?? '').trim(),
        description: trimToUndefined(form.description),
        defaultValue: trimToUndefined(form.defaultValue),
        isSystem: Boolean(form.isSystem),
        sortOrder: typeof form.sortOrder === 'number' ? form.sortOrder : 0,
        status: form.status || 'active',
      })
      ElMessage.success('系统参数已创建')
    } else if (selectedConfig.value) {
      await updateSystemConfig(selectedConfig.value.id, {
        configValue: form.configValue ?? '',
        displayName: String(form.displayName ?? '').trim(),
        description: trimToUndefined(form.description),
        sortOrder: typeof form.sortOrder === 'number' ? form.sortOrder : 0,
        status: form.status || 'active',
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

/**
 * 按钮动作注册表:与 sys_list_config.actions_json 的 action 名一一对应,
 * disabled 提供配置无法表达的动态禁用(系统内置参数禁删)。
 */
const configActionHandlers: Record<string, ActionHandler> = {
  'config-create': { handler: () => openCreateConfigDialog() },
  'config-edit': { handler: (row) => openEditConfigDialog(row as SystemConfigSummary) },
  'config-delete': {
    handler: (row) => void handleDeleteConfig(row as SystemConfigSummary),
    disabled: (row) => row?.isSystem === true,
  },
}

onMounted(() => {
  void ensureStatusDictLoaded()
})
</script>
