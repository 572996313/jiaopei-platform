<template>
  <ConfigurableCrudPage
    resource-code="system-configs"
    :form-context="configFormContext"
    :crud="configCrud"
  />
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import {
  createSystemConfig,
  deleteSystemConfig,
  updateSystemConfig,
} from '@cornerstone/core/api/system'
import type { SystemConfigSummary } from '@cornerstone/core/api/system'
import ConfigurableCrudPage from '@cornerstone/core/components/ConfigurableCrudPage/index.vue'
import type { CrudPageOptions } from '@cornerstone/core/components/ConfigurableCrudPage/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import { ensureStatusDictLoaded, trimToUndefined, useStatusOptions } from './shared'

const statusOptions = useStatusOptions()

/** 表单 optionsLoader 数据源(状态下拉) */
const configFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
}))

const configCrud: CrudPageOptions = {
  resourceName: '系统参数',
  buildInitial: (mode, row) => {
    if (mode === 'edit' && row) {
      const config = row as SystemConfigSummary
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
    return {
      configKey: '',
      configValue: '',
      valueType: 'string',
      configGroup: '',
      displayName: '',
      description: '',
      defaultValue: '',
      isSystem: false,
      sortOrder: 0,
      status: statusOptions.value[0]?.value || 'active',
    }
  },
  buildCreatePayload: (form) => ({
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
  }),
  buildUpdatePayload: (form) => ({
    configValue: form.configValue ?? '',
    displayName: String(form.displayName ?? '').trim(),
    description: trimToUndefined(form.description),
    sortOrder: typeof form.sortOrder === 'number' ? form.sortOrder : 0,
    status: form.status || 'active',
  }),
  createApi: (payload) => createSystemConfig(payload),
  updateApi: (id, payload) => updateSystemConfig(id, payload),
  deleteApi: (id) => deleteSystemConfig(id),
  actionExtras: {
    delete: {
      // 系统内置参数禁删(配置无法表达的动态禁用)
      disabled: (row) => row?.isSystem === true,
    },
  },
}

onMounted(() => {
  void ensureStatusDictLoaded()
})
</script>
