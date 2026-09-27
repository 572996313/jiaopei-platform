<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 这一页只管课程本身与两张配套小档案：班级怎么搭去「班级档案」，教室节次去「教学资源」 -->
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
      <span class="education-context-text">
        课程是班级主档与排课的前置档案；分类与班型只列常用列，备注等其余字段在编辑弹窗里看。
      </span>
    </div>

    <!-- 两张同类小档案并排、各自封顶：它们再长也不把下面的课程主表挤出首屏 -->
    <div class="course-aux-grid">
      <article class="panel-card resource-card course-aux-card">
        <div class="education-table-head">
          <div class="panel-title">课程分类</div>
          <div class="table-actions">
            <el-button
              v-if="canManage"
              link
              type="primary"
              @click="openCourseCategoryDialog('create')"
              >新增分类</el-button
            >
          </div>
        </div>
        <!-- 分类/班型是全量小表,排序走 Element Plus 本地比较,不发请求 -->
        <el-table
          :data="courseCategories"
          border
          row-key="id"
          highlight-current-row
          :max-height="AUX_TABLE_MAX_HEIGHT"
        >
          <el-table-column prop="categoryCode" label="分类编码" width="120" fixed="left" sortable />
          <el-table-column
            prop="categoryName"
            label="分类名称"
            min-width="140"
            show-overflow-tooltip
            sortable
          />
          <el-table-column label="状态" width="90" prop="status" sortable>
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                enabledStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="70" align="right" sortable />
          <el-table-column v-if="canManage" label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCourseCategoryDialog('edit', row)"
                >编辑</el-button
              >
              <el-button link type="danger" @click="confirmDeleteCourseCategory(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card course-aux-card">
        <div class="education-table-head">
          <div class="panel-title">班型列表</div>
          <div class="table-actions">
            <el-button v-if="canManage" link type="primary" @click="openClassTypeDialog('create')"
              >新增班型</el-button
            >
          </div>
        </div>
        <el-table
          :data="classTypes"
          border
          row-key="id"
          highlight-current-row
          :max-height="AUX_TABLE_MAX_HEIGHT"
        >
          <el-table-column prop="typeCode" label="班型编码" width="120" fixed="left" sortable />
          <el-table-column
            prop="typeName"
            label="班型名称"
            min-width="130"
            show-overflow-tooltip
            sortable
          />
          <!-- 最少/最多合成一列：两列并排在这张半宽卡里放不下,而这两个数总是一起看 -->
          <el-table-column label="人数区间" width="110" align="right" sortable>
            <template #default="{ row }"
              >{{ row.minStudents ?? '-' }} ~ {{ row.maxStudents ?? '-' }}</template
            >
          </el-table-column>
          <el-table-column label="状态" width="90" prop="status" sortable>
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                enabledStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canManage" label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openClassTypeDialog('edit', row)"
                >编辑</el-button
              >
              <el-button link type="danger" @click="confirmDeleteClassType(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </div>

    <article class="panel-card education-panel course-main-panel">
      <div class="education-table-head">
        <div class="panel-title">课程列表</div>
        <div class="table-actions">
          <el-button v-if="canManage" link type="primary" @click="openCourseDialog('create')"
            >新增课程</el-button
          >
        </div>
      </div>
      <div class="table-filters">
        <el-input
          v-model="courseKeyword"
          placeholder="编码/名称/学科"
          clearable
          class="panel-search-input"
          @keyup.enter="searchCourses"
          @clear="searchCourses"
        />
      </div>
      <TableFillBox v-slot="{ height }">
        <el-table
          ref="courseTableRef"
          :data="courses"
          border
          row-key="id"
          highlight-current-row
          v-loading="tableLoading"
          :height="height"
          :cell-class-name="courseSort.cellClass"
          @sort-change="courseSort.onSortChange"
        >
          <el-table-column
            prop="courseCode"
            label="课程编码"
            width="150"
            fixed="left"
            sortable="custom"
          />
          <el-table-column
            prop="courseName"
            label="课程名称"
            min-width="180"
            show-overflow-tooltip
            fixed="left"
            sortable="custom"
          />
          <el-table-column label="课程分类" min-width="160">
            <template #default="{ row }">
              {{ courseCategories.find((item) => item.id === row.categoryId)?.categoryName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="subjectName" label="学科" width="120" sortable="custom" />
          <el-table-column label="授课方式" width="140" prop="teachingMethod" sortable="custom">
            <template #default="{ row }">{{ teachingMethodLabel(row.teachingMethod) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100" prop="status" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                enabledStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column v-if="canManage" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCourseDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeleteCourse(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!tableLoading && courses.length === 0" class="education-empty">
        当前筛选范围下暂无课程，点击"新增课程"录入。
      </div>

      <el-pagination
        v-model:current-page="coursePage"
        v-model:page-size="coursePageSize"
        class="panel-pagination"
        :total="courseTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadCourses"
        @size-change="handleCourseSizeChange"
      />
    </article>

    <el-dialog
      v-model="courseCategoryDialogVisible"
      :title="courseCategoryDialogMode === 'create' ? '新增课程分类' : '编辑课程分类'"
      width="560px"
    >
      <el-alert
        v-if="courseCategoryFormError"
        :title="courseCategoryFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="分类编码"
          ><el-input v-model="courseCategoryForm.categoryCode"
        /></el-form-item>
        <el-form-item label="分类名称"
          ><el-input v-model="courseCategoryForm.categoryName"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="courseCategoryForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"
          ><el-input-number v-model="courseCategoryForm.sortOrder" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="courseCategoryForm.remark" type="textarea" :rows="2"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseCategoryDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="courseCategorySubmitting"
          @click="submitCourseCategory"
          >{{ courseCategoryDialogMode === 'create' ? '提交分类' : '保存修改' }}</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="courseDialogVisible"
      :title="courseDialogMode === 'create' ? '新增课程' : '编辑课程'"
      width="560px"
    >
      <el-alert
        v-if="courseFormError"
        :title="courseFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="课程分类">
          <el-select v-model="courseForm.categoryId" clearable filterable>
            <el-option
              v-for="item in courseCategories"
              :key="`${item.id}-${item.categoryName || item.categoryCode || ''}`"
              :label="item.categoryName || item.categoryCode || `分类 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程编码"><el-input v-model="courseForm.courseCode" /></el-form-item>
        <el-form-item label="课程名称"><el-input v-model="courseForm.courseName" /></el-form-item>
        <el-form-item label="学科"><el-input v-model="courseForm.subjectName" /></el-form-item>
        <el-form-item label="授课方式"
          ><DictSelect v-model="courseForm.teachingMethod" type-code="teaching_method"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="courseForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="courseForm.remark" type="textarea" :rows="2"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="courseSubmitting" @click="submitCourse">{{
          courseDialogMode === 'create' ? '提交课程' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="classTypeDialogVisible"
      :title="classTypeDialogMode === 'create' ? '新增班型' : '编辑班型'"
      width="560px"
    >
      <el-alert
        v-if="classTypeFormError"
        :title="classTypeFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="班型编码"><el-input v-model="classTypeForm.typeCode" /></el-form-item>
        <el-form-item label="班型名称"><el-input v-model="classTypeForm.typeName" /></el-form-item>
        <el-form-item label="授课方式"
          ><DictSelect v-model="classTypeForm.teachingMethod" type-code="teaching_method"
        /></el-form-item>
        <el-form-item label="最少人数"
          ><el-input-number v-model="classTypeForm.minStudents" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="最多人数"
          ><el-input-number v-model="classTypeForm.maxStudents" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classTypeForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="classTypeForm.remark" type="textarea" :rows="2"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classTypeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classTypeSubmitting" @click="submitClassType">{{
          classTypeDialogMode === 'create' ? '提交班型' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { tableViewportHeight } from '@cornerstone/core/utils/table-view'
import {
  createClassType,
  createCourse,
  createCourseCategory,
  deleteClassType,
  deleteCourse,
  deleteCourseCategory,
  fetchClassTypes,
  fetchCourseCategories,
  fetchCoursePage,
  updateClassType,
  updateCourse,
  updateCourseCategory,
  type ClassTypeRecord,
  type CourseCategoryRecord,
  type CourseRecord,
} from '@cornerstone/module-education/api/education'

const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const auth = useAuthStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)

// 写操作与后端 @RequiresPermission("education:catalog:write") 同一口径
const canManage = computed(() => auth.hasPermission('education:catalog:write'))

/**
 * 分类/班型两张卡各给三行（表头 38 + 3×36 = 146）。
 * 这个数字不是随手取的：整页 729 里按钮行 32 + 两处间距 36 + 课程卡固定部分 201 已经占掉 269，
 * 两张卡加到四行（265）就只剩 195 给主表，低于 TableFillBox 的 200 保底，整页会顶出 734。
 * 这两张是小体量档案，十几条时表体自己滚，改完点卡头上的「新增」始终免滚轮。
 */
const AUX_TABLE_MAX_HEIGHT = tableViewportHeight(3)

const courseCategories = ref<CourseCategoryRecord[]>([])
const courses = ref<CourseRecord[]>([])
const classTypes = ref<ClassTypeRecord[]>([])
const lookupLoading = ref(false)
const tableLoading = ref(false)
const pageError = ref('')
const courseTableRef = ref()

// 分页状态:课程列表服务端分页
const coursePage = ref(1)
const coursePageSize = ref(10)
const courseTotal = ref(0)
const courseKeyword = ref('')

/** 课程列表表头排序(服务端):无序时回到后端的默认排序 */
const courseSort = useTableSort({
  onSorted: () => {
    coursePage.value = 1
    void loadCourses()
  },
  clearTableSort: () => courseTableRef.value?.clearSort(),
})

const courseCategoryDialogVisible = ref(false)
const courseDialogVisible = ref(false)
const classTypeDialogVisible = ref(false)

const courseCategoryDialogMode = ref<'create' | 'edit'>('create')
const courseDialogMode = ref<'create' | 'edit'>('create')
const classTypeDialogMode = ref<'create' | 'edit'>('create')

const editingCourseCategoryId = ref<string | null>(null)
const editingCourseId = ref<string | null>(null)
const editingClassTypeId = ref<string | null>(null)

const courseCategorySubmitting = ref(false)
const courseSubmitting = ref(false)
const classTypeSubmitting = ref(false)

const courseCategoryFormError = ref('')
const courseFormError = ref('')
const classTypeFormError = ref('')

const courseCategoryForm = reactive({
  categoryCode: '',
  categoryName: '',
  status: 'active',
  sortOrder: 10,
  remark: '',
})

const courseForm = reactive({
  categoryId: '' as string | null,
  courseCode: '',
  courseName: '',
  subjectName: '',
  teachingMethod: '',
  status: 'active',
  remark: '',
})

const classTypeForm = reactive({
  typeCode: '',
  typeName: '',
  teachingMethod: '',
  minStudents: 1,
  maxStudents: 12,
  status: 'active',
  remark: '',
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function statusTagType(value?: string | null) {
  return value === 'active' ? 'success' : 'info'
}

function enabledStatusLabel(value?: string | null) {
  if (value === 'inactive') {
    return '停用'
  }
  if (value === 'active') {
    return '启用'
  }
  return value || '-'
}

function teachingMethodLabel(value?: string | null) {
  return dictStore.displayLabel('teaching_method', value)
}

function resetCourseCategoryForm() {
  courseCategoryForm.categoryCode = ''
  courseCategoryForm.categoryName = ''
  courseCategoryForm.status = 'active'
  courseCategoryForm.sortOrder = 10
  courseCategoryForm.remark = ''
  courseCategoryFormError.value = ''
}

function resetCourseForm() {
  courseForm.categoryId = courseCategories.value[0]?.id || null
  courseForm.courseCode = ''
  courseForm.courseName = ''
  courseForm.subjectName = ''
  courseForm.teachingMethod = ''
  courseForm.status = 'active'
  courseForm.remark = ''
  courseFormError.value = ''
}

function resetClassTypeForm() {
  classTypeForm.typeCode = ''
  classTypeForm.typeName = ''
  classTypeForm.teachingMethod = ''
  classTypeForm.minStudents = 1
  classTypeForm.maxStudents = 12
  classTypeForm.status = 'active'
  classTypeForm.remark = ''
  classTypeFormError.value = ''
}

async function loadCourses() {
  if (!institutionId.value) {
    courses.value = []
    courseTotal.value = 0
    return
  }
  tableLoading.value = true
  try {
    const result = await fetchCoursePage({
      institutionId: institutionId.value,
      keyword: trimToUndefined(courseKeyword.value),
      page: coursePage.value,
      pageSize: coursePageSize.value,
      ...courseSort.sortParams(),
    })
    courses.value = result.records
    courseTotal.value = result.total
  } catch (error) {
    courses.value = []
    courseTotal.value = 0
    ElMessage.error(toMessage(error, '课程列表加载失败'))
  } finally {
    tableLoading.value = false
  }
}

// 关键词/翻页变化后重查课程列表
function searchCourses() {
  coursePage.value = 1
  return loadCourses()
}

function handleCourseSizeChange() {
  coursePage.value = 1
  return loadCourses()
}

function openCourseCategoryDialog(mode: 'create' | 'edit', row?: CourseCategoryRecord) {
  courseCategoryDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingCourseCategoryId.value = row.id
    courseCategoryForm.categoryCode = row.categoryCode ?? ''
    courseCategoryForm.categoryName = row.categoryName ?? ''
    courseCategoryForm.status = row.status ?? 'active'
    courseCategoryForm.sortOrder = row.sortOrder ?? 10
    courseCategoryForm.remark = row.remark ?? ''
  } else {
    editingCourseCategoryId.value = null
    resetCourseCategoryForm()
  }
  courseCategoryFormError.value = ''
  courseCategoryDialogVisible.value = true
}

function openCourseDialog(mode: 'create' | 'edit', row?: CourseRecord) {
  courseDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingCourseId.value = row.id
    courseForm.categoryId = row.categoryId ?? null
    courseForm.courseCode = row.courseCode ?? ''
    courseForm.courseName = row.courseName ?? ''
    courseForm.subjectName = row.subjectName ?? ''
    courseForm.teachingMethod = row.teachingMethod ?? ''
    courseForm.status = row.status ?? 'active'
    courseForm.remark = row.remark ?? ''
  } else {
    editingCourseId.value = null
    resetCourseForm()
  }
  courseFormError.value = ''
  courseDialogVisible.value = true
}

function openClassTypeDialog(mode: 'create' | 'edit', row?: ClassTypeRecord) {
  classTypeDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingClassTypeId.value = row.id
    classTypeForm.typeCode = row.typeCode ?? ''
    classTypeForm.typeName = row.typeName ?? ''
    classTypeForm.teachingMethod = row.teachingMethod ?? ''
    classTypeForm.minStudents = row.minStudents ?? 1
    classTypeForm.maxStudents = row.maxStudents ?? 12
    classTypeForm.status = row.status ?? 'active'
    classTypeForm.remark = row.remark ?? ''
  } else {
    editingClassTypeId.value = null
    resetClassTypeForm()
  }
  classTypeFormError.value = ''
  classTypeDialogVisible.value = true
}

