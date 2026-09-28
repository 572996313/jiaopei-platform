<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 这一页只管报名：学员本人的档案去「学员档案」维护 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openDialog">新增报名</el-button>
      <el-button :icon="Refresh" circle :loading="tableLoading" @click="refreshAll" />
      <el-button @click="goStudents">学员档案</el-button>
      <span class="education-context-text">
        报名记录不等于入班：报上名后到「班级名册」办理入班才会进班。
      </span>
    </div>

    <!-- 统一筛选栏:四个条件都不再自动触发,点「查询」才按当前组合重查报名 -->
    <FilterBar :loading="tableLoading" @query="applyFilters" @reset="resetFilters">
      <el-select v-model="campusId" placeholder="筛选校区" clearable filterable>
        <el-option
          v-for="item in campuses"
          :key="item.id"
          :label="item.campusName || item.campusCode || `校区 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <el-select v-model="termId" placeholder="筛选学期" clearable filterable>
        <el-option
          v-for="item in terms"
          :key="item.id"
          :label="item.termName || item.termCode || `学期 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <ClassSelect
        v-model="classId"
        :institution-id="institutionId"
        :campus-id="campusId"
        :term-id="termId"
        placeholder="筛选班级"
      />
      <StudentSelect
        v-model="studentId"
        :institution-id="institutionId"
        placeholder="搜索学员（姓名/学号）"
      />
    </FilterBar>

    <article class="panel-card education-panel enrollment-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          ref="enrollmentTableRef"
          :data="records"
          border
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
          :cell-class-name="enrollmentSort.cellClass"
          @sort-change="enrollmentSort.onSortChange"
        >
          <el-table-column
            prop="enrollmentNo"
            label="报名编号"
            width="160"
            fixed="left"
            sortable="custom"
          />
          <el-table-column label="学员" min-width="180">
            <template #default="{ row }">
              {{ studentLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column label="班级" min-width="180">
            <template #default="{ row }">
              {{ classLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="enrollmentTagType(row.status)">
                {{ enrollmentStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="channel" label="来源渠道" width="140" sortable="custom">
            <template #default="{ row }">{{ enrollmentChannelLabel(row.channel) }}</template>
          </el-table-column>
          <el-table-column prop="enrolledAt" label="报名日期" width="140" sortable="custom" />
          <el-table-column
            prop="remark"
            label="备注"
            min-width="220"
            show-overflow-tooltip
            sortable="custom"
          />
        </el-table>
      </TableFillBox>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无报名记录。
      </div>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="panel-pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadEnrollments"
        @size-change="handleSizeChange"
      />
    </article>

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
          <ClassSelect
            v-model="form.classId"
            :institution-id="institutionId"
            :campus-id="campusId"
            placeholder="选择报名班级"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item label="报名学员" required>
          <StudentSelect
            v-model="form.studentId"
            :institution-id="institutionId"
            placeholder="搜索学员（姓名/学号）"
            :clearable="false"
          />
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
        <el-button type="primary" :loading="submitting" @click="submitEnrollment">
          提交报名
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import {
  rememberStudentOptions,
  studentLabelOf,
} from '@cornerstone/module-education/composables/studentLabels'
import { classLabelOf } from '@cornerstone/module-education/composables/classLabels'
import ClassSelect from './components/ClassSelect.vue'
import StudentSelect from './components/StudentSelect.vue'
import {
  createEnrollment,
  fetchEnrollmentPage,
  type EnrollmentViewRecord,
} from '@cornerstone/module-education/api/education'

const route = useRoute()
const router = useRouter()
const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { institutionId, campuses, terms, loadScope } = useEducationScope()

const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)

const records = ref<EnrollmentViewRecord[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const tableLoading = ref(false)
const pageError = ref('')
const enrollmentTableRef = ref<{ clearSort: () => void } | null>(null)
/** 表头排序走服务端：点表头回第一页重查，取消排序回到接口默认排序 */
const enrollmentSort = useTableSort({
  onSorted: () => {
    page.value = 1
    void loadEnrollments()
  },
  clearTableSort: () => enrollmentTableRef.value?.clearSort(),
})

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')

const form = reactive({
  classId: '' as string | null,
  studentId: '' as string | null,
  enrollmentNo: '',
  status: 'submitted',
  channel: '',
  enrolledAt: todayValue(),
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

/** 分页行自带班级与学员名称，查不到时才回落本地缓存 */
function classLabel(row: EnrollmentViewRecord) {
  return row.className || classLabelOf(row.classId)
}

function studentLabel(row: EnrollmentViewRecord) {
  return row.studentName || row.studentNo || studentLabelOf(row.studentId)
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

function enrollmentChannelLabel(value?: string | null) {
  return dictStore.displayLabel('enrollment_channel', value)
}

function resetForm() {
  form.classId = classId.value || ''
  form.studentId = studentId.value || ''
  form.enrollmentNo = ''
  form.status = 'submitted'
  form.channel = ''
  form.enrolledAt = todayValue()
  form.remark = ''
  formError.value = ''
}

async function loadEnrollments() {
  if (!institutionId.value) {
    records.value = []
    total.value = 0
    return
  }
  tableLoading.value = true
  pageError.value = ''
  try {
    const result = await fetchEnrollmentPage({
      institutionId: institutionId.value,
      classId: classId.value,
      studentId: studentId.value,
      page: page.value,
      pageSize: pageSize.value,
      ...enrollmentSort.sortParams(),
    })
    records.value = result.records
    total.value = result.total
    // 行上带来的学员名称记进缓存，弹窗与筛选框回显要用
    rememberStudentOptions(
      result.records
        .filter((row) => row.studentId)
        .map((row) => ({
          id: row.studentId as string,
          studentNo: row.studentNo,
          studentName: row.studentName,
        }))
    )
  } catch (error) {
    records.value = []
    total.value = 0
    pageError.value = toMessage(error, '报名数据加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function loadRecords() {
  page.value = 1
  await loadEnrollments()
}

function handleSizeChange() {
  page.value = 1
  void loadEnrollments()
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    terms.value = []
    return
  }
  try {
    await loadScope()
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  }
}

async function refreshAll() {
  await refreshScopeData()
  await loadEnrollments()
}

/** 点「查询」才应用:保留已选班级/学员草稿,重拉范围数据后按当前组合查报名 */
async function applyFilters() {
  await refreshScopeData()
  await loadRecords()
}

/** 重置 = 清空校区/学期/班级/学员筛选并重查 */
async function resetFilters() {
  campusId.value = null
  termId.value = null
  classId.value = null
  studentId.value = null
  await applyFilters()
}

function openDialog() {
  resetForm()
  dialogVisible.value = true
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

function goStudents() {
  router.push({ path: '/students' })
}

/** 档案页跳过来时带 ?studentId=，缓存中的页签不会重新挂载，所以要盯着路由参数 */
function applyRouteStudentId() {
  const value = route.query.studentId
  const next = value ? String(value) : null
  if (next === studentId.value) {
    return
  }
  studentId.value = next
  void loadRecords()
}

watch(
  () => route.query.studentId,
  () => {
    if (route.name !== 'enrollments') {
      return
    }
    applyRouteStudentId()
  }
)

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    campusId.value = null
    termId.value = null
    classId.value = null
    studentId.value = null
    page.value = 1
    await refreshScopeData()
    await loadEnrollments()
  }
)

onMounted(async () => {
  // 渠道列要用的字典在进页面时就拉取（DictSelect 自己拉，表格列不会）
  void dictStore.ensureLoaded(['enrollment_channel']).catch(() => undefined)
  await institutionStore.loadInstitutions()
  await refreshAll()
  // 机构装载时上面那条 watch 会把学员筛选清空，所以带 ?studentId= 进来要等初始化完再落一次
  applyRouteStudentId()
})
</script>

<style scoped>
/* 一屏只有一张主表：表体吃满剩余高度，整页不出现纵向滚动 */
.enrollment-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
</style>
