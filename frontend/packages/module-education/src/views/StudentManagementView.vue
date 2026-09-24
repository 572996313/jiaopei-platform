<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 整页级视图开关:按钮行放查询条件上方,统计条在其下方展开/收起 -->
    <div class="table-toolbar">
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
    </div>

    <div v-show="statsVisible" class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">学员数</div>
        <div class="stat-value">{{ studentTotal }}</div>
        <div class="stat-hint">{{ campusId ? '按当前校区筛选' : '全部校区' }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classes.length }}</div>
        <div class="stat-hint">{{ termId ? '按当前学期筛选' : '按校区范围筛选' }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">报名记录</div>
        <div class="stat-value">{{ enrollmentTotal }}</div>
        <div class="stat-hint">
          {{ classId ? '按当前班级筛选' : studentId ? '按当前学员筛选' : '全部报名记录' }}
        </div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">学员档案</div>
      </div>

      <!-- 本表主操作:表格上方一行、左对齐,排在查询条件上面 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="openStudentDialog('create')">新增学员</el-button>
        <el-button v-if="canImportAccounts" @click="importDialogVisible = true">
          批量导入账号
        </el-button>
      </div>

      <div class="table-filters">
        <el-input
          v-model="studentKeyword"
          placeholder="学号/姓名"
          clearable
          size="small"
          class="panel-search-input"
          @keyup.enter="searchStudents"
          @clear="searchStudents"
        />
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="pagedStudents"
          border
          stripe
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
        >
          <el-table-column prop="studentNo" label="学号" width="140" fixed="left" />
          <el-table-column
            prop="studentName"
            label="姓名"
            min-width="120"
            show-overflow-tooltip
            fixed="left"
          />
          <el-table-column label="性别" width="80">
            <template #default="{ row }">
              {{ genderLabel(row.gender) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="guardianName"
            label="监护人"
            min-width="120"
            show-overflow-tooltip
          />
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
              <el-button link type="primary" @click="openStudentDialog('edit', row)"
                >编辑</el-button
              >
              <el-button link type="danger" @click="confirmDeleteStudent(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!tableLoading && pagedStudents.length === 0" class="education-empty">
        当前筛选范围下暂无学员，点击"新增学员"录入。
      </div>

      <el-pagination
        v-model:current-page="studentPage"
        v-model:page-size="studentPageSize"
        class="panel-pagination"
        :total="studentTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadStudents"
        @size-change="handleStudentSizeChange"
      />
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">报名记录</div>
      </div>

      <div class="table-toolbar">
        <el-button type="primary" @click="openDialog">新增报名</el-button>
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="records"
          border
          stripe
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
        >
          <el-table-column prop="enrollmentNo" label="报名编号" width="160" fixed="left" />
          <el-table-column label="学员" min-width="180">
            <template #default="{ row }">
              {{ row.studentName || row.studentNo || formatStudent(row.studentId) }}
            </template>
          </el-table-column>
          <el-table-column label="班级" min-width="180">
            <template #default="{ row }">
              {{ row.className || formatClass(row.classId) }}
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
      </TableFillBox>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无报名记录。
      </div>

      <el-pagination
        v-model:current-page="enrollmentPage"
        v-model:page-size="enrollmentPageSize"
        class="panel-pagination"
        :total="enrollmentTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadEnrollments"
        @size-change="handleEnrollmentSizeChange"
      />
    </article>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">家长联系人</div>
      </div>

      <div class="table-toolbar">
        <el-button type="primary" @click="openParentDialog">新增家长联系人</el-button>
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="studentParents"
          border
          stripe
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
        >
          <el-table-column label="学员" min-width="180" fixed="left">
            <template #default="{ row }">
              {{ row.studentName || row.studentNo || formatStudent(row.studentId) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="parentName"
            label="联系人"
            min-width="140"
            show-overflow-tooltip
            fixed="left"
          />
          <el-table-column label="关系" width="120">
            <template #default="{ row }">{{ parentRelationLabel(row.relationType) }}</template>
          </el-table-column>
          <el-table-column prop="parentPhone" label="联系电话" width="160" />
          <el-table-column prop="wechatNo" label="微信号" width="160" />
          <el-table-column label="主联系人" width="100">
            <template #default="{ row }">
              {{ row.isPrimary ? '是' : '否' }}
            </template>
          </el-table-column>
          <el-table-column label="紧急联系" width="100">
            <template #default="{ row }">
              {{ row.isEmergencyContact ? '是' : '否' }}
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
      </TableFillBox>

      <div v-if="!tableLoading && studentParents.length === 0" class="education-empty">
        当前筛选范围下暂无家长联系人。
      </div>

      <el-pagination
        v-model:current-page="parentPage"
        v-model:page-size="parentPageSize"
        class="panel-pagination"
        :total="parentTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadParents"
        @size-change="handleParentSizeChange"
      />
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
          <DictSelect v-model="studentForm.gender" type-code="gender" placeholder="选择性别" />
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
          <DictSelect
            v-model="form.channel"
            type-code="enrollment_channel"
            placeholder="选择渠道"
          />
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
          <DictSelect
            v-model="parentForm.relationType"
            type-code="parent_relation"
            placeholder="选择关系"
          />
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

    <!-- 账号批量导入向导:一行=一个账号绑一个学员,导入后在同一处批量出激活码 -->
    <StudentAccountImportDialog
      v-model="importDialogVisible"
      :institution-id="institutionId"
      :campus-id="campusId"
      @imported="refreshAll"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createEnrollment,
  createStudent,
  createStudentParent,
  deleteStudent,
  fetchEnrollmentPage,
  fetchStudentPage,
  fetchStudentParentPage,
  updateStudent,
  type EnrollmentViewRecord,
  type StudentParentViewRecord,
  type StudentSummary,
} from '@cornerstone/module-education/api/education'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import StudentAccountImportDialog from '@cornerstone/module-education/views/components/StudentAccountImportDialog.vue'

const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const auth = useAuthStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { institutionId, campuses, terms, classes, students, loadScope } = useEducationScope()

const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)

const statsVisible = ref(false)

const pagedStudents = ref<StudentSummary[]>([])
const records = ref<EnrollmentViewRecord[]>([])
const studentParents = ref<StudentParentViewRecord[]>([])

// 分页状态:学员档案/报名记录/家长联系人各自独立翻页
const studentPage = ref(1)
const studentPageSize = ref(10)
const studentTotal = ref(0)
const studentKeyword = ref('')
const enrollmentPage = ref(1)
const enrollmentPageSize = ref(10)
const enrollmentTotal = ref(0)
const parentPage = ref(1)
const parentPageSize = ref(10)
const parentTotal = ref(0)
const pageError = ref('')
const formError = ref('')
const parentFormError = ref('')
const dialogVisible = ref(false)
const parentDialogVisible = ref(false)
const importDialogVisible = ref(false)
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
  channel: '',
  enrolledAt: todayValue(),
  remark: '',
})

const parentForm = reactive({
  studentId: '' as string | null,
  parentName: '',
  relationType: '',
  parentPhone: '',
  wechatNo: '',
  isPrimary: true,
  isEmergencyContact: false,
  status: 'active',
  remark: '',
})

/** 导入会建账号并绑学员档案，与后端接口的 education:write 同一口径 */
const canImportAccounts = computed(() => auth.hasPermission('education:write'))

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
  return dictStore.displayLabel('gender', gender)
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
  return dictStore.displayLabel('enrollment_channel', value)
}

