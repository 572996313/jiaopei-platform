/**
 * 错误消息归一：取 `Error.message`，拿不到就用调用方给的中文兜底文案。
 *
 * <p>请求层（`api/request`）在 `success=false` 时抛 `ApiError`，它继承自 `Error`，
 * 所以后端给的业务提示会原样带出来；网络中断、超时这类没有可用 message 的场合才落兜底。</p>
 *
 * <p>历史上 25 个页面各写一份逐字节相同的实现，改一处口径要改 25 处；统一收口到这里。
 * 需要覆盖文案的调用方（配置化 CRUD 的 `crud.toMessage`）自己包一层再转调本函数。</p>
 */
export function toMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}
