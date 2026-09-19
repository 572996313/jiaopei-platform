<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">
          {{ pageTitle
          }}<span v-if="config?.page.showCount" class="panel-count">{{ countText }}</span>
        </div>
        <div v-if="config?.page.description" class="table-actions">
          <span class="education-context-text">{{ config.page.description }}</span>
        </div>
      </div>

      <ConfigurableList
        ref="listRef"
        :resource-code="resourceCode"
        :context="listContext"
        :actions="resolvedActions"
        :empty-text="config?.page.emptyText ?? '暂无数据'"
        @loaded="onLoaded"
      />
    </article>

    <!-- 内置新增/编辑弹窗(由 dialog_json 驱动文案/宽度) -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" :width="dialogWidth">
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <ConfigurableForm
        ref="formRef"
        :resource-code="resourceCode"
        :mode="mode"
        :initial="initialForForm"
        :context="formContext"
        :on-field-change="onFieldChange"
      >
        <!-- 转发宿主的字段级具名插槽(如 #field-permissionCodes)到 ConfigurableForm。
             forwardFieldSlots 是数组,数组 v-for 第二变量为下标,遍历须用单变量取插槽名 -->
        <template v-for="name in forwardFieldSlots" :key="name" #[name]="slotProps">
          <slot :name="name" v-bind="slotProps ?? {}" />
        </template>
      </ConfigurableForm>

      <template #footer>
        <el-button @click="cancel">{{ dialogSafe.cancelText }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ dialogSafe.submitText }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useSlots } from 'vue'
import ConfigurableList from '@/components/ConfigurableList/index.vue'
import ConfigurableForm from '@/components/ConfigurableForm/index.vue'
import { useListConfigStore } from '@/stores/listConfig'
import { useCrudModal } from '@/composables/useCrudModal'
import type { ActionHandler, FormFieldConfig } from '@/components/ConfigurableList/types'
import type { RenderContext } from '@/components/ConfigurableList/renderers'
import { buildDeleteHandler, buildToggleStatusHandler } from './actions'
import { parsePageConfig } from './types'
import type { CrudPageConfig, CrudPageOptions } from './types'

const props = withDefaults(
  defineProps<{
    /** 列表/表单配置资源编码,如 system-users */
    resourceCode: string
    /** 页面主标题兜底(优先 page_json.title,再此值,再 resourceName) */
    pageTitle?: string
    /** 列表渲染上下文(机构/校区名称映射、查询下拉数据源) */
    listContext?: RenderContext
    /** 表单 optionsLoader 数据源上下文 */
    formContext?: RenderContext
    /** 表单字段联动回调(如机构变化刷新校区),透传给 ConfigurableForm */
    onFieldChange?: Record<string, (form: Record<string, any>, field: FormFieldConfig) => void>
    /** 资源 CRUD 实现画像:api / payload 组装 / 内置动作(含 extraActions 覆盖) */
    crud?: CrudPageOptions
  }>(),
  { pageTitle: '' }
)

const listConfigStore = useListConfigStore()
const slots = useSlots()

const listRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
const formRef = ref<InstanceType<typeof ConfigurableForm> | null>(null)
const config = ref<CrudPageConfig | null>(null)
const total = ref(0)

function toMessage(error: unknown, fallback: string): string {
  const custom = props.crud?.toMessage
  return custom
    ? custom(error, fallback)
    : error instanceof Error && error.message
      ? error.message
      : fallback
}

async function reload() {
  await listRef.value?.reload()
}

function rowName(row?: Record<string, any>): string {
  if (!row) {
    return '该记录'
  }
  for (const key of [
    'name',
    'displayName',
    'roleName',
    'username',
    'configKey',
    'title',
    'label',
  ]) {
    const value = row[key]
    if (value !== null && value !== undefined && String(value).trim() !== '') {
      return String(value)
    }
  }
  return row.id != null ? `#${row.id}` : '该记录'
}

const resourceName = computed(
  () => props.crud?.resourceName || config.value?.list.resourceName || props.resourceCode
)

/** 页面主标题:page_json.title 优先,其次 pageTitle prop,再 resourceName */
const pageTitle = computed(
  () =>
    config.value?.page?.title ||
    props.pageTitle ||
    config.value?.list.resourceName ||
    props.resourceCode
)

const countText = computed(() =>
  (config.value?.page?.countFormat ?? '共 {total} 条').replace(/\{total\}/g, String(total.value))
)

/** 弹窗标题:create/edit 文案各自替换 {name} 占位符 */
const dialogTitle = computed(() => {
  const shell = config.value?.dialog
  const template = mode.value === 'create' ? shell?.createTitle : shell?.editTitle
  return (template || (mode.value === 'create' ? '新增{name}' : '编辑{name}')).replace(
    /\{name\}/g,
    resourceName.value
  )
})

/** 弹窗宽度:缺省 640px */
const dialogWidth = computed(() => config.value?.dialog?.width || '640px')

