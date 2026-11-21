<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
onLaunch(() => {
  // 登录态判断在登录页 onShow 中处理:未登录停在登录页,已登录跳转首页
});
</script>
<style>
page {
  background-color: #f5f6fa;
}

/* ===== 企业端（收费台账）通用样式，类名统一 ent- 前缀，避免影响学生/家长端 ===== */

.ent-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 48rpx;
  box-sizing: border-box;
}

.ent-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
  box-sizing: border-box;
}

.ent-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.ent-card-title .ent-link {
  font-size: 24rpx;
  font-weight: 400;
  color: #2563eb;
}

.ent-section-title {
  margin: 32rpx 4rpx 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.ent-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 10rpx 0;
  font-size: 26rpx;
  line-height: 1.5;
}

.ent-label {
  flex: 0 0 auto;
  margin-right: 20rpx;
  color: #6b7280;
}

.ent-value {
  flex: 1;
  min-width: 0;
  text-align: right;
  color: #111827;
  word-break: break-all;
}

.ent-value.left {
  text-align: left;
}

.ent-muted {
  font-size: 24rpx;
  color: #9ca3af;
}

.ent-amount {
  font-variant-numeric: tabular-nums;
}

.ent-amount.warn {
  color: #dc2626;
}

.ent-amount.ok {
  color: #059669;
}

.ent-divider {
  height: 1rpx;
  margin: 16rpx 0;
  background: #f3f4f6;
}

.ent-summary-grid {
  display: flex;
  flex-wrap: wrap;
  margin: -8rpx;
}

.ent-summary-item {
  width: 33.33%;
  padding: 8rpx;
  box-sizing: border-box;
}

.ent-summary-box {
  padding: 16rpx 12rpx;
  border-radius: 12rpx;
  background: #f8fafc;
  text-align: center;
}

.ent-summary-label {
  display: block;
  font-size: 22rpx;
  color: #6b7280;
}

.ent-summary-value {
  display: block;
  margin-top: 6rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.ent-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 72rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  border: 1rpx solid #e5e7eb;
  background: #ffffff;
  color: #374151;
  font-size: 27rpx;
  line-height: 1.2;
}

.ent-btn.primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.ent-btn.danger {
  border-color: #fecaca;
  background: #fff5f5;
  color: #dc2626;
}

.ent-btn.ghost {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.ent-btn.small {
  min-height: 56rpx;
  padding: 0 20rpx;
  font-size: 24rpx;
}

.ent-btn.disabled {
  opacity: 0.5;
}

.ent-btn-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.ent-btn-row.right {
  justify-content: flex-end;
}

.ent-form-item {
  margin-bottom: 22rpx;
}

.ent-form-label {
  display: block;
  margin-bottom: 10rpx;
  font-size: 26rpx;
  color: #374151;
}

.ent-required {
  color: #dc2626;
}

.ent-input,
.ent-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 0 20rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 28rpx;
  color: #111827;
}

.ent-input {
  height: 80rpx;
}

.ent-textarea {
  min-height: 150rpx;
  padding: 18rpx 20rpx;
  line-height: 1.5;
}

.ent-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 80rpx;
  padding: 0 20rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 28rpx;
  color: #111827;
}

.ent-picker.is-placeholder {
  color: #9ca3af;
}

/* 列表页的搜索与筛选：搜索框占满，筛选器二等分 */
.ent-search-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.ent-search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: 12rpx;
  background: #f5f6fa;
  font-size: 26rpx;
  color: #111827;
}

.ent-filter-row {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}

.ent-filter-row .ent-picker {
  flex: 1;
  min-height: 68rpx;
  font-size: 25rpx;
}

.ent-card-hover {
  background: #fafbfc;
}

.ent-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 76rpx;
  font-size: 27rpx;
  color: #374151;
}

.ent-date-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.ent-date-row .ent-picker {
  flex: 1;
}

.ent-tag {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  background: #f3f4f6;
  color: #6b7280;
}

.ent-tag.blue {
  background: #eff6ff;
  color: #2563eb;
}

.ent-tag.green {
  background: #ecfdf5;
  color: #059669;
}

