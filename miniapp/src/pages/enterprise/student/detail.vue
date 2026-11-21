<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { fetchDictOptions, type DictOptionItem } from '@/api/enterprise'
import {
  createStudentParent,
  deleteStudent,
  deleteStudentParent,
  fetchClassStudentPage,
  fetchStudentParents,
  parentStatusLabel,
  stageStudentRecord,
  studentStatusLabel,
  studentStatusTone,
  takeStudentRecord,
  textOrUndefined,
  updateStudentParent,
  classStudentStatusLabel,
  type ClassStudentRecord,
  type StudentParentRecord,
  type StudentRecord,
} from '@/api/archive'

const authStore = useAuthStore()
const canWriteProfile = computed(
  () => !authStore.userInfo || authStore.hasPermission('education:profile:write'),
)

const student = ref<StudentRecord | null>(null)
const handoffMissing = ref(false)
const initialized = ref(false)

const institutionId = computed(
  () => authStore.userInfo?.institutionId
    || authStore.userInfo?.orgId
    || student.value?.institutionId
    || '',
)

const genderOptions = ref<DictOptionItem[]>([])
const relationDict = ref<DictOptionItem[]>([])
const relationOptions = computed(() => [
  { value: '', label: '不指定' },
  ...relationDict.value.map((item) => ({ value: item.itemValue, label: item.itemName })),
])

const parents = ref<StudentParentRecord[]>([])
const parentsLoading = ref(false)
const parentsError = ref('')

const enrollments = ref<ClassStudentRecord[]>([])
const enrollmentsLoading = ref(false)
const enrollmentsError = ref('')

const popupRef = ref<any>(null)
const editingParentId = ref('')
const parentSubmitting = ref(false)
const parentFormError = ref('')
const parentStatusOptions = [
  { value: 'active', label: '启用' },
  // PC 端家长联系人写的是 inactive；DTO 注释写 disabled。两个值后端都照存，
  // 这里统一按 PC 的口径写 inactive，避免小程序改过之后 PC 把「停用」显示成「启用」。
  { value: 'inactive', label: '停用' },
]
const statusIndex = ref(0)
const relationIndex = ref(0)

const parentForm = ref({
  parentName: '',
  relationType: '',
  parentPhone: '',
  wechatNo: '',
  alipayPhone: '',
  isPrimary: true,
  isEmergencyContact: false,
  status: 'active',
  remark: '',
})

onShow(() => {
  // 从编辑页返回时带的是更新后的整条档案；首次进入则是列表交接的那条
  const staged = takeStudentRecord()
  if (staged) {
    student.value = staged
    handoffMissing.value = false
  } else if (!student.value) {
    handoffMissing.value = true
  }
  void init()
})

async function init() {
  if (!authStore.isLoggedIn()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!authStore.userInfo) {
    try {
      await authStore.refreshUserInfo()
    } catch {
      return
    }
  }
  if (!initialized.value) {
    await loadDicts()
    initialized.value = true
  }
  if (!student.value) {
    return
  }
  await Promise.all([loadParents(), loadEnrollments()])
}

/** 字典只影响显示名；失败时关系回显原始值，不硬编码中文 */
async function loadDicts() {
  try {
    const [genders, relations] = await Promise.all([
      fetchDictOptions('gender'),
      fetchDictOptions('parent_relation'),
    ])
    genderOptions.value = genders
    relationDict.value = relations
  } catch {
    // 忽略：详情本身仍可查看
  }
}

function genderText(value?: string | null) {
  if (!value) {
    return '-'
  }
  return genderOptions.value.find((item) => item.itemValue === value)?.itemName || value
}

function relationLabel(value?: string | null) {
  if (!value) {
    return '-'
  }
  return relationDict.value.find((item) => item.itemValue === value)?.itemName || value
}

async function loadParents() {
  if (!student.value || !institutionId.value) {
    parents.value = []
    return
  }
  parentsLoading.value = true
  parentsError.value = ''
  try {
    parents.value = await fetchStudentParents({
      institutionId: institutionId.value,
      studentId: student.value.id,
    })
  } catch (error) {
    parents.value = []
    parentsError.value = (error as Error)?.message || '家长联系人加载失败'
  } finally {
    parentsLoading.value = false
  }
}

/** 学员所在班级只读展示；入班/退班统一在班级详情维护 */
async function loadEnrollments() {
  if (!student.value) {
    enrollments.value = []
    return
  }
  enrollmentsLoading.value = true
  enrollmentsError.value = ''
  try {
    const result = await fetchClassStudentPage({
      page: 1,
      pageSize: 50,
      institutionId: institutionId.value || undefined,
      studentId: student.value.id,
    })
    enrollments.value = result.records
  } catch (error) {
    enrollments.value = []
    enrollmentsError.value = (error as Error)?.message || '所在班级加载失败'
  } finally {
    enrollmentsLoading.value = false
  }
}

function openEditStudent() {
  if (!student.value) {
    return
  }
  stageStudentRecord(student.value)
  uni.navigateTo({ url: `/pages/enterprise/student/form?id=${student.value.id}` })
}

function confirmDeleteStudent() {
  const current = student.value
  if (!current) {
    return
  }
  uni.showModal({
    title: '删除学员',
    content: `确定删除「${current.studentName || '该学员'}」吗？删除后档案不可恢复，请先确认没有在用的入班关系。`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteStudent(current.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch (error) {
        uni.showToast({
          title: (error as Error)?.message || '删除失败',
          icon: 'none',
          duration: 2800,
        })
      }
    },
  })
}

function openParentCreate() {
  editingParentId.value = ''
  parentForm.value = {
    parentName: '',
    relationType: '',
    parentPhone: '',
    wechatNo: '',
    alipayPhone: '',
    isPrimary: parents.value.length === 0,
    isEmergencyContact: false,
    status: 'active',
    remark: '',
  }
  relationIndex.value = 0
  statusIndex.value = 0
  parentFormError.value = ''
  popupRef.value?.open()
}

function openParentEdit(row: StudentParentRecord) {
  editingParentId.value = row.id
  parentForm.value = {
    parentName: row.parentName || '',
    relationType: row.relationType || '',
    parentPhone: row.parentPhone || '',
    wechatNo: row.wechatNo || '',
    alipayPhone: row.alipayPhone || '',
    isPrimary: Boolean(row.isPrimary),
    isEmergencyContact: Boolean(row.isEmergencyContact),
    status: row.status === 'disabled' || row.status === 'inactive' ? 'inactive' : 'active',
    remark: row.remark || '',
  }
  relationIndex.value = Math.max(
    0,
    relationOptions.value.findIndex((item) => item.value === parentForm.value.relationType),
  )
  statusIndex.value = parentStatusOptions.findIndex((item) => item.value === parentForm.value.status)
  if (statusIndex.value < 0) {
    statusIndex.value = 0
  }
  parentFormError.value = ''
  popupRef.value?.open()
}

function closeParentDialog() {
  popupRef.value?.close()
}

function onRelationChange(event: { detail: { value: string | number } }) {
  relationIndex.value = Number(event.detail.value)
  parentForm.value.relationType = relationOptions.value[relationIndex.value]?.value || ''
}

function onParentStatusChange(event: { detail: { value: string | number } }) {
  statusIndex.value = Number(event.detail.value)
  parentForm.value.status = parentStatusOptions[statusIndex.value]?.value || 'active'
}

function onPrimaryChange(event: { detail: { value: boolean } }) {
  parentForm.value.isPrimary = Boolean(event.detail.value)
}

function onEmergencyChange(event: { detail: { value: boolean } }) {
  parentForm.value.isEmergencyContact = Boolean(event.detail.value)
}

async function submitParent() {
  if (parentSubmitting.value || !student.value || !institutionId.value) {
    return
  }
  if (!parentForm.value.parentName.trim()) {
    parentFormError.value = '请填写联系人姓名'
    return
  }
  const payload = {
    institutionId: institutionId.value,
    studentId: student.value.id,
    parentName: parentForm.value.parentName.trim(),
    relationType: textOrUndefined(parentForm.value.relationType),
    parentPhone: parentForm.value.parentPhone.trim(),
    wechatNo: parentForm.value.wechatNo.trim(),
    alipayPhone: parentForm.value.alipayPhone.trim(),
    isPrimary: parentForm.value.isPrimary,
    isEmergencyContact: parentForm.value.isEmergencyContact,
    status: parentForm.value.status,
    remark: parentForm.value.remark.trim(),
  }
  parentSubmitting.value = true
  parentFormError.value = ''
  uni.showLoading({ title: '保存中…' })
  try {
    if (editingParentId.value) {
      await updateStudentParent(editingParentId.value, payload)
    } else {
      await createStudentParent(payload)
    }
    uni.hideLoading()
    closeParentDialog()
    uni.showToast({ title: '已保存', icon: 'success' })
    await loadParents()
  } catch (error) {
    uni.hideLoading()
    parentFormError.value = (error as Error)?.message || '保存失败'
  } finally {
    parentSubmitting.value = false
  }
}

function confirmDeleteParent(row: StudentParentRecord) {
  uni.showModal({
    title: '删除家长联系人',
    content: `确定删除「${row.parentName || '该联系人'}」吗？`,
    confirmColor: '#dc2626',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteStudentParent(row.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        await loadParents()
      } catch (error) {
        uni.showToast({
          title: (error as Error)?.message || '删除失败',
          icon: 'none',
          duration: 2800,
        })
      }
    },
  })
}

function openClass(row: ClassStudentRecord) {
  if (!row.classId) {
    return
  }
  uni.navigateTo({ url: `/pages/enterprise/class/detail?id=${row.classId}` })
}
</script>

<template>
  <view class="ent-page">
    <view v-if="!student" class="ent-card ent-empty">
      {{ handoffMissing ? '档案信息已过期，请返回学员列表重新进入' : '加载中…' }}
    </view>

    <template v-else>
      <view class="ent-card">
        <view class="ent-card-title">
          <text>{{ student.studentName || '学员档案' }}</text>
          <text class="ent-tag" :class="studentStatusTone(student.status)">
            {{ studentStatusLabel(student.status) }}
          </text>
        </view>
        <view class="ent-row">
          <text class="ent-label">学号</text>
          <text class="ent-value">{{ student.studentNo || '-' }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">性别</text>
          <text class="ent-value">{{ genderText(student.gender) }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">出生日期</text>
          <text class="ent-value">{{ student.birthday || '-' }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">入学日期</text>
          <text class="ent-value">{{ student.enrolledAt || '-' }}</text>
        </view>
        <view class="ent-row">
          <text class="ent-label">监护人</text>
          <text class="ent-value">
            {{ student.guardianName || '-' }}
            <text v-if="student.guardianPhone" class="ent-muted">（{{ student.guardianPhone }}）</text>
          </text>
        </view>
        <view v-if="student.remark" class="ent-row">
          <text class="ent-label">备注</text>
          <text class="ent-value">{{ student.remark }}</text>
        </view>
        <view v-if="canWriteProfile" class="ent-btn-row right">
          <view class="ent-btn small" @click="openEditStudent">编辑档案</view>
          <view class="ent-btn danger small" @click="confirmDeleteStudent">删除学员</view>
        </view>
      </view>

      <view class="ent-card">
        <view class="ent-card-title">
          <text>家长联系人</text>
          <text class="ent-link">{{ parents.length }} 人</text>
        </view>
        <view v-if="canWriteProfile" class="ent-btn-row">
          <view class="ent-btn primary small" @click="openParentCreate">新增联系人</view>
          <view class="ent-btn small" @click="loadParents">刷新</view>
        </view>

        <view v-if="parentsLoading" class="ent-empty">加载中…</view>
        <view v-else-if="parentsError" class="ent-empty">{{ parentsError }}</view>
        <view v-else-if="parents.length === 0" class="ent-empty">
          还没有家长联系人，新增后收费通知与紧急联络才有落点。
        </view>
        <template v-else>
          <view v-for="row in parents" :key="row.id" class="parent-row">
            <view class="ent-item-title">
              <text class="ent-item-name">{{ row.parentName || '未命名联系人' }}</text>
              <view class="parent-tags">
                <text v-if="row.isPrimary" class="ent-tag green">主联系人</text>
                <text v-if="row.isEmergencyContact" class="ent-tag orange">紧急联系</text>
                <text class="ent-tag">{{ parentStatusLabel(row.status) }}</text>
              </view>
            </view>
            <view class="ent-row">
              <text class="ent-label">关系</text>
              <text class="ent-value">{{ relationLabel(row.relationType) }}</text>
            </view>
            <view class="ent-row">
              <text class="ent-label">电话</text>
              <text class="ent-value">{{ row.parentPhone || '-' }}</text>
            </view>
            <view v-if="row.wechatNo" class="ent-row">
              <text class="ent-label">微信号</text>
              <text class="ent-value">{{ row.wechatNo }}</text>
            </view>
            <view v-if="row.alipayPhone" class="ent-row">
              <text class="ent-label">支付宝电话</text>
              <text class="ent-value">{{ row.alipayPhone }}</text>
            </view>
            <view v-if="row.remark" class="ent-row">
              <text class="ent-label">备注</text>
              <text class="ent-value">{{ row.remark }}</text>
            </view>
            <view v-if="canWriteProfile" class="ent-btn-row right">
              <view class="ent-btn small" @click="openParentEdit(row)">编辑</view>
              <view class="ent-btn danger small" @click="confirmDeleteParent(row)">删除</view>
            </view>
          </view>
        </template>
      </view>

      <view class="ent-card">
        <view class="ent-card-title">
          <text>所在班级</text>
          <text class="ent-link">{{ enrollments.length }} 条</text>
        </view>
        <view v-if="enrollmentsLoading" class="ent-empty">加载中…</view>
        <view v-else-if="enrollmentsError" class="ent-empty">{{ enrollmentsError }}</view>
        <view v-else-if="enrollments.length === 0" class="ent-empty">
          还没有入班记录，入班与退班在班级详情维护。
        </view>
        <template v-else>
          <view
            v-for="row in enrollments"
            :key="row.id"
            class="enroll-row"
            hover-class="ent-card-hover"
            @click="openClass(row)"
          >
            <view class="ent-item-title">
              <text class="ent-item-name">{{ row.className || row.classCode || '未命名班级' }}</text>
              <text class="ent-tag" :class="row.status === 'active' ? 'green' : ''">
                {{ classStudentStatusLabel(row.status) }}
              </text>
            </view>
            <view class="ent-row">
              <text class="ent-label">班级编码</text>
              <text class="ent-value">{{ row.classCode || '-' }}</text>
            </view>
            <view class="ent-row">
              <text class="ent-label">入班日期</text>
              <text class="ent-value">{{ row.joinedAt || '-' }}</text>
            </view>
          </view>
        </template>
        <view class="ent-tip">入班、退班请进入对应班级详情操作；这里只做留痕查看。</view>
      </view>
    </template>

    <uni-popup ref="popupRef" type="bottom" background-color="#ffffff">
      <view class="ent-sheet">
        <view class="ent-sheet-head">
          <text class="ent-sheet-title">{{ editingParentId ? '编辑家长联系人' : '新增家长联系人' }}</text>
          <text class="ent-sheet-close" @click="closeParentDialog">关闭</text>
        </view>
        <view class="ent-sheet-body">
          <view v-if="parentFormError" class="sheet-error">{{ parentFormError }}</view>

          <view class="ent-form-item">
            <text class="ent-form-label"><text class="ent-required">*</text> 联系人姓名</text>
            <input
              v-model="parentForm.parentName"
              class="ent-input"
              type="text"
              maxlength="64"
              placeholder="如 学员妈妈"
              placeholder-class="input-placeholder"
            />
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">关系</text>
            <picker
              mode="selector"
              :range="relationOptions"
              range-key="label"
              :value="relationIndex"
              @change="onRelationChange"
            >
              <view class="ent-picker">
                {{ relationOptions[relationIndex]?.label || '不指定' }}
              </view>
            </picker>
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">联系电话</text>
            <input
              v-model="parentForm.parentPhone"
              class="ent-input"
              type="number"
              maxlength="20"
              placeholder="家长手机号"
              placeholder-class="input-placeholder"
            />
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">微信号</text>
            <input
              v-model="parentForm.wechatNo"
              class="ent-input"
              type="text"
              maxlength="64"
              placeholder="选填"
              placeholder-class="input-placeholder"
            />
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">支付宝绑定电话</text>
            <input
              v-model="parentForm.alipayPhone"
              class="ent-input"
              type="number"
              maxlength="20"
              placeholder="用于核对该家长的收款，选填"
              placeholder-class="input-placeholder"
            />
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">联系人状态</text>
            <picker
              mode="selector"
              :range="parentStatusOptions"
              range-key="label"
              :value="statusIndex"
              @change="onParentStatusChange"
            >
              <view class="ent-picker">{{ parentStatusOptions[statusIndex]?.label || '启用' }}</view>
            </picker>
          </view>

          <view class="ent-switch-row">
            <text>主联系人（同一学员只保留一个）</text>
            <switch :checked="parentForm.isPrimary" color="#2563eb" @change="onPrimaryChange" />
          </view>
          <view class="ent-switch-row">
            <text>紧急联系人</text>
            <switch :checked="parentForm.isEmergencyContact" color="#2563eb" @change="onEmergencyChange" />
          </view>

          <view class="ent-form-item">
            <text class="ent-form-label">备注</text>
            <textarea
              v-model="parentForm.remark"
              class="ent-textarea"
              maxlength="500"
              placeholder="选填"
              placeholder-class="input-placeholder"
            />
          </view>
        </view>
        <view class="ent-sheet-footer">
          <view class="ent-btn primary" :class="{ disabled: parentSubmitting }" @click="submitParent">
            {{ parentSubmitting ? '保存中…' : '保存联系人' }}
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<style scoped>
.input-placeholder {
  color: #9ca3af;
}

.parent-row {
  padding: 18rpx 0;
  border-top: 1rpx solid #f3f4f6;
}

.parent-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8rpx;
}

.enroll-row {
  padding: 18rpx 0;
  border-top: 1rpx solid #f3f4f6;
}

.sheet-error {
  margin-bottom: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 10rpx;
  background: #fef2f2;
  font-size: 24rpx;
  line-height: 1.5;
  color: #dc2626;
}
</style>
