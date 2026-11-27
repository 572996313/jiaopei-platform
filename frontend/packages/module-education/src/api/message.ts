import { requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/** 事件级别：代码判定值，按 AGENTS 的字典边界不入字典 */
export const MESSAGE_LEVEL_OPTIONS = [
  { value: 'info', label: '提示', tag: 'info' },
  { value: 'warning', label: '警告', tag: 'warning' },
  { value: 'critical', label: '严重', tag: 'danger' },
]

/** 系统事件与人工事项的状态是两套口径：系统 active/resolved，人工 open/in_progress/done/closed */
export const MESSAGE_STATUS_OPTIONS = [
  { value: 'active', label: '待处理（系统）', tag: 'warning' },
  { value: 'open', label: '待处理（人工）', tag: 'warning' },
  { value: 'in_progress', label: '处理中', tag: 'primary' },
  { value: 'done', label: '已完成', tag: 'success' },
  { value: 'closed', label: '已关闭', tag: 'info' },
  { value: 'resolved', label: '已消解', tag: 'success' },
]

export const MESSAGE_STATUS_FILTER_OPTIONS = [
  { value: 'all', label: '全部状态' },
  ...MESSAGE_STATUS_OPTIONS.map((item) => ({ value: item.value, label: item.label })),
]

export const MESSAGE_MANUAL_STATUS_OPTIONS = [
  { value: 'open', label: '待处理' },
  { value: 'in_progress', label: '处理中' },
  { value: 'done', label: '已完成' },
  { value: 'closed', label: '已关闭' },
]

export const MESSAGE_SOURCE_MODE_OPTIONS = [
  { value: 'scan', label: '配置化巡检' },
  { value: 'manual', label: '人工事项' },
]

export const MESSAGE_VISIBILITY_OPTIONS = [
  { value: 'internal', label: '内部员工' },
  { value: 'parent', label: '家长端' },
  { value: 'student', label: '学生端' },
]

export const MESSAGE_CONDITION_OPS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'contains', label: '包含' },
  { value: 'in', label: '在列表中（逗号分隔）' },
]

export const SUBSCRIPTION_RULE_TYPES = [
  { value: 'type_code', label: '事件类型' },
  { value: 'category', label: '分类' },
  { value: 'level', label: '级别' },
  { value: 'campus', label: '校区' },
  { value: 'class', label: '班级' },
  { value: 'student', label: '学员' },
  { value: 'teacher', label: '教师' },
  { value: 'keyword', label: '标题/正文关键字' },
]

/** 只在订阅页手动新增时可选的维度：实体维度优先用「推荐订阅」一键带入，避免一次拉全量下拉 */
export const MANUAL_SUBSCRIPTION_RULE_TYPES = ['type_code', 'category', 'level', 'keyword']

export interface MessageEventRecord {
  id: string
  institutionId?: string | null
  campusId?: string | null
  typeCode: string
  typeName?: string | null
  category?: string | null
  level?: string | null
  title: string
  body: string
  status: string
  classId?: string | null
  studentId?: string | null
  teacherId?: string | null
  lessonId?: string | null
  refType?: string | null
  refId?: string | null
  occurredAt?: string | null
  expireAt?: string | null
  assigneeUserId?: string | null
  assigneeName?: string | null
  dueAt?: string | null
  actionUrl?: string | null
  readAt?: string | null
  read: boolean
  resolvedAt?: string | null
  resolvedBy?: string | null
  resolvedByName?: string | null
  createdAt?: string | null
  createdBy?: string | null
  createdByName?: string | null
}

export interface MessageTimelineRecord {
  id: string
  action: string
  content?: string | null
  userId?: string | null
  userName?: string | null
  createdAt?: string | null
}

export interface MessageEventDetail {
  event: MessageEventRecord
  timeline: MessageTimelineRecord[]
}

export interface MessageEventPageParams extends PageParams {
  typeCode?: string | null
  category?: string | null
  campusId?: string | null
  classId?: string | null
  studentId?: string | null
  teacherId?: string | null
  assigneeUserId?: string | null
  status?: string | null
  openOnly?: boolean | null
  level?: string | null
  keyword?: string | null
  dateFrom?: string | null
  dateTo?: string | null
  unreadOnly?: boolean | null
  mine?: boolean | null
}

export interface MessageManualCreatePayload {
  typeCode: string
  title: string
  body: string
  level?: string | null
  campusId?: string | null
  classId?: string | null
  studentId?: string | null
  teacherId?: string | null
  assigneeUserId?: string | null
  dueAt?: string | null
  occurredAt?: string | null
  actionUrl?: string | null
}

export interface MessageManualUpdatePayload {
  title: string
  body: string
  level?: string | null
  assigneeUserId?: string | null
  dueAt?: string | null
  status: string
  comment?: string | null
  actionUrl?: string | null
}

export interface MessageNotifyPayload {
  userIds: string[]
  note?: string | null
}

export interface MessageTypeOption {
  typeCode: string
  typeName: string
  category?: string | null
  level?: string | null
  sourceMode?: string | null
  creatable?: boolean | null
}

export interface MessageAssignee {
  userId: string
  displayName?: string | null
  username?: string | null
}