async function submitCourseCategory() {
  if (!institutionId.value) {
    courseCategoryFormError.value = '请先选择机构范围'
    return
  }
  if (
    !trimToUndefined(courseCategoryForm.categoryCode) ||
    !trimToUndefined(courseCategoryForm.categoryName)
  ) {
    courseCategoryFormError.value = '请完整填写分类编码和名称'
    return
  }
  courseCategorySubmitting.value = true
  courseCategoryFormError.value = ''
  try {
    if (courseCategoryDialogMode.value === 'create') {
      await createCourseCategory({
        institutionId: institutionId.value,
        categoryCode: courseCategoryForm.categoryCode.trim(),
        categoryName: courseCategoryForm.categoryName.trim(),
        status: courseCategoryForm.status,
        sortOrder: courseCategoryForm.sortOrder,
        remark: trimToUndefined(courseCategoryForm.remark),
      })
      ElMessage.success('课程分类已新增')
    } else if (editingCourseCategoryId.value) {
      await updateCourseCategory(editingCourseCategoryId.value, {
        categoryCode: courseCategoryForm.categoryCode.trim(),
        categoryName: courseCategoryForm.categoryName.trim(),
        status: courseCategoryForm.status,
        sortOrder: courseCategoryForm.sortOrder,
        remark: trimToUndefined(courseCategoryForm.remark),
      })
      ElMessage.success('课程分类已更新')
    }
    courseCategoryDialogVisible.value = false
    courseCategories.value = await fetchCourseCategories({ institutionId: institutionId.value })
  } catch (error) {
    courseCategoryFormError.value = toMessage(
      error,
      courseCategoryDialogMode.value === 'create' ? '新增课程分类失败' : '更新课程分类失败'
    )
  } finally {
    courseCategorySubmitting.value = false
  }
}

