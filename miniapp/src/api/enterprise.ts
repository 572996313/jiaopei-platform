import { requestDelete, requestGet, requestPost, requestPut } from '@/utils/request'

/**
 * 小程序企业端（收费台账）接口。
 *
 * <p>全部复用管理后台已有端点，不新增后端接口；金额一律由服务端现算，
 * 这里只负责透传与展示辅助，不做第二套公式。</p>
 */

// ---------------------------------------------------------------- 通用分页

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

// ---------------------------------------------------------------- 基础资料

export interface TermRecord {
  id: string
  termCode?: string | null
  termName: string
  startDate?: string | null
  endDate?: string | null
  status?: string | null
}

export interface CampusRecord {
  id: string
  campusCode?: string | null
  campusName: string
  status?: string | null
}

export interface ClassOption {
  id: string
  classCode?: string | null
  className: string
  campusId?: string | null
  termId?: string | null
}

export interface StudentOption {
  id: string
  studentNo?: string | null
  studentName: string
}

export interface DictOptionItem {
  itemCode: string
  itemName: string
  itemValue: string
}

export interface DictOptionGroup {
  typeCode: string
  items: DictOptionItem[]
}

export function fetchTerms() {
  return requestGet<TermRecord[]>('/organizations/terms')
}

export function fetchCampuses() {
  return requestGet<CampusRecord[]>('/organizations/campuses')
}

export function fetchClassOptions(params?: { keyword?: string; termId?: string; ids?: string }) {
  return requestGet<ClassOption[]>('/education/classes/options', params)
}

export function fetchStudentOptions(params?: { keyword?: string; ids?: string }) {
  return requestGet<StudentOption[]>('/education/students/options', params)
}

export async function fetchDictOptions(typeCode: string) {
  const groups = await requestGet<DictOptionGroup[]>('/system/dicts/options', { typeCodes: typeCode })
  return groups.find((group) => group.typeCode === typeCode)?.items ?? []
}

// ---------------------------------------------------------------- 收费项目

export interface FeeItemRecord {
  id: string
  institutionId?: string | null
  itemCode?: string | null
  itemName?: string | null
  billingMode?: string | null
  sortNo?: number | null
  status?: string | null
  remark?: string | null
}

export interface FeeItemPayload {
  itemCode?: string
  itemName: string
  billingMode: string
  sortNo?: number | null
  status?: string
  remark?: string
}

export function fetchFeeItems(params?: { status?: string }) {
  return requestGet<FeeItemRecord[]>('/education/fee-items', params)
}

export function createFeeItem(payload: FeeItemPayload) {
  return requestPost<FeeItemRecord>('/education/fee-items', payload as unknown as Record<string, unknown>)
}

export function updateFeeItem(id: string, payload: FeeItemPayload) {
  return requestPut<FeeItemRecord>(`/education/fee-items/${id}`, payload as unknown as Record<string, unknown>)
}

export function deleteFeeItem(id: string) {
  return requestDelete<null>(`/education/fee-items/${id}`)
}

// ---------------------------------------------------------------- 价目

export interface FeePriceRecord {
  id: string
  feeItemId?: string | null
  campusId?: string | null
  classId?: string | null
  termId?: string | null
  studentId?: string | null
  monthlyPrice?: number | null
  dailyPrice?: number | null
  status?: string | null
  remark?: string | null
}

export interface FeePricePayload {
  feeItemId: string
  campusId?: string | null
  classId?: string | null
  termId?: string | null
  studentId?: string | null
  monthlyPrice?: number | null
  dailyPrice?: number | null
  status?: string
  remark?: string
}

export function fetchFeePrices(params?: { feeItemId?: string; campusId?: string }) {
  return requestGet<FeePriceRecord[]>('/education/fee-prices', params)
}

export function createFeePrice(payload: FeePricePayload) {
  return requestPost<FeePriceRecord>('/education/fee-prices', payload as unknown as Record<string, unknown>)
}

export function updateFeePrice(
  id: string,
  payload: Pick<FeePriceRecord, 'monthlyPrice' | 'dailyPrice' | 'status' | 'remark'>,
) {
  return requestPut<FeePriceRecord>(`/education/fee-prices/${id}`, payload as unknown as Record<string, unknown>)
}

export function deleteFeePrice(id: string) {
  return requestDelete<null>(`/education/fee-prices/${id}`)
}

// ---------------------------------------------------------------- 收费台账

