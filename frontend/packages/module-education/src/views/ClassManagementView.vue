<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 档案页只管"这个班是怎么搭的"：入班、退班和家长联系人在「班级名册」 -->
    <div class="table-toolbar">
      <el-button type="primary" @click="openClassCreateDialog">新增班级</el-button>
      <el-button @click="statsVisible = !statsVisible">
        统计
        <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"
          ><ArrowDown
        /></el-icon>
      </el-button>
      <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
      <el-button @click="goRoster">班级名册</el-button>
      <span class="education-context-text">
        入班、退班和家长联系人在「班级名册」；设为主讲会把原主讲降级为普通教师。
      </span>
    </div>

    <!-- 统一筛选栏:校区/班级都不再自动触发,点「查询」才加载选中班级的档案 -->
    <FilterBar :loading="lookupLoading" @query="applyFilters" @reset="resetFilters">
      <el-select v-model="campusId" placeholder="筛选校区" clearable filterable>
        <el-option
          v-for="item in campuses"
          :key="item.id"
          :label="item.campusName || item.campusCode || `校区 #${item.id}`"
          :value="item.id"
        />
      </el-select>
      <ClassSelect
        v-model="classId"
        :institution-id="institutionId"
        :campus-id="campusId"
        placeholder="筛选班级"
      />
    </FilterBar>

    <div v-show="statsVisible" class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classTotal }}</div>
        <div class="stat-hint">{{ campusId ? '按当前校区筛选' : '当前机构全部校区' }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级变动通知</div>
        <div class="stat-value">{{ classChangeNoticeItems.length }}</div>
        <div class="stat-hint">待处理的班级变动提醒</div>
      </article>
    </div>

    <!-- 未选班级时不铺三张空卡，一行提示就够，把高度留给下面的任课教师表 -->
    <div v-if="!currentClass" class="class-empty-hint">{{ classMissingText }}</div>

    <div v-else class="dashboard-grid class-profile-grid">
      <article class="panel-card class-profile-panel">
        <div class="education-table-head">
          <div class="panel-title">班级主档</div>
          <div class="table-actions">
            <el-button link type="primary" @click="openClassProfileDialog">编辑</el-button>
            <el-button link type="danger" @click="confirmDeleteClass">删除</el-button>
          </div>
        </div>

        <div class="class-profile-summary">
          <div class="class-profile-item">
            <strong>班级编码</strong>
            <span>{{ currentClass.classCode || `班级 #${currentClass.id}` }}</span>
          </div>
          <div class="class-profile-item">
            <strong>班级名称</strong>
            <span>{{ currentClass.className || `班级 #${currentClass.id}` }}</span>
          </div>
          <div class="class-profile-item">
            <strong>课程</strong>
            <span>{{ formatCourse(currentClass.courseId) }}</span>
          </div>
          <div class="class-profile-item">
            <strong>班型</strong>
            <span>{{ formatClassType(currentClass.classTypeId) }}</span>
          </div>
          <div class="class-profile-item">
            <strong>容量 / 当前人数</strong>
            <span>{{ currentClass.capacityLimit ?? '-' }} / {{ activeClassStudentCount }}</span>
          </div>
          <div class="class-profile-item">
            <strong>课时目标</strong>
            <!-- 留空=未设定，显示为空而不是 0 -->
            <span>{{ lessonTargetLabel }}</span>
          </div>
          <div class="class-profile-item">
            <strong>整班总课时</strong>
            <span>{{ currentClass.totalLessons ?? '' }}</span>
          </div>
          <div class="class-profile-item">
            <strong>主讲教师</strong>
            <span>{{ primaryTeacherLabel }}</span>
          </div>
          <div class="class-profile-item">
            <strong>班级状态</strong>
            <span>{{ teachingClassStatusLabel(currentClass.status) }}</span>
          </div>
          <div class="class-profile-item">
            <strong>备注</strong>
            <span>{{ currentClass.remark || '暂无备注' }}</span>
          </div>
        </div>
      </article>

      <article class="panel-card class-risk-panel">
        <div class="panel-title">班级风险与通知</div>

        <div class="class-risk-scroll">
          <div class="notice-section">
            <div class="notice-section-head">
              <strong>班级主档风险</strong>
              <span>未绑定课程、班型、主讲或人数超容时在此提示。</span>
            </div>
            <div v-if="classProfileRiskItems.length === 0" class="education-empty">
              当前班级暂无主档风险。
            </div>
            <div v-else class="reminder-list">
              <div v-for="item in classProfileRiskItems" :key="item.key" class="reminder-item">
                <strong>{{ item.title }}</strong>
                <span>{{ item.text }}</span>
              </div>
            </div>
          </div>

          <div class="notice-section">
            <div class="notice-section-head">
              <strong>班级变动通知</strong>
              <span>系统生成的变动提醒，处理后自动消除。</span>
            </div>
            <div v-if="noticeError" class="education-empty">{{ noticeError }}</div>
            <div v-else-if="noticeLoading" class="education-context-text">
              正在加载班级变动通知...
            </div>
            <div v-else-if="classChangeNoticeItems.length === 0" class="education-empty">
              当前班级暂无待处理的变动通知。
            </div>
            <div v-else class="reminder-list">
              <div
                v-for="item in classChangeNoticeItems"
                :key="item.key"
                class="reminder-item is-notice"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.text }}</span>
                <span v-if="item.action" class="reminder-meta">建议动作：{{ item.action }}</span>
              </div>
            </div>
          </div>
        </div>
      </article>
    </div>

    <article class="panel-card education-panel class-teacher-panel">
      <div class="education-table-head">
        <div class="panel-title">班级教师</div>
        <div v-if="currentClass" class="table-actions">
          <el-button link type="primary" @click="openTeacherDialog('create')">新增教师</el-button>
        </div>
      </div>

      <TableFillBox v-if="currentClass" v-slot="{ height }">
        <el-table :data="classTeachers" border row-key="id" highlight-current-row :height="height">
          <el-table-column label="教师" min-width="180" fixed="left">
            <template #default="{ row }">
              {{ formatTeacher(row.teacherId) }}
            </template>
          </el-table-column>
          <el-table-column label="角色" width="120" prop="teacherRole" sortable>
            <template #default="{ row }">
              {{ classTeacherRoleLabel(row.teacherRole) }}
            </template>
          </el-table-column>
          <el-table-column label="主讲" width="90" prop="isPrimary" sortable>
            <template #default="{ row }">
              {{ row.isPrimary ? '是' : '否' }}
            </template>
          </el-table-column>
          <el-table-column prop="joinedAt" label="生效日期" width="130" sortable />
          <el-table-column prop="leftAt" label="结束日期" width="130" sortable />
          <el-table-column prop="status" label="状态" width="110" sortable>
            <template #default="{ row }">
              <el-tag :type="classTeacherStatusTagType(row.status)">{{
                classTeacherStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button
                  link
                  type="warning"
                  :disabled="row.isPrimary"
                  @click="promotePrimaryTeacher(row)"
                  >设为主讲</el-button
                >
                <el-button link type="primary" @click="openTeacherDialog('edit', row)"
                  >编辑</el-button
                >
                <el-button link type="danger" @click="removeClassTeacher(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="currentClass && classTeachers.length === 0" class="education-empty">
        当前班级下暂无班级教师关系。
      </div>
    </article>

    <el-dialog
      v-model="teacherDialogVisible"
      :title="teacherDialogMode === 'create' ? '新增班级教师' : '编辑班级教师'"
      width="520px"
    >
      <el-alert
        v-if="teacherFormError"
        :title="teacherFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="所属班级" required>
          <ClassSelect
            v-model="teacherForm.classId"
            :institution-id="institutionId"
            :campus-id="campusId"
          />
        </el-form-item>
        <el-form-item label="教师">
          <el-select v-model="teacherForm.teacherId" placeholder="选择教师" filterable>
            <el-option
              v-for="item in teachers"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教师角色">
          <DictSelect
            v-model="teacherForm.teacherRole"
            type-code="teacher_role"
            placeholder="选择角色"
          />
        </el-form-item>
        <el-form-item label="设为主讲">
          <el-switch v-model="teacherForm.isPrimary" />
        </el-form-item>
        <el-form-item label="关系状态">
          <el-select v-model="teacherForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker
            v-model="teacherForm.joinedAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生效日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="teacherForm.leftAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="teacherForm.remark"
            type="textarea"
            :rows="3"
            placeholder="补充班级教师说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="teacherDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="teacherSubmitting" @click="submitClassTeacher">
          {{ teacherDialogMode === 'create' ? '提交教师关系' : '保存教师关系' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classProfileDialogVisible" title="编辑班级" width="560px">
      <div class="education-dialog-context">
        <div><strong>班级：</strong>{{ formatClass(classId) }}</div>
        <div>
          <strong>主讲：</strong
          >{{ primaryTeacherLabel }}（改主讲会同步班级教师关系，原主讲降级为普通教师）
        </div>
      </div>

      <el-alert
        v-if="classProfileFormError"
        :title="classProfileFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="班级编码" required>
          <el-input v-model="classProfileForm.classCode" placeholder="例如 2026S-MATH-A1" />
        </el-form-item>
        <el-form-item label="班级名称" required>
          <el-input v-model="classProfileForm.className" placeholder="例如 周六上午数学A1班" />
        </el-form-item>
        <el-form-item label="学期（可选）">
          <el-select v-model="classProfileForm.termId" clearable filterable placeholder="选择学期">
            <el-option
              v-for="item in terms"
              :key="item.id"
              :label="item.termName || item.termCode || `学期 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="classProfileForm.courseId" clearable placeholder="选择课程">
            <el-option
              v-for="item in courses"
              :key="item.id"
              :label="item.courseName || item.courseCode || `课程 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班型">
          <el-select v-model="classProfileForm.classTypeId" clearable placeholder="选择班型">
            <el-option
              v-for="item in classTypes"
              :key="item.id"
              :label="item.typeName || item.typeCode || `班型 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主讲教师">
          <el-select
            v-model="classProfileForm.teacherId"
            clearable
            filterable
            placeholder="选择主讲教师"
          >
            <el-option
              v-for="item in teachers"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="容量上限">
          <el-input-number
            v-model="classProfileForm.capacityLimit"
            :min="0"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <LessonTargetFields
          v-model:mode="classProfileForm.weeklyLessonsMode"
          v-model:input="classProfileForm.weeklyLessonsInput"
          v-model:total="classProfileForm.totalLessons"
        />
        <el-form-item label="班级状态">
          <el-select v-model="classProfileForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
            <el-option label="已结课" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="classProfileForm.remark"
            type="textarea"
            :rows="3"
            placeholder="补充班级主档说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="classProfileDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="classProfileSubmitting"
          @click="submitClassProfileUpdate"
          >保存修改</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="classCreateDialogVisible" title="新增班级主档" width="560px">
      <el-alert
        v-if="classCreateFormError"
        :title="classCreateFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="所属校区" required>
          <el-select v-model="classCreateForm.campusId" placeholder="选择校区" filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级编码" required>
          <el-input v-model="classCreateForm.classCode" placeholder="例如 2026S-MATH-A1" />
        </el-form-item>
        <el-form-item label="班级名称" required>
          <el-input v-model="classCreateForm.className" placeholder="例如 周六上午数学A1班" />
        </el-form-item>
        <el-form-item label="学期（可选）">
          <el-select v-model="classCreateForm.termId" clearable filterable placeholder="选择学期">
            <el-option
              v-for="item in terms"
              :key="item.id"
              :label="item.termName || item.termCode || `学期 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="classCreateForm.courseId" clearable filterable placeholder="选择课程">
            <el-option
              v-for="item in courses"
              :key="item.id"
              :label="item.courseName || item.courseCode || `课程 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班型">
          <el-select
            v-model="classCreateForm.classTypeId"
            clearable
            filterable
            placeholder="选择班型"
          >
            <el-option
              v-for="item in classTypes"
              :key="item.id"
              :label="item.typeName || item.typeCode || `班型 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主讲教师">
          <el-select
            v-model="classCreateForm.teacherId"
            clearable
            filterable
            placeholder="选择主讲教师"
          >
            <el-option
              v-for="item in teachers"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="容量上限">
          <el-input-number
            v-model="classCreateForm.capacityLimit"
            :min="0"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <LessonTargetFields
          v-model:mode="classCreateForm.weeklyLessonsMode"
          v-model:input="classCreateForm.weeklyLessonsInput"
          v-model:total="classCreateForm.totalLessons"
        />
        <el-form-item label="班级状态">
          <el-select v-model="classCreateForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
            <el-option label="已结课" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="开班日期">
          <el-date-picker
            v-model="classCreateForm.openedAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开班日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="classCreateForm.remark"
            type="textarea"
            :rows="3"
            placeholder="补充班级主档说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="classCreateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classCreateSubmitting" @click="submitClassCreate"
          >新增班级</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createClassTeacher,
  createTeachingClass,
  deleteClassTeacher,
  deleteTeachingClass,
  fetchClassChangeNotices,
  fetchClassCount,
  fetchClassStudentActiveCount,
  fetchClassTeachers,
  fetchClassTypes,
  fetchCourses,
  fetchTeachers,
  fetchTeachingClass,
  updateClassTeacher,
  updateTeachingClass,
  type ClassChangeNoticeRecord,
  type ClassTeacherCreatePayload,
  type ClassTeacherRecord,
  type ClassTypeRecord,
  type CourseRecord,
  type TeacherSummary,
  type TeachingClassCreatePayload,
  type TeachingClassSummary,
  type TeachingClassUpdatePayload,
} from '@cornerstone/module-education/api/education'
import { useEducationScope } from '@cornerstone/module-education/composables/useEducationScope'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { useDictStore } from '@cornerstone/core/stores/dict'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import {
  classLabelOf,
  rememberClassOptions,
} from '@cornerstone/module-education/composables/classLabels'
import LessonTargetFields from './components/LessonTargetFields.vue'
import ClassSelect from './components/ClassSelect.vue'

interface ReminderItem {
  key: string
  title: string
  text: string
  noticeType?: string | null
}

interface NoticeItem {
  key: string
  title: string
  text: string
  action?: string | null
}

const route = useRoute()
const router = useRouter()
const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const dictStore = useDictStore()
const { campusId } = storeToRefs(scopeStore)
const { institutionId, campuses, terms, loadScope } = useEducationScope()
const teachers = ref<TeacherSummary[]>([])
const courses = ref<CourseRecord[]>([])
const classTypes = ref<ClassTypeRecord[]>([])

const classId = ref<string | null>(null)

const statsVisible = ref(false)

// 在班人数另走 active-count 统计：这一页不再拉名册分页
const activeClassStudentCount = ref(0)
/** 统计卡的"班级数"走服务端计数：本机已没有全量班级数组 */
const classTotal = ref(0)
const classTeachers = ref<ClassTeacherRecord[]>([])
const classChangeNotices = ref<ClassChangeNoticeRecord[]>([])
const pageError = ref('')
const noticeError = ref('')
const teacherDialogVisible = ref(false)
const classProfileDialogVisible = ref(false)
const teacherDialogMode = ref<'create' | 'edit'>('create')
const editingTeacherId = ref<string | null>(null)
const lookupLoading = ref(false)
const noticeLoading = ref(false)
const teacherSubmitting = ref(false)
const classProfileSubmitting = ref(false)
const teacherFormError = ref('')
const classProfileFormError = ref('')
const classCreateDialogVisible = ref(false)
const classCreateSubmitting = ref(false)
const classCreateFormError = ref('')

const teacherForm = reactive({
  classId: '' as string | null,
  teacherId: '',
  teacherRole: '',
  isPrimary: true,
  joinedAt: todayValue(),
  leftAt: '',
  status: 'active',
  remark: '',
})

const classProfileForm = reactive({
  classCode: '',
  className: '',
  termId: '',
  courseId: '',
  classTypeId: '',
  teacherId: '' as string | null,
  capacityLimit: 0,
  // 课时目标：口径 + 原值 + 整班总课时。换算结果由后端算，表单不再存 weeklyLessons
  weeklyLessonsMode: 'weekly',
  weeklyLessonsInput: null as number | null,
  totalLessons: null as number | null,
  status: 'planning',
  remark: '',
})

const classCreateForm = reactive({
  campusId: '' as string | null,
  classCode: '',
  className: '',
  termId: '',
  courseId: '',
  classTypeId: '',
  teacherId: '',
  capacityLimit: 0,
  weeklyLessonsMode: 'weekly',
  weeklyLessonsInput: null as number | null,
  totalLessons: null as number | null,
  status: 'planning',
  openedAt: '',
  remark: '',
})

const teacherMap = computed(() => {
  return new Map(
    teachers.value.map((item) => [
      item.id,
      item.teacherName || item.teacherCode || `教师 #${item.id}`,
    ])
  )
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

/** 班级主档只展示选中的那一个班，档案按需单条取，不再把全机构班级拉回本机当字典 */
const currentClass = ref<TeachingClassSummary | null>(null)

const primaryTeacher = computed(() => {
  return classTeachers.value.find((item) => item.isPrimary) || null
})

// 与后端 ClassChangeNoticeServiceImpl#hasPrimaryTeacher 同口径：主档列与班级教师关系任一处有主讲即视为已设置
const hasPrimaryTeacher = computed(
  () => Boolean(primaryTeacher.value) || Boolean(currentClass.value?.teacherId)
)

const primaryTeacherLabel = computed(() => {
  const teacherId = primaryTeacher.value?.teacherId || currentClass.value?.teacherId
  if (!teacherId) {
    return '未设置主讲教师'
  }
  return formatTeacher(teacherId)
})

/** 班级详情里的课时目标：按哪个口径填的就按哪个口径说，换算结果括在后面 */
const lessonTargetLabel = computed(() => {
  const clazz = currentClass.value
  if (!clazz?.weeklyLessons) {
    return ''
  }
  if (clazz.weeklyLessonsMode === 'monthly' && clazz.weeklyLessonsInput) {
    return `${clazz.weeklyLessonsInput} 节/月（≈每周 ${clazz.weeklyLessons} 节）`
  }
  return `${clazz.weeklyLessons} 节/周`
})

/** 没选中班级与"选了但档案取不到"是两回事，提示要分开说 */
const classMissingText = computed(() => {
  if (!classId.value) {
    return '请先在上方选择一个班级，再看主档、风险与任课教师。'
  }
  return `班级 ${formatClass(classId.value)} 的主档暂时取不到，可能是已被删除或不在当前校区，请重新选择班级。`
})

const localRiskItems = computed<ReminderItem[]>(() => {
  const items: ReminderItem[] = []
  const clazz = currentClass.value

  if (clazz) {
    if (!clazz.courseId) {
      items.push({
        key: 'missing-course',
        title: '班级未绑定课程',
        text: '当前班级还没有绑定课程信息，后续排课和统计口径会受影响。',
        noticeType: 'missing_course',
      })
    }
    if (!clazz.classTypeId) {
      items.push({
        key: 'missing-class-type',
        title: '班级未绑定班型',
        text: '请尽快补齐班型信息，便于成班规则和容量判断承接。',
        noticeType: 'missing_class_type',
      })
    }
    if (!hasPrimaryTeacher.value) {
      items.push({
        key: 'missing-primary-teacher',
        title: '班级未设置主讲教师',
        text: '建议至少指定一位主讲教师，避免排课和责任归属模糊。',
        noticeType: 'missing_primary_teacher',
      })
    }
    if (
      typeof clazz.capacityLimit === 'number' &&
      activeClassStudentCount.value > clazz.capacityLimit
    ) {
      items.push({
        key: 'capacity-overflow',
        title: '在班人数超过容量上限',
        text: `当前在班 ${activeClassStudentCount.value} 人，已超过容量 ${clazz.capacityLimit} 人。名册页可移除多余学员。`,
      })
    }
  }

  return items
})

const classChangeNoticeItems = computed<NoticeItem[]>(() => {
  return classChangeNotices.value.map((item) => ({
    key: `${item.id}-${item.noticeType || 'notice'}`,
    title: classChangeNoticeTitle(item.noticeType),
    text: item.noticeMessage || '当前班级存在待处理的变动通知。',
    action: item.suggestedAction || null,
  }))
})

const classProfileRiskItems = computed<ReminderItem[]>(() => {
  const noticeTypes = new Set(
    classChangeNotices.value.map((item) => item.noticeType).filter(Boolean)
  )
  return localRiskItems.value.filter(
    (item) => !item.noticeType || !noticeTypes.has(item.noticeType)
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

function classChangeNoticeTitle(noticeType?: string | null) {
  switch (noticeType) {
    case 'missing_course':
      return '班级缺少课程绑定'
    case 'missing_class_type':
      return '班级缺少班型信息'
    case 'missing_primary_teacher':
      return '班级缺少主讲教师'
    case 'primary_teacher_changed':
      return '班级主讲已发生变更'
    default:
      return '班级变动通知'
  }
}

function formatClass(id?: string | null) {
  return classLabelOf(id)
}

function formatTeacher(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return teacherMap.value.get(id) || `教师 #${id}`
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

function classTeacherRoleLabel(role?: string | null) {
  return dictStore.displayLabel('teacher_role', role)
}

function classTeacherStatusLabel(status?: string | null) {
  switch (status) {
    case 'inactive':
      return '停用'
    case 'active':
    default:
      return '启用'
  }
}

function classTeacherStatusTagType(status?: string | null) {
  switch (status) {
    case 'inactive':
      return 'info'
    case 'active':
    default:
      return 'success'
  }
}

function teachingClassStatusLabel(status?: string | null) {
  switch (status) {
    case 'active':
      return '启用'
    case 'inactive':
      return '停用'
    case 'closed':
      return '已结课'
    case 'planning':
    default:
      return '规划中'
  }
}

function resetTeacherForm() {
  teacherForm.classId = classId.value || ''
  teacherForm.teacherId = ''
  teacherForm.teacherRole = ''
  // 已有主讲时默认不再勾主讲：新加的教师通常是助教，勾上会把原主讲降级
  teacherForm.isPrimary = !hasPrimaryTeacher.value
  teacherForm.joinedAt = todayValue()
  teacherForm.leftAt = ''
  teacherForm.status = 'active'
  teacherForm.remark = ''
  teacherFormError.value = ''
  editingTeacherId.value = null
}

function resetClassProfileForm(target?: TeachingClassSummary | null) {
  classProfileForm.classCode = target?.classCode ?? ''
  classProfileForm.className = target?.className ?? ''
  classProfileForm.termId = target?.termId ?? ''
  classProfileForm.courseId = target?.courseId ?? ''
  classProfileForm.classTypeId = target?.classTypeId ?? ''
  classProfileForm.teacherId = target?.teacherId ?? ''
  classProfileForm.capacityLimit = target?.capacityLimit ?? 0
  classProfileForm.weeklyLessonsMode = target?.weeklyLessonsMode ?? 'weekly'
  classProfileForm.weeklyLessonsInput = target?.weeklyLessonsInput ?? null
  classProfileForm.totalLessons = target?.totalLessons ?? null
  classProfileForm.status = target?.status ?? 'planning'
  classProfileForm.remark = target?.remark ?? ''
  classProfileFormError.value = ''
}

function resetClassCreateForm() {
  classCreateForm.campusId = campusId.value
  classCreateForm.classCode = ''
  classCreateForm.className = ''
  classCreateForm.termId = ''
  classCreateForm.courseId = ''
  classCreateForm.classTypeId = ''
  classCreateForm.teacherId = ''
  classCreateForm.capacityLimit = 0
  classCreateForm.weeklyLessonsMode = 'weekly'
  classCreateForm.weeklyLessonsInput = null
  classCreateForm.totalLessons = null
  classCreateForm.status = 'planning'
  classCreateForm.openedAt = ''
  classCreateForm.remark = ''
  classCreateFormError.value = ''
}

function openClassCreateDialog() {
  resetClassCreateForm()
  classCreateDialogVisible.value = true
}

function buildTeacherPayload(): ClassTeacherCreatePayload {
  return {
    institutionId: institutionId.value as string,
    classId: teacherForm.classId as string,
    teacherId: teacherForm.teacherId,
    teacherRole: teacherForm.teacherRole,
    isPrimary: teacherForm.isPrimary,
    joinedAt: teacherForm.joinedAt || null,
    leftAt: teacherForm.leftAt || null,
    status: trimToUndefined(teacherForm.status),
    remark: trimToUndefined(teacherForm.remark),
  }
}

function buildClassProfilePayload(target: TeachingClassSummary): TeachingClassUpdatePayload {
  return {
    institutionId: target.institutionId || (institutionId.value as string),
    campusId: target.campusId || (campusId.value as string),
    termId: classProfileForm.termId || null,
    courseId: classProfileForm.courseId || null,
    classTypeId: classProfileForm.classTypeId || null,
    classCode: classProfileForm.classCode.trim(),
    className: classProfileForm.className.trim(),
    teacherId: classProfileForm.teacherId || null,
    capacityLimit: Number.isFinite(classProfileForm.capacityLimit)
      ? classProfileForm.capacityLimit
      : 0,
    // 留空即未设定：更新时显式传 null 才能清掉旧值（weeklyLessons 由后端按口径换算）
    weeklyLessonsMode: classProfileForm.weeklyLessonsMode,
    weeklyLessonsInput: classProfileForm.weeklyLessonsInput ?? null,
    totalLessons: classProfileForm.totalLessons ?? null,
    status: classProfileForm.status,
    openedAt: target.openedAt || null,
    remark: trimToUndefined(classProfileForm.remark),
  }
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    classTotal.value = 0
    teachers.value = []
    courses.value = []
    classTypes.value = []
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadScope()
    const [teacherList, courseList, classTypeList, count] = await Promise.all([
      fetchTeachers({ institutionId: institutionId.value, campusId: campusId.value }),
      fetchCourses({ institutionId: institutionId.value }),
      fetchClassTypes({ institutionId: institutionId.value }),
      fetchClassCount({ institutionId: institutionId.value, campusId: campusId.value }),
    ])
    teachers.value = teacherList
    courses.value = courseList
    classTypes.value = classTypeList
    classTotal.value = count
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function loadClassChangeNoticeRecords() {
  if (!institutionId.value || !classId.value) {
    classChangeNotices.value = []
    noticeError.value = ''
    return
  }

  noticeLoading.value = true
  noticeError.value = ''
  try {
    classChangeNotices.value = await fetchClassChangeNotices({
      institutionId: institutionId.value,
      classId: classId.value,
      noticeStatus: 'pending',
    })
  } catch (error) {
    classChangeNotices.value = []
    noticeError.value = toMessage(error, '班级变动通知加载失败，当前仅展示本地主档风险兜底。')
  } finally {
    noticeLoading.value = false
  }
}

/** 在班人数按整班统计：这一页不拉名册，容量风险要的是全量人数 */
async function loadActiveClassStudentCount() {
  if (!institutionId.value || !classId.value) {
    activeClassStudentCount.value = 0
    return
  }
  try {
    activeClassStudentCount.value = await fetchClassStudentActiveCount(
      institutionId.value,
      classId.value
    )
  } catch (error) {
    activeClassStudentCount.value = 0
    pageError.value = toMessage(error, '班级在班人数加载失败')
  }
}

/** 班级教师只在选中班级时加载 */
async function loadClassTeachers() {
  if (!classId.value) {
    classTeachers.value = []
    return
  }
  classTeachers.value = await fetchClassTeachers({ classId: classId.value })
}

/** 选中的那个班按需取整条档案：主档卡、风险项和编辑弹窗预填都读它 */
async function loadCurrentClass() {
  if (!institutionId.value || !classId.value) {
    currentClass.value = null
    return
  }
  try {
    const detail = await fetchTeachingClass(institutionId.value, classId.value)
    currentClass.value = detail
    rememberClassOptions([detail])
  } catch (error) {
    currentClass.value = null
    pageError.value = toMessage(error, '班级主档加载失败')
  }
}

async function loadRecords() {
  if (!institutionId.value) {
    activeClassStudentCount.value = 0
    currentClass.value = null
    classTeachers.value = []
    classChangeNotices.value = []
    noticeError.value = ''
    return
  }
  pageError.value = ''
  try {
    await Promise.all([
      loadClassTeachers(),
      loadActiveClassStudentCount(),
      loadCurrentClass(),
      loadClassChangeNoticeRecords(),
    ])
  } catch (error) {
    classTeachers.value = []
    pageError.value = toMessage(error, '班级主档数据加载失败')
  }
}

/** 点「查询」才应用:不清已选班级(与旧"换校区即清空"不同,草稿要等按钮才生效) */
async function applyFilters() {
  await refreshScopeData()
  await loadRecords()
}

/** 重置 = 取消校区与班级筛选,回到未选班级的档案总览 */
async function resetFilters() {
  campusId.value = null
  classId.value = null
  await applyFilters()
}

function openTeacherDialog(mode: 'create' | 'edit', row?: ClassTeacherRecord) {
  resetTeacherForm()
  teacherDialogMode.value = mode

  if (mode === 'edit' && row) {
    editingTeacherId.value = row.id
    teacherForm.teacherId = row.teacherId || ''
    teacherForm.teacherRole = row.teacherRole || ''
    teacherForm.isPrimary = Boolean(row.isPrimary)
    teacherForm.joinedAt = row.joinedAt || todayValue()
    teacherForm.leftAt = row.leftAt || ''
    teacherForm.status = row.status || 'active'
    teacherForm.remark = row.remark || ''
  }

  teacherDialogVisible.value = true
}

function openClassProfileDialog() {
  if (!currentClass.value) {
    ElMessage.warning('请先选择班级')
    return
  }

  resetClassProfileForm(currentClass.value)
  classProfileDialogVisible.value = true
}

function buildClassCreatePayload(): TeachingClassCreatePayload {
  return {
    institutionId: institutionId.value as string,
    campusId: classCreateForm.campusId as string,
    termId: classCreateForm.termId || null,
    courseId: classCreateForm.courseId || null,
    classTypeId: classCreateForm.classTypeId || null,
    classCode: classCreateForm.classCode.trim(),
    className: classCreateForm.className.trim(),
    teacherId: classCreateForm.teacherId || null,
    capacityLimit: Number.isFinite(classCreateForm.capacityLimit)
      ? classCreateForm.capacityLimit
      : 0,
    weeklyLessonsMode: classCreateForm.weeklyLessonsMode,
    weeklyLessonsInput: classCreateForm.weeklyLessonsInput ?? null,
    totalLessons: classCreateForm.totalLessons ?? null,
    status: classCreateForm.status || 'planning',
    openedAt: classCreateForm.openedAt || null,
    remark: trimToUndefined(classCreateForm.remark),
  }
}

async function submitClassCreate() {
  if (!institutionId.value) {
    classCreateFormError.value = '请先选择机构范围'
    return
  }
  if (!classCreateForm.campusId) {
    classCreateFormError.value = '请选择所属校区'
    return
  }
  if (!classCreateForm.classCode.trim()) {
    classCreateFormError.value = '请填写班级编码'
    return
  }
  if (!classCreateForm.className.trim()) {
    classCreateFormError.value = '请填写班级名称'
    return
  }

  classCreateSubmitting.value = true
  classCreateFormError.value = ''
  try {
    const created = await createTeachingClass(buildClassCreatePayload())
    ElMessage.success('班级主档已新增')
    classCreateDialogVisible.value = false
    resetClassCreateForm()
    await refreshScopeData()
    if (created?.id) {
      classId.value = created.id
    }
    await loadRecords()
  } catch (error) {
    classCreateFormError.value = toMessage(error, '新增班级失败')
  } finally {
    classCreateSubmitting.value = false
  }
}

async function confirmDeleteClass() {
  const current = currentClass.value
  if (!current) {
    ElMessage.warning('请先选择班级')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除班级「${current.className || current.classCode || current.id}」吗？若班级仍有在班学员，删除会被拒绝。`,
      '删除班级',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteTeachingClass(current.id)
    ElMessage.success('班级已删除')
    classId.value = null
    await refreshScopeData()
    await loadRecords()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除班级失败'))
  }
}

async function submitClassTeacher() {
  if (!institutionId.value) {
    teacherFormError.value = '请先选择机构范围'
    return
  }
  if (!teacherForm.classId) {
    teacherFormError.value = '请选择所属班级'
    return
  }
  if (!teacherForm.teacherId) {
    teacherFormError.value = '请选择教师'
    return
  }

  teacherSubmitting.value = true
  teacherFormError.value = ''
  try {
    const payload = buildTeacherPayload()
    if (teacherDialogMode.value === 'create') {
      await createClassTeacher(payload)
      ElMessage.success('班级教师关系已新增')
    } else if (editingTeacherId.value) {
      await updateClassTeacher(editingTeacherId.value, payload)
      ElMessage.success('班级教师关系已更新')
    }
    teacherDialogVisible.value = false
    resetTeacherForm()
    await loadRecords()
  } catch (error) {
    teacherFormError.value = toMessage(
      error,
      teacherDialogMode.value === 'create' ? '新增班级教师失败' : '更新班级教师失败'
    )
  } finally {
    teacherSubmitting.value = false
  }
}

async function submitClassProfileUpdate() {
  if (!currentClass.value) {
    classProfileFormError.value = '请先选择班级'
    return
  }
  if (!currentClass.value.institutionId || !currentClass.value.campusId) {
    classProfileFormError.value = '当前班级缺少机构或校区信息，暂无法更新'
    return
  }
  if (!trimToUndefined(classProfileForm.classCode)) {
    classProfileFormError.value = '请填写班级编码'
    return
  }
  if (!trimToUndefined(classProfileForm.className)) {
    classProfileFormError.value = '请填写班级名称'
    return
  }
  if (activeClassStudentCount.value > classProfileForm.capacityLimit) {
    classProfileFormError.value = '容量上限不能小于当前在班人数'
    return
  }

  classProfileSubmitting.value = true
  classProfileFormError.value = ''
  try {
    await updateTeachingClass(currentClass.value.id, buildClassProfilePayload(currentClass.value))
    ElMessage.success('班级主档已更新')
    classProfileDialogVisible.value = false
    await refreshScopeData()
    await loadRecords()
  } catch (error) {
    classProfileFormError.value = toMessage(error, '班级主档更新失败')
  } finally {
    classProfileSubmitting.value = false
  }
}

async function promotePrimaryTeacher(row: ClassTeacherRecord) {
  try {
    await ElMessageBox.confirm(
      `确定将 ${formatTeacher(row.teacherId)} 设为班级主讲吗？`,
      '切换主讲',
      { type: 'warning', confirmButtonText: '设为主讲', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await updateClassTeacher(row.id, {
      institutionId: institutionId.value as string,
      classId: row.classId || (classId.value as string),
      teacherId: row.teacherId || '',
      teacherRole: 'lead',
      isPrimary: true,
      joinedAt: row.joinedAt || null,
      leftAt: row.leftAt || null,
      status: row.status || 'active',
      remark: trimToUndefined(row.remark || ''),
    })
    ElMessage.success('已切换主讲教师')
    await loadRecords()
  } catch (error) {
    ElMessage.error(toMessage(error, '切换主讲教师失败'))
  }
}

async function removeClassTeacher(row: ClassTeacherRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${formatTeacher(row.teacherId)} 的班级教师关系吗？`,
      '删除班级教师',
      { type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await deleteClassTeacher(row.id)
    ElMessage.success('班级教师关系已删除')
    await loadRecords()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除班级教师失败'))
  }
}

async function refreshAll() {
  await refreshScopeData()
  await loadRecords()
}

function goRoster() {
  // 跳转把当前班级带过去，名册页落地就停在同一个班
  router.push({
    path: '/class-roster',
    query: classId.value ? { classId: String(classId.value) } : {},
  })
}

/** 名册页跳回来时带 ?classId=，缓存中的页签不会重新挂载，所以要盯着路由参数 */
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
    if (route.name !== 'classes') {
      return
    }
    applyRouteClassId()
  }
)

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    campusId.value = null
    classId.value = null
    await refreshScopeData()
    await loadRecords()
  }
)

onMounted(async () => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore.ensureLoaded(['teacher_role']).catch(() => undefined)
  await institutionStore.loadInstitutions()
  await refreshAll()
  // 机构装载时上面那条 watch 会把班级清空，所以带 ?classId= 进来要等初始化完再落一次
  applyRouteClassId()
})
</script>

<style scoped>
/* 档案两卡封顶：主档与风险再长也不把任课教师表挤出首屏，超出部分面板内部滚。
   grid-template-rows 必须是 minmax(0,1fr)——行高默认由内容撑开，只写 max-height
   的话卡片会顶破这一格、直接压在下面的教师表上。 */
.class-profile-grid {
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  align-items: stretch;
  flex: 0 0 auto;
  max-height: 280px;
  overflow: hidden;
}

.class-profile-panel,
.class-risk-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 14px 16px;
}

.class-profile-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px 10px;
  overflow: auto;
  min-height: 0;
  align-content: start;
}

/* 字段只读不回改，标签与取值排同一行，竖排会把这张卡顶到四行以上 */
.class-profile-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  padding: 5px 10px;
  border-radius: 8px;
  color: var(--tint-ink);
  background: var(--tint-bg);
}

.class-profile-item strong {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--text-secondary);
}

.class-profile-item strong::after {
  content: '：';
}

.class-profile-item span {
  min-width: 0;
  word-break: break-word;
}

.class-risk-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 12px;
  align-content: start;
}

/* 任课教师是这一页的主表：吃掉档案卡之外的剩余高度，表体保底由 TableFillBox 兜 */
.class-teacher-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

/* 未选班级时的一行提示，替代过去的三张空卡 */
.class-empty-hint {
  flex: 0 0 auto;
  padding: 12px 16px;
  border-radius: 10px;
  color: var(--tint-ink);
  background: var(--tint-bg);
}

.reminder-list {
  display: grid;
  gap: 12px;
}

.notice-section {
  display: grid;
  gap: 12px;
}

.notice-section + .notice-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}

.notice-section-head {
  display: grid;
  gap: 4px;
}

.notice-section-head strong {
  color: var(--text-primary);
}

.notice-section-head span {
  color: var(--text-secondary);
  font-size: 13px;
}

.reminder-item {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 10px;
  color: var(--soft-warn-ink);
  background: rgba(249, 199, 79, 0.18);
}

.reminder-item strong {
  color: var(--text-primary);
}

.reminder-item.is-notice {
  color: var(--soft-success-ink);
  background: var(--soft-success-bg);
}

.reminder-meta {
  color: var(--text-strong);
  font-size: 13px;
}

@media (max-width: 1280px) {
  .class-profile-grid {
    grid-template-columns: minmax(0, 1fr);
    max-height: none;
  }
}

@media (max-width: 900px) {
  .class-profile-summary {
    grid-template-columns: 1fr;
  }
}
</style>