.ent-tag.orange {
  background: #fff7ed;
  color: #c2410c;
}

.ent-tag.red {
  background: #fef2f2;
  color: #dc2626;
}

.ent-empty {
  padding: 48rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9ca3af;
}

.ent-tip {
  padding: 12rpx 4rpx;
  font-size: 23rpx;
  color: #9ca3af;
  line-height: 1.6;
}

.ent-item-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.ent-item-name {
  font-size: 29rpx;
  font-weight: 600;
  color: #111827;
}

.ent-tabs {
  display: flex;
  padding: 6rpx;
  margin-bottom: 20rpx;
  border-radius: 14rpx;
  background: #eef1f6;
}

.ent-tab {
  flex: 1;
  padding: 14rpx 0;
  border-radius: 10rpx;
  text-align: center;
  font-size: 26rpx;
  color: #4b5563;
}

.ent-tab.active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(17, 24, 39, 0.06);
}

.ent-footer {
  position: sticky;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  margin: 0 -24rpx -48rpx;
  background: rgba(245, 246, 250, 0.96);
}

.ent-footer .ent-btn {
  width: 100%;
}

/* 底部弹层：新增/编辑表单共用 */
.ent-sheet {
  display: flex;
  flex-direction: column;
  max-height: 84vh;
  padding-bottom: env(safe-area-inset-bottom);
}

.ent-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.ent-sheet-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.ent-sheet-close {
  font-size: 26rpx;
  color: #6b7280;
}

.ent-sheet-body {
  padding: 12rpx 32rpx 4rpx;
  overflow-y: auto;
}

.ent-sheet-footer {
  padding: 16rpx 32rpx 24rpx;
}

.ent-sheet-footer .ent-btn {
  width: 100%;
}

/* ===== 列表页数据优先布局：筛选默认收起，把首屏留给数据 ===== */

.list-page {
  padding-bottom: 180rpx;
}

.list-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding-bottom: 12rpx;
  background: #f5f6fa;
}

.toolbar-search {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10rpx;
  height: 76rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.05);
}

.toolbar-search-input {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  font-size: 27rpx;
  color: #111827;
}

.toolbar-clear {
  flex: 0 0 auto;
  padding: 8rpx;
}

.toolbar-btn {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 76rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.05);
  font-size: 25rpx;
  color: #4b5563;
}

.toolbar-btn.active {
  color: #2563eb;
  background: #eff6ff;
}

.toolbar-badge {
  min-width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #2563eb;
  color: #ffffff;
  font-size: 19rpx;
  text-align: center;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
  padding-bottom: 14rpx;
}

.chip {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2563eb;
  font-size: 22rpx;
}

.chip-clear {
  padding: 8rpx 10rpx;
  font-size: 22rpx;
  color: #6b7280;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6rpx 4rpx 14rpx;
}

.list-title {
  font-size: 29rpx;
  font-weight: 600;
  color: #111827;
}

.list-count {
  font-size: 23rpx;
  color: #9ca3af;
}

.filter-panel {
  margin-bottom: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: #ffffff;
  box-shadow: 0 2rpx 16rpx rgba(17, 24, 39, 0.05);
}

.filter-grid {
  display: flex;
  gap: 16rpx;
}

.filter-field {
  flex: 1;
  min-width: 0;
}

.filter-field + .filter-field {
  margin-top: 0;
}

.filter-field-block {
  margin-top: 16rpx;
}

.filter-label {
  display: block;
  margin-bottom: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
}

.status-chips {
  display: flex;
  gap: 12rpx;
}

.status-chip {
  flex: 1;
  padding: 14rpx 0;
  border-radius: 10rpx;
  background: #f5f6fa;
  text-align: center;
  font-size: 25rpx;
  color: #4b5563;
}

.status-chip.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 600;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 18rpx;
}

.fab {
  position: fixed;
  right: 36rpx;
  bottom: calc(48rpx + env(safe-area-inset-bottom));
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 12rpx 30rpx -8rpx rgba(37, 99, 235, 0.55);
}

.fab:active {
  background: #1d4ed8;
}
</style>
