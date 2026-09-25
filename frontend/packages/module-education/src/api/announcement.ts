import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/** 接收对象三档：与后端 AnnouncementServiceImpl 的解析分支一一对应，不是字典值 */
export const AUDIENCE_OPTIONS = [
  { value: 'teacher', label: '教师' },
  { value: 'parent', label: '学员家长' },
  { value: 'account', label: '全部登录账号' },
]

/** 公告状态由发布/撤回动作判定，按 AGENTS 的字典边界不入字典，选项写在这里 */
export const ANNOUNCEMENT_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿', tag: 'info' },
  { value: 'published', label: '已发布', tag: 'success' },
  { value: 'recalled', label: '已撤回', tag: 'warning' },
]

export interface AnnouncementRecord {
  id: string
  title: string
  content: string
  typeCode?: string | null
  status?: string | null
  top?: boolean | null
  audienceCodes?: string[] | null
  campusScope?: string | null
  campusIds?: string[] | null
  classIds?: string[] | null
  receiverTotal?: number | null
  readCount?: number | null
  publishedAt?: string | null
  publishedByName?: string | null
  recalledAt?: string | null
  recalledByName?: string | null
  recallReason?: string | null
  remark?: string | null
  createdAt?: string | null
}

export interface AnnouncementPayload {
  title: string
  content: string
  typeCode: string
  audienceCodes?: string[]
  campusScope?: string
  campusIds?: string[]
  classIds?: string[]
  top?: boolean
  remark?: string
}

export interface AnnouncementRecipientPreview {
  teacherCount: number
  parentCount: number
  accountCount: number
  total: number
  unboundStudentCount: number
  notices: string[]
}

export interface AnnouncementPageParams extends PageParams {
  status?: string | null
  typeCode?: string | null
  audienceCode?: string | null
  keyword?: string | null
}

export function fetchAnnouncementPage(params: AnnouncementPageParams) {
  return requestGet<PageResult<AnnouncementRecord>>('/education/announcements/page', { ...params })
}

export function fetchAnnouncement(id: string) {
  return requestGet<AnnouncementRecord>(`/education/announcements/${id}`)
}

/** 试算不落库：范围一改就该重算，所以它是 POST 但没有副作用 */
export function previewAnnouncementRecipients(payload: AnnouncementPayload) {
  return requestPost<AnnouncementRecipientPreview>('/education/announcements/recipient-preview', payload)
}

export function createAnnouncement(payload: AnnouncementPayload) {
  return requestPost<AnnouncementRecord>('/education/announcements', payload)
}

export function updateAnnouncement(id: string, payload: AnnouncementPayload) {
  return requestPut<AnnouncementRecord>(`/education/announcements/${id}`, payload)
}

export function publishAnnouncement(id: string) {
  return requestPost<AnnouncementRecord>(`/education/announcements/${id}/publish`, {})
}

export function recallAnnouncement(id: string, reason: string) {
  return requestPost<AnnouncementRecord>(`/education/announcements/${id}/recall`, { reason })
}

export function deleteAnnouncement(id: string) {
  return requestDelete<void>(`/education/announcements/${id}`)
}

/**
 * 公告模板：新建公告时预填的可复用骨架。
 * 取值对象（typeCode/audienceCodes/范围）与公告同一套规则，所以表单能整份搬过来。
 */
export interface AnnouncementTemplate {
  id: string
  name: string
  title: string
  content: string
  typeCode?: string | null
  top?: boolean | null
  audienceCodes?: string[] | null
  campusScope?: string | null
  campusIds?: string[] | null
  classIds?: string[] | null
  remark?: string | null
}

export interface AnnouncementTemplatePayload {
  name: string
  title: string
  content: string
  typeCode: string
  audienceCodes?: string[]
  campusScope?: string
  campusIds?: string[]
  classIds?: string[]
  top?: boolean
  remark?: string
}

/** 模板量小、套用的下拉要一次拿全，所以后端不分页 */
export function fetchAnnouncementTemplates() {
  return requestGet<AnnouncementTemplate[]>('/education/announcement-templates')
}

export function createAnnouncementTemplate(payload: AnnouncementTemplatePayload) {
  return requestPost<AnnouncementTemplate>('/education/announcement-templates', payload)
}

export function updateAnnouncementTemplate(id: string, payload: AnnouncementTemplatePayload) {
  return requestPut<AnnouncementTemplate>(`/education/announcement-templates/${id}`, payload)
}

export function deleteAnnouncementTemplate(id: string) {
  return requestDelete<void>(`/education/announcement-templates/${id}`)
}

/** 收件箱条目：/mine/** 只认登录态，所以教师和家长拿到的是同一份结构 */
export interface MyAnnouncement {
  id: string
  title: string
  content: string
  typeCode?: string | null
  top?: boolean | null
  publishedAt?: string | null
  readAt?: string | null
  audienceCode?: string | null
}

export function fetchMyAnnouncements(page: number, pageSize: number, unreadOnly: boolean) {
  return requestGet<PageResult<MyAnnouncement>>('/education/announcements/mine/page', {
    page,
    pageSize,
    unreadOnly: unreadOnly ? 'true' : 'false',
  })
}

export function fetchMyUnreadCount() {
  return requestGet<number>('/education/announcements/mine/unread-count')
}

/** 打开即已读：这个请求本身就是打读动作，服务端靠 read_at IS NULL 保证只记首次 */
export function openMyAnnouncement(id: string) {
  return requestGet<MyAnnouncement>(`/education/announcements/mine/${id}`)
}
