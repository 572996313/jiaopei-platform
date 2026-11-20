import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'
import { saveBlob } from './download'

/**
 * 收费管理接口。
 *
 * <p>应收/实收/未收费/合计发生/结余全部由服务端现算，前端不再做第二套金额推导；
 * 页面上的实时预览只用于录入时给操作员一个参照。</p>
 */

/** 收费项目：billingMode 决定发生额怎么算（monthly=月标准×系数，daily=天数×日标准） */
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
  institutionId?: string
  itemCode?: string
  itemName: string
  billingMode: string
  sortNo?: number | null
  status?: string
  remark?: string
}

/** 价目：四个维度 null/0 都表示「不限」，特异度 学员 > 班级 > 校区 > 学期 */
export interface FeePriceRecord {
  id: string
  institutionId?: string | null
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
  institutionId?: string
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

export interface FeeLedgerRowView {
  id: string
  institutionId?: string | null
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
  itemCount: number
  receivable?: number | null
  paid?: number | null
  unpaid?: number | null
  totalOccurrence?: number | null
  balance?: number | null
  remark?: string | null
}

export interface FeeLedgerDetailView extends Omit<FeeLedgerRowView, 'itemCount'> {
  termStartDate?: string | null
  termEndDate?: string | null
  items: FeeLedgerItemView[]
  monthly: FeeMonthlyView[]
  attendance: FeeAttendanceView[]
  payments: FeePaymentView[]
}

export interface FeeLedgerPageParams extends PageParams {
  institutionId?: string
  campusId?: string
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

export interface FeeLedgerCreatePayload {
  institutionId?: string
  classId: string
  termId: string
  studentId: string
  status?: string
  remark?: string
}

export interface FeeLedgerUpdatePayload {
  status?: string
  remark?: string
  items: FeeLedgerItemPayload[]
}

export interface FeeLedgerGeneratePayload {
  institutionId?: string
  termId: string
  classId: string
}

export interface FeeLedgerGenerateResult {
  createdCount: number
  skippedCount: number
  itemCount: number
}

export interface FeePaymentPayload {
  ledgerId: string
  ledgerItemId: string
  paymentNo?: string
  amount: number
  paidOn: string
  paymentMethod?: string
  billingMonth?: string | null
  voucherNo?: string
  remark?: string
}

export interface FeePaymentUpdatePayload {
  amount?: number
  paidOn?: string
  paymentMethod?: string
  billingMonth?: string | null
  voucherNo?: string
  remark?: string
}

export interface FeeMonthlyOverridePayload {
  occurrenceAmount: number
  monthFactor?: number | null
  overrideReason?: string
}

export interface FeeAttendanceOverridePayload {
  attendanceDays: number
  overrideReason?: string
}

export interface FeeRecalculatePayload {
  ledgerId?: string
  termId?: string
  classId?: string
}

export type FeeAttendanceSyncPayload = FeeRecalculatePayload

export interface FeeAttendanceSyncResult {
  ledgerCount: number
  syncedCount: number
  keptOverrideCount: number
}

const ITEM_BASE = '/education/fee-items'
const PRICE_BASE = '/education/fee-prices'
const LEDGER_BASE = '/education/fee-ledgers'
const PAYMENT_BASE = '/education/fee-payments'
const MONTHLY_BASE = '/education/fee-monthly'
const ATTENDANCE_BASE = '/education/fee-attendance'

// ------------------------------------------------------------------ 收费项目

export function fetchFeeItems(params?: { institutionId?: string; status?: string }) {
  return requestGet<FeeItemRecord[]>(ITEM_BASE, params)
}

export function createFeeItem(payload: FeeItemPayload) {
  return requestPost<FeeItemRecord>(ITEM_BASE, payload)
}

export function updateFeeItem(id: string, payload: Omit<FeeItemPayload, 'institutionId'>) {
  return requestPut<FeeItemRecord>(`${ITEM_BASE}/${id}`, payload)
}

export function deleteFeeItem(id: string) {
  return requestDelete<null>(`${ITEM_BASE}/${id}`)
}

// ------------------------------------------------------------------ 价目

export function fetchFeePrices(params: {
  institutionId?: string
  feeItemId?: string
  campusId?: string
}) {
  return requestGet<FeePriceRecord[]>(PRICE_BASE, params)
}

export function createFeePrice(payload: FeePricePayload) {
  return requestPost<FeePriceRecord>(PRICE_BASE, payload)
}

export function updateFeePrice(
  id: string,
  payload: Pick<FeePriceRecord, 'monthlyPrice' | 'dailyPrice' | 'status' | 'remark'>
) {
  return requestPut<FeePriceRecord>(`${PRICE_BASE}/${id}`, payload)
}

export function deleteFeePrice(id: string) {
  return requestDelete<null>(`${PRICE_BASE}/${id}`)
}

// ------------------------------------------------------------------ 收费台账

export function fetchFeeLedgerPage(params: FeeLedgerPageParams) {
  return requestGet<PageResult<FeeLedgerRowView>>(
    `${LEDGER_BASE}/page`,
    params as unknown as Record<string, unknown>
  )
}

export function fetchFeeLedger(id: string) {
  return requestGet<FeeLedgerDetailView>(`${LEDGER_BASE}/${id}`)
}

export function createFeeLedger(payload: FeeLedgerCreatePayload) {
  return requestPost<FeeLedgerDetailView>(LEDGER_BASE, payload)
}

export function updateFeeLedger(id: string, payload: FeeLedgerUpdatePayload) {
  return requestPut<FeeLedgerDetailView>(`${LEDGER_BASE}/${id}`, payload)
}

export function deleteFeeLedger(id: string) {
  return requestDelete<null>(`${LEDGER_BASE}/${id}`)
}

export function generateFeeLedgers(payload: FeeLedgerGeneratePayload) {
  return requestPost<FeeLedgerGenerateResult>(`${LEDGER_BASE}/generate`, payload)
}

export function resolveFeeLedgerPrices(id: string) {
  return requestPost<FeeLedgerDetailView>(`${LEDGER_BASE}/${id}/resolve-prices`, {})
}

/** 导出沿用列表的筛选条件（分页参数不参与：导出一次给全量） */
export function exportFeeMatrix(
  params: Omit<FeeLedgerPageParams, 'page' | 'pageSize' | 'sortField' | 'sortOrder'>,
  filename: string
) {
  return saveBlob(
    `${LEDGER_BASE}/export/matrix`,
    filename,
    params as unknown as Record<string, unknown>
  )
}

export function exportFeeDetail(
  params: Omit<FeeLedgerPageParams, 'page' | 'pageSize' | 'sortField' | 'sortOrder'>,
  filename: string
) {
  return saveBlob(
    `${LEDGER_BASE}/export/detail`,
    filename,
    params as unknown as Record<string, unknown>
  )
}

// ------------------------------------------------------------------ 收款流水

export function fetchFeePayments(params: { ledgerId?: string; ledgerItemId?: string }) {
  return requestGet<FeePaymentView[]>(PAYMENT_BASE, params)
}

export function createFeePayment(payload: FeePaymentPayload) {
  return requestPost<FeePaymentView>(PAYMENT_BASE, payload)
}

export function updateFeePayment(id: string, payload: FeePaymentUpdatePayload) {
  return requestPut<FeePaymentView>(`${PAYMENT_BASE}/${id}`, payload)
}

export function deleteFeePayment(id: string) {
  return requestDelete<null>(`${PAYMENT_BASE}/${id}`)
}

// ------------------------------------------------------------------ 逐月发生额与出勤

export function fetchFeeMonthly(ledgerId: string) {
  return requestGet<FeeMonthlyView[]>(MONTHLY_BASE, { ledgerId })
}

export function overrideFeeMonthly(
  ledgerItemId: string,
  billingMonth: string,
  payload: FeeMonthlyOverridePayload
) {
  return requestPut<FeeMonthlyView>(
    `${MONTHLY_BASE}/${ledgerItemId}/${billingMonth}`,
    payload
  )
}

/** 撤销手工覆盖：月份系数与发生额回到引擎默认口径 */
export function revertFeeMonthly(ledgerItemId: string, billingMonth: string) {
  return requestDelete<FeeMonthlyView>(`${MONTHLY_BASE}/${ledgerItemId}/${billingMonth}`)
}

export function recalculateFees(payload: FeeRecalculatePayload) {
  return requestPost<number>(`${MONTHLY_BASE}/recalculate`, payload)
}

export function fetchFeeAttendance(ledgerId: string) {
  return requestGet<FeeAttendanceView[]>(ATTENDANCE_BASE, { ledgerId })
}

export function overrideFeeAttendance(
  ledgerId: string,
  billingMonth: string,
  payload: FeeAttendanceOverridePayload
) {
  return requestPut<FeeAttendanceView>(
    `${ATTENDANCE_BASE}/${ledgerId}/${billingMonth}`,
    payload
  )
}

/** 撤销手工覆盖：出勤天数回到最近一次考勤汇总值 */
export function revertFeeAttendance(ledgerId: string, billingMonth: string) {
  return requestDelete<FeeAttendanceView>(`${ATTENDANCE_BASE}/${ledgerId}/${billingMonth}`)
}

export function syncFeeAttendance(payload: FeeAttendanceSyncPayload) {
  return requestPost<FeeAttendanceSyncResult>(`${ATTENDANCE_BASE}/sync`, payload)
}

// ------------------------------------------------------------------ 展示辅助

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

/** 账期统一用当月 1 日的 YYYY-MM-DD，月份列按年月比较，绝不按月份数字排序 */
export function monthText(value?: string | null) {
  if (!value) {
    return '-'
  }
  const [year, month] = value.split('-')
  return `${Number(year)}年${Number(month)}月`
}

export function monthValue(value?: string | null) {
  return value ? value.slice(0, 7) : ''
}
