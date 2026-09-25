import { requestGet } from '@/utils/request'
import type { DictOptionGroup } from '@/api/miniapp'

/** 分页壳，与后端 common/api/PageResult 一致 */
export interface AnnouncementPage<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** 我收到的一条公告（接收端视角，不含发布留痕） */
export interface MyAnnouncement {
  id: string
  title: string
  content: string
  typeCode: string | null
  top: boolean | null
  publishedAt: string | null
  readAt: string | null
  audienceCode: string | null
}

/** 收到原因三档：与后端接收人解析分支一致，是代码判定值，不进字典 */
const AUDIENCE_LABELS: Record<string, string> = {
  teacher: '教师',
  parent: '学员家长',
  account: '全部账号',
}

export function audienceLabel(code?: string | null) {
  if (!code) {
    return ''
  }
  return AUDIENCE_LABELS[code] ?? code
}

/** 收件箱：按置顶、发布时间倒序 */
export function fetchMyAnnouncements(page: number, pageSize: number, unreadOnly: boolean) {
  return requestGet<AnnouncementPage<MyAnnouncement>>('/education/announcements/mine/page', {
    page,
    pageSize,
    unreadOnly: unreadOnly ? 'true' : 'false',
  })
}

/** 未读数，首页角标用 */
export function fetchUnreadCount() {
  return requestGet<number>('/education/announcements/mine/unread-count')
}

/** 打开公告：服务端在首次打开时记录已读时间 */
export function openMyAnnouncement(id: string) {
  return requestGet<MyAnnouncement>('/education/announcements/mine/' + id)
}

// 公告类型中文名走系统字典，与 PC 端同一来源（仅登录即可访问）
let typeOptionsPromise: Promise<Record<string, string>> | null = null

export function fetchAnnouncementTypeLabels() {
  if (!typeOptionsPromise) {
    typeOptionsPromise = requestGet<DictOptionGroup[]>('/system/dicts/options', {
      typeCodes: 'announcement_type',
    })
      .then((groups) => {
        const items = groups.find((group) => group.typeCode === 'announcement_type')?.items ?? []
        return Object.fromEntries(items.map((item) => [item.itemValue, item.itemName]))
      })
      .catch(() => {
        // 拉不到字典时不缓存，下次进页面重拉；标签回退原始值
        typeOptionsPromise = null
        return {}
      })
  }
  return typeOptionsPromise
}
