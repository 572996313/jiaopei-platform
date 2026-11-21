import { onUnmounted, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { fetchFileBlob } from '@cornerstone/core/api/file'

/**
 * 把 fileId 换成一个可给 `<img>` 用的 blob URL。
 *
 * <p>为什么不能直接拿 `/api/system/files/{id}/content` 当 src：`<img>` 发的是裸 GET，
 * 带不上 Authorization 头，直连必定 401。所以先带 token 取 Blob，再 `createObjectURL`。</p>
 *
 * <p>同一文件常被列表里的多行、多个组件同时显示，所以 URL 按 fileId 全局缓存并做引用计数，
 * 最后一个持有者卸载时才 revoke——否则每行各建一个 URL，几百行就是几百份内存。</p>
 */
interface CachedBlobUrl {
  url: string
  refs: number
}

const urlCache = new Map<string, CachedBlobUrl>()
/** 同一 fileId 并发首次取数只发一次请求，其余等同一个 Promise */
const pendingCache = new Map<string, Promise<string>>()

function acquire(fileId: string): Promise<string> {
  const cached = urlCache.get(fileId)
  if (cached) {
    cached.refs += 1
    return Promise.resolve(cached.url)
  }

  let pending = pendingCache.get(fileId)
  if (!pending) {
    pending = fetchFileBlob(fileId)
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        urlCache.set(fileId, { url, refs: 0 })
        return url
      })
      .finally(() => {
        pendingCache.delete(fileId)
      })
    pendingCache.set(fileId, pending)
  }

  return pending.then((url) => {
    const entry = urlCache.get(fileId)
    if (entry) {
      entry.refs += 1
    }
    return url
  })
}

function release(fileId: string) {
  const cached = urlCache.get(fileId)
  if (cached) {
    cached.refs -= 1
    if (cached.refs <= 0) {
      URL.revokeObjectURL(cached.url)
      urlCache.delete(fileId)
    }
    return
  }
  // 还在加载中就卸载了：等它落地后再减，避免漏掉一次引用
  const pending = pendingCache.get(fileId)
  if (pending) {
    void pending.then(() => release(fileId)).catch(() => undefined)
  }
}

/**
 * 用法：`const { url, error } = useFileUrl(() => props.fileId)`。
 * `fileId` 变化或组件卸载都会自动换/放引用，调用方不需要手动清。
 */
export function useFileUrl(source: MaybeRefOrGetter<string | null | undefined>) {
  const url = ref('')
  const error = ref('')
  let heldFileId: string | null = null
  /** 只让最后一次请求的结果落到界面上，防止慢请求覆盖新文件 */
  let sequence = 0

  async function load(fileId: string | null) {
    const token = ++sequence
    if (heldFileId && heldFileId !== fileId) {
      release(heldFileId)
      heldFileId = null
    }
    if (!fileId) {
      url.value = ''
      error.value = ''
      return
    }
    if (heldFileId === fileId) {
      return
    }
    heldFileId = fileId
    url.value = ''
    error.value = ''
    try {
      const resolved = await acquire(fileId)
      if (token === sequence) {
        url.value = resolved
      }
    } catch (failure) {
      if (token === sequence) {
        url.value = ''
        error.value = failure instanceof Error && failure.message ? failure.message : '文件加载失败'
      }
    }
  }

  watch(
    () => toValue(source),
    (value) => {
      void load(value ? String(value) : null)
    },
    { immediate: true }
  )

  onUnmounted(() => {
    sequence += 1
    if (heldFileId) {
      release(heldFileId)
      heldFileId = null
    }
  })

  return { url, error }
}
