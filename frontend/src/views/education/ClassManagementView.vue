<template>
  <section class="education-page">
    <div class="education-hero">
      <div>
        <div class="page-kicker">班级 · 档案 · 成员</div>
        <h2 class="panel-headline">班级主档与成员</h2>
        <p class="panel-subtitle">
          当前页面统一承接班级主档、学员关系、班级教师与家长联系人。先看课程和班型，再处理主讲、主联系人和班级变动。
        </p>
      </div>

      <div class="education-hero-actions">
        <el-button @click="refreshAll" :loading="lookupLoading || tableLoading || parentLoading">刷新数据</el-button>
        <el-button type="primary" :disabled="!institutionId || !campusId" @click="openClassCreateDialog">新增班级</el-button>
        <el-button type="primary" @click="openDialog">新增入班</el-button>
        <el-button @click="openTeacherDialog('create')">新增班级教师</el-button>
        <el-button :disabled="!contactStudentId" @click="openParentDialog('create')">新增家长联系人</el-button>
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">班级数</div>
        <div class="stat-value">{{ classes.length }}</div>
        <div class="stat-hint">当前机构 / 校区范围</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">在班学员</div>
        <div class="stat-value">{{ activeClassStudentCount }}</div>
        <div class="stat-hint">当前班级在班人数</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级教师</div>
        <div class="stat-value">{{ classTeachers.length }}</div>
        <div class="stat-hint">主讲 / 助教 / 代课关系</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">班级变动通知</div>
        <div class="stat-value">{{ classChangeNoticeItems.length }}</div>
        <div class="stat-hint">正式 class_change_notice 待处理项</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="panel-title">当前范围</div>
      <div class="education-toolbar" v-loading="lookupLoading">
        <el-select v-model="campusId" placeholder="选择校区" clearable filterable @change="handleCampusChange">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="classId" placeholder="选择班级" clearable filterable @change="loadRecords">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="studentId" placeholder="筛选学员" clearable filterable @change="handleStudentFilterChange">
          <el-option
            v-for="item in students"
            :key="item.id"
            :label="item.studentName || item.studentNo || `学员 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="education-context-text">
        新增入班、班级教师和家长联系人会使用当前机构范围。班级页会直接承接课程、班型、主讲与主联系人。
      </div>
    </article>

    <div class="dashboard-grid class-profile-grid">
      <article class="panel-card panel-card-wide">
        <div class="education-table-head">
          <div>
            <div class="panel-title">班级主档</div>
            <div class="education-context-text">
              当前班级：{{ formatClass(classId) }} / 课程：{{ formatCourse(currentClass?.courseId) }} / 班型：{{ formatClassType(currentClass?.classTypeId) }}
            </div>
          </div>
          <div class="table-actions">
            <el-button link type="primary" :disabled="!currentClass" @click="openClassProfileDialog">编辑</el-button>
            <el-button link type="danger" :disabled="!currentClass" @click="confirmDeleteClass">删除</el-button>
          </div>
        </div>

        <div v-if="!currentClass" class="education-empty">
          请先选择一个班级，查看课程、班型、容量、主档风险和变动通知。
        </div>

        <div v-else class="class-profile-summary">
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
            <span>{{ currentClass.capacityLimit ?? '-' }} / {{ currentClass.currentStudents ?? records.length }}</span>
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

      <article class="panel-card">
        <div class="education-table-head">
          <div>
            <div class="panel-title">班级风险与通知</div>
            <div class="education-context-text">本地兜底展示班级主档风险，正式后端接口展示班级变动通知。</div>
          </div>
        </div>

        <div class="notice-section">
          <div class="notice-section-head">
            <strong>班级主档风险</strong>
            <span>本地兜底，仅提示当前主档状态风险。</span>
          </div>
          <div v-if="!currentClass" class="education-empty">
            请选择班级后查看班级主档风险。
          </div>
          <div v-else-if="classProfileRiskItems.length === 0" class="education-empty">
            当前班级暂无额外主档风险。
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
            <span>正式后端 notice，来源于 `GET /api/education/class-change-notices`。</span>
          </div>
          <div v-if="!currentClass" class="education-empty">
            请选择班级后查看班级变动通知。
          </div>
          <div v-else-if="noticeError" class="education-empty">
            {{ noticeError }}
          </div>
          <div v-else-if="noticeLoading" class="education-context-text">
            正在加载班级变动通知...
          </div>
          <div v-else-if="classChangeNoticeItems.length === 0" class="education-empty">
            当前班级暂无待处理的班级变动通知。
          </div>
          <div v-else class="reminder-list">
            <div v-for="item in classChangeNoticeItems" :key="item.key" class="reminder-item is-notice">
              <strong>{{ item.title }}</strong>
              <span>{{ item.text }}</span>
              <span v-if="item.action" class="reminder-meta">建议动作：{{ item.action }}</span>
            </div>
          </div>
        </div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">班级学员关系</div>
        <div class="education-context-text">
          当前班级：{{ formatClass(classId) }}，当前学员：{{ formatStudent(studentId) }}
        </div>
      </div>

      <el-table :data="records" border v-loading="tableLoading">
        <el-table-column label="班级" min-width="180">
          <template #default="{ row }">
            {{ formatClass(row.classId) }}
          </template>
        </el-table-column>
        <el-table-column label="课程 / 班型" min-width="220">
          <template #default="{ row }">
            <div class="class-inline-meta">
              <span>{{ formatCourse(classOf(row.classId)?.courseId) }}</span>
              <span>{{ formatClassType(classOf(row.classId)?.classTypeId) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学员" min-width="180">
          <template #default="{ row }">
            {{ formatStudent(row.studentId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="classStudentTagType(row.status)">
              {{ classStudentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="joinedAt" label="入班日期" width="140" />
        <el-table-column prop="leftAt" label="离班日期" width="140" />
        <el-table-column prop="remark" label="备注" min-width="220" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openParentPanel(row.studentId)">联系人</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!tableLoading && records.length === 0" class="education-empty">
        当前筛选范围下暂无班级学员关系。
      </div>
    </article>

    <div class="dashboard-grid class-relation-grid">
      <article class="panel-card">
        <div class="education-table-head">
          <div>
            <div class="panel-title">班级教师</div>
            <div class="education-context-text">
              当前班级：{{ formatClass(classId) }}，支持主讲切换、编辑和删除。
            </div>
          </div>
          <el-button link type="primary" @click="openTeacherDialog('create')">新增教师</el-button>
        </div>

        <el-table :data="classTeachers" border v-loading="tableLoading">
          <el-table-column label="教师" min-width="180">
            <template #default="{ row }">
              {{ formatTeacher(row.teacherId) }}
            </template>
          </el-table-column>
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              {{ classTeacherRoleLabel(row.teacherRole) }}
            </template>
          </el-table-column>
          <el-table-column label="主讲" width="90">
            <template #default="{ row }">
              {{ row.isPrimary ? '是' : '否' }}
            </template>
          </el-table-column>
          <el-table-column prop="joinedAt" label="生效日期" width="130" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="classTeacherStatusTagType(row.status)">{{ classTeacherStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="warning" :disabled="row.isPrimary" @click="promotePrimaryTeacher(row)">设为主讲</el-button>
                <el-button link type="primary" @click="openTeacherDialog('edit', row)">编辑</el-button>
                <el-button link type="danger" @click="removeClassTeacher(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!tableLoading && classTeachers.length === 0" class="education-empty">
          当前班级下暂无班级教师关系。
        </div>
      </article>

      <article class="panel-card">
        <div class="education-table-head">
          <div>
            <div class="panel-title">家长联系人</div>
            <div class="education-context-text">
              当前学员：{{ formatStudent(contactStudentId) }}，支持主联系人切换、编辑和删除。
            </div>
          </div>
          <div class="table-actions">
            <el-button link type="primary" :disabled="!contactStudentId" @click="openParentDialog('create')">新增联系人</el-button>
          </div>
        </div>

        <div v-if="!contactStudentId" class="education-empty">
          请选择学员或从班级学员表点击“联系人”，再维护家长联系人。
        </div>

        <template v-else>
          <el-table :data="studentParents" border v-loading="parentLoading">
            <el-table-column prop="parentName" label="联系人" min-width="140" />
            <el-table-column label="关系" width="110">
              <template #default="{ row }">
                {{ parentRelationLabel(row.relationType) }}
              </template>
            </el-table-column>
            <el-table-column prop="parentPhone" label="联系电话" width="150" />
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
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="studentParentTagType(row.status)">{{ studentParentStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="warning" :disabled="row.primaryContact" @click="promotePrimaryParent(row)">设为主联系人</el-button>
                  <el-button link type="primary" @click="openParentDialog('edit', row)">编辑</el-button>
                  <el-button link type="danger" @click="removeStudentParent(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!parentLoading && studentParents.length === 0" class="education-empty">
            当前学员下暂无家长联系人。
          </div>
        </template>
      </article>
    </div>

    <el-dialog v-model="dialogVisible" title="新增入班关系" width="520px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>班级：</strong>{{ formatClass(classId) }}</div>
      </div>

      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <el-form-item label="学员" required>
          <el-select
            v-model="form.studentId"
            placeholder="输入姓名或学号搜索学员"
            filterable
            remote
            :remote-method="searchStudents"
            :loading="studentSearchLoading"
            @focus="onStudentSelectFocus"
            style="width: 100%"
          >
            <el-option
              v-for="item in studentSearchResults"
              :key="item.id"
              :label="`${item.studentName || ''}（${item.studentNo || ''}）`"
              :value="item.id"
            />
          </el-select>
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
        <el-button type="primary" :loading="submitting" @click="submitClassStudent">提交入班</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="teacherDialogVisible" :title="teacherDialogMode === 'create' ? '新增班级教师' : '编辑班级教师'" width="520px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>班级：</strong>{{ formatClass(classId) }}</div>
      </div>

      <el-alert v-if="teacherFormError" :title="teacherFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
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
          <el-select v-model="teacherForm.teacherRole">
            <el-option label="主讲" value="lead" />
            <el-option label="助教" value="assistant" />
            <el-option label="代课" value="substitute" />
          </el-select>
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
          <el-date-picker v-model="teacherForm.joinedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="teacherForm.leftAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="teacherForm.remark" type="textarea" :rows="3" placeholder="补充班级教师说明" />
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
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>班级：</strong>{{ formatClass(classId) }}</div>
        <div><strong>主讲：</strong>{{ primaryTeacherLabel }}（主讲切换仍在班级教师关系中维护）</div>
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
        <el-form-item label="容量上限">
          <el-input-number v-model="classProfileForm.capacityLimit" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="班级状态">
          <el-select v-model="classProfileForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
            <el-option label="已结班" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="classProfileForm.remark" type="textarea" :rows="3" placeholder="补充班级主档说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="classProfileDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classProfileSubmitting" @click="submitClassProfileUpdate">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classCreateDialogVisible" title="新增班级主档" width="560px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>校区：</strong>{{ formatCampus(campusId) }}</div>
      </div>

      <el-alert
        v-if="classCreateFormError"
        :title="classCreateFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
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
          <el-select v-model="classCreateForm.classTypeId" clearable filterable placeholder="选择班型">
            <el-option
              v-for="item in classTypes"
              :key="item.id"
              :label="item.typeName || item.typeCode || `班型 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主讲教师">
          <el-select v-model="classCreateForm.teacherId" clearable filterable placeholder="选择主讲教师">
            <el-option
              v-for="item in teachers"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="容量上限">
          <el-input-number v-model="classCreateForm.capacityLimit" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="班级状态">
          <el-select v-model="classCreateForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
            <el-option label="已结班" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="开班日期">
          <el-date-picker v-model="classCreateForm.openedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="classCreateForm.remark" type="textarea" :rows="3" placeholder="补充班级主档说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="classCreateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classCreateSubmitting" @click="submitClassCreate">新增班级</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="parentDialogVisible" :title="parentDialogMode === 'create' ? '新增家长联系人' : '编辑家长联系人'" width="560px">
      <div class="education-dialog-context">
        <div><strong>机构：</strong>{{ formatInstitution(institutionId) }}</div>
        <div><strong>学员：</strong>{{ formatStudent(contactStudentId) }}</div>
      </div>

      <el-alert
        v-if="parentFormError"
        :title="parentFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
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
          <el-input v-model="parentForm.remark" type="textarea" :rows="3" placeholder="补充联系人说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="parentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="parentSubmitting" @click="submitStudentParent">
          {{ parentDialogMode === 'create' ? '提交联系人' : '保存联系人' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  createClassStudent,
  fetchClassChangeNotices,
  createClassTeacher,
  createStudentParent,
  createTeachingClass,
  deleteClassTeacher,
  deleteStudentParent,
  deleteTeachingClass,
  fetchClassStudents,
  fetchClassTeachers,
  fetchClassTypes,
  fetchCourses,
  fetchStudentParents,
  fetchStudents,
  fetchTeachers,
  type ClassChangeNoticeRecord,
  type TeachingClassSummary,
  type TeachingClassUpdatePayload,
  updateClassTeacher,
  updateTeachingClass,
  updateStudentParent,
  type ClassStudentRecord,
  type ClassTeacherCreatePayload,
  type ClassTeacherRecord,
  type ClassTypeRecord,
  type CourseRecord,
  type StudentParentCreatePayload,
  type StudentParentRecord,
  type StudentSummary,
  type TeacherSummary,
  type TeachingClassCreatePayload,
} from '@/api/education'
import { useEducationScope } from '@/composables/useEducationScope'
import { useInstitutionStore } from '@/stores/institution'

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

const institutionStore = useInstitutionStore()
const { institutions, institutionId, campuses, classes, students, terms, loadScope } = useEducationScope()
const teachers = ref<TeacherSummary[]>([])
const courses = ref<CourseRecord[]>([])
const classTypes = ref<ClassTypeRecord[]>([])

const campusId = ref<string | null>(null)
const classId = ref<string | null>(null)
const studentId = ref<string | null>(null)
const activeParentStudentId = ref<string | null>(null)

const records = ref<ClassStudentRecord[]>([])
const classTeachers = ref<ClassTeacherRecord[]>([])
const studentParents = ref<StudentParentRecord[]>([])
const classChangeNotices = ref<ClassChangeNoticeRecord[]>([])
const pageError = ref('')
const noticeError = ref('')
const formError = ref('')
const dialogVisible = ref(false)
const teacherDialogVisible = ref(false)
const classProfileDialogVisible = ref(false)
const parentDialogVisible = ref(false)
const teacherDialogMode = ref<'create' | 'edit'>('create')
const parentDialogMode = ref<'create' | 'edit'>('create')
const editingTeacherId = ref<string | null>(null)
const editingParentId = ref<string | null>(null)
const lookupLoading = ref(false)
const tableLoading = ref(false)
const parentLoading = ref(false)
const noticeLoading = ref(false)
const submitting = ref(false)
const teacherSubmitting = ref(false)
const classProfileSubmitting = ref(false)
const parentSubmitting = ref(false)
const teacherFormError = ref('')
const classProfileFormError = ref('')
const parentFormError = ref('')
const classCreateDialogVisible = ref(false)
const classCreateSubmitting = ref(false)
const classCreateFormError = ref('')

const form = reactive({
  studentId: '' as string,
  status: 'active',
  joinedAt: todayValue(),
  leftAt: '',
  remark: '',
})

const studentSearchResults = ref<StudentSummary[]>([])
const studentSearchLoading = ref(false)
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

const teacherForm = reactive({
  teacherId: '',
  teacherRole: 'lead',
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
  capacityLimit: 0,
  status: 'planning',
  remark: '',
})

const classCreateForm = reactive({
  classCode: '',
  className: '',
  termId: '',
  courseId: '',
  classTypeId: '',
  teacherId: '',
  capacityLimit: 0,
  status: 'planning',
  openedAt: '',
  remark: '',
})

const parentForm = reactive({
  parentName: '',
  relationType: 'mother',
  parentPhone: '',
  wechatNo: '',
  isPrimary: true,
  isEmergencyContact: false,
  status: 'active',
  remark: '',
})

const institutionMap = computed(() => {
  return new Map(
    institutions.value.map((item) => [item.id, item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`]),
  )
})

const classMap = computed(() => {
  return new Map(classes.value.map((item) => [item.id, item.className || item.classCode || `班级 #${item.id}`]))
})

const campusMap = computed(() => {
  return new Map(campuses.value.map((item) => [item.id, item.campusName || item.campusCode || `校区 #${item.id}`]))
})

const studentMap = computed(() => {
  return new Map(students.value.map((item) => [item.id, item.studentName || item.studentNo || `学员 #${item.id}`]))
})

const teacherMap = computed(() => {
  return new Map(teachers.value.map((item) => [item.id, item.teacherName || item.teacherCode || `教师 #${item.id}`]))
})

const courseMap = computed(() => {
  return new Map(courses.value.map((item) => [item.id, item.courseName || item.courseCode || `课程 #${item.id}`]))
})

const classTypeMap = computed(() => {
  return new Map(classTypes.value.map((item) => [item.id, item.typeName || item.typeCode || `班型 #${item.id}`]))
})

const currentClass = computed(() => {
  return classes.value.find((item) => item.id === classId.value) || null
})

const contactStudentId = computed(() => {
  return activeParentStudentId.value || studentId.value || null
})

const activeClassStudentCount = computed(() => {
  return records.value.filter((item) => item.status === 'active' || !item.status).length
})

const primaryTeacher = computed(() => {
  return classTeachers.value.find((item) => item.isPrimary) || null
})

const primaryTeacherLabel = computed(() => {
  if (!primaryTeacher.value) {
    return '未设置主讲教师'
  }
  return formatTeacher(primaryTeacher.value.teacherId)
})

const localRiskItems = computed<ReminderItem[]>(() => {
  const items: ReminderItem[] = []

  if (currentClass.value) {
    if (!currentClass.value.courseId) {
      items.push({
        key: 'missing-course',
        title: '班级未绑定课程',
        text: '当前班级还没有绑定课程信息，后续排课和统计口径会受影响。',
        noticeType: 'missing_course',
      })
    }
    if (!currentClass.value.classTypeId) {
      items.push({
        key: 'missing-class-type',
        title: '班级未绑定班型',
        text: '请尽快补齐班型信息，便于成班规则和容量判断承接。',
        noticeType: 'missing_class_type',
      })
    }
    if (!primaryTeacher.value) {
      items.push({
        key: 'missing-primary-teacher',
        title: '班级未设置主讲教师',
        text: '建议至少指定一位主讲教师，避免排课和责任归属模糊。',
        noticeType: 'missing_primary_teacher',
      })
    }
    if (typeof currentClass.value.capacityLimit === 'number' && activeClassStudentCount.value > currentClass.value.capacityLimit) {
      items.push({
        key: 'capacity-overflow',
        title: '在班人数超过容量上限',
        text: `当前在班 ${activeClassStudentCount.value} 人，已超过容量 ${currentClass.value.capacityLimit} 人。`,
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
  const noticeTypes = new Set(classChangeNotices.value.map((item) => item.noticeType).filter(Boolean))
  return localRiskItems.value.filter((item) => !item.noticeType || !noticeTypes.has(item.noticeType))
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

function classOf(id?: string | null) {
  return classes.value.find((item) => item.id === id) || null
}

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return institutionMap.value.get(id) || `机构 #${id}`
}

function formatClass(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classMap.value.get(id) || `班级 #${id}`
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function formatStudent(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return studentMap.value.get(id) || `学员 #${id}`
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

function classTeacherRoleLabel(role?: string | null) {
  switch (role) {
    case 'assistant':
      return '助教'
    case 'substitute':
      return '代课'
    case 'lead':
    default:
      return '主讲'
  }
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
      return '已结班'
    case 'planning':
    default:
      return '规划中'
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
    default:
      return '母亲'
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

function validateContext() {
  if (!institutionId.value) {
    return '请先选择机构范围'
  }
  if (!classId.value) {
    return '请先选择班级'
  }
  if (form.leftAt && form.joinedAt && form.leftAt < form.joinedAt) {
    return '离班日期不能早于入班日期'
  }
  return ''
}

function resetForm() {
  form.studentId = studentId.value || ''
  form.status = 'active'
  form.joinedAt = todayValue()
  form.leftAt = ''
  form.remark = ''
  formError.value = ''
  studentSearchResults.value = students.value
}

async function searchStudents(query: string) {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
  }
  const trimmed = query.trim()
  if (!trimmed || !institutionId.value) {
    studentSearchResults.value = students.value
    return
  }
  searchDebounceTimer = setTimeout(async () => {
    studentSearchLoading.value = true
    try {
      studentSearchResults.value = await fetchStudents({
        institutionId: institutionId.value,
        keyword: trimmed,
      })
    } catch {
      studentSearchResults.value = []
    } finally {
      studentSearchLoading.value = false
    }
  }, 300)
}

function onStudentSelectFocus() {
  if (studentSearchResults.value.length === 0) {
    studentSearchResults.value = students.value
  }
}

function resetTeacherForm() {
  teacherForm.teacherId = ''
  teacherForm.teacherRole = 'lead'
  teacherForm.isPrimary = true
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
  classProfileForm.capacityLimit = target?.capacityLimit ?? 0
  classProfileForm.status = target?.status ?? 'planning'
  classProfileForm.remark = target?.remark ?? ''
  classProfileFormError.value = ''
}

function resetClassCreateForm() {
  classCreateForm.classCode = ''
  classCreateForm.className = ''
  classCreateForm.termId = ''
  classCreateForm.courseId = ''
  classCreateForm.classTypeId = ''
  classCreateForm.teacherId = ''
  classCreateForm.capacityLimit = 0
  classCreateForm.status = 'planning'
  classCreateForm.openedAt = ''
  classCreateForm.remark = ''
  classCreateFormError.value = ''
}

function openClassCreateDialog() {
  if (!institutionId.value) {
    ElMessage.warning('请先选择机构范围')
    return
  }
  if (!campusId.value) {
    ElMessage.warning('请先选择校区')
    return
  }

  resetClassCreateForm()
  classCreateDialogVisible.value = true
}

function buildClassCreatePayload(): TeachingClassCreatePayload {
  return {
    institutionId: institutionId.value as string,
    campusId: campusId.value as string,
    termId: classCreateForm.termId || null,
    courseId: classCreateForm.courseId || null,
    classTypeId: classCreateForm.classTypeId || null,
    classCode: classCreateForm.classCode.trim(),
    className: classCreateForm.className.trim(),
    teacherId: classCreateForm.teacherId || null,
    capacityLimit: Number.isFinite(classCreateForm.capacityLimit) ? classCreateForm.capacityLimit : 0,
    status: classCreateForm.status || 'planning',
    openedAt: classCreateForm.openedAt || null,
    remark: trimToUndefined(classCreateForm.remark),
  }
}

async function submitClassCreate() {
  if (!institutionId.value || !campusId.value) {
    classCreateFormError.value = '请先选择机构与校区范围'
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
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
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

function resetParentForm() {
  parentForm.parentName = ''
  parentForm.relationType = 'mother'
  parentForm.parentPhone = ''
  parentForm.wechatNo = ''
  parentForm.isPrimary = true
  parentForm.isEmergencyContact = false
  parentForm.status = 'active'
  parentForm.remark = ''
  parentFormError.value = ''
  editingParentId.value = null
}

function buildTeacherPayload(): ClassTeacherCreatePayload {
  return {
    institutionId: institutionId.value as string,
    classId: classId.value as string,
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
    teacherId: target.teacherId || null,
    capacityLimit: Number.isFinite(classProfileForm.capacityLimit) ? classProfileForm.capacityLimit : 0,
    currentStudents: target.currentStudents ?? activeClassStudentCount.value,
    status: classProfileForm.status,
    openedAt: target.openedAt || null,
    remark: trimToUndefined(classProfileForm.remark),
  }
}

function buildParentPayload(): StudentParentCreatePayload {
  return {
    institutionId: institutionId.value as string,
    studentId: contactStudentId.value as string,
    parentName: parentForm.parentName.trim(),
    relationType: trimToUndefined(parentForm.relationType),
    parentPhone: trimToUndefined(parentForm.parentPhone),
    wechatNo: trimToUndefined(parentForm.wechatNo),
    isPrimary: parentForm.isPrimary,
    isEmergencyContact: parentForm.isEmergencyContact,
    status: trimToUndefined(parentForm.status),
    remark: trimToUndefined(parentForm.remark),
  }
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    classes.value = []
    students.value = []
    teachers.value = []
    courses.value = []
    classTypes.value = []
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadScope(campusId.value, null)
    const [teacherList, courseList, classTypeList] = await Promise.all([
      fetchTeachers({ institutionId: institutionId.value, campusId: campusId.value }),
      fetchCourses({ institutionId: institutionId.value }),
      fetchClassTypes({ institutionId: institutionId.value }),
    ])
    teachers.value = teacherList
    courses.value = courseList
    classTypes.value = classTypeList

    if (classId.value && !classes.value.some((item) => item.id === classId.value)) {
      classId.value = null
    }
    if (studentId.value && !students.value.some((item) => item.id === studentId.value)) {
      studentId.value = null
    }
    if (activeParentStudentId.value && !students.value.some((item) => item.id === activeParentStudentId.value)) {
      activeParentStudentId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function loadParentRecords(targetStudentId?: string | null) {
  if (!institutionId.value || !targetStudentId) {
    studentParents.value = []
    return
  }

  parentLoading.value = true
  try {
    studentParents.value = await fetchStudentParents({
      institutionId: institutionId.value,
      studentId: targetStudentId,
    })
  } catch (error) {
    studentParents.value = []
    pageError.value = toMessage(error, '家长联系人加载失败')
  } finally {
    parentLoading.value = false
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

async function loadRecords() {
  if (!institutionId.value) {
    records.value = []
    classTeachers.value = []
    studentParents.value = []
    classChangeNotices.value = []
    noticeError.value = ''
    return
  }

  tableLoading.value = true
  pageError.value = ''
  try {
    const [studentRecords, teacherRecords] = await Promise.all([
      fetchClassStudents({
        classId: classId.value,
        studentId: studentId.value,
      }),
      classId.value
        ? fetchClassTeachers({
            classId: classId.value,
          })
        : Promise.resolve([]),
    ])
    records.value = studentRecords
    classTeachers.value = teacherRecords
    await Promise.all([loadParentRecords(contactStudentId.value), loadClassChangeNoticeRecords()])
  } catch (error) {
    records.value = []
    classTeachers.value = []
    studentParents.value = []
    classChangeNotices.value = []
    pageError.value = toMessage(error, '班级主档数据加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function handleCampusChange() {
  classId.value = null
  studentId.value = null
  activeParentStudentId.value = null
  await refreshScopeData()
  await loadRecords()
}

async function handleStudentFilterChange() {
  activeParentStudentId.value = studentId.value
  await loadRecords()
}

function openDialog() {
  const contextError = validateContext()
  if (contextError) {
    ElMessage.warning(contextError)
    return
  }
  resetForm()
  dialogVisible.value = true
}

function openTeacherDialog(mode: 'create' | 'edit', row?: ClassTeacherRecord) {
  if (!institutionId.value) {
    ElMessage.warning('请先选择机构范围')
    return
  }
  if (!classId.value) {
    ElMessage.warning('请先选择班级')
    return
  }

  resetTeacherForm()
  teacherDialogMode.value = mode

  if (mode === 'edit' && row) {
    editingTeacherId.value = row.id
    teacherForm.teacherId = row.teacherId || ''
    teacherForm.teacherRole = row.teacherRole || 'lead'
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

async function openParentPanel(targetStudentId?: string | null) {
  activeParentStudentId.value = targetStudentId || studentId.value || null
  await loadParentRecords(activeParentStudentId.value)
}

function openParentDialog(mode: 'create' | 'edit', row?: StudentParentRecord) {
  if (!institutionId.value) {
    ElMessage.warning('请先选择机构范围')
    return
  }
  if (!contactStudentId.value) {
    ElMessage.warning('请先选择学员或从班级学员表进入联系人管理')
    return
  }

  resetParentForm()
  parentDialogMode.value = mode

  if (mode === 'edit' && row) {
    editingParentId.value = row.id
    parentForm.parentName = row.parentName || ''
    parentForm.relationType = row.relationType || 'mother'
    parentForm.parentPhone = row.parentPhone || ''
    parentForm.wechatNo = row.wechatNo || ''
    parentForm.isPrimary = Boolean(row.primaryContact)
    parentForm.isEmergencyContact = Boolean(row.emergencyContact)
    parentForm.status = row.status || 'active'
    parentForm.remark = row.remark || ''
  }

  parentDialogVisible.value = true
}

async function submitClassStudent() {
  const contextError = validateContext()
  if (contextError) {
    formError.value = contextError
    return
  }
  if (!form.studentId) {
    formError.value = '请选择学员'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await createClassStudent({
      institutionId: institutionId.value as string,
      classId: classId.value as string,
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

async function submitClassTeacher() {
  if (!institutionId.value) {
    teacherFormError.value = '请先选择机构范围'
    return
  }
  if (!classId.value) {
    teacherFormError.value = '请先选择班级'
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
    teacherFormError.value = toMessage(error, teacherDialogMode.value === 'create' ? '新增班级教师失败' : '更新班级教师失败')
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
  if ((currentClass.value.currentStudents ?? activeClassStudentCount.value) > classProfileForm.capacityLimit) {
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
      { type: 'warning' },
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

async function submitStudentParent() {
  if (!institutionId.value) {
    parentFormError.value = '请先选择机构范围'
    return
  }
  if (!contactStudentId.value) {
    parentFormError.value = '请先选择学员'
    return
  }
  if (!trimToUndefined(parentForm.parentName)) {
    parentFormError.value = '请填写联系人姓名'
    return
  }

  parentSubmitting.value = true
  parentFormError.value = ''
  try {
    const payload = buildParentPayload()
    if (parentDialogMode.value === 'create') {
      await createStudentParent(payload)
      ElMessage.success('家长联系人已新增')
    } else if (editingParentId.value) {
      await updateStudentParent(editingParentId.value, payload)
      ElMessage.success('家长联系人已更新')
    }
    parentDialogVisible.value = false
    resetParentForm()
    await loadParentRecords(contactStudentId.value)
  } catch (error) {
    parentFormError.value = toMessage(error, parentDialogMode.value === 'create' ? '新增家长联系人失败' : '更新家长联系人失败')
  } finally {
    parentSubmitting.value = false
  }
}

async function promotePrimaryParent(row: StudentParentRecord) {
  try {
    await updateStudentParent(row.id, {
      institutionId: institutionId.value as string,
      studentId: row.studentId || (contactStudentId.value as string),
      parentName: row.parentName || '',
      relationType: trimToUndefined(row.relationType || ''),
      parentPhone: trimToUndefined(row.parentPhone || ''),
      wechatNo: trimToUndefined(row.wechatNo || ''),
      isPrimary: true,
      isEmergencyContact: Boolean(row.emergencyContact),
      status: row.status || 'active',
      remark: trimToUndefined(row.remark || ''),
    })
    ElMessage.success('已切换主联系人')
    await loadParentRecords(contactStudentId.value)
  } catch (error) {
    ElMessage.error(toMessage(error, '切换主联系人失败'))
  }
}

async function removeStudentParent(row: StudentParentRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${row.parentName || '该联系人'} 吗？`,
      '删除家长联系人',
      { type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteStudentParent(row.id)
    ElMessage.success('家长联系人已删除')
    await loadParentRecords(contactStudentId.value)
  } catch (error) {
    ElMessage.error(toMessage(error, '删除家长联系人失败'))
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
      await refreshScopeData()
    }
    await loadRecords()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
}

watch(() => institutionStore.currentInstitutionId, async () => {
  campusId.value = null
  classId.value = null
  studentId.value = null
  activeParentStudentId.value = null
  await refreshScopeData()
  await loadRecords()
})

onMounted(() => {
  initializePage()
})

onUnmounted(() => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
    searchDebounceTimer = null
  }
})
</script>

<style scoped>
.class-profile-grid,
.class-relation-grid {
  align-items: start;
}

.class-profile-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 16px;
}

.class-profile-item {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  color: #234c77;
  background: rgba(35, 76, 119, 0.08);
}

.class-profile-item strong {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #5c6b82;
}

.class-profile-item span {
  word-break: break-word;
}

.class-inline-meta {
  display: grid;
  gap: 4px;
  color: #5c6b82;
  font-size: 13px;
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
  border-top: 1px solid rgba(20, 33, 61, 0.08);
}

.notice-section-head {
  display: grid;
  gap: 4px;
}

.notice-section-head strong {
  color: #14213d;
}

.notice-section-head span {
  color: #5c6b82;
  font-size: 13px;
}

.reminder-item {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 16px;
  color: #8f5f00;
  background: rgba(249, 199, 79, 0.18);
}

.reminder-item strong {
  color: #14213d;
}

.reminder-item.is-notice {
  color: #245c45;
  background: rgba(45, 156, 108, 0.14);
}

.reminder-meta {
  color: #3a4b61;
  font-size: 13px;
}

@media (max-width: 900px) {
  .class-profile-summary {
    grid-template-columns: 1fr;
  }
}
</style>
