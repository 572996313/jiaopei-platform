import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormMode } from '@/components/ConfigurableList/types'

/**
 * useCrudModal:弹窗式新增/编辑表单的「状态 + 提交流程」骨架(阶段 A 收口)。
 * 由 ConfigurableCrudPage 内部使用;对纯手写页面也可独立使用,消除同类重复样板。
 *
 * 流程:
 *   openCreate/openEdit 打开弹窗并通知宿主执行 reset(重置表单/联动缓存);
 *   submit 固定为:自检 → validate → 组装 payload → create/update API → toast → 关窗 → reload。
 * 宿主通过 options 注入「打开前重置 / 提交前自检 / payload 组装 / create / update / reload / 文案」,
 * 页面只保留本资源特有逻辑(联动、特殊校验、特殊弹窗)。
 */

export interface CrudModalOptions {
  /** 资源显示名,用于 toast 文案 {name} 占位符与空回退 */
  resourceName?: string
  /** 打开弹窗后、渲染表单前,宿主重置表单与联动缓存(reset + 预载校区等) */
  onOpenReset?: (mode: FormMode, row: Record<string, any> | null) => void | Promise<void>
  /** 提交前自检(如“至少勾选一个权限码”),返回非空字符串则中止并展示 */
  beforeSubmit?: (form: Record<string, any>, mode: FormMode) => string | null
  /** 组装新增 payload */
  buildCreatePayload?: (form: Record<string, any>) => Record<string, any>
  /** 组装编辑 payload(id 由 selectedRow 提供) */
  buildUpdatePayload?: (form: Record<string, any>, row: Record<string, any>) => Record<string, any>
  /** 新增接口调用 */
  create?: (payload: any) => Promise<any>
  /** 编辑接口调用 */
  update?: (id: string, payload: any) => Promise<any>
  /** 提交后列表刷新;不注入则不做 */
  reload?: () => Promise<void> | void
  /** 成功/失败文案(可含 {name}=资源名 占位符) */
  messages?: {
    createSuccess?: string
    updateSuccess?: string
    createError?: string
    updateError?: string
  }
  toMessage?: (error: unknown, fallback: string) => string
}

export function useCrudModal(options: () => CrudModalOptions) {
  const dialogVisible = ref(false)
  const mode = ref<FormMode>('create')
  const submitting = ref(false)
  const formError = ref('')
  const selectedRow = ref<Record<string, any> | null>(null)

  function getOpts(): CrudModalOptions {
    return options() || {}
  }

  function toMessage(error: unknown, fallback: string): string {
    const t = getOpts().toMessage
    return t ? t(error, fallback) : error instanceof Error && error.message ? error.message : fallback
  }

  /** 把配置文案里的 {name} 替换为资源名(缺省回退传入文案本身) */
  function render(template: string | undefined, fallback: string): string {
    if (!template) {
      return fallback
    }
    return template.replace(/\{name\}/g, getOpts().resourceName ?? '')
  }

  async function openCreate() {
    mode.value = 'create'
    selectedRow.value = null
    formError.value = ''
    dialogVisible.value = true
    await getOpts().onOpenReset?.(mode.value, null)
  }

  async function openEdit(row: Record<string, any>) {
    mode.value = 'edit'
    selectedRow.value = row
    formError.value = ''
    dialogVisible.value = true
    await getOpts().onOpenReset?.(mode.value, row)
  }

  async function submit(
    validate: () => Promise<boolean> | boolean,
    buildForm: () => Record<string, any>,
  ): Promise<void> {
    const valid = await validate()
    if (!valid) {
      formError.value = '请检查表单填写是否完整'
      return
    }
    const form = buildForm()

    const opts = getOpts()
    const selfCheck = opts.beforeSubmit?.(form, mode.value)
    if (selfCheck) {
      formError.value = selfCheck
      return
    }

    submitting.value = true
    formError.value = ''
    try {
      if (mode.value === 'create') {
        const payload = opts.buildCreatePayload?.(form) ?? (form as Record<string, any>)
        await opts.create?.(payload)
        ElMessage.success(render(opts.messages?.createSuccess, `${opts.resourceName ?? ''}已创建`))
      } else {
        const row = selectedRow.value
        if (!row) {
          return
        }
        const payload = opts.buildUpdatePayload?.(form, row) ?? (form as Record<string, any>)
        await opts.update?.(String(row.id), payload)
        ElMessage.success(render(opts.messages?.updateSuccess, `${opts.resourceName ?? ''}已更新`))
      }
      dialogVisible.value = false
      await opts.reload?.()
    } catch (error) {
      formError.value = toMessage(
        error,
        mode.value === 'create'
          ? render(opts.messages?.createError, `${opts.resourceName ?? ''}创建失败`)
          : render(opts.messages?.updateError, `${opts.resourceName ?? ''}编辑失败`),
      )
    } finally {
      submitting.value = false
    }
  }

  return {
    dialogVisible,
    mode,
    submitting,
    formError,
    selectedRow,
    openCreate,
    openEdit,
    submit,
  }
}

export type CrudModalState = ReturnType<typeof useCrudModal>