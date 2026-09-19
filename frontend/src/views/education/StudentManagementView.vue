<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="page-toolbar">
      <div class="page-toolbar-filters" v-loading="lookupLoading">
        <el-select
          v-model="campusId"
          placeholder="筛选校区"
          clearable
          filterable
          @change="handleCampusChange"
        >
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="termId"
          placeholder="筛选学期"
          clearable
          filterable
          @change="handleTermChange"
        >
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="classId"
          placeholder="筛选班级"
          clearable
          filterable
          @change="loadRecords"
        >
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="studentId"
          placeholder="筛选学员"
          clearable
          filterable
          @change="loadRecords"
        >
          <el-option
            v-for="item in students"
            :key="item.id"
            :label="item.studentName || item.studentNo || `学员 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="page-toolbar-actions">
        <el-button @click="statsVisible = !statsVisible">
          统计
          <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"
            ><ArrowDown
          /></el-icon>
        </el-button>
        <el-button
          :icon="Refresh"
          circle
          :loading="lookupLoading || tableLoading"
          @click="refreshAll"
        />
      </div>
    </div>

    <div v-show="statsVisible" class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">学员数</div>
        <div class="stat-value">{{ students.length }}</div>
        <div class="stat-hint">{{ campusId ? '按当前校区筛选' : '全部校区' }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classes.length }}</div>
        <div class="stat-hint">{{ termId ? '按当前学期筛选' : '按校区范围筛选' }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">报名记录</div>
        <div class="stat-value">{{ records.length }}</div>
        <div class="stat-hint">
          {{ classId ? '按当前班级筛选' : studentId ? '按当前学员筛选' : '全部报名记录' }}
        </div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">
          学员档案<span class="panel-count">共 {{ students.length }} 条</span>
        </div>
        <el-button type="primary" @click="openStudentDialog('create')">新增学员</el-button>
      </div>

      <el-table :data="students" border v-loading="tableLoading" :height="TABLE_FILL_HEIGHT">
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="studentName" label="姓名" min-width="120" show-overflow-tooltip />
        <el-table-column label="性别" width="80">
          <template #default="{ row }">
            {{ genderLabel(row.gender) }}
          </template>
        </el-table-column>
        <el-table-column prop="guardianName" label="监护人" min-width="120" show-overflow-tooltip />
        <el-table-column prop="guardianPhone" label="监护人电话" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="studentStatusTagType(row.status)">
              {{ studentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledAt" label="入学日期" width="130" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openStudentDialog('edit', row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDeleteStudent(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!tableLoading && students.length === 0" class="education-empty">
        当前筛选范围下暂无学员，点击"新增学员"录入。
      </div>
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">
          报名记录<span class="panel-count">共 {{ records.length }} 条</span>
        </div>
        <el-button type="primary" @click="openDialog">新增报名</el-button>
      </div>

      <el-table :data="records" border v-loading="tableLoading" :height="TABLE_FILL_HEIGHT">
        <el-table-column prop="enrollmentNo" label="报名编号" width="160" />
        <el-table-column label="学员" min-width="180">
          <template #default="{ row }">
            {{ formatStudent(row.studentId) }}
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="180">
          <template #default="{ row }">
            {{ formatClass(row.classId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="enrollmentTagType(row.status)">
              {{ enrollmentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源渠道" width="140">
          <template #default="{ row }">{{ enrollmentChannelLabel(row.channel) }}</template>
        </el-table-column>
        <el-table-column prop="enrolledAt" label="报名日期" width="140" />
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
      </el-table>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无报名记录。
      </div>
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">
          家长联系人<span class="panel-count">共 {{ studentParents.length }} 条</span>
        </div>
        <el-button type="primary" @click="openParentDialog">新增家长联系人</el-button>
      </div>

      <el-table :data="studentParents" border v-loading="tableLoading" :height="TABLE_FILL_HEIGHT">
        <el-table-column label="学员" min-width="180">
          <template #default="{ row }">
            {{ formatStudent(row.studentId) }}
          </template>
        </el-table-column>
        <el-table-column prop="parentName" label="联系人" min-width="140" show-overflow-tooltip />
        <el-table-column label="关系" width="120">
          <template #default="{ row }">{{ parentRelationLabel(row.relationType) }}</template>
        </el-table-column>
        <el-table-column prop="parentPhone" label="联系电话" width="160" />
        <el-table-column prop="wechatNo" label="微信号" width="160" />
        <el-table-column label="主联系人" width="100">
          <template #default="{ row }">
            {{ row.primaryContact ? '是' : '否' }}
          </template>
        </el-table-column>
        <el-table-column label="紧急联系" width="100">
          <template #default="{ row }">
            {{ row.emergencyContact ? '是' : '否' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="studentParentTagType(row.status)">
              {{ studentParentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
      </el-table>

      <div v-if="!tableLoading && studentParents.length === 0" class="education-empty">
        当前筛选范围下暂无家长联系人。
      </div>
    </article>

    <el-dialog
      v-model="studentDialogVisible"
      :title="studentDialogMode === 'create' ? '新增学员' : '编辑学员'"
      width="560px"
    >
      <el-alert
        v-if="studentFormError"
        :title="studentFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="学号" required>
          <el-input v-model="studentForm.studentNo" placeholder="例如 STU-20260902-001" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="studentForm.studentName" placeholder="学员真实姓名" />
        </el-form-item>
        <el-form-item label="校区">
          <el-select v-model="studentForm.campusId" placeholder="选择校区" clearable filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="studentForm.gender" placeholder="选择性别" clearable>
            <el-option label="男" value="male" />
            <el-option label="女" value="female" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker
            v-model="studentForm.birthday"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择出生日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="监护人姓名">
          <el-input v-model="studentForm.guardianName" placeholder="家长姓名" />
        </el-form-item>
        <el-form-item label="监护人电话">
          <el-input v-model="studentForm.guardianPhone" placeholder="家长手机号" />
        </el-form-item>
        <el-form-item label="学员状态">
          <el-select v-model="studentForm.status">
            <el-option label="在读" value="studying" />
            <el-option label="停课" value="suspended" />
            <el-option label="毕业" value="graduated" />
            <el-option label="退学" value="withdrawn" />
          </el-select>
        </el-form-item>
        <el-form-item label="入学日期">
          <el-date-picker
            v-model="studentForm.enrolledAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择入学日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="studentForm.remark"
            type="textarea"
            :rows="3"
            placeholder="补充学员说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="studentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="studentSubmitting" @click="submitStudent">
          {{ studentDialogMode === 'create' ? '新增学员' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" title="新增报名" width="540px">
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="报名班级" required>
          <el-select v-model="form.classId" placeholder="选择报名班级" filterable>
            <el-option
              v-for="item in classes"
              :key="item.id"
              :label="item.className || item.classCode || `班级 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报名学员" required>
          <el-select v-model="form.studentId" placeholder="选择报名学员" filterable>
            <el-option
              v-for="item in students"
              :key="item.id"
              :label="item.studentName || item.studentNo || `学员 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报名编号">
          <el-input v-model="form.enrollmentNo" placeholder="例如 ENR-20260608-001" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="选择状态">
            <el-option label="已提交" value="submitted" />
            <el-option label="已确认" value="confirmed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源渠道">
          <el-select v-model="form.channel" placeholder="选择渠道" clearable>
            <el-option label="前台报名" value="front_desk" />
            <el-option label="顾问录入" value="consultant" />
            <el-option label="线上线索" value="online" />
            <el-option label="转介绍" value="referral" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名日期">
          <el-date-picker
            v-model="form.enrolledAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择报名日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充报名说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEnrollment"
          >提交报名</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="parentDialogVisible" title="新增家长联系人" width="560px">
      <el-alert
        v-if="parentFormError"
        :title="parentFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="所属学员" required>
          <el-select v-model="parentForm.studentId" placeholder="选择学员" filterable>
            <el-option
              v-for="item in students"
              :key="item.id"
              :label="item.studentName || item.studentNo || `学员 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人姓名">
          <el-input v-model="parentForm.parentName" placeholder="例如 学员妈妈" />
        </el-form-item>
        <el-form-item label="关系">
          <el-select v-model="parentForm.relationType" clearable placeholder="选择关系">
            <el-option label="母亲" value="mother" />
            <el-option label="父亲" value="father" />
            <el-option label="监护人" value="guardian" />
            <el-option label="其他亲属" value="relative" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="parentForm.parentPhone" placeholder="填写家长手机号" />
        </el-form-item>
        <el-form-item label="微信号">
          <el-input v-model="parentForm.wechatNo" placeholder="可选" />
        </el-form-item>
        <el-form-item label="联系人状态">
          <el-select v-model="parentForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="主联系人">
          <el-switch v-model="parentForm.isPrimary" />
        </el-form-item>
        <el-form-item label="紧急联系人">
          <el-switch v-model="parentForm.isEmergencyContact" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="parentForm.remark"
            type="textarea"
            :rows="3"
            placeholder="补充联系人说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="parentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="parentSubmitting" @click="submitStudentParent"
          >提交联系人</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_FILL_HEIGHT } from '@/utils/table-view'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createEnrollment,
  createStudent,
  createStudentParent,
  deleteStudent,
  fetchEnrollments,
  fetchStudentParents,
  updateStudent,
  type EnrollmentRecord,
  type StudentParentRecord,
  type StudentSummary,
} from '@/api/education'
import { useEducationScope } from '@/composables/useEducationScope'
import { useEducationScopeStore } from '@/stores/educationScope'
import { useInstitutionStore } from '@/stores/institution'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { institutionId, campuses, terms, classes, students, loadScope } = useEducationScope()

const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)

const statsVisible = ref(false)

const records = ref<EnrollmentRecord[]>([])
const studentParents = ref<StudentParentRecord[]>([])
const pageError = ref('')
const formError = ref('')
const parentFormError = ref('')
const dialogVisible = ref(false)
const parentDialogVisible = ref(false)
const lookupLoading = ref(false)
const tableLoading = ref(false)
const submitting = ref(false)
const parentSubmitting = ref(false)

const studentDialogVisible = ref(false)
const studentDialogMode = ref<'create' | 'edit'>('create')
const editingStudentId = ref<string | null>(null)
const studentSubmitting = ref(false)
const studentFormError = ref('')

const studentForm = reactive({
  campusId: '' as string | null,
  studentNo: '',
  studentName: '',
  gender: '',
  birthday: '',
  guardianName: '',
  guardianPhone: '',
  status: 'studying',
  enrolledAt: '',
  remark: '',
})

const form = reactive({
  classId: '' as string | null,
  studentId: '' as string | null,
  enrollmentNo: '',
  status: 'submitted',
  channel: 'front_desk',
  enrolledAt: todayValue(),
  remark: '',
})

const parentForm = reactive({
  studentId: '' as string | null,
  parentName: '',
  relationType: 'mother',
  parentPhone: '',
  wechatNo: '',
  isPrimary: true,
  isEmergencyContact: false,
  status: 'active',
  remark: '',
})

const classMap = computed(() => {
  return new Map(
    classes.value.map((item) => [item.id, item.className || item.classCode || `班级 #${item.id}`])
  )
})

const studentMap = computed(() => {
  return new Map(
    students.value.map((item) => [
      item.id,
      item.studentName || item.studentNo || `学员 #${item.id}`,
    ])
  )
})

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function formatClass(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classMap.value.get(id) || `班级 #${id}`
}

function formatStudent(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return studentMap.value.get(id) || `学员 #${id}`
}

function genderLabel(gender?: string | null) {
  switch (gender) {
    case 'male':
      return '男'
    case 'female':
      return '女'
    default:
      return '—'
  }
}

function studentStatusLabel(status?: string | null) {
  switch (status) {
    case 'suspended':
      return '停课'
    case 'graduated':
      return '毕业'
    case 'withdrawn':
      return '退学'
    case 'studying':
    default:
      return '在读'
  }
}

function studentStatusTagType(status?: string | null) {
  switch (status) {
    case 'withdrawn':
      return 'info'
    case 'suspended':
      return 'warning'
    case 'graduated':
      return 'success'
    case 'studying':
    default:
      // 在读为常态,用中性点+文字展示
      return 'info'
  }
}

function enrollmentStatusLabel(status?: string | null) {
  switch (status) {
    case 'confirmed':
      return '已确认'
    case 'cancelled':
      return '已取消'
    case 'submitted':
    default:
      return '已提交'
  }
}

function enrollmentTagType(status?: string | null) {
  switch (status) {
    case 'confirmed':
      return 'success'
    case 'cancelled':
      return 'info'
    case 'submitted':
    default:
      return 'warning'
  }
}

function studentParentStatusLabel(status?: string | null) {
  switch (status) {
    case 'inactive':
      return '停用'
    case 'active':
    default:
      return '启用'
  }
}

function studentParentTagType(status?: string | null) {
  switch (status) {
    case 'inactive':
      return 'info'
    case 'active':
    default:
      return 'success'
  }
}

function enrollmentChannelLabel(value?: string | null) {
  switch (value) {
    case 'front_desk':
      return '前台报名'
    case 'consultant':
      return '顾问录入'
    case 'online':
      return '线上线索'
    case 'referral':
      return '转介绍'
    default:
      return value || '-'
  }
}

function parentRelationLabel(value?: string | null) {
  switch (value) {
    case 'father':
      return '父亲'
    case 'guardian':
      return '监护人'
    case 'relative':
      return '其他亲属'
    case 'mother':
      return '母亲'
    default:
      return value || '-'
  }
}

function resetForm() {
  form.classId = classId.value
  form.studentId = studentId.value
  form.enrollmentNo = ''
  form.status = 'submitted'
  form.channel = 'front_desk'
  form.enrolledAt = todayValue()
  form.remark = ''
  formError.value = ''
}

function resetParentForm() {
  parentForm.studentId = studentId.value
  parentForm.parentName = ''
  parentForm.relationType = 'mother'
  parentForm.parentPhone = ''
  parentForm.wechatNo = ''
  parentForm.isPrimary = true
  parentForm.isEmergencyContact = false
  parentForm.status = 'active'
  parentForm.remark = ''
  parentFormError.value = ''
}

function resetStudentForm() {
  studentForm.campusId = campusId.value
  studentForm.studentNo = ''
  studentForm.studentName = ''
  studentForm.gender = ''
  studentForm.birthday = ''
  studentForm.guardianName = ''
  studentForm.guardianPhone = ''
  studentForm.status = 'studying'
  studentForm.enrolledAt = todayValue()
  studentForm.remark = ''
  studentFormError.value = ''
}

function openStudentDialog(mode: 'create' | 'edit', row?: StudentSummary) {
  studentDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingStudentId.value = row.id
    studentForm.campusId = row.campusId ?? null
    studentForm.studentNo = row.studentNo ?? ''
    studentForm.studentName = row.studentName ?? ''
    studentForm.gender = row.gender ?? ''
    studentForm.birthday = row.birthday ?? ''
    studentForm.guardianName = row.guardianName ?? ''
    studentForm.guardianPhone = row.guardianPhone ?? ''
    studentForm.status = row.status ?? 'studying'
    studentForm.enrolledAt = row.enrolledAt ?? ''
    studentForm.remark = row.remark ?? ''
  } else {
    editingStudentId.value = null
    resetStudentForm()
  }
  studentFormError.value = ''
  studentDialogVisible.value = true
}

async function submitStudent() {
  if (!institutionId.value) {
    studentFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(studentForm.studentNo)) {
    studentFormError.value = '请填写学号'
    return
  }
  if (!trimToUndefined(studentForm.studentName)) {
    studentFormError.value = '请填写学员姓名'
    return
  }

  studentSubmitting.value = true
  studentFormError.value = ''
  try {
    if (studentDialogMode.value === 'create') {
      await createStudent({
        institutionId: institutionId.value,
        campusId: studentForm.campusId || null,
        studentNo: studentForm.studentNo.trim(),
        studentName: studentForm.studentName.trim(),
        gender: trimToUndefined(studentForm.gender),
        birthday: studentForm.birthday || null,
        guardianName: trimToUndefined(studentForm.guardianName),
        guardianPhone: trimToUndefined(studentForm.guardianPhone),
        status: trimToUndefined(studentForm.status) || 'studying',
        enrolledAt: studentForm.enrolledAt || null,
        remark: trimToUndefined(studentForm.remark),
      })
      ElMessage.success('学员已新增')
    } else if (editingStudentId.value) {
      await updateStudent(editingStudentId.value, {
        campusId: studentForm.campusId || null,
        studentNo: studentForm.studentNo.trim(),
        studentName: studentForm.studentName.trim(),
        gender: trimToUndefined(studentForm.gender),
        birthday: studentForm.birthday || null,
        guardianName: trimToUndefined(studentForm.guardianName),
        guardianPhone: trimToUndefined(studentForm.guardianPhone),
        status: trimToUndefined(studentForm.status) || 'studying',
        enrolledAt: studentForm.enrolledAt || null,
        remark: trimToUndefined(studentForm.remark),
      })
      ElMessage.success('学员已更新')
    }
    studentDialogVisible.value = false
    resetStudentForm()
    await refreshScopeData()
    await loadRecords()
  } catch (error) {
    studentFormError.value = toMessage(
      error,
      studentDialogMode.value === 'create' ? '新增学员失败' : '更新学员失败'
    )
  } finally {
    studentSubmitting.value = false
  }
}

async function confirmDeleteStudent(row: StudentSummary) {
  try {
    await ElMessageBox.confirm(
      `确认删除学员「${row.studentName || row.studentNo}」吗？`,
      '删除学员',
      {
        type: 'warning',
      }
    )
  } catch {
    return
  }
  try {
    await deleteStudent(row.id)
    ElMessage.success('学员已删除')
    await refreshScopeData()
    await loadRecords()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除学员失败'))
  }
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    terms.value = []
    classes.value = []
    students.value = []
    return
  }

  lookupLoading.value = true
  try {
    await loadScope(campusId.value, termId.value)
    if (classId.value && !classes.value.some((item) => item.id === classId.value)) {
      classId.value = null
    }
    if (studentId.value && !students.value.some((item) => item.id === studentId.value)) {
      studentId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function loadRecords() {
  if (!institutionId.value) {
    records.value = []
    return
  }

  tableLoading.value = true
  pageError.value = ''
  try {
    const [enrollments, parents] = await Promise.all([
      fetchEnrollments({
        institutionId: institutionId.value,
        classId: classId.value,
        studentId: studentId.value,
      }),
      fetchStudentParents({
        institutionId: institutionId.value,
        studentId: studentId.value,
      }),
    ])
    records.value = enrollments
    studentParents.value = parents
  } catch (error) {
    records.value = []
    studentParents.value = []
    pageError.value = toMessage(error, '报名与联系人数据加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function handleCampusChange() {
  classId.value = null
  studentId.value = null
  await refreshScopeData()
  await loadRecords()
}

async function handleTermChange() {
  classId.value = null
  await refreshScopeData()
  await loadRecords()
}

function openDialog() {
  resetForm()
  dialogVisible.value = true
}

function openParentDialog() {
  resetParentForm()
  parentDialogVisible.value = true
}

async function submitEnrollment() {
  if (!institutionId.value) {
    formError.value = '请先选择机构范围'
    return
  }
  if (!form.classId) {
    formError.value = '请选择报名班级'
    return
  }
  if (!form.studentId) {
    formError.value = '请选择报名学员'
    return
  }

  if (!trimToUndefined(form.enrollmentNo)) {
    formError.value = '请填写报名编号'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await createEnrollment({
      institutionId: institutionId.value,
      classId: form.classId,
      studentId: form.studentId,
      enrollmentNo: form.enrollmentNo.trim(),
      status: form.status,
      channel: trimToUndefined(form.channel),
      enrolledAt: form.enrolledAt,
      remark: trimToUndefined(form.remark),
    })
    ElMessage.success('报名记录已新增')
    dialogVisible.value = false
    resetForm()
    await loadRecords()
  } catch (error) {
    formError.value = toMessage(error, '新增报名失败')
  } finally {
    submitting.value = false
  }
}

async function submitStudentParent() {
  if (!institutionId.value) {
    parentFormError.value = '请先选择机构范围'
    return
  }
  if (!parentForm.studentId) {
    parentFormError.value = '请选择学员'
    return
  }
  if (!trimToUndefined(parentForm.parentName)) {
    parentFormError.value = '请填写联系人姓名'
    return
  }

  parentSubmitting.value = true
  parentFormError.value = ''
  try {
    await createStudentParent({
      institutionId: institutionId.value,
      studentId: parentForm.studentId,
      parentName: parentForm.parentName.trim(),
      relationType: trimToUndefined(parentForm.relationType),
      parentPhone: trimToUndefined(parentForm.parentPhone),
      wechatNo: trimToUndefined(parentForm.wechatNo),
      isPrimary: parentForm.isPrimary,
      isEmergencyContact: parentForm.isEmergencyContact,
      status: trimToUndefined(parentForm.status),
      remark: trimToUndefined(parentForm.remark),
    })
    ElMessage.success('家长联系人已新增')
    parentDialogVisible.value = false
    resetParentForm()
    await loadRecords()
  } catch (error) {
    parentFormError.value = toMessage(error, '新增家长联系人失败')
  } finally {
    parentSubmitting.value = false
  }
}

async function refreshAll() {
  await refreshScopeData()
  await loadRecords()
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await loadScope(campusId.value, termId.value)
    }
    await loadRecords()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    campusId.value = null
    termId.value = null
    classId.value = null
    studentId.value = null
    await refreshScopeData()
    await loadRecords()
  }
)

onMounted(() => {
  initializePage()
})
</script>