async function confirmDeleteCourseCategory(row: CourseCategoryRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${row.categoryName || row.categoryCode || row.id}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteCourseCategory(row.id)
    ElMessage.success('课程分类已删除')
    courseCategories.value = await fetchCourseCategories({ institutionId: institutionId.value })
  } catch (error) {
    ElMessage.error(toMessage(error, '删除课程分类失败'))
  }
}

async function submitCourse() {
  if (!institutionId.value) {
    courseFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(courseForm.courseCode) || !trimToUndefined(courseForm.courseName)) {
    courseFormError.value = '请完整填写课程编码和名称'
    return
  }
  courseSubmitting.value = true
  courseFormError.value = ''
  try {
    if (courseDialogMode.value === 'create') {
      await createCourse({
        institutionId: institutionId.value,
        categoryId: courseForm.categoryId || null,
        courseCode: courseForm.courseCode.trim(),
        courseName: courseForm.courseName.trim(),
        subjectName: trimToUndefined(courseForm.subjectName),
        teachingMethod: trimToUndefined(courseForm.teachingMethod),
        status: courseForm.status,
        remark: trimToUndefined(courseForm.remark),
      })
      ElMessage.success('课程已新增')
    } else if (editingCourseId.value) {
      await updateCourse(editingCourseId.value, {
        categoryId: courseForm.categoryId || null,
        courseCode: courseForm.courseCode.trim(),
        courseName: courseForm.courseName.trim(),
        subjectName: trimToUndefined(courseForm.subjectName),
        teachingMethod: trimToUndefined(courseForm.teachingMethod),
        status: courseForm.status,
        remark: trimToUndefined(courseForm.remark),
      })
      ElMessage.success('课程已更新')
    }
    courseDialogVisible.value = false
    await loadCourses()
  } catch (error) {
    courseFormError.value = toMessage(
      error,
      courseDialogMode.value === 'create' ? '新增课程失败' : '更新课程失败'
    )
  } finally {
    courseSubmitting.value = false
  }
}