export interface FeeLedgerRowView {
  id: string
  campusId?: string | null
  campusName?: string | null
  classId?: string | null
  className?: string | null
  termId?: string | null
  termName?: string | null
  studentId?: string | null
  studentNo?: string | null
  studentName?: string | null
  status?: string | null
  itemCount?: number
  receivable?: number | null
  paid?: number | null
  unpaid?: number | null
  totalOccurrence?: number | null
  balance?: number | null
  remark?: string | null
}

export interface FeeLedgerItemView {
  id: string
  feeItemId: string
  itemCode?: string | null
  itemName?: string | null
  billingMode?: string | null
  billableMonths?: number | null
  monthlyStandard?: number | null
  dailyRate?: number | null
  openingBalance?: number | null
  otherDeduction?: number | null
  receivable?: number | null
  paid?: number | null
  unpaid?: number | null
  totalOccurrence?: number | null
  balance?: number | null
  priceSource?: string | null
  remark?: string | null
}

export interface FeeMonthlyView {
  id: string
  ledgerItemId: string
  feeItemId: string
  itemName?: string | null
  billingMode?: string | null
  billingMonth: string
  monthFactor?: number | null
  autoAmount?: number | null
  occurrenceAmount?: number | null
  manualOverride: boolean
  remark?: string | null
}

export interface FeeAttendanceView {
  id: string
  billingMonth: string
  attendanceDays?: number | null
  autoDays?: number | null
  manualOverride: boolean
  source?: string | null
  remark?: string | null
}

export interface FeePaymentView {
  id: string
  ledgerItemId: string
  paymentNo?: string | null
  amount?: number | null
  paidOn?: string | null
  paymentMethod?: string | null
  billingMonth?: string | null
  voucherNo?: string | null
  remark?: string | null
}

export interface FeeLedgerDetailView extends FeeLedgerRowView {
  termStartDate?: string | null
  termEndDate?: string | null
  items: FeeLedgerItemView[]
  monthly: FeeMonthlyView[]
  attendance: FeeAttendanceView[]
  payments: FeePaymentView[]
}

export interface FeeLedgerPageParams {
  page?: number
  pageSize?: number
  termId?: string | null
  classId?: string | null
  studentId?: string | null
  status?: string | null
  keyword?: string
}

export interface FeeLedgerItemPayload {
  feeItemId: string
  billableMonths?: number | null
  monthlyStandard?: number | null
  dailyRate?: number | null
  openingBalance?: number | null
  otherDeduction?: number | null
  remark?: string
}

export function fetchFeeLedgerPage(params: FeeLedgerPageParams) {
  return requestGet<PageResult<FeeLedgerRowView>>(
    '/education/fee-ledgers/page',
    params as unknown as Record<string, unknown>,
  )
}

export function fetchFeeLedger(id: string) {
  return requestGet<FeeLedgerDetailView>(`/education/fee-ledgers/${id}`)
}

export function createFeeLedger(payload: { classId: string; termId: string; studentId: string }) {
  return requestPost<FeeLedgerDetailView>('/education/fee-ledgers', payload)
}

export function updateFeeLedger(
  id: string,
  payload: { status?: string; remark?: string; items?: FeeLedgerItemPayload[] },
) {
  return requestPut<FeeLedgerDetailView>(
    `/education/fee-ledgers/${id}`,
    payload as unknown as Record<string, unknown>,
  )
}

export function deleteFeeLedger(id: string) {
  return requestDelete<null>(`/education/fee-ledgers/${id}`)
}

export interface FeeLedgerGenerateResult {
  createdCount: number
  skippedCount: number
  itemCount: number
}

export function generateFeeLedgers(payload: { termId: string; classId: string }) {
  return requestPost<FeeLedgerGenerateResult>('/education/fee-ledgers/generate', payload)
}

export function resolveFeeLedgerPrices(id: string) {
  return requestPost<FeeLedgerDetailView>(`/education/fee-ledgers/${id}/resolve-prices`, {})
}

// ---------------------------------------------------------------- 收款流水

export function fetchFeePayments(params: { ledgerId?: string; ledgerItemId?: string }) {
  return requestGet<FeePaymentView[]>('/education/fee-payments', params)
}

export interface FeePaymentPayload {
  ledgerId?: string
  ledgerItemId?: string
  paymentNo?: string
  amount: number
  paidOn: string
  paymentMethod?: string
  billingMonth?: string | null
  voucherNo?: string
  remark?: string
}