export interface MessageSubscriptionRule {
  ruleType: string
  ruleValue: string
  label?: string | null
  enabled: boolean
}

export interface MessageSubscription {
  rules: MessageSubscriptionRule[]
}

export interface MessageSubscriptionRuleInput {
  ruleType: string
  ruleValue: string
  enabled?: boolean
}

export interface MessageRecommendation {
  ruleType: string
  ruleValue: string
  label?: string | null
  reason?: string | null
  subscribed: boolean
}

export interface MessageCondition {
  column: string
  op: string
  value: unknown
}

export interface MessageEventTypeRecord {
  id: string
  typeCode: string
  typeName: string
  category: string
  sourceMode: string
  sourceCode?: string | null
  sourceViewName?: string | null
  conditions?: MessageCondition[] | null
  titleTemplate: string
  bodyTemplate: string
  actionTemplate?: string | null
  dedupeTemplate: string
  level: string
  visibility: string
  intervalMinutes?: number | null
  retentionDays?: number | null
  creatable?: boolean | null
  enabled?: boolean | null
  lastRunAt?: string | null
  lastRunCount?: number | null
  lastError?: string | null
  sortNo?: number | null
}

export interface MessageEventTypePayload {
  typeCode: string
  typeName: string
  category: string
  sourceMode: string
  sourceCode?: string | null
  conditions?: MessageCondition[]
  titleTemplate: string
  bodyTemplate: string
  actionTemplate?: string | null
  dedupeTemplate: string
  level: string
  visibility: string
  intervalMinutes?: number | null
  retentionDays?: number | null
  creatable?: boolean | null
  enabled?: boolean | null
  sortNo?: number | null
}

export interface MessageSourceRecord {
  id: string
  sourceCode: string
  displayName: string
  viewName: string
  description?: string | null
  columns: string[]
  enabled?: boolean | null
  lastValidatedAt?: string | null
}

export interface MessageReadLedgerSummary {
  typeCode: string
  typeName: string
  category?: string | null
  sourceMode?: string | null
  totalCount: number
  activeCount: number
  readCount: number
  subscriberCount: number
  lastRunAt?: string | null
  lastRunCount?: number | null
  lastError?: string | null
}

export interface MessageReader {
  userId: string
  userName?: string | null
  readAt?: string | null
}

export function fetchMessageStream(params: MessageEventPageParams) {
  return requestGet<PageResult<MessageEventRecord>>('/education/messages/stream', { ...params })
}

export function fetchMyMessagePage(params: MessageEventPageParams) {
  return requestGet<PageResult<MessageEventRecord>>('/education/messages/mine', { ...params })
}

export function fetchMessageUnreadCount() {
  return requestGet<number>('/education/messages/unread-count')
}

/** 打开即已读：这个 GET 本身就是打读动作，服务端只记首次 */
export function fetchMessageDetail(id: string) {
  return requestGet<MessageEventDetail>(`/education/messages/${id}`)
}

export function createManualMessage(payload: MessageManualCreatePayload) {
  return requestPost<MessageEventRecord>('/education/messages', payload)
}

export function updateManualMessage(id: string, payload: MessageManualUpdatePayload) {
  return requestPut<MessageEventRecord>(`/education/messages/${id}`, payload)
}

export function notifyMessageUsers(id: string, payload: MessageNotifyPayload) {
  return requestPost<number>(`/education/messages/${id}/notify`, payload)
}

export function fetchMessageTypeOptions() {
  return requestGet<MessageTypeOption[]>('/education/messages/types')
}

export function fetchMessageAssignees(keyword?: string) {
  return requestGet<MessageAssignee[]>('/education/messages/assignees', { keyword })
}

export function fetchMySubscriptions() {
  return requestGet<MessageSubscription>('/education/message-subscriptions')
}

export function saveMySubscriptions(rules: MessageSubscriptionRuleInput[]) {
  return requestPut<void>('/education/message-subscriptions', { rules })
}

export function fetchMessageRecommendations() {
  return requestGet<MessageRecommendation[]>('/education/message-subscriptions/recommendations')
}

export function fetchMessageTypes() {
  return requestGet<MessageEventTypeRecord[]>('/education/message-admin/types')
}

export function createMessageType(payload: MessageEventTypePayload) {
  return requestPost<MessageEventTypeRecord>('/education/message-admin/types', payload)
}

export function updateMessageType(typeCode: string, payload: MessageEventTypePayload) {
  return requestPut<MessageEventTypeRecord>(`/education/message-admin/types/${typeCode}`, payload)
}

export function fetchMessageSources() {
  return requestGet<MessageSourceRecord[]>('/education/message-admin/sources')
}

export function validateMessageSource(sourceCode: string) {
  return requestPost<MessageSourceRecord>(
    `/education/message-admin/sources/${sourceCode}/validate`,
    {}
  )
}

export function fetchMessageReadLedger() {
  return requestGet<MessageReadLedgerSummary[]>('/education/message-admin/read-ledger')
}

export function fetchMessageReaders(eventId: string) {
  return requestGet<MessageReader[]>(`/education/message-admin/${eventId}/readers`)
}