/** 弹窗壳(配置未加载完成时回退默认文案,保证底部按钮可用) */
const dialogSafe = computed(() => {
  const d = config.value?.dialog
  if (!d) {
    return { cancelText: '取消', submitText: '保存' }
  }
  return d
})

/** 字段级具名插槽转发名单(仅 field-*),避免把页面壳插槽误传进表单 */
const forwardFieldSlots = computed(() =>
  Object.keys(slots).filter((name) => name.startsWith('field-'))
)

/** 打开弹窗后重置表单(create 用默认值重建;edit 靠 initial watch 重建) */
async function resetForm() {
  await nextTick()
  formRef.value?.reset()
}

const modal = useCrudModal(() => ({
  resourceName: resourceName.value,
  onOpenReset: (mode, row) => {
    if (props.crud?.onOpenReset) {
      return props.crud.onOpenReset(mode, row)
    }
    // create 需显式 reset(触发字段联动钩子);edit 由 initial computed 变化触发表单重建
    if (mode === 'create') {
      return resetForm()
    }
    return undefined
  },
  beforeSubmit: (form, mode) => props.crud?.beforeSubmit?.(form, mode) ?? null,
  buildCreatePayload: (form) => props.crud?.buildCreatePayload?.(form) ?? form,
  buildUpdatePayload: (form, row) => props.crud?.buildUpdatePayload?.(form, row) ?? form,
  create: props.crud?.createApi,
  update: props.crud?.updateApi,
  reload,
  toMessage,
  messages: {
    createSuccess: config.value?.dialog?.createMessage,
    updateSuccess: config.value?.dialog?.updateMessage,
  },
}))

/** 解构为顶层 ref,模板可直接使用并自动解包 */
const { dialogVisible, mode, submitting, formError } = modal

function cancel() {
  dialogVisible.value = false
}

/** 弹窗回填初值(create 由宿主给默认值,edit 回填选中行) */
const initialForForm = computed(() => {
  const row = modal.selectedRow.value
  return props.crud?.buildInitial?.(modal.mode.value, row) ?? (row ? { ...row } : {})
})

function onLoaded(payload: { total: number; records: Record<string, any>[] }) {
  total.value = payload.total
}

async function submit() {
  await modal.submit(
    () => formRef.value?.validate() ?? Promise.resolve(false),
    () => formRef.value?.values() ?? {}
  )
}

/** 依据 actions_json 的 actionType 组装动作处理器;宿主 extraActions 优先覆盖 */
const resolvedActions = computed<Record<string, ActionHandler>>(() => {
  const result: Record<string, ActionHandler> = {}
  const actions = config.value?.actions ?? []
  const ctx = { reload, toMessage, resourceName: resourceName.value, formatRowName: rowName }

  for (const action of actions) {
    const name = action.action
    const hostHandler = props.crud?.extraActions?.[name]
    if (hostHandler) {
      result[name] = hostHandler
      continue
    }
    switch (action.actionType) {
      case 'create':
        result[name] = { handler: () => void modal.openCreate() }
        break
      case 'edit':
        result[name] = { handler: (row) => void modal.openEdit(row ?? {}) }
        break
      case 'delete': {
        const extras = props.crud?.actionExtras?.delete
        const deleteApi = props.crud?.deleteApi
        if (!deleteApi) {
          console.warn(`[ConfigurableCrudPage] 动作 "${name}" 需要 crud.deleteApi`)
          break
        }
        result[name] = buildDeleteHandler(ctx, {
          deleteApi,
          disabled: extras?.disabled,
          confirmTitle: extras?.confirmTitle,
          confirmMessage: extras?.confirmMessage,
          successMessage: extras?.successMessage,
        })
        break
      }
      case 'toggle-status': {
        const extras = props.crud?.actionExtras?.toggleStatus
        const toggleStatusApi = props.crud?.toggleStatusApi
        if (!toggleStatusApi) {
          console.warn(`[ConfigurableCrudPage] 动作 "${name}" 需要 crud.toggleStatusApi`)
          break
        }
        result[name] = buildToggleStatusHandler(ctx, {
          toggleStatusApi,
          label: (row) => (row?.status === 'active' ? '停用' : '启用'),
          disabled: extras?.disabled,
          confirmMessage: extras?.confirmMessage,
          successMessage: extras?.successMessage,
        })
        break
      }
      default:
        console.warn(
          `[ConfigurableCrudPage] 动作 "${name}" 无内置 actionType,请通过 crud.extraActions 提供`
        )
    }
  }
  return result
})

async function loadShellConfig() {
  try {
    const summary = await listConfigStore.getOf(props.resourceCode)
    config.value = parsePageConfig(summary)
  } catch {
    // 配置解析失败:页面壳/弹窗用默认值,ConfigurableList 自行展示配置错误
    config.value = null
  }
}

onMounted(() => {
  void loadShellConfig()
})

defineExpose({ reload, formRef })
</script>
