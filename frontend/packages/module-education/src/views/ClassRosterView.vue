<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 名册只管"班里有哪些人"：班级怎么搭的去「班级档案」页维护 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openDialog">新增入班</el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="refreshAll" />
      <el-button @click="goClassProfile">班级档案</el-button>
      <span class="education-context-text">
        移除只断班级与学员的关系，不动学员档案；点行上「联系人」在右侧维护家长电话。
      </span>
    </div>

    <!-- 统一筛选栏:校区范围在横幅那颗芯片上选,本页只按它筛名册;班级/学员都不再自动触发,点「查询」才重拉 -->
    <FilterBar :loading="tableLoading" @query="applyFilters" @reset="resetFilters">
      <ClassSelect
        v-model="classId"
        :institution-id="institutionId"
        :campus-id="campusId"
        placeholder="筛选班级"
      />
      <StudentSelect
        v-model="studentId"
        :institution-id="institutionId"
        placeholder="搜索学员（姓名/学号）"
      />
    </FilterBar>

    <article class="panel-card education-panel roster-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          ref="rosterTableRef"
          :data="records"
          border
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
          :cell-class-name="rosterSort.cellClass"
          @sort-change="rosterSort.onSortChange"
        >
          <el-table-column label="班级" min-width="180" fixed="left">
            <template #default="{ row }">
              {{ classLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column label="课程 / 班型" min-width="220">
            <template #default="{ row }">
              <div class="class-inline-meta">
                <span>{{ formatCourse(row.courseId) }}</span>
                <span>{{ formatClassType(row.classTypeId) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="学员" min-width="180">
            <template #default="{ row }">
              {{ studentLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120" prop="status" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="classStudentTagType(row.status)">
                {{ classStudentStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="joinedAt" label="入班日期" width="140" sortable="custom" />
          <el-table-column prop="leftAt" label="离班日期" width="140" sortable="custom" />
          <el-table-column
            prop="remark"
            label="备注"
            min-width="220"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="openParentDrawer(row)">联系人</el-button>
                <el-button link type="danger" @click="removeClassStudent(row)">移除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无班级学员关系。
      </div>

      <el-pagination
        v-model:current-page="rosterPage"
        v-model:page-size="rosterPageSize"
        class="panel-pagination"
        :total="rosterTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRosterPage"
        @size-change="handleRosterPageSizeChange"
      />
    </article>

    <el-dialog v-model="dialogVisible" title="新增入班关系" width="520px">
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="入班班级" required>
          <ClassSelect
            v-model="form.classId"
            :institution-id="institutionId"
            :campus-id="campusId"
          />
        </el-form-item>
        <el-form-item label="学员" required>
          <StudentSelect v-model="form.studentId" :institution-id="institutionId" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="选择状态">
            <el-option label="在班" value="active" />
            <el-option label="停班" value="suspended" />
            <el-option label="已离班" value="left" />
          </el-select>
        </el-form-item>
        <el-form-item label="入班日期">
          <el-date-picker
            v-model="form.joinedAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择入班日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="离班日期">
          <el-date-picker
            v-model="form.leftAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择离班日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充关系说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClassStudent">
          提交入班
        </el-button>
      </template>
    </el-dialog>

    <!-- 家长联系人是"打电话才看"的明细，收进右侧抽屉，不占名册页的纵向预算 -->
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
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import {
  rememberStudentOptions,
  studentLabelOf,
} from '@cornerstone/module-education/composables/studentLabels'
import { classLabelOf } from '@cornerstone/module-education/composables/classLabels'
import ClassSelect from './components/ClassSelect.vue'
import StudentSelect from './components/StudentSelect.vue'
import StudentParentDrawer from './components/StudentParentDrawer.vue'
import {
  createClassStudent,
  deleteClassStudent,
  fetchClassStudentPage,
  fetchClassTypes,
  fetchCourses,
  type ClassStudentViewRecord,
  type ClassTypeRecord,
  type CourseRecord,
} from '@cornerstone/module-education/api/education'

const route = useRoute()
const router = useRouter()
const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId } = storeToRefs(scopeStore)
const { institutionId, loadScope } = useEducationScope()

const courses = ref<CourseRecord[]>([])
const classTypes = ref<ClassTypeRecord[]>([])

const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)
const contactStudentId = ref<string | null>(null)

const records = ref<ClassStudentViewRecord[]>([])
const rosterPage = ref(1)
const rosterPageSize = ref(20)
const rosterTotal = ref(0)
const rosterTableRef = ref<{ clearSort: () => void } | null>(null)
/** 表头排序走服务端：点表头回第一页重查，取消排序回到接口默认排序 */
const rosterSort = useTableSort({
  onSorted: () => {
    rosterPage.value = 1
    void loadRosterPage()
  },
  clearTableSort: () => rosterTableRef.value?.clearSort(),
})

const pageError = ref('')
const formError = ref('')
const loading = ref(false)
const tableLoading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const parentDrawerVisible = ref(false)

const form = reactive({
  classId: '' as string | null,
  studentId: '' as string | null,
  status: 'active',
  joinedAt: todayValue(),
  leftAt: '',
  remark: '',
})

const courseMap = computed(() => {
  return new Map(
    courses.value.map((item) => [item.id, item.courseName || item.courseCode || `课程 #${item.id}`])
  )
})

const classTypeMap = computed(() => {
  return new Map(
    classTypes.value.map((item) => [item.id, item.typeName || item.typeCode || `班型 #${item.id}`])
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

/** 分页行自带班级与学员名称，查不到时才回落本地缓存 */
function classLabel(row: ClassStudentViewRecord) {
  return row.className || row.classCode || classLabelOf(row.classId)
}

function studentLabel(row: ClassStudentViewRecord) {
  return row.studentName || row.studentNo || studentLabelOf(row.studentId)
}

function formatCourse(id?: string | null) {
  if (!id) {
    return '未绑定课程'
  }
  return courseMap.value.get(id) || `课程 #${id}`
}

function formatClassType(id?: string | null) {
  if (!id) {
    return '未绑定班型'
  }
  return classTypeMap.value.get(id) || `班型 #${id}`
}

function classStudentStatusLabel(status?: string | null) {
  switch (status) {
    case 'suspended':
      return '停班'
    case 'left':
      return '已离班'
    case 'active':
    default:
      return '在班'
  }
}

function classStudentTagType(status?: string | null) {
  switch (status) {
    case 'suspended':
      return 'warning'
    case 'left':
      return 'info'
    case 'active':
    default:
      return 'success'
  }
}

function resetForm() {
  form.classId = classId.value || ''
  form.studentId = studentId.value || ''
  form.status = 'active'
  form.joinedAt = todayValue()
  form.leftAt = ''
  form.remark = ''
  formError.value = ''
}

/** 名册页只需要课程与班型两张字典做名称回显，教师与班级数组不进这页 */
async function refreshLookups() {
  if (!institutionId.value) {
    courses.value = []
    classTypes.value = []
    return
  }
  try {
    await loadScope()
    const [courseList, classTypeList] = await Promise.all([
      fetchCourses({ institutionId: institutionId.value }),
      fetchClassTypes({ institutionId: institutionId.value }),
    ])
    courses.value = courseList
    classTypes.value = classTypeList
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  }
}

async function loadRosterPage() {
  if (!institutionId.value) {
    records.value = []
    rosterTotal.value = 0
    return
  }
  tableLoading.value = true
  pageError.value = ''
  try {
    const result = await fetchClassStudentPage({
      institutionId: institutionId.value,
      campusId: campusId.value,
      classId: classId.value,
      studentId: studentId.value,
      page: rosterPage.value,
      pageSize: rosterPageSize.value,
      ...rosterSort.sortParams(),
    })
    records.value = result.records
    rosterTotal.value = result.total
  } catch (error) {
    records.value = []
    rosterTotal.value = 0
    pageError.value = toMessage(error, '班级名册加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function loadRecords() {
  rosterPage.value = 1
  await loadRosterPage()
}

function handleRosterPageSizeChange() {
  rosterPage.value = 1
  void loadRosterPage()
}

/** 点「查询」才应用:保留已选的班级/学员草稿,重拉范围数据后按当前条件查名册 */
async function applyFilters() {
  await refreshLookups()
  await loadRecords()
}

/** 重置 = 清空班级/学员筛选并重查（校区是全站范围，不由本页重置） */
async function resetFilters() {
  classId.value = null
  studentId.value = null
  contactStudentId.value = null
  parentDrawerVisible.value = false
  await applyFilters()
}

function openDialog() {
  resetForm()
  dialogVisible.value = true
}

/** 从行上进入联系人抽屉：行自带学员名称，先记进缓存供抽屉标题回显 */
function openParentDrawer(row?: ClassStudentViewRecord | null) {
  if (row?.studentId) {
    rememberStudentOptions([
      { id: row.studentId, studentNo: row.studentNo, studentName: row.studentName },
    ])
  }
  contactStudentId.value = row?.studentId || studentId.value || null
  parentDrawerVisible.value = true
}

async function submitClassStudent() {
  if (!institutionId.value) {
    formError.value = '请先选择机构范围'
    return
  }
  if (!form.classId) {
    formError.value = '请选择入班班级'
    return
  }
  if (!form.studentId) {
    formError.value = '请选择学员'
    return
  }
  if (form.leftAt && form.joinedAt && form.leftAt < form.joinedAt) {
    formError.value = '离班日期不能早于入班日期'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await createClassStudent({
      institutionId: institutionId.value,
      classId: form.classId,
      studentId: form.studentId,
      status: form.status,
      joinedAt: form.joinedAt,
      leftAt: form.leftAt || null,
      remark: trimToUndefined(form.remark),
    })
    ElMessage.success('入班关系已新增')
    dialogVisible.value = false
    resetForm()
    await loadRecords()
  } catch (error) {
    formError.value = toMessage(error, '新增入班失败')
  } finally {
    submitting.value = false
  }
}

async function removeClassStudent(row: ClassStudentViewRecord) {
  try {
    await ElMessageBox.confirm(
      `确认将 ${studentLabel(row)} 移出班级 ${classLabel(row)} 吗？`,
      '移出班级',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteClassStudent(row.id)
    ElMessage.success('学员已移出班级')
    await loadRecords()
  } catch (error) {
    ElMessage.error(toMessage(error, '移出班级失败'))
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await refreshLookups()
    await loadRecords()
  } finally {
    loading.value = false
  }
}

function goClassProfile() {
  // 跳转把当前班级带过去，档案页落地就停在同一个班
  router.push({ path: '/classes', query: classId.value ? { classId: String(classId.value) } : {} })
}

/** 档案页跳过来时带 ?classId=，缓存中的页签不会重新挂载，所以要盯着路由参数 */
function applyRouteClassId() {
  const value = route.query.classId
  const next = value ? String(value) : null
  if (next === classId.value) {
    return
  }
  classId.value = next
  void loadRecords()
}

watch(
  () => route.query.classId,
  () => {
    if (route.name !== 'class-roster') {
      return
    }
    applyRouteClassId()
  }
)

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    // 校区范围由横幅芯片在新机构上重新 initScope，本页只清自己选的筛选
    classId.value = null
    studentId.value = null
    contactStudentId.value = null
    parentDrawerVisible.value = false
    await refreshLookups()
    await loadRecords()
  }
)

onMounted(async () => {
  // 抽屉里的关系列要字典，由 StudentParentDrawer 自己拉
  await institutionStore.loadInstitutions()
  await refreshAll()
  // 机构装载时上面那条 watch 会把班级清空，所以带 ?classId= 进来要等初始化完再落一次
  applyRouteClassId()
})
</script>

<style scoped>
/* 一屏只有一张主表：表体吃满剩余高度，整页不出现纵向滚动 */
.roster-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

.class-inline-meta {
  display: grid;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