async function confirmDeleteCourse(row: CourseRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除课程「${row.courseName || row.courseCode || row.id}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteCourse(row.id)
    ElMessage.success('课程已删除')
    await loadCourses()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除课程失败'))
  }
}

async function submitClassType() {
  if (!institutionId.value) {
    classTypeFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(classTypeForm.typeCode) || !trimToUndefined(classTypeForm.typeName)) {
    classTypeFormError.value = '请完整填写班型编码和名称'
    return
  }
  classTypeSubmitting.value = true
  classTypeFormError.value = ''
  try {
    if (classTypeDialogMode.value === 'create') {
      await createClassType({
        institutionId: institutionId.value,
        typeCode: classTypeForm.typeCode.trim(),
        typeName: classTypeForm.typeName.trim(),
        teachingMethod: trimToUndefined(classTypeForm.teachingMethod),
        minStudents: classTypeForm.minStudents,
        maxStudents: classTypeForm.maxStudents,
        status: classTypeForm.status,
        remark: trimToUndefined(classTypeForm.remark),
      })
      ElMessage.success('班型已新增')
    } else if (editingClassTypeId.value) {
      await updateClassType(editingClassTypeId.value, {
        typeCode: classTypeForm.typeCode.trim(),
        typeName: classTypeForm.typeName.trim(),
        teachingMethod: trimToUndefined(classTypeForm.teachingMethod),
        minStudents: classTypeForm.minStudents,
        maxStudents: classTypeForm.maxStudents,
        status: classTypeForm.status,
        remark: trimToUndefined(classTypeForm.remark),
      })
      ElMessage.success('班型已更新')
    }
    classTypeDialogVisible.value = false
    classTypes.value = await fetchClassTypes({ institutionId: institutionId.value })
  } catch (error) {
    classTypeFormError.value = toMessage(
      error,
      classTypeDialogMode.value === 'create' ? '新增班型失败' : '更新班型失败'
    )
  } finally {
    classTypeSubmitting.value = false
  }
}

