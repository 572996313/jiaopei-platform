import { ElMessage, ElMessageBox } from 'element-plus'
import type { ActionHandler } from '../ConfigurableList/types'

/**
 * ConfigurableCrudPage 内置动作构建器。
 * 依据 actions_json 的 actionType,由引擎为 create/edit/delete/toggle-status 提供默认处理:
 *   - create / edit:打开内置弹窗(状态与提交流程在 useCrudModal);
 *   - delete / toggle-status:确认框 → 调宿主 api → 提示 → reload(与三个系统页面现状一致的样板)。
 * reset-password / assign-roles / assign-menus 等业务强相关动作由宿主在 extraActions 中提供,
 * 引擎不猜测其弹窗形态(如重置密码需展示新密码 alert)。
 */

export interface BuiltActionContext {
  /** 提交后刷新列表(宿主/组件提供) */
  reload: () => Promise<void> | void
  /** 错误→中文展示 */
  toMessage: (error: unknown, fallback: string) => string
  /** 资源显示名,用于提示文案 */
  resourceName: string
  /** 行数据 → 展示名(确认框/提示用),缺省取 name/displayName */
  formatRowName?: (row?: Record<string, any>) => string
}

function rowName(ctx: BuiltActionContext, row?: Record<string, any>): string {
  if (ctx.formatRowName) {
    return ctx.formatRowName(row)
  }
  return String(row?.name || row?.displayName || row?.id || '该记录')
}

/** 删除:确认 → 删除 → 提示 → reload。disabled 由宿主按行动态提供(如系统内置禁删)。 */
export function buildDeleteHandler(
  ctx: BuiltActionContext,
  options: {
    deleteApi: (id: string) => Promise<unknown>
    disabled?: (row?: Record<string, any>) => boolean
    confirmTitle?: string
    /** 确认正文模板,{name} 为行展示名,{Resource} 为资源名 */
    confirmMessage?: string
    successMessage?: (row?: Record<string, any>) => string
  }
): ActionHandler {
  return {
    handler: async (row) => {
      const name = rowName(ctx, row)
      const confirm = await ElMessageBox.confirm(
        (options.confirmMessage ?? '确定删除「{name}」吗?')
          .replace('{name}', name)
          .replace('{Resource}', ctx.resourceName),
        options.confirmTitle || '删除确认',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).catch(() => null)
      if (confirm === null) {
        return
      }
      try {
        await options.deleteApi(String(row?.id))
        ElMessage.success(
          (options.successMessage ? options.successMessage(row) : '') || `${ctx.resourceName}已删除`
        )
        await ctx.reload()
      } catch (error) {
        ElMessage.error(ctx.toMessage(error, `${ctx.resourceName}删除失败`))
      }
    },
    disabled: options.disabled,
  }
}

/** 启停:确认(文案随状态切换)→ 调宿主 api → 提示 → reload。label 由宿主按行动态提供(停用/启用)。 */
export function buildToggleStatusHandler(
  ctx: BuiltActionContext,
  options: {
    toggleStatusApi: (row: Record<string, any>) => Promise<unknown>
    label?: (row?: Record<string, any>) => string
    disabled?: (row?: Record<string, any>) => boolean
    statusField?: string
    /** {name} = 行展示名;{action} = 当前状态文案(停用/启用) */
    confirmMessage?: (row?: Record<string, any>) => string
    successMessage?: (row?: Record<string, any>) => string
  }
): ActionHandler {
  const statusOf = (row?: Record<string, any>) => row?.[options.statusField || 'status']
  const actionWord = (row?: Record<string, any>) => (statusOf(row) === 'active' ? '停用' : '启用')

  return {
    handler: async (row) => {
      const name = rowName(ctx, row)
      const word = actionWord(row)
      const confirm = await ElMessageBox.confirm(
        options.confirmMessage?.(row) ?? `确定${word}「${name}」吗?`,
        '操作确认',
        { type: 'warning', confirmButtonText: word, cancelButtonText: '取消' }
      ).catch(() => null)
      if (confirm === null) {
        return
      }
      try {
        await options.toggleStatusApi(row!)
        ElMessage.success(options.successMessage?.(row) || `${name}已${word}`)
        await ctx.reload()
      } catch (error) {
        ElMessage.error(ctx.toMessage(error, `${ctx.resourceName}状态更新失败`))
      }
    },
    label: options.label ?? ((row) => actionWord(row)),
    disabled: options.disabled,
  }
}