function parentRelationLabel(value?: string | null) {
  return dictStore.displayLabel('parent_relation', value)
}

function resetForm() {
  form.classId = classId.value
  form.studentId = studentId.value
  form.enrollmentNo = ''
  form.status = 'submitted'
  form.channel = ''
  form.enrolledAt = todayValue()
  form.remark = ''
  formError.value = ''
}

function resetParentForm() {
  parentForm.studentId = studentId.value
  parentForm.parentName = ''
  parentForm.relationType = ''
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
    await Promise.all([loadStudents(), loadRecords()])
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
    await Promise.all([loadStudents(), loadRecords()])
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

let tableLoadCount = 0

async function withTableLoading(load: () => Promise<void>) {
  tableLoadCount += 1
  tableLoading.value = true
  try {
    await load()
  } finally {
    tableLoadCount -= 1
    if (tableLoadCount === 0) {
      tableLoading.value = false
    }
  }
}

async function loadStudents() {
  if (!institutionId.value) {
    pagedStudents.value = []
    studentTotal.value = 0
    return
  }
  await withTableLoading(async () => {
    pageError.value = ''
    try {
      const result = await fetchStudentPage({
        institutionId: institutionId.value,
        campusId: campusId.value,
        keyword: trimToUndefined(studentKeyword.value),
        page: studentPage.value,
        pageSize: studentPageSize.value,
      })
      pagedStudents.value = result.records
      studentTotal.value = result.total
    } catch (error) {
      pagedStudents.value = []
      studentTotal.value = 0
      pageError.value = toMessage(error, '学员数据加载失败')
    }
  })
}

async function loadEnrollments() {
  if (!institutionId.value) {
    records.value = []
    enrollmentTotal.value = 0
    return
  }
  await withTableLoading(async () => {
    pageError.value = ''
    try {
      const result = await fetchEnrollmentPage({
        institutionId: institutionId.value,
        classId: classId.value,
        studentId: studentId.value,
        page: enrollmentPage.value,
        pageSize: enrollmentPageSize.value,
      })
      records.value = result.records
      enrollmentTotal.value = result.total
    } catch (error) {
      records.value = []
      enrollmentTotal.value = 0
      pageError.value = toMessage(error, '报名数据加载失败')
    }
  })
}

async function loadParents() {
  if (!institutionId.value) {
    studentParents.value = []
    parentTotal.value = 0
    return
  }
  await withTableLoading(async () => {
    pageError.value = ''
    try {
      const result = await fetchStudentParentPage({
        institutionId: institutionId.value,
        studentId: studentId.value,
        page: parentPage.value,
        pageSize: parentPageSize.value,
      })
      studentParents.value = result.records
      parentTotal.value = result.total
    } catch (error) {
      studentParents.value = []
      parentTotal.value = 0
      pageError.value = toMessage(error, '家长联系人数据加载失败')
    }
  })
}

// 筛选变化时报名/家长联动重载,并回到第一页
async function loadRecords() {
  enrollmentPage.value = 1
  parentPage.value = 1
  await Promise.all([loadEnrollments(), loadParents()])
}

function searchStudents() {
  studentPage.value = 1
  return loadStudents()
}

function handleStudentSizeChange() {
  studentPage.value = 1
  return loadStudents()
}

function handleEnrollmentSizeChange() {
  enrollmentPage.value = 1
  return loadEnrollments()
}

function handleParentSizeChange() {
  parentPage.value = 1
  return loadParents()
}

async function handleCampusChange() {
  classId.value = null
  studentId.value = null
  studentPage.value = 1
  await refreshScopeData()
  await Promise.all([loadStudents(), loadRecords()])
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
    await loadEnrollments()
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
    await loadParents()
  } catch (error) {
    parentFormError.value = toMessage(error, '新增家长联系人失败')
  } finally {
    parentSubmitting.value = false
  }
}

async function refreshAll() {
  await refreshScopeData()
  await Promise.all([loadStudents(), loadRecords()])
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await loadScope(campusId.value, termId.value)
    }
    await Promise.all([loadStudents(), loadRecords()])
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
    studentKeyword.value = ''
    studentPage.value = 1
    enrollmentPage.value = 1
    parentPage.value = 1
    await refreshScopeData()
    await Promise.all([loadStudents(), loadRecords()])
  }
)

onMounted(() => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore
    .ensureLoaded(['gender', 'enrollment_channel', 'parent_relation'])
    .catch(() => undefined)
  initializePage()
})
</script>