export function createFeePayment(payload: FeePaymentPayload) {
  return requestPost<FeePaymentView>(
    '/education/fee-payments',
    payload as unknown as Record<string, unknown>,
  )
}

export function updateFeePayment(
  id: string,
  payload: {
    amount?: number
    paidOn?: string
    paymentMethod?: string
    billingMonth?: string | null
    voucherNo?: string
    remark?: string
  },
) {
  return requestPut<FeePaymentView>(
    `/education/fee-payments/${id}`,
    payload as unknown as Record<string, unknown>,
  )
}

export function deleteFeePayment(id: string) {
  return requestDelete<null>(`/education/fee-payments/${id}`)
}

// ---------------------------------------------------------------- 逐月发生额

export function fetchFeeMonthly(ledgerId: string) {
  return requestGet<FeeMonthlyView[]>('/education/fee-monthly', { ledgerId })
}

export function overrideFeeMonthly(
  ledgerItemId: string,
  billingMonth: string,
  payload: { occurrenceAmount: number; monthFactor?: number | null; overrideReason?: string },
) {
  return requestPut<FeeMonthlyView>(
    `/education/fee-monthly/${ledgerItemId}/${billingMonth}`,
    payload as unknown as Record<string, unknown>,
  )
}

export function revertFeeMonthly(ledgerItemId: string, billingMonth: string) {
  return requestDelete<FeeMonthlyView>(`/education/fee-monthly/${ledgerItemId}/${billingMonth}`)
}

export function recalculateFees(payload: { ledgerId: string }) {
  return requestPost<number>('/education/fee-monthly/recalculate', payload)
}

// ---------------------------------------------------------------- 月度出勤

export function fetchFeeAttendance(ledgerId: string) {
  return requestGet<FeeAttendanceView[]>('/education/fee-attendance', { ledgerId })
}

export function overrideFeeAttendance(
  ledgerId: string,
  billingMonth: string,
  payload: { attendanceDays: number; overrideReason?: string },
) {
  return requestPut<FeeAttendanceView>(
    `/education/fee-attendance/${ledgerId}/${billingMonth}`,
    payload as unknown as Record<string, unknown>,
  )
}

export function revertFeeAttendance(ledgerId: string, billingMonth: string) {
  return requestDelete<FeeAttendanceView>(`/education/fee-attendance/${ledgerId}/${billingMonth}`)
}

export interface FeeAttendanceSyncResult {
  ledgerCount: number
  syncedCount: number
  keptOverrideCount: number
}

export function syncFeeAttendance(payload: { ledgerId: string }) {
  return requestPost<FeeAttendanceSyncResult>('/education/fee-attendance/sync', payload)
}

// ---------------------------------------------------------------- 展示辅助

export function billingModeLabel(value?: string | null) {
  if (value === 'monthly') {
    return '按月'
  }
  if (value === 'daily') {
    return '按天'
  }
  return value || '-'
}

export function moneyText(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toFixed(2)
}

/** 账期统一用当月 1 日的 YYYY-MM-DD，展示按年月 */
export function monthText(value?: string | null) {
  if (!value) {
    return '-'
  }
  const [year, month] = value.split('-')
  return `${Number(year)}年${Number(month)}月`
}

export function ledgerStatusLabel(value?: string | null) {
  if (value === 'settled') {
    return '已结清'
  }
  return '在收'
}

/** 价目级别：学员 > 班级 > 校区 > 学期 > 全局 */
export function priceLevelLabel(price: FeePriceRecord) {
  if (price.studentId && price.studentId !== '0') {
    return '学员级'
  }
  if (price.classId && price.classId !== '0') {
    return '班级级'
  }
  if (price.campusId && price.campusId !== '0') {
    return '校区级'
  }
  if (price.termId && price.termId !== '0') {
    return '学期级'
  }
  return '全局'
}

export function pad2(value: number) {
  return value < 10 ? `0${value}` : String(value)
}

/** 今天，YYYY-MM-DD */
export function todayText() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}

/** 当前账期，YYYY-MM-01 */
export function currentBillingMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-01`
}

/** 空串转 null，数值串转 number；用于"留空=按价目表"这类语义 */
export function optionalNumber(value: string): number | null {
  const trimmed = (value ?? '').trim()
  if (trimmed === '') {
    return null
  }
  const parsed = Number(trimmed)
  return Number.isNaN(parsed) ? null : parsed
}

export function optionalText(value?: string | null) {
  const trimmed = (value ?? '').trim()
  return trimmed === '' ? undefined : trimmed
}
