<template>
  <!--
    校区管理页的教务卡片：课程分类 / 课程 / 班型。
    原本是 CampusManagementView 内的三个资源块，阶段三拆包时抽到教务模块，
    通过底座页面插槽 registerPageSlot('campus-detail') 注入；
    未装配教务模块时校区页自动少这三块，组织模块不需要依赖教务。
  -->
  <article class="panel-card resource-card">
    <div class="resource-head">
      <div>
        <div class="panel-title">课程分类</div>
        <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
      </div>
    </div>
    <!-- 本卡片主操作:表格上方一行、左对齐 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openCourseCategoryDialog('create')">新增分类</el-button>
    </div>
    <el-table :data="courseCategories" border :max-height="TABLE_STACK_MAX_HEIGHT">
      <el-table-column prop="categoryCode" label="分类编码" width="150" />
      <el-table-column prop="categoryName" label="分类名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ enabledStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="100" />
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCourseCategoryDialog('edit', row)"
            >编辑</el-button
          >
          <el-button link type="danger" @click="confirmDeleteCourseCategory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </article>

  <article class="panel-card resource-card">
    <div class="resource-head">
      <div>
        <div class="panel-title">课程列表</div>
        <div class="education-context-text">课程作为排课与班级主档的前置档案</div>
      </div>
    </div>
    <!-- 主操作在上、查询条件在中、表格在下 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openCourseDialog('create')">新增课程</el-button>
    </div>
    <div class="table-filters">
      <el-input
        v-model="courseKeyword"
        placeholder="编码/名称/学科"
        clearable
        size="small"
        class="panel-search-input"
        @keyup.enter="searchCourses"
        @clear="searchCourses"
      />
    </div>
    <el-table :data="courses" border :max-height="TABLE_STACK_MAX_HEIGHT">
      <el-table-column prop="courseCode" label="课程编码" width="150" />
      <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="课程分类" min-width="160">
        <template #default="{ row }">
          {{ courseCategories.find((item) => item.id === row.categoryId)?.categoryName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="subjectName" label="学科" width="120" />
      <el-table-column label="授课方式" width="160">
        <template #default="{ row }">{{ teachingMethodLabel(row.teachingMethod) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ enabledStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCourseDialog('edit', row)">编辑</el-button>
          <el-button link type="danger" @click="confirmDeleteCourse(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
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

  <article class="panel-card resource-card">
    <div class="resource-head">
      <div>
        <div class="panel-title">班型列表</div>
        <div class="education-context-text">后续用于成班规则和班级主档承接</div>
      </div>
    </div>
    <!-- 本卡片主操作:表格上方一行、左对齐 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openClassTypeDialog('create')">新增班型</el-button>
    </div>
    <el-table :data="classTypes" border :max-height="TABLE_STACK_MAX_HEIGHT">
      <el-table-column prop="typeCode" label="班型编码" width="150" />
      <el-table-column prop="typeName" label="班型名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="授课方式" width="160">
        <template #default="{ row }">{{ teachingMethodLabel(row.teachingMethod) }}</template>
      </el-table-column>
      <el-table-column prop="minStudents" label="最少人数" width="100" />
      <el-table-column prop="maxStudents" label="最多人数" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ enabledStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openClassTypeDialog('edit', row)">编辑</el-button>
          <el-button link type="danger" @click="confirmDeleteClassType(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
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
      <el-button type="primary" :loading="courseCategorySubmitting" @click="submitCourseCategory">{{
        courseCategoryDialogMode === 'create' ? '提交分类' : '保存修改'
      }}</el-button>
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
            :key="item.id"
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
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onPageRefresh } from '@cornerstone/core/extension/module-extensions'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { TABLE_STACK_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import {
  createClassType,
  createCourse,
  createCourseCategory,
  deleteClassType,
  deleteCourse,
  deleteCourseCategory,
  fetchClassTypes,
  fetchCoursePage,
  fetchCourseCategories,
  updateClassType,
  updateCourse,
  updateCourseCategory,
  type ClassTypeRecord,
  type CourseCategoryRecord,
  type CourseRecord,
} from '../../api/education'

const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function formatInstitution(id?: string | null) {
  return institutionStore.currentInstitutionName || (id ? `机构 #${id}` : '未选择')
}

function statusTagType(value?: string | null) {
  return value === 'active' ? 'success' : 'info'
}

const courseCategories = ref<CourseCategoryRecord[]>([])
const courses = ref<CourseRecord[]>([])
const classTypes = ref<ClassTypeRecord[]>([])

// 分页状态:课程列表服务端分页
const coursePage = ref(1)
const coursePageSize = ref(10)
const courseTotal = ref(0)
const courseKeyword = ref('')

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
  teachingMethod: 'offline_small_class',
  status: 'active',
  remark: '',
})

const classTypeForm = reactive({
  typeCode: '',
  typeName: '',
  teachingMethod: 'offline_small_class',
  minStudents: 1,
  maxStudents: 12,
  status: 'active',
  remark: '',
})

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
  const dictLabel = dictStore.labelOf('teaching_method', value)
  if (dictLabel) {
    return dictLabel
  }
  switch (value) {
    case 'offline_small_class':
      return '线下小班'
    case 'offline_large_class':
      return '线下大班'
    case 'offline_one_to_one':
      return '线下1对1'
    case 'online_small_class':
      return '线上小班'
    case 'online_large_class':
      return '线上大班'
    case 'online_one_to_one':
      return '线上1对1'
    default:
      return value || '-'
  }
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
  courseForm.teachingMethod = 'offline_small_class'
  courseForm.status = 'active'
  courseForm.remark = ''
  courseFormError.value = ''
}

function resetClassTypeForm() {
  classTypeForm.typeCode = ''
  classTypeForm.typeName = ''
  classTypeForm.teachingMethod = 'offline_small_class'
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
  try {
    const result = await fetchCoursePage({
      institutionId: institutionId.value,
      keyword: trimToUndefined(courseKeyword.value),
      page: coursePage.value,
      pageSize: coursePageSize.value,
    })
    courses.value = result.records
    courseTotal.value = result.total
  } catch (error) {
    courses.value = []
    courseTotal.value = 0
    ElMessage.error(toMessage(error, '课程列表加载失败'))
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
    courseForm.teachingMethod = row.teachingMethod ?? 'offline_small_class'
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
    classTypeForm.teachingMethod = row.teachingMethod ?? 'offline_small_class'
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

function loadAll() {
  return Promise.all([loadCourseCategories(), loadCourses(), loadClassTypes()])
}

watch(institutionId, async () => {
  courseKeyword.value = ''
  coursePage.value = 1
  await loadAll()
})

// 宿主(校区管理页)的刷新按钮统一广播，本页卸载时注销，避免重复挂载累积回调
const stopHostRefresh = onPageRefresh('campus-detail', loadAll)

onMounted(() => {
  void dictStore.ensureLoaded(['teaching_method']).catch(() => undefined)
  void institutionStore.loadInstitutions().then(loadAll)
})

onUnmounted(stopHostRefresh)
</script>
