<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 这一页只管学员本身：报名去「学员报名」，进班去「班级名册」 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openStudentDialog('create')">新增学员</el-button>
      <el-button :icon="Refresh" circle :loading="tableLoading" @click="refreshAll" />
      <el-button @click="goEnrollments">学员报名</el-button>
      <span class="education-context-text">
        删除学员会连带影响报名与入班记录，请先确认该学员已无在读班级。
      </span>
    </div>

    <!-- 统一筛选栏:点「查询」才按关键字重查,不再选中即触发 -->
    <FilterBar :loading="tableLoading" @query="searchStudents" @reset="resetStudentFilters">
      <el-input
        v-model="keyword"
        placeholder="学号/姓名"
        clearable
        class="panel-search-input"
        @keyup.enter="searchStudents"
      />
    </FilterBar>

    <article class="panel-card education-panel student-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          ref="studentTableRef"
          :data="pagedStudents"
          border
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
          :cell-class-name="studentSort.cellClass"
          @sort-change="studentSort.onSortChange"
        >
          <el-table-column
            prop="studentNo"
            label="学号"
            width="140"
            fixed="left"
            sortable="custom"
          />
          <el-table-column
            prop="studentName"
            label="姓名"
            min-width="120"
            show-overflow-tooltip
            fixed="left"
            sortable="custom"
          />
          <el-table-column prop="gender" label="性别" width="80" sortable="custom">
            <template #default="{ row }">
              {{ genderLabel(row.gender) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="guardianName"
            label="监护人"
            min-width="120"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column prop="guardianPhone" label="监护人电话" width="150" sortable="custom" />
          <el-table-column prop="status" label="状态" width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="studentStatusTagType(row.status)">
                {{ studentStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="enrolledAt" label="入学日期" width="130" sortable="custom" />
          <el-table-column
            prop="remark"
            label="备注"
            min-width="180"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openParentDrawer(row)">联系人</el-button>
              <el-button link type="primary" @click="goStudentEnrollments(row)">报名</el-button>
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
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="panel-pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadStudents"
        @size-change="handleSizeChange"
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
          <DictSelect
            v-model="studentForm.status"
            type-code="student_status"
            placeholder="选择学员状态"
          />
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

    <!-- 家长联系人是"打电话才看"的明细，收进右侧抽屉，不占档案页的纵向预算 -->
    <StudentParentDrawer
      v-model="parentDrawerVisible"
      :institution-id="institutionId"
      :student-id="contactStudentId"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { rememberStudentOptions } from '@cornerstone/module-education/composables/studentLabels'
import StudentParentDrawer from './components/StudentParentDrawer.vue'
import {
  createStudent,
  deleteStudent,
  fetchStudentPage,
  updateStudent,
  type StudentSummary,
} from '@cornerstone/module-education/api/education'

const router = useRouter()
const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)

const pagedStudents = ref<StudentSummary[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')
const tableLoading = ref(false)
const pageError = ref('')
const studentTableRef = ref<{ clearSort: () => void } | null>(null)
/** 表头排序走服务端：点表头回第一页重查，取消排序回到接口默认排序 */
const studentSort = useTableSort({
  onSorted: () => {
    void searchStudents()
  },
  clearTableSort: () => studentTableRef.value?.clearSort(),
})

const studentDialogVisible = ref(false)
const studentDialogMode = ref<'create' | 'edit'>('create')
const editingStudentId = ref<string | null>(null)
const studentSubmitting = ref(false)
const studentFormError = ref('')

const parentDrawerVisible = ref(false)
const contactStudentId = ref<string | null>(null)

const studentForm = reactive({
  studentNo: '',
  studentName: '',
  gender: '',
  birthday: '',
  guardianName: '',
  guardianPhone: '',
  status: '',
  enrolledAt: '',
  remark: '',
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

function genderLabel(gender?: string | null) {
  return dictStore.displayLabel('gender', gender)
}

function studentStatusLabel(status?: string | null) {
  return dictStore.displayLabel('student_status', status)
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

function resetStudentForm() {
  studentForm.studentNo = ''
  studentForm.studentName = ''
  studentForm.gender = ''
  studentForm.birthday = ''
  studentForm.guardianName = ''
  studentForm.guardianPhone = ''
  studentForm.status = ''
  studentForm.enrolledAt = todayValue()
  studentForm.remark = ''
  studentFormError.value = ''
}

function openStudentDialog(mode: 'create' | 'edit', row?: StudentSummary) {
  studentDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingStudentId.value = row.id
    studentForm.studentNo = row.studentNo ?? ''
    studentForm.studentName = row.studentName ?? ''
    studentForm.gender = row.gender ?? ''
    studentForm.birthday = row.birthday ?? ''
    studentForm.guardianName = row.guardianName ?? ''
    studentForm.guardianPhone = row.guardianPhone ?? ''
    studentForm.status = row.status ?? ''
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
        studentNo: studentForm.studentNo.trim(),
        studentName: studentForm.studentName.trim(),
        gender: trimToUndefined(studentForm.gender),
        birthday: studentForm.birthday || null,
        guardianName: trimToUndefined(studentForm.guardianName),
        guardianPhone: trimToUndefined(studentForm.guardianPhone),
        status: trimToUndefined(studentForm.status),
        enrolledAt: studentForm.enrolledAt || null,
        remark: trimToUndefined(studentForm.remark),
      })
      ElMessage.success('学员已新增')
    } else if (editingStudentId.value) {
      await updateStudent(editingStudentId.value, {
        studentNo: studentForm.studentNo.trim(),
        studentName: studentForm.studentName.trim(),
        gender: trimToUndefined(studentForm.gender),
        birthday: studentForm.birthday || null,
        guardianName: trimToUndefined(studentForm.guardianName),
        guardianPhone: trimToUndefined(studentForm.guardianPhone),
        status: trimToUndefined(studentForm.status),
        enrolledAt: studentForm.enrolledAt || null,
        remark: trimToUndefined(studentForm.remark),
      })
      ElMessage.success('学员已更新')
    }
    studentDialogVisible.value = false
    resetStudentForm()
    await loadStudents()
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
    await loadStudents()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除学员失败'))
  }
}

/** 行上点「联系人」：把这一行的学员名称记进缓存，抽屉标题才不显示裸 ID */
function openParentDrawer(row: StudentSummary) {
  rememberStudentOptions([{ id: row.id, studentNo: row.studentNo, studentName: row.studentName }])
  contactStudentId.value = row.id
  parentDrawerVisible.value = true
}

/** 行上「报名」跳过去看这个孩子报过哪些班，把学员带过去当筛选条件 */
function goStudentEnrollments(row: StudentSummary) {
  rememberStudentOptions([{ id: row.id, studentNo: row.studentNo, studentName: row.studentName }])
  router.push({ path: '/enrollments', query: { studentId: String(row.id) } })
}

function goEnrollments() {
  router.push({ path: '/enrollments' })
}

async function loadStudents() {
  if (!institutionId.value) {
    pagedStudents.value = []
    total.value = 0
    return
  }
  tableLoading.value = true
  pageError.value = ''
  try {
    const result = await fetchStudentPage({
      institutionId: institutionId.value,
      keyword: trimToUndefined(keyword.value),
      page: page.value,
      pageSize: pageSize.value,
      ...studentSort.sortParams(),
    })
    pagedStudents.value = result.records
    total.value = result.total
    // 行上的姓名/学号记进缓存，抽屉标题与报名页的回显都靠它
    rememberStudentOptions(
      result.records.map((row) => ({
        id: row.id,
        studentNo: row.studentNo,
        studentName: row.studentName,
      }))
    )
  } catch (error) {
    pagedStudents.value = []
    total.value = 0
    pageError.value = toMessage(error, '学员数据加载失败')
  } finally {
    tableLoading.value = false
  }
}

function searchStudents() {
  page.value = 1
  return loadStudents()
}

function handleSizeChange() {
  page.value = 1
  return loadStudents()
}

async function refreshAll() {
  await loadStudents()
}

/** 重置 = 清空关键字后重查 */
async function resetStudentFilters() {
  keyword.value = ''
  await searchStudents()
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    keyword.value = ''
    page.value = 1
    contactStudentId.value = null
    parentDrawerVisible.value = false
    await loadStudents()
  }
)

onMounted(async () => {
  // 性别与状态列走字典回显，DictSelect 自己拉、表格列不会
  void dictStore.ensureLoaded(['gender', 'student_status']).catch(() => undefined)
  await institutionStore.loadInstitutions()
  await refreshAll()
})
</script>

<style scoped>
/* 一屏只有一张主表：表体吃满剩余高度，整页不出现纵向滚动 */
.student-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
</style>
