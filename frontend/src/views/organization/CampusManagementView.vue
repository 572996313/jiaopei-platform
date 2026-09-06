<template>
  <section class="campus-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="page-toolbar">
      <div class="page-toolbar-filters" v-loading="lookupLoading">
        <el-select v-model="campusId" placeholder="筛选校区" clearable filterable @change="loadClassrooms">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="page-toolbar-actions">
        <el-button @click="statsVisible = !statsVisible">
          统计
          <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"><ArrowDown /></el-icon>
        </el-button>
        <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
      </div>
    </div>

    <div v-show="statsVisible" class="dashboard-grid campus-stat-grid">
      <article class="stat-card">
        <div class="stat-label">机构数量</div>
        <div class="stat-value">{{ institutionStore.institutions.length }}</div>
        <div class="stat-hint">当前机构：{{ formatInstitution(institutionId) }}</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">校区数量</div>
        <div class="stat-value">{{ campuses.length }}</div>
        <div class="stat-hint">数据来自校区主数据接口</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">教室数量</div>
        <div class="stat-value">{{ classrooms.length }}</div>
        <div class="stat-hint">当前校区：{{ formatCampus(campusId) }}</div>
      </article>
      <article class="panel-card">
        <div class="panel-title">当前迁移脚本</div>
        <div class="schema-file">{{ schemaMigration }}</div>
      </article>
    </div>

    <div class="resource-grid">
      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">校区列表<span class="panel-count">共 {{ campuses.length }} 条</span></div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openCampusDialog('create')">新增校区</el-button>
        </div>
        <el-table :data="campuses" border>
          <el-table-column prop="campusCode" label="校区编码" width="150" />
          <el-table-column prop="campusName" label="校区名称" min-width="180" />
          <el-table-column prop="campusType" label="校区类型" width="120">
            <template #default="{ row }">{{ campusTypeLabel(row.campusType) }}</template>
          </el-table-column>
          <el-table-column prop="principalName" label="负责人" width="120" />
          <el-table-column prop="phone" label="联系电话" width="150" />
          <el-table-column prop="address" label="地址" min-width="220" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ campusStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCampusDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeleteCampus(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">教室列表<span class="panel-count">共 {{ classrooms.length }} 条</span></div>
            <div class="education-context-text">当前校区：{{ formatCampus(campusId) }}</div>
          </div>
          <el-button type="primary" @click="openClassroomDialog('create')">新增教室</el-button>
        </div>
        <el-table :data="classrooms" border>
          <el-table-column prop="classroomCode" label="教室编码" width="150" />
          <el-table-column prop="classroomName" label="教室名称" min-width="180" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ roomTypeLabel(row.roomType) }}</template>
          </el-table-column>
          <el-table-column prop="floorNo" label="楼层" width="100" />
          <el-table-column prop="seatCapacity" label="容量" width="100" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ classroomStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可排课" width="100">
            <template #default="{ row }">{{ row.allowScheduling ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openClassroomDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeleteClassroom(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">学期列表<span class="panel-count">共 {{ terms.length }} 条</span></div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openTermDialog('create')">新增学期</el-button>
        </div>
        <el-table :data="terms" border>
          <el-table-column prop="termCode" label="学期编码" width="150" />
          <el-table-column prop="termName" label="学期名称" min-width="180" />
          <el-table-column prop="academicYear" label="学年" width="120" />
          <el-table-column label="学季" width="120">
            <template #default="{ row }">{{ seasonLabel(row.season) }}</template>
          </el-table-column>
          <el-table-column label="起止日期" min-width="220">
            <template #default="{ row }">{{ row.startDate || '-' }} 至 {{ row.endDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ termStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTermDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeleteTerm(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">时间段列表<span class="panel-count">共 {{ timeSlots.length }} 条</span></div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openTimeSlotDialog('create')">新增时间段</el-button>
        </div>
        <el-table :data="timeSlots" border>
          <el-table-column prop="slotCode" label="时间段编码" width="150" />
          <el-table-column prop="slotName" label="时间段名称" min-width="180" />
          <el-table-column label="星期" width="90">
            <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="180">
            <template #default="{ row }">{{ row.startTime || '-' }} - {{ row.endTime || '-' }}</template>
          </el-table-column>
          <el-table-column prop="durationMinutes" label="时长" width="100" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ slotTypeLabel(row.slotType) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ timeSlotStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTimeSlotDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeleteTimeSlot(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">课程分类<span class="panel-count">共 {{ courseCategories.length }} 条</span></div>
            <div class="education-context-text">当前机构：{{ formatInstitution(institutionId) }}</div>
          </div>
          <el-button type="primary" @click="openCourseCategoryDialog('create')">新增分类</el-button>
        </div>
        <el-table :data="courseCategories" border>
          <el-table-column prop="categoryCode" label="分类编码" width="150" />
          <el-table-column prop="categoryName" label="分类名称" min-width="180" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ enabledStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="100" />
          <el-table-column prop="remark" label="备注" min-width="180" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCourseCategoryDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeleteCourseCategory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">课程列表<span class="panel-count">共 {{ courses.length }} 条</span></div>
            <div class="education-context-text">课程作为排课与班级主档的前置档案</div>
          </div>
          <el-button type="primary" @click="openCourseDialog('create')">新增课程</el-button>
        </div>
        <el-table :data="courses" border>
          <el-table-column prop="courseCode" label="课程编码" width="150" />
          <el-table-column prop="courseName" label="课程名称" min-width="180" />
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
      </article>

      <article class="panel-card resource-card">
        <div class="resource-head">
          <div>
            <div class="panel-title">班型列表<span class="panel-count">共 {{ classTypes.length }} 条</span></div>
            <div class="education-context-text">后续用于成班规则和班级主档承接</div>
          </div>
          <el-button type="primary" @click="openClassTypeDialog('create')">新增班型</el-button>
        </div>
        <el-table :data="classTypes" border>
          <el-table-column prop="typeCode" label="班型编码" width="150" />
          <el-table-column prop="typeName" label="班型名称" min-width="180" />
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
    </div>

    <article class="panel-card campus-panel">
      <div class="panel-title">组织模块元数据</div>
      <div class="schema-grid">
        <div class="schema-block">
          <div class="schema-label">聚合根</div>
          <div class="schema-chips">
            <span v-for="item in schemaAggregates" :key="item" class="schema-chip">{{ item }}</span>
          </div>
        </div>
        <div class="schema-block">
          <div class="schema-label">表结构</div>
          <div class="schema-chips">
            <span v-for="item in schemaTables" :key="item" class="schema-chip is-secondary">{{ item }}</span>
          </div>
        </div>
        <div class="schema-block">
          <div class="schema-label">下一步入口</div>
          <div class="schema-chips">
            <span class="schema-chip is-secondary">班级教师</span>
            <span class="schema-chip is-secondary">家长联系人</span>
            <span class="schema-chip is-secondary">成班规则</span>
            <span class="schema-chip is-secondary">变动提示</span>
          </div>
        </div>
      </div>
    </article>

    <el-dialog v-model="campusDialogVisible" :title="campusDialogMode === 'create' ? '新增校区' : '编辑校区'" width="560px">
      <el-alert v-if="campusFormError" :title="campusFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="校区编码"><el-input v-model="campusForm.campusCode" /></el-form-item>
        <el-form-item label="校区名称"><el-input v-model="campusForm.campusName" /></el-form-item>
        <el-form-item label="校区类型">
          <el-select v-model="campusForm.campusType">
            <el-option label="直营" value="direct" />
            <el-option label="加盟" value="franchise" />
            <el-option label="合作" value="partner" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="campusForm.principalName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="campusForm.phone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="campusForm.address" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="容量上限"><el-input-number v-model="campusForm.capacityLimit" :min="0" :step="10" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="campusForm.status">
            <el-option label="规划中" value="planning" />
            <el-option label="启用中" value="active" />
            <el-option label="暂停中" value="suspended" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="campusSubmitting" @click="submitCampus">{{ campusDialogMode === 'create' ? '提交校区' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classroomDialogVisible" :title="classroomDialogMode === 'create' ? '新增教室' : '编辑教室'" width="560px">
      <el-alert v-if="classroomFormError" :title="classroomFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="归属校区">
          <el-select v-model="classroomForm.campusId" placeholder="选择校区" filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教室编码"><el-input v-model="classroomForm.classroomCode" /></el-form-item>
        <el-form-item label="教室名称"><el-input v-model="classroomForm.classroomName" /></el-form-item>
        <el-form-item label="教室类型"><el-input v-model="classroomForm.roomType" placeholder="例如 normal / piano / lab" /></el-form-item>
        <el-form-item label="楼层"><el-input v-model="classroomForm.floorNo" /></el-form-item>
        <el-form-item label="座位数"><el-input-number v-model="classroomForm.seatCapacity" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classroomForm.status">
            <el-option label="空闲" value="idle" />
            <el-option label="占用" value="occupied" />
            <el-option label="维护" value="maintenance" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classroomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classroomSubmitting" @click="submitClassroom">{{ classroomDialogMode === 'create' ? '提交教室' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="termDialogVisible" :title="termDialogMode === 'create' ? '新增学期' : '编辑学期'" width="560px">
      <el-alert v-if="termFormError" :title="termFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="学期编码"><el-input v-model="termForm.termCode" /></el-form-item>
        <el-form-item label="学期名称"><el-input v-model="termForm.termName" /></el-form-item>
        <el-form-item label="学年"><el-input v-model="termForm.academicYear" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学季"><el-input v-model="termForm.season" placeholder="例如 summer / autumn" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="termForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="termForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="termForm.status">
            <el-option label="草稿" value="draft" />
            <el-option label="启用" value="active" />
            <el-option label="归档" value="archived" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="termDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="termSubmitting" @click="submitTerm">{{ termDialogMode === 'create' ? '提交学期' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="timeSlotDialogVisible" :title="timeSlotDialogMode === 'create' ? '新增时间段' : '编辑时间段'" width="560px">
      <el-alert v-if="timeSlotFormError" :title="timeSlotFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="适用校区">
          <el-select v-model="timeSlotForm.campusId" placeholder="选择校区" clearable filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段编码"><el-input v-model="timeSlotForm.slotCode" /></el-form-item>
        <el-form-item label="时间段名称"><el-input v-model="timeSlotForm.slotName" /></el-form-item>
        <el-form-item label="星期">
          <el-select v-model="timeSlotForm.weekday">
            <el-option v-for="day in weekdays" :key="day.value" :label="day.label" :value="day.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间"><el-time-picker v-model="timeSlotForm.startTime" value-format="HH:mm:ss" placeholder="选择开始时间" style="width: 100%" /></el-form-item>
        <el-form-item label="结束时间"><el-time-picker v-model="timeSlotForm.endTime" value-format="HH:mm:ss" placeholder="选择结束时间" style="width: 100%" /></el-form-item>
        <el-form-item label="时长（分钟）">
          <el-input-number v-model="timeSlotForm.durationMinutes" :min="1" disabled style="width: 100%" />
          <div class="education-context-text">由开始和结束时间自动计算</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="timeSlotForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeSlotDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="timeSlotSubmitting" @click="submitTimeSlot">{{ timeSlotDialogMode === 'create' ? '提交时间段' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="courseCategoryDialogVisible" :title="courseCategoryDialogMode === 'create' ? '新增课程分类' : '编辑课程分类'" width="560px">
      <el-alert v-if="courseCategoryFormError" :title="courseCategoryFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="分类编码"><el-input v-model="courseCategoryForm.categoryCode" /></el-form-item>
        <el-form-item label="分类名称"><el-input v-model="courseCategoryForm.categoryName" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="courseCategoryForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="courseCategoryForm.sortOrder" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="courseCategoryForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseCategoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="courseCategorySubmitting" @click="submitCourseCategory">{{ courseCategoryDialogMode === 'create' ? '提交分类' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="courseDialogVisible" :title="courseDialogMode === 'create' ? '新增课程' : '编辑课程'" width="560px">
      <el-alert v-if="courseFormError" :title="courseFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="课程分类">
          <el-select v-model="courseForm.categoryId" clearable filterable>
            <el-option v-for="item in courseCategories" :key="item.id" :label="item.categoryName || item.categoryCode || `分类 #${item.id}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程编码"><el-input v-model="courseForm.courseCode" /></el-form-item>
        <el-form-item label="课程名称"><el-input v-model="courseForm.courseName" /></el-form-item>
        <el-form-item label="学科"><el-input v-model="courseForm.subjectName" /></el-form-item>
        <el-form-item label="授课方式"><el-input v-model="courseForm.teachingMethod" placeholder="例如 offline_small_class" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="courseForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="courseForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="courseSubmitting" @click="submitCourse">{{ courseDialogMode === 'create' ? '提交课程' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classTypeDialogVisible" :title="classTypeDialogMode === 'create' ? '新增班型' : '编辑班型'" width="560px">
      <el-alert v-if="classTypeFormError" :title="classTypeFormError" type="error" show-icon :closable="false" class="education-alert" />
      <el-form label-position="top" class="education-form">
        <el-form-item label="班型编码"><el-input v-model="classTypeForm.typeCode" /></el-form-item>
        <el-form-item label="班型名称"><el-input v-model="classTypeForm.typeName" /></el-form-item>
        <el-form-item label="授课方式"><el-input v-model="classTypeForm.teachingMethod" placeholder="例如 offline_small_class" /></el-form-item>
        <el-form-item label="最少人数"><el-input-number v-model="classTypeForm.minStudents" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="最多人数"><el-input-number v-model="classTypeForm.maxStudents" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classTypeForm.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="classTypeForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classTypeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classTypeSubmitting" @click="submitClassType">{{ classTypeDialogMode === 'create' ? '提交班型' : '保存修改' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createClassType,
  createCourse,
  createCourseCategory,
  deleteClassType,
  deleteCourse,
  deleteCourseCategory,
  fetchClassTypes,
  fetchCourses,
  fetchCourseCategories,
  updateClassType,
  updateCourse,
  updateCourseCategory,
  type ClassTypeRecord,
  type CourseCategoryRecord,
  type CourseRecord,
} from '@/api/education'
import {
  createCampus,
  createClassroom,
  createTerm,
  createTimeSlot,
  deleteCampus,
  deleteClassroom,
  deleteTerm,
  deleteTimeSlot,
  fetchCampuses,
  fetchClassrooms,
  fetchTerms,
  fetchTimeSlots,
  getOrganizationSchema,
  updateCampus,
  updateClassroom,
  updateTerm,
  updateTimeSlot,
  type CampusSummary,
  type ClassroomSummary,
  type TermSummary,
  type TimeSlotSummary,
} from '@/api/organization'
import { useEducationScopeStore } from '@/stores/educationScope'
import { useInstitutionStore } from '@/stores/institution'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId } = storeToRefs(scopeStore)
const institutionId = computed(() => institutionStore.currentInstitutionId)
const campuses = ref<CampusSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const terms = ref<TermSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])
const courseCategories = ref<CourseCategoryRecord[]>([])
const courses = ref<CourseRecord[]>([])
const classTypes = ref<ClassTypeRecord[]>([])

const statsVisible = ref(false)

const pageError = ref('')
const lookupLoading = ref(false)
const schemaTables = ref<string[]>([])
const schemaAggregates = ref<string[]>([])
const schemaMigration = ref('V2__create_organization_core_tables.sql')

const campusDialogVisible = ref(false)
const classroomDialogVisible = ref(false)
const termDialogVisible = ref(false)
const timeSlotDialogVisible = ref(false)
const courseCategoryDialogVisible = ref(false)
const courseDialogVisible = ref(false)
const classTypeDialogVisible = ref(false)

const campusDialogMode = ref<'create' | 'edit'>('create')
const classroomDialogMode = ref<'create' | 'edit'>('create')
const termDialogMode = ref<'create' | 'edit'>('create')
const timeSlotDialogMode = ref<'create' | 'edit'>('create')
const courseCategoryDialogMode = ref<'create' | 'edit'>('create')
const courseDialogMode = ref<'create' | 'edit'>('create')
const classTypeDialogMode = ref<'create' | 'edit'>('create')

const editingCampusId = ref<string | null>(null)
const editingClassroomId = ref<string | null>(null)
const editingTermId = ref<string | null>(null)
const editingTimeSlotId = ref<string | null>(null)
const editingCourseCategoryId = ref<string | null>(null)
const editingCourseId = ref<string | null>(null)
const editingClassTypeId = ref<string | null>(null)

const campusSubmitting = ref(false)
const classroomSubmitting = ref(false)
const termSubmitting = ref(false)
const timeSlotSubmitting = ref(false)
const courseCategorySubmitting = ref(false)
const courseSubmitting = ref(false)
const classTypeSubmitting = ref(false)

const campusFormError = ref('')
const classroomFormError = ref('')
const termFormError = ref('')
const timeSlotFormError = ref('')
const courseCategoryFormError = ref('')
const courseFormError = ref('')
const classTypeFormError = ref('')

const campusForm = reactive({
  campusCode: '',
  campusName: '',
  campusType: 'direct',
  principalName: '',
  phone: '',
  address: '',
  capacityLimit: 100,
  status: 'planning',
})

const classroomForm = reactive({
  campusId: '' as string | null,
  classroomCode: '',
  classroomName: '',
  roomType: 'normal',
  floorNo: '',
  seatCapacity: 20,
  status: 'idle',
})

const termForm = reactive({
  termCode: '',
  termName: '',
  academicYear: '2026-2027',
  season: 'autumn',
  startDate: '',
  endDate: '',
  status: 'draft',
})

const timeSlotForm = reactive({
  campusId: '' as string | null,
  slotCode: '',
  slotName: '',
  weekday: 1,
  startTime: '08:00:00',
  endTime: '09:30:00',
  durationMinutes: 90,
  status: 'active',
})

function timeSlotMinutes(value?: string | null) {
  if (!value) {
    return Number.NaN
  }
  const [hours, minutes] = value.split(':').map(Number)
  return (hours || 0) * 60 + (minutes || 0)
}

// 后端要求 durationMinutes 与起止时间差值完全一致，这里随时间选择自动重算
watch(
  () => [timeSlotForm.startTime, timeSlotForm.endTime] as const,
  ([start, end]) => {
    const diff = timeSlotMinutes(end) - timeSlotMinutes(start)
    if (Number.isFinite(diff) && diff > 0) {
      timeSlotForm.durationMinutes = diff
    }
  },
)

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

const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
]

const campusMap = computed(() => {
  return new Map(campuses.value.map((item) => [item.id, item.campusName || item.campusCode || `校区 #${item.id}`]))
})

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

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function campusTypeLabel(value?: string | null) {
  switch (value) {
    case 'franchise':
      return '加盟'
    case 'partner':
      return '合作'
    case 'direct':
    default:
      return '直营'
  }
}

function campusStatusLabel(value?: string | null) {
  switch (value) {
    case 'active':
      return '启用中'
    case 'suspended':
      return '暂停中'
    case 'planning':
    default:
      return '规划中'
  }
}

function classroomStatusLabel(value?: string | null) {
  switch (value) {
    case 'occupied':
      return '占用'
    case 'maintenance':
      return '维护'
    case 'idle':
    default:
      return '空闲'
  }
}

function termStatusLabel(value?: string | null) {
  switch (value) {
    case 'active':
      return '启用'
    case 'archived':
      return '归档'
    case 'draft':
    default:
      return '草稿'
  }
}

function timeSlotStatusLabel(value?: string | null) {
  switch (value) {
    case 'inactive':
      return '停用'
    case 'active':
    default:
      return '启用'
  }
}

function statusTagType(value?: string | null) {
  switch (value) {
    case 'active':
      return 'success'
    case 'suspended':
    case 'maintenance':
      return 'warning'
    case 'archived':
    case 'inactive':
      return 'info'
    default:
      return ''
  }
}

function weekdayLabel(value?: number | null) {
  return weekdays.find((item) => item.value === value)?.label || '-'
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

function roomTypeLabel(value?: string | null) {
  switch (value) {
    case 'normal':
      return '标准教室'
    case 'piano':
      return '琴房'
    case 'lab':
      return '实验室'
    case 'dance':
      return '舞蹈教室'
    case 'art':
      return '美术教室'
    default:
      return value || '-'
  }
}

function seasonLabel(value?: string | null) {
  switch (value) {
    case 'spring':
      return '春季'
    case 'summer':
      return '夏季'
    case 'autumn':
      return '秋季'
    case 'winter':
      return '冬季'
    default:
      return value || '-'
  }
}

function slotTypeLabel(value?: string | null) {
  switch (value) {
    case 'teaching':
      return '授课'
    case 'break':
      return '休息'
    default:
      return value || '-'
  }
}

function teachingMethodLabel(value?: string | null) {
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

function resetCampusForm() {
  campusForm.campusCode = ''
  campusForm.campusName = ''
  campusForm.campusType = 'direct'
  campusForm.principalName = ''
  campusForm.phone = ''
  campusForm.address = ''
  campusForm.capacityLimit = 100
  campusForm.status = 'planning'
  campusFormError.value = ''
}

function resetClassroomForm() {
  classroomForm.campusId = campusId.value
  classroomForm.classroomCode = ''
  classroomForm.classroomName = ''
  classroomForm.roomType = 'normal'
  classroomForm.floorNo = ''
  classroomForm.seatCapacity = 20
  classroomForm.status = 'idle'
  classroomFormError.value = ''
}

function resetTermForm() {
  termForm.termCode = ''
  termForm.termName = ''
  termForm.academicYear = '2026-2027'
  termForm.season = 'autumn'
  termForm.startDate = ''
  termForm.endDate = ''
  termForm.status = 'draft'
  termFormError.value = ''
}

function resetTimeSlotForm() {
  timeSlotForm.campusId = campusId.value
  timeSlotForm.slotCode = ''
  timeSlotForm.slotName = ''
  timeSlotForm.weekday = 1
  timeSlotForm.startTime = '08:00:00'
  timeSlotForm.endTime = '09:30:00'
  timeSlotForm.durationMinutes = 90
  timeSlotForm.status = 'active'
  timeSlotFormError.value = ''
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

async function reloadSchema() {
  try {
    const { data } = await getOrganizationSchema()
    schemaTables.value = data.data.tables
    schemaAggregates.value = data.data.aggregates
    schemaMigration.value = data.data.migration
  } catch {
    schemaTables.value = ['jp_institution', 'jp_campus', 'jp_classroom', 'jp_term', 'jp_time_slot']
    schemaAggregates.value = ['institution', 'campus', 'classroom', 'term', 'timeSlot']
  }
}

async function loadClassrooms() {
  if (!campusId.value) {
    classrooms.value = []
    return
  }

  try {
    classrooms.value = await fetchClassrooms(campusId.value)
  } catch (error) {
    classrooms.value = []
    pageError.value = toMessage(error, '教室列表加载失败')
  }
}

async function loadResources() {
  if (!institutionId.value) {
    campuses.value = []
    classrooms.value = []
    terms.value = []
    timeSlots.value = []
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    const [campusList, termList, timeSlotList, categoryList, courseList, classTypeList] = await Promise.all([
      fetchCampuses(institutionId.value),
      fetchTerms(institutionId.value),
      fetchTimeSlots(institutionId.value),
      fetchCourseCategories({ institutionId: institutionId.value }),
      fetchCourses({ institutionId: institutionId.value }),
      fetchClassTypes({ institutionId: institutionId.value }),
    ])

    campuses.value = campusList
    terms.value = termList
    timeSlots.value = timeSlotList
    courseCategories.value = categoryList
    courses.value = courseList
    classTypes.value = classTypeList

    if (!campusId.value || !campusList.some((item) => item.id === campusId.value)) {
      campusId.value = campusList[0]?.id || null
    }

    if (campusId.value) {
      classrooms.value = await fetchClassrooms(campusId.value)
    } else {
      classrooms.value = []
    }
  } catch (error) {
    pageError.value = toMessage(error, '组织资源加载失败')
  } finally {
    lookupLoading.value = false
  }
}

function openCampusDialog(mode: 'create' | 'edit', row?: CampusSummary) {
  campusDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingCampusId.value = row.id
    campusForm.campusCode = row.campusCode ?? ''
    campusForm.campusName = row.campusName ?? ''
    campusForm.campusType = row.campusType ?? 'direct'
    campusForm.principalName = row.principalName ?? ''
    campusForm.phone = row.phone ?? ''
    campusForm.address = row.address ?? ''
    campusForm.capacityLimit = row.capacityLimit ?? 100
    campusForm.status = row.status ?? 'planning'
  } else {
    editingCampusId.value = null
    resetCampusForm()
  }
  campusFormError.value = ''
  campusDialogVisible.value = true
}

function openClassroomDialog(mode: 'create' | 'edit', row?: ClassroomSummary) {
  classroomDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingClassroomId.value = row.id
    classroomForm.campusId = row.campusId ?? campusId.value
    classroomForm.classroomCode = row.classroomCode ?? ''
    classroomForm.classroomName = row.classroomName ?? ''
    classroomForm.roomType = row.roomType ?? 'normal'
    classroomForm.floorNo = row.floorNo ?? ''
    classroomForm.seatCapacity = row.seatCapacity ?? 20
    classroomForm.status = row.status ?? 'idle'
  } else {
    editingClassroomId.value = null
    resetClassroomForm()
  }
  classroomFormError.value = ''
  classroomDialogVisible.value = true
}

function openTermDialog(mode: 'create' | 'edit', row?: TermSummary) {
  termDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingTermId.value = row.id
    termForm.termCode = row.termCode ?? ''
    termForm.termName = row.termName ?? ''
    termForm.academicYear = row.academicYear ?? ''
    termForm.season = row.season ?? 'autumn'
    termForm.startDate = row.startDate ?? ''
    termForm.endDate = row.endDate ?? ''
    termForm.status = row.status ?? 'draft'
  } else {
    editingTermId.value = null
    resetTermForm()
  }
  termFormError.value = ''
  termDialogVisible.value = true
}

function openTimeSlotDialog(mode: 'create' | 'edit', row?: TimeSlotSummary) {
  timeSlotDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingTimeSlotId.value = row.id
    timeSlotForm.campusId = row.campusId ?? null
    timeSlotForm.slotCode = row.slotCode ?? ''
    timeSlotForm.slotName = row.slotName ?? ''
    timeSlotForm.weekday = row.weekday ?? 1
    timeSlotForm.startTime = row.startTime ?? '08:00:00'
    timeSlotForm.endTime = row.endTime ?? '09:30:00'
    timeSlotForm.durationMinutes = row.durationMinutes ?? 90
    timeSlotForm.status = row.status ?? 'active'
  } else {
    editingTimeSlotId.value = null
    resetTimeSlotForm()
  }
  timeSlotFormError.value = ''
  timeSlotDialogVisible.value = true
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

async function submitCampus() {
  if (!institutionId.value) {
    campusFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(campusForm.campusCode) || !trimToUndefined(campusForm.campusName) || !trimToUndefined(campusForm.address)) {
    campusFormError.value = '请完整填写校区编码、名称和地址'
    return
  }

  campusSubmitting.value = true
  campusFormError.value = ''
  try {
    if (campusDialogMode.value === 'create') {
      await createCampus({
        institutionId: institutionId.value,
        campusCode: campusForm.campusCode.trim(),
        campusName: campusForm.campusName.trim(),
        campusType: campusForm.campusType,
        principalName: trimToUndefined(campusForm.principalName),
        phone: trimToUndefined(campusForm.phone),
        address: campusForm.address.trim(),
        capacityLimit: campusForm.capacityLimit,
        status: campusForm.status,
      })
      ElMessage.success('校区已新增')
    } else if (editingCampusId.value) {
      await updateCampus(editingCampusId.value, {
        campusCode: campusForm.campusCode.trim(),
        campusName: campusForm.campusName.trim(),
        campusType: campusForm.campusType,
        principalName: trimToUndefined(campusForm.principalName),
        phone: trimToUndefined(campusForm.phone),
        address: campusForm.address.trim(),
        capacityLimit: campusForm.capacityLimit,
        status: campusForm.status,
      })
      ElMessage.success('校区已更新')
    }
    campusDialogVisible.value = false
    await loadResources()
  } catch (error) {
    campusFormError.value = toMessage(error, campusDialogMode.value === 'create' ? '新增校区失败' : '更新校区失败')
  } finally {
    campusSubmitting.value = false
  }
}

async function confirmDeleteCampus(row: CampusSummary) {
  try {
    await ElMessageBox.confirm(`确定删除校区「${row.campusName || row.campusCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteCampus(row.id)
    ElMessage.success('校区已删除')
    if (campusId.value === row.id) {
      campusId.value = null
    }
    await loadResources()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除校区失败'))
  }
}

async function submitClassroom() {
  if (!classroomForm.campusId) {
    classroomFormError.value = '请选择归属校区'
    return
  }
  if (!trimToUndefined(classroomForm.classroomCode) || !trimToUndefined(classroomForm.classroomName)) {
    classroomFormError.value = '请完整填写教室编码和名称'
    return
  }

  classroomSubmitting.value = true
  classroomFormError.value = ''
  try {
    if (classroomDialogMode.value === 'create') {
      await createClassroom({
        campusId: classroomForm.campusId,
        classroomCode: classroomForm.classroomCode.trim(),
        classroomName: classroomForm.classroomName.trim(),
        roomType: trimToUndefined(classroomForm.roomType),
        floorNo: trimToUndefined(classroomForm.floorNo),
        seatCapacity: classroomForm.seatCapacity,
        status: classroomForm.status,
      })
      ElMessage.success('教室已新增')
    } else if (editingClassroomId.value) {
      await updateClassroom(editingClassroomId.value, {
        campusId: classroomForm.campusId,
        classroomCode: classroomForm.classroomCode.trim(),
        classroomName: classroomForm.classroomName.trim(),
        roomType: trimToUndefined(classroomForm.roomType),
        floorNo: trimToUndefined(classroomForm.floorNo),
        seatCapacity: classroomForm.seatCapacity,
        status: classroomForm.status,
      })
      ElMessage.success('教室已更新')
    }
    classroomDialogVisible.value = false
    if (campusId.value !== classroomForm.campusId) {
      campusId.value = classroomForm.campusId
    }
    await loadClassrooms()
  } catch (error) {
    classroomFormError.value = toMessage(error, classroomDialogMode.value === 'create' ? '新增教室失败' : '更新教室失败')
  } finally {
    classroomSubmitting.value = false
  }
}

async function confirmDeleteClassroom(row: ClassroomSummary) {
  try {
    await ElMessageBox.confirm(`确定删除教室「${row.classroomName || row.classroomCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteClassroom(row.id)
    ElMessage.success('教室已删除')
    await loadClassrooms()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除教室失败'))
  }
}

async function submitTerm() {
  if (!institutionId.value) {
    termFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(termForm.termCode) || !trimToUndefined(termForm.termName) || !trimToUndefined(termForm.academicYear)) {
    termFormError.value = '请完整填写学期编码、名称和学年'
    return
  }
  if (!termForm.startDate || !termForm.endDate) {
    termFormError.value = '请选择学期开始和结束日期'
    return
  }
  if (termForm.startDate > termForm.endDate) {
    termFormError.value = '开始日期不能晚于结束日期'
    return
  }

  termSubmitting.value = true
  termFormError.value = ''
  try {
    if (termDialogMode.value === 'create') {
      await createTerm({
        institutionId: institutionId.value,
        termCode: termForm.termCode.trim(),
        termName: termForm.termName.trim(),
        academicYear: termForm.academicYear.trim(),
        season: trimToUndefined(termForm.season),
        startDate: termForm.startDate,
        endDate: termForm.endDate,
        status: termForm.status,
      })
      ElMessage.success('学期已新增')
    } else if (editingTermId.value) {
      await updateTerm(editingTermId.value, {
        termCode: termForm.termCode.trim(),
        termName: termForm.termName.trim(),
        academicYear: termForm.academicYear.trim(),
        season: trimToUndefined(termForm.season),
        startDate: termForm.startDate,
        endDate: termForm.endDate,
        status: termForm.status,
      })
      ElMessage.success('学期已更新')
    }
    termDialogVisible.value = false
    terms.value = await fetchTerms(institutionId.value)
  } catch (error) {
    termFormError.value = toMessage(error, termDialogMode.value === 'create' ? '新增学期失败' : '更新学期失败')
  } finally {
    termSubmitting.value = false
  }
}

async function confirmDeleteTerm(row: TermSummary) {
  try {
    await ElMessageBox.confirm(`确定删除学期「${row.termName || row.termCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteTerm(row.id)
    ElMessage.success('学期已删除')
    terms.value = await fetchTerms(institutionId.value)
  } catch (error) {
    ElMessage.error(toMessage(error, '删除学期失败'))
  }
}

async function submitTimeSlot() {
  if (!institutionId.value) {
    timeSlotFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(timeSlotForm.slotCode) || !trimToUndefined(timeSlotForm.slotName)) {
    timeSlotFormError.value = '请完整填写时间段编码和名称'
    return
  }
  if (!timeSlotForm.startTime || !timeSlotForm.endTime) {
    timeSlotFormError.value = '请选择开始和结束时间'
    return
  }
  if (timeSlotMinutes(timeSlotForm.endTime) <= timeSlotMinutes(timeSlotForm.startTime)) {
    timeSlotFormError.value = '结束时间必须晚于开始时间'
    return
  }

  timeSlotSubmitting.value = true
  timeSlotFormError.value = ''
  try {
    if (timeSlotDialogMode.value === 'create') {
      await createTimeSlot({
        institutionId: institutionId.value,
        campusId: timeSlotForm.campusId || null,
        slotCode: timeSlotForm.slotCode.trim(),
        slotName: timeSlotForm.slotName.trim(),
        weekday: timeSlotForm.weekday,
        startTime: timeSlotForm.startTime,
        endTime: timeSlotForm.endTime,
        durationMinutes: timeSlotForm.durationMinutes,
        status: timeSlotForm.status,
      })
      ElMessage.success('时间段已新增')
    } else if (editingTimeSlotId.value) {
      await updateTimeSlot(editingTimeSlotId.value, {
        campusId: timeSlotForm.campusId || null,
        slotCode: timeSlotForm.slotCode.trim(),
        slotName: timeSlotForm.slotName.trim(),
        weekday: timeSlotForm.weekday,
        startTime: timeSlotForm.startTime,
        endTime: timeSlotForm.endTime,
        durationMinutes: timeSlotForm.durationMinutes,
        status: timeSlotForm.status,
      })
      ElMessage.success('时间段已更新')
    }
    timeSlotDialogVisible.value = false
    timeSlots.value = await fetchTimeSlots(institutionId.value)
  } catch (error) {
    timeSlotFormError.value = toMessage(error, timeSlotDialogMode.value === 'create' ? '新增时间段失败' : '更新时间段失败')
  } finally {
    timeSlotSubmitting.value = false
  }
}

async function confirmDeleteTimeSlot(row: TimeSlotSummary) {
  try {
    await ElMessageBox.confirm(`确定删除时间段「${row.slotName || row.slotCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteTimeSlot(row.id)
    ElMessage.success('时间段已删除')
    timeSlots.value = await fetchTimeSlots(institutionId.value)
  } catch (error) {
    ElMessage.error(toMessage(error, '删除时间段失败'))
  }
}

async function submitCourseCategory() {
  if (!institutionId.value) {
    courseCategoryFormError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(courseCategoryForm.categoryCode) || !trimToUndefined(courseCategoryForm.categoryName)) {
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
    courseCategoryFormError.value = toMessage(error, courseCategoryDialogMode.value === 'create' ? '新增课程分类失败' : '更新课程分类失败')
  } finally {
    courseCategorySubmitting.value = false
  }
}

async function confirmDeleteCourseCategory(row: CourseCategoryRecord) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.categoryName || row.categoryCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
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
    courses.value = await fetchCourses({ institutionId: institutionId.value })
  } catch (error) {
    courseFormError.value = toMessage(error, courseDialogMode.value === 'create' ? '新增课程失败' : '更新课程失败')
  } finally {
    courseSubmitting.value = false
  }
}

async function confirmDeleteCourse(row: CourseRecord) {
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.courseName || row.courseCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteCourse(row.id)
    ElMessage.success('课程已删除')
    courses.value = await fetchCourses({ institutionId: institutionId.value })
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
    classTypeFormError.value = toMessage(error, classTypeDialogMode.value === 'create' ? '新增班型失败' : '更新班型失败')
  } finally {
    classTypeSubmitting.value = false
  }
}

async function confirmDeleteClassType(row: ClassTypeRecord) {
  try {
    await ElMessageBox.confirm(`确定删除班型「${row.typeName || row.typeCode || row.id}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
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

async function refreshAll() {
  await Promise.all([reloadSchema(), loadResources()])
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
  await refreshAll()
}

watch(() => institutionStore.currentInstitutionId, async () => {
  campusId.value = null
  await loadResources()
})

onMounted(() => {
  initializePage()
})
</script>