async function confirmDeleteClassType(row: ClassTypeRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除班型「${row.typeName || row.typeCode || row.id}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteClassType(row.id)
    ElMessage.success('班型已删除')
    classTypes.value = await fetchClassTypes({ institutionId: institutionId.value })
  } catch (error) {
    ElMessage.error(toMessage(error, '删除班型失败'))
  }
}

async function loadCourseCategories() {
  if (!institutionId.value) {
    courseCategories.value = []
    return
  }
  try {
    courseCategories.value = await fetchCourseCategories({ institutionId: institutionId.value })
  } catch (error) {
    courseCategories.value = []
    ElMessage.error(toMessage(error, '课程分类加载失败'))
  }
}

async function loadClassTypes() {
  if (!institutionId.value) {
    classTypes.value = []
    return
  }
  try {
    classTypes.value = await fetchClassTypes({ institutionId: institutionId.value })
  } catch (error) {
    classTypes.value = []
    ElMessage.error(toMessage(error, '班型加载失败'))
  }
}

async function refreshAll() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await Promise.all([loadCourseCategories(), loadCourses(), loadClassTypes()])
  } catch (error) {
    pageError.value = toMessage(error, '课程档案加载失败')
  } finally {
    lookupLoading.value = false
  }
}

watch(institutionId, async () => {
  courseKeyword.value = ''
  coursePage.value = 1
  await refreshAll()
})

onMounted(async () => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore.ensureLoaded(['teaching_method']).catch(() => undefined)
  await institutionStore.loadInstitutions()
  await refreshAll()
})
</script>

<style scoped>
/* 两张小档案卡并排。整行不给 max-height：表体已按三行封顶，卡片高度只由内容决定
   （43 卡头 + 12 卡内间距 + 146 表体 + 28 内边距 = 229），剩下的都是课程主表的。
   grid-template-rows 仍要 minmax(0,1fr)：默认行高由内容撑开，两张卡一高一矮时矮的那张
   会被拉伸到行的上限、把下面的主表顶下去。 */
.course-aux-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
  flex: 0 0 auto;
}

.course-aux-card {
  min-height: 0;
  overflow: hidden;
  gap: 12px;
  padding: 14px 16px;
}

/* 课程是这一页的主表：吃掉两张小卡之外的剩余高度 */
.course-main-panel {
  min-height: 240px;
}

@media (max-width: 1280px) {
  .course-aux-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
