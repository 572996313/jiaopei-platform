<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="page-toolbar">
      <div class="page-toolbar-filters" v-loading="lookupLoading">
        <el-select v-model="campusId" placeholder="筛选校区" clearable filterable @change="handleCampusChange">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="termId" placeholder="筛选学期" clearable filterable @change="handleTermChange">
          <el-option
            v-for="item in terms"
            :key="item.id"
            :label="item.termName || item.termCode || `学期 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="classId" placeholder="筛选班级" clearable filterable @change="refreshAll">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="item.className || item.classCode || `班级 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="lessonDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="日课表日期"
          @change="refreshLessonsAndDay"
        />
        <el-date-picker
          v-model="weekStart"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="周开始"
          @change="handleWeekStartChange"
        />
        <el-date-picker
          v-model="weekEnd"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="周结束"
          @change="refreshWeekTimetable"
        />
      </div>
      <div class="page-toolbar-actions">
        <el-button @click="statsVisible = !statsVisible">
          统计
          <el-icon class="stats-toggle-icon" :class="{ 'is-open': statsVisible }"><ArrowDown /></el-icon>
        </el-button>
        <el-button :icon="Refresh" circle :loading="lookupLoading || lessonsLoading || dayLoading || weekLoading" @click="refreshAll" />
        <el-button @click="jumpToToday">回到今天</el-button>
        <el-button type="primary" @click="openCreateDialog">新增排课</el-button>
      </div>
    </div>

    <div v-show="statsVisible" class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">今日课次</div>
        <div class="stat-value">{{ dayLessons.length }}</div>
        <div class="stat-hint">{{ lessonDate || '未选择日期' }} 的日课表</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待点名</div>
        <div class="stat-value">{{ pendingAttendanceLessons.length }}</div>
        <div class="stat-hint">优先处理未完成点名的课次</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待结课</div>
        <div class="stat-value">{{ readyToCompleteLessons.length }}</div>
        <div class="stat-hint">点名覆盖应到学员后可直接结课</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">待补课</div>
        <div class="stat-value">{{ pendingMakeupLessons.length }}</div>
        <div class="stat-hint">已停课，待安排补课的原课次</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">变动提醒</div>
        <div class="stat-value">{{ scheduleChangeReminders.length }}</div>
        <div class="stat-hint">待补课、备注、取消和点名提示都会收敛在这里</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="daily-center-head">
        <div>
          <div class="panel-title">今日执行分区</div>
          <div class="education-context-text">
            日期：{{ lessonDate || '未选择' }} / 已结课 {{ completedDayLessons.length }} 节 / 已排课 {{ dayLessons.filter((item) => item.lessonStatus === 'scheduled').length }} 节 / 待补课 {{ pendingMakeupLessons.length }} 节
          </div>
        </div>
        <el-button link type="primary" @click="openDayBoard">打开日课表</el-button>
      </div>

      <div class="daily-center-grid">
        <section
          class="daily-center-block is-wide"
          :class="{ 'is-highlight': focusedSection === 'pending' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">待点名</div>
              <div class="education-context-text">未完成点名的课次会优先保留在这里。</div>
            </div>
            <el-tag type="warning" effect="plain">待点 {{ pendingAttendanceLessons.length }}</el-tag>
          </div>

          <div v-if="pendingAttendanceLessons.length === 0" class="education-empty">
            当前日期下暂无待点名课次。
          </div>

          <div v-else class="daily-lesson-list">
            <button
              v-for="lesson in pendingAttendanceLessons.slice(0, 6)"
              :key="lesson.id"
              type="button"
              class="daily-lesson-item"
              @click="openAttendanceDialog(lesson)"
            >
              <div class="daily-lesson-item-main">
                <div class="daily-lesson-item-title">
                  {{ formatClass(lesson.classId) }}
                  <el-tag
                    v-if="lesson.lessonStatus && lesson.lessonStatus !== 'scheduled'"
                    size="small"
                    :type="lessonStatusTagType(lesson.lessonStatus)"
                  >
                    {{ lessonStatusLabel(lesson.lessonStatus) }}
                  </el-tag>
                  <el-tag size="small" type="warning">待点名</el-tag>
                </div>
                <div class="daily-lesson-item-text">
                  {{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }} / {{ formatTeacherNames(lesson.teacherIds) }} / {{ formatClassroom(lesson.classroomId) }}
                </div>
              </div>
              <div class="daily-lesson-item-meta">
                {{ attendanceProgressText(lesson) }}
              </div>
            </button>
          </div>

          <div v-if="readyToCompleteLessons.length > 0" class="daily-center-inline-tip">
            <span>已有 {{ readyToCompleteLessons.length }} 节课点名已齐，可直接结课。</span>
            <el-button link type="warning" @click="completeLesson(readyToCompleteLessons[0])">先结最近一节</el-button>
          </div>
        </section>

        <section
          class="daily-center-block"
          :class="{ 'is-highlight': focusedSection === 'pending-makeup' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">待补课</div>
              <div class="education-context-text">已停课且未关联补课的原课次集中处理，直接进入补课动作。</div>
            </div>
            <el-tag type="danger" effect="plain">待补 {{ pendingMakeupLessons.length }}</el-tag>
          </div>

          <div v-if="pendingMakeupLessons.length === 0" class="education-empty">
            当前日期下暂无待补课课次。
          </div>

          <div v-else class="daily-mini-list">
            <div v-for="lesson in pendingMakeupLessons.slice(0, 5)" :key="lesson.id" class="daily-mini-item">
              <strong>{{ formatClass(lesson.classId) }}</strong>
              <span>{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }} / {{ formatTeacherNames(lesson.teacherIds) }}</span>
              <span>已停课，待安排补课</span>
              <div class="daily-mini-item-actions">
                <el-button link type="danger" @click="openExecutionChangeDialog('makeup', lesson)">安排补课</el-button>
              </div>
            </div>
          </div>
        </section>

        <section
          class="daily-center-block"
          :class="{ 'is-highlight': focusedSection === 'completed' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">已结课</div>
              <div class="education-context-text">回看今日已完成课次，便于复盘和回查。</div>
            </div>
            <el-tag type="success" effect="plain">已结 {{ completedDayLessons.length }}</el-tag>
          </div>

          <div v-if="completedDayLessons.length === 0" class="education-empty">
            当前日期下还没有已结课课次。
          </div>

          <div v-else class="daily-mini-list">
            <div v-for="lesson in completedDayLessons.slice(0, 5)" :key="lesson.id" class="daily-mini-item">
              <strong>{{ formatClass(lesson.classId) }}</strong>
              <span>{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }}</span>
              <span>{{ formatTeacherNames(lesson.teacherIds) }}</span>
            </div>
          </div>
        </section>

        <section
          class="daily-center-block"
          :class="{ 'is-highlight': focusedSection === 'reminders' }"
        >
          <div class="daily-center-block-head">
            <div>
              <div class="daily-center-title">变动提醒</div>
              <div class="education-context-text">课次备注、取消和点名提示集中提示。</div>
            </div>
            <el-tag type="info" effect="plain">提醒 {{ scheduleChangeReminders.length }}</el-tag>
          </div>

          <div v-if="scheduleChangeReminders.length === 0" class="education-empty">
            当前日期暂无变动提醒。
          </div>

          <div v-else class="daily-mini-list">
            <button
              v-for="item in scheduleChangeReminders.slice(0, 5)"
              :key="item.key"
              type="button"
              class="daily-mini-item is-button"
              @click="handleReminderClick(item.lessonId)"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.text }}</span>
            </button>
          </div>
        </section>
      </div>
    </article>

    <article class="panel-card education-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="排课管理" name="management">
          <div class="education-table-head">
            <div class="panel-title">排课列表<span class="panel-count">共 {{ lessons.length }} 条</span></div>
            <div class="education-context-text">
              校区：{{ formatCampus(campusId) }}，班级：{{ formatClass(classId) }}，日期：{{ lessonDate || '全部' }}
            </div>
          </div>

          <el-table :data="lessons" border v-loading="lessonsLoading">
            <el-table-column prop="lessonDate" label="上课日期" width="120" />
            <el-table-column label="星期" width="100">
              <template #default="{ row }">
                {{ weekdayLabel(row.lessonWeekday) }}
              </template>
            </el-table-column>
            <el-table-column label="班级" min-width="160">
              <template #default="{ row }">
                {{ formatClass(row.classId) }}
              </template>
            </el-table-column>
            <el-table-column label="教室" min-width="150">
              <template #default="{ row }">
                {{ formatClassroom(row.classroomId) }}
              </template>
            </el-table-column>
            <el-table-column label="时段" min-width="170">
              <template #default="{ row }">
                {{ formatTimeSlot(row.timeSlotId, row.startTime, row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="教师" min-width="200">
              <template #default="{ row }">
                {{ formatTeacherNames(row.teacherIds) }}
              </template>
            </el-table-column>
            <el-table-column prop="lessonTitle" label="课程主题" min-width="160" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <div class="lesson-status-cell">
                  <el-tag :type="lessonStatusTagType(row.lessonStatus)">
                    {{ lessonStatusLabel(row.lessonStatus) }}
                  </el-tag>
                  <el-tag v-if="isPendingMakeupLesson(row)" size="small" type="danger" effect="plain">待补课</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" />
            <el-table-column label="停补关联" min-width="220">
              <template #default="{ row }">
                <div class="relation-cell">
                  <span>{{ lessonRelationSummary(row) }}</span>
                  <el-button v-if="relationJumpLabel(row)" link type="primary" @click="jumpToRelatedLesson(row)">
                    {{ relationJumpLabel(row) }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="420" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    link
                    type="success"
                    :disabled="row.lessonStatus === 'cancelled'"
                    @click="openAttendanceDialog(row)"
                  >
                    点名
                  </el-button>
                  <el-button
                    link
                    type="warning"
                    :disabled="!canCompleteLesson(row)"
                    @click="completeLesson(row)"
                  >
                    结课
                  </el-button>
                  <el-button link type="primary" @click="openExecutionChangeDialog('rescheduled', row)">调课</el-button>
                  <el-button link type="info" @click="openExecutionChangeDialog('cancelled', row)">停课</el-button>
                  <el-button link type="danger" @click="openExecutionChangeDialog('makeup', row)">补课</el-button>
                  <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
                  <el-button
                    link
                    type="danger"
                    :loading="deletingLessonId === row.id"
                    @click="removeLesson(row)"
                  >
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!lessonsLoading && lessons.length === 0" class="education-empty">
            当前筛选范围下暂无排课记录。
          </div>
        </el-tab-pane>

        <el-tab-pane label="课表视图" name="timetable">
          <el-tabs v-model="timetableTab">
            <el-tab-pane label="日课表" name="day">
              <div class="education-table-head">
                <div class="panel-title">日课表</div>
                <div class="education-context-text">
                  课表日期：{{ lessonDate || '未选择' }}；范围：{{ formatInstitution(institutionId) }} / {{ formatCampus(campusId) }}
                </div>
              </div>

              <el-table :data="dayLessons" border v-loading="dayLoading">
                <el-table-column label="时段" min-width="170">
                  <template #default="{ row }">
                    {{ formatTimeSlot(row.timeSlotId, row.startTime, row.endTime) }}
                  </template>
                </el-table-column>
                <el-table-column label="班级" min-width="160">
                  <template #default="{ row }">
                    {{ formatClass(row.classId) }}
                  </template>
                </el-table-column>
                <el-table-column label="教室" min-width="150">
                  <template #default="{ row }">
                    {{ formatClassroom(row.classroomId) }}
                  </template>
                </el-table-column>
                <el-table-column label="教师" min-width="200">
                  <template #default="{ row }">
                    {{ formatTeacherNames(row.teacherIds) }}
                  </template>
                </el-table-column>
                <el-table-column prop="lessonTitle" label="课程主题" min-width="160" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <div class="lesson-status-cell">
                      <el-tag :type="lessonStatusTagType(row.lessonStatus)">
                        {{ lessonStatusLabel(row.lessonStatus) }}
                      </el-tag>
                      <el-tag v-if="isPendingMakeupLesson(row)" size="small" type="danger" effect="plain">待补课</el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="停补关联" min-width="220">
                  <template #default="{ row }">
                    <div class="relation-cell">
                      <span>{{ lessonRelationSummary(row) }}</span>
                      <el-button v-if="relationJumpLabel(row)" link type="primary" @click="jumpToRelatedLesson(row)">
                        {{ relationJumpLabel(row) }}
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="执行动作" width="280">
                  <template #default="{ row }">
                    <el-button link type="success" :disabled="row.lessonStatus === 'cancelled'" @click="openAttendanceDialog(row)">进入</el-button>
                    <el-button
                      link
                      type="warning"
                      :disabled="!canCompleteLesson(row)"
                      @click="completeLesson(row)"
                    >
                      结课
                    </el-button>
                    <el-button link type="primary" @click="openExecutionChangeDialog('rescheduled', row)">调课</el-button>
                    <el-button link type="info" @click="openExecutionChangeDialog('cancelled', row)">停课</el-button>
                    <el-button link type="danger" @click="openExecutionChangeDialog('makeup', row)">补课</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <div v-if="!dayLoading && dayLessons.length === 0" class="education-empty">
                当前日期下暂无排课。
              </div>
            </el-tab-pane>

            <el-tab-pane label="周课表" name="week">
              <div class="education-table-head">
                <div class="panel-title">周课表</div>
                <div class="education-context-text">
                  日期：{{ weekStart || '未选择' }} 至 {{ weekEnd || '未选择' }}；范围：{{ formatInstitution(institutionId) }} / {{ formatCampus(campusId) }}
                </div>
              </div>

              <div v-loading="weekLoading" class="week-board">
                <div v-for="group in weekTimetableGroups" :key="group.date" class="week-board-day">
                  <div class="week-board-title">{{ group.title }}</div>
                  <div v-if="group.lessons.length === 0" class="week-board-empty">无排课</div>
                  <div v-for="lesson in group.lessons" :key="lesson.id" class="week-board-card">
                    <div class="week-board-main">
                      <strong>{{ formatClass(lesson.classId) }}</strong>
                      <el-tag size="small" :type="lessonStatusTagType(lesson.lessonStatus)">
                        {{ lessonStatusLabel(lesson.lessonStatus) }}
                      </el-tag>
                    </div>
                    <div>{{ compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段' }}</div>
                    <div>{{ formatClassroom(lesson.classroomId) }}</div>
                    <div>{{ formatTeacherNames(lesson.teacherIds) }}</div>
                    <div v-if="lesson.lessonTitle">{{ lesson.lessonTitle }}</div>
                    <div v-if="lessonRelationSummary(lesson) !== '无关联'" class="week-board-relation">
                      {{ lessonRelationSummary(lesson) }}
                    </div>
                    <div class="week-board-actions">
                      <el-button link type="success" :disabled="lesson.lessonStatus === 'cancelled'" @click="openAttendanceDialog(lesson)">点名</el-button>
                      <el-button
                        link
                        type="warning"
                        :disabled="!canCompleteLesson(lesson)"
                        @click="completeLesson(lesson)"
                      >
                        结课
                      </el-button>
                      <el-button link type="primary" @click="openExecutionChangeDialog('rescheduled', lesson)">调课</el-button>
                      <el-button link type="info" @click="openExecutionChangeDialog('cancelled', lesson)">停课</el-button>
                      <el-button link type="danger" @click="openExecutionChangeDialog('makeup', lesson)">补课</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>
      </el-tabs>
    </article>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-alert
        v-if="conflictResult?.conflict"
        title="冲突检查未通过，请调整后再保存"
        type="warning"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <div v-if="conflictResult?.conflict" class="schedule-conflict-list">
        <div
          v-for="(message, index) in normalizedConflictMessages"
          :key="`${message}-${index}`"
          class="schedule-conflict-item"
        >
          {{ message }}
        </div>
      </div>

      <el-form label-position="top" class="education-form">
        <div class="schedule-form-grid">
          <el-form-item label="机构">
            <el-select v-model="form.institutionId" disabled>
              <el-option
                v-for="item in institutions"
                :key="item.id"
                :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="校区">
            <el-select v-model="form.campusId" placeholder="选择校区" filterable @change="handleFormCampusChange">
              <el-option
                v-for="item in campuses"
                :key="item.id"
                :label="item.campusName || item.campusCode || `校区 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="学期">
            <el-select v-model="form.termId" placeholder="选择学期" filterable>
              <el-option
                v-for="item in terms"
                :key="item.id"
                :label="item.termName || item.termCode || `学期 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="form.classId" placeholder="选择班级" filterable>
              <el-option
                v-for="item in classes"
                :key="item.id"
                :label="item.className || item.classCode || `班级 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="教室">
            <el-select v-model="form.classroomId" placeholder="选择教室" filterable>
              <el-option
                v-for="item in classrooms"
                :key="item.id"
                :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="时间段">
            <el-select v-model="form.timeSlotId" placeholder="选择时间段" filterable>
              <el-option
                v-for="item in timeSlots"
                :key="item.id"
                :label="timeSlotOptionLabel(item.slotName, item.startTime, item.endTime, item.weekday)"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="上课日期">
            <el-date-picker
              v-model="form.lessonDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择上课日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="排课状态">
            <el-select v-model="form.lessonStatus" disabled>
              <el-option label="已排课" value="scheduled" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="教师（可多选）">
          <el-select v-model="form.teacherIds" multiple collapse-tags collapse-tags-tooltip filterable placeholder="选择教师">
            <el-option
              v-for="item in teachers"
              :key="item.id"
              :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="课程主题">
          <el-input v-model="form.lessonTitle" placeholder="例如 周三数学强化课" maxlength="128" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充排课说明" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitLesson">
          {{ dialogMode === 'create' ? '检查冲突并创建' : '检查冲突并保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="executionDialogVisible" :title="executionDialogTitle" width="680px" destroy-on-close>
      <div class="education-dialog-context">
        <div><strong>班级：</strong>{{ formatClass(executionTargetLesson?.classId) }}</div>
        <div><strong>原课次：</strong>{{ executionTargetLesson?.lessonDate || '未设置日期' }} {{ compactTimeRange(executionTargetLesson?.startTime, executionTargetLesson?.endTime) || '待定时段' }}</div>
        <div><strong>当前状态：</strong>{{ lessonStatusLabel(executionTargetLesson?.lessonStatus) }}</div>
      </div>

      <el-alert
        v-if="executionFormError"
        :title="executionFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-alert
        v-if="executionConflictResult?.conflict"
        title="执行变更冲突未通过，请调整后再保存"
        type="warning"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <div v-if="executionConflictResult?.conflict" class="schedule-conflict-list">
        <div
          v-for="(message, index) in normalizedExecutionConflictMessages"
          :key="`${message}-${index}`"
          class="schedule-conflict-item"
        >
          {{ message }}
        </div>
      </div>

      <el-form label-position="top" class="education-form">
        <div class="schedule-form-grid">
          <el-form-item label="执行动作">
            <el-select v-model="executionForm.action" disabled>
              <el-option label="调课" value="rescheduled" />
              <el-option label="停课" value="cancelled" />
              <el-option label="补课" value="makeup" />
            </el-select>
          </el-form-item>
          <el-form-item label="课次状态">
            <el-select v-model="executionForm.lessonStatus" disabled>
              <el-option label="已调课" value="rescheduled" />
              <el-option label="已停课" value="cancelled" />
              <el-option label="已补课" value="makeup" />
            </el-select>
          </el-form-item>
          <el-form-item label="上课日期">
            <el-date-picker
              v-model="executionForm.lessonDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择上课日期"
              style="width: 100%"
              :disabled="executionForm.action === 'cancelled'"
            />
          </el-form-item>
          <el-form-item label="时间段">
            <el-select v-model="executionForm.timeSlotId" placeholder="选择时间段" filterable :disabled="executionForm.action === 'cancelled'">
              <el-option
                v-for="item in timeSlots"
                :key="item.id"
                :label="timeSlotOptionLabel(item.slotName, item.startTime, item.endTime, item.weekday)"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="教室">
            <el-select v-model="executionForm.classroomId" placeholder="选择教室" filterable :disabled="executionForm.action === 'cancelled'">
              <el-option
                v-for="item in classrooms"
                :key="item.id"
                :label="item.classroomName || item.classroomCode || `教室 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="授课教师">
            <el-select
              v-model="executionForm.teacherIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              filterable
              placeholder="选择授课教师"
              :disabled="executionForm.action === 'cancelled'"
            >
              <el-option
                v-for="item in teachers"
                :key="item.id"
                :label="item.teacherName || item.teacherCode || `教师 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="executionForm.action === 'makeup'" label="关联停课原课">
            <el-select v-model="executionForm.relatedLessonId" filterable clearable placeholder="选择被补的停课原课">
              <el-option
                v-for="item in cancelledLessonOptions"
                :key="item.id"
                :label="cancelledLessonOptionLabel(item)"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="课程主题">
          <el-input v-model="executionForm.lessonTitle" placeholder="例如 周末补课 / 节假日调课" maxlength="128" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="executionForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="executionRemarkPlaceholder"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="executionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="executionSubmitting" @click="submitExecutionChange">保存变更</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="attendanceDialogVisible"
      title="课次点名"
      width="960px"
      destroy-on-close
    >
      <div v-loading="attendanceLoading">
        <div v-if="attendancePage" class="attendance-shell">
          <div class="education-dialog-context">
            <div><strong>班级：</strong>{{ attendancePage.lesson.className || formatClass(attendancePage.lesson.classId) }}</div>
            <div><strong>日期：</strong>{{ attendancePage.lesson.lessonDate || '未设置' }}</div>
            <div><strong>时段：</strong>{{ compactTimeRange(attendancePage.lesson.startTime, attendancePage.lesson.endTime) || '未设置' }}</div>
            <div><strong>主题：</strong>{{ attendancePage.lesson.lessonTitle || '未设置' }}</div>
            <div><strong>停补关联：</strong>{{ lessonRelationSummary(attendanceLessonRecord) }}</div>
            <div v-if="relationJumpLabel(attendanceLessonRecord)">
              <el-button link type="primary" @click="jumpToRelatedLesson(attendanceLessonRecord)">
                {{ relationJumpLabel(attendanceLessonRecord) }}
              </el-button>
            </div>
          </div>

          <el-alert
            v-if="attendanceError"
            :title="attendanceError"
            type="error"
            show-icon
            :closable="false"
            class="education-alert"
          />
          <el-alert
            v-else-if="attendancePage.message"
            :title="attendancePage.message"
            type="warning"
            show-icon
            :closable="false"
            class="education-alert"
          />

          <div class="dashboard-grid attendance-stat-grid">
            <article class="stat-card">
              <div class="stat-label">应到</div>
              <div class="stat-value">{{ attendanceSummary.expectedCount }}</div>
              <div class="stat-hint">课次在班学员</div>
            </article>
            <article class="stat-card">
              <div class="stat-label">已点</div>
              <div class="stat-value">{{ attendanceSummary.markedCount }}</div>
              <div class="stat-hint">已选择点名状态</div>
            </article>
            <article class="stat-card">
              <div class="stat-label">到课</div>
              <div class="stat-value">{{ attendanceSummary.presentCount }}</div>
              <div class="stat-hint">出勤</div>
            </article>
            <article class="stat-card">
              <div class="stat-label">缺勤 / 请假 / 迟到</div>
              <div class="stat-value attendance-multi-stat">
                {{ attendanceSummary.absentCount }} / {{ attendanceSummary.leaveCount }} / {{ attendanceSummary.lateCount }}
              </div>
              <div class="stat-hint">缺勤 / 请假 / 迟到</div>
            </article>
          </div>

          <div class="attendance-toolbar">
            <div class="education-context-text">
              支持再次进入回显；未设置状态的学员不会写入记录。
            </div>
            <div class="attendance-toolbar-actions">
              <el-button @click="applyAttendanceStatusToAll('present')">全员到课</el-button>
              <el-button @click="clearAttendanceStatuses">清空状态</el-button>
            </div>
          </div>

          <el-table :data="attendanceRows" border>
            <el-table-column label="学号" min-width="140">
              <template #default="{ row }">
                {{ row.studentNo || `学员 #${row.studentId}` }}
              </template>
            </el-table-column>
            <el-table-column label="姓名" min-width="140">
              <template #default="{ row }">
                {{ row.studentName || `学员 #${row.studentId}` }}
              </template>
            </el-table-column>
            <el-table-column label="当前状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.attendanceStatus" :type="attendanceStatusTagType(row.attendanceStatus)">
                  {{ attendanceStatusLabel(row.attendanceStatus) }}
                </el-tag>
                <span v-else class="attendance-empty-text">未点名</span>
              </template>
            </el-table-column>
            <el-table-column label="点名状态" min-width="240">
              <template #default="{ row }">
                <el-radio-group v-model="row.draftStatus" class="attendance-status-group">
                  <el-radio-button label="present">到课</el-radio-button>
                  <el-radio-button label="absent">缺勤</el-radio-button>
                  <el-radio-button label="leave">请假</el-radio-button>
                  <el-radio-button label="late">迟到</el-radio-button>
                </el-radio-group>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="220">
              <template #default="{ row }">
                <el-input v-model="row.draftRemark" maxlength="100" placeholder="补充点名说明" />
              </template>
            </el-table-column>
          </el-table>

          <div v-if="attendanceRows.length === 0" class="education-empty">
            当前课次未找到可点名学员。
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="attendanceDialogVisible = false">关闭</el-button>
        <el-button
          type="warning"
          :loading="attendanceSubmitting || completingLesson"
          @click="saveAttendanceAndComplete"
        >
          保存并结课
        </el-button>
        <el-button type="primary" :loading="attendanceSubmitting" @click="saveAttendance">
          保存点名
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  checkScheduleConflicts,
  createScheduleLesson,
  deleteScheduleLesson,
  fetchDayTimetable,
  fetchLessonAttendancePage,
  fetchScheduleLesson,
  fetchScheduleLessons,
  fetchTeachers,
  fetchWeekTimetable,
  saveLessonAttendanceBatch,
  updateScheduleLesson,
  type AttendanceLessonPage,
  type AttendanceLessonStudent,
  type ScheduleConflictCheckResult,
  type ScheduleLessonPayload,
  type ScheduleLessonRecord,
  type TeacherSummary,
} from '@/api/education'
import { fetchClassrooms, fetchTimeSlots, type ClassroomSummary, type TimeSlotSummary } from '@/api/organization'
import { useEducationScope } from '@/composables/useEducationScope'
import { useEducationScopeStore } from '@/stores/educationScope'
import { useInstitutionStore } from '@/stores/institution'

interface ScheduleReminderItem {
  key: string
  title: string
  text: string
  lessonId: string
}

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const { campusId, termId } = storeToRefs(scopeStore)
const { institutions, institutionId, campuses, terms, classes, loadScope } = useEducationScope()
const route = useRoute()

interface AttendanceEditorRow extends AttendanceLessonStudent {
  draftStatus: string
  draftRemark: string
}

const teachers = ref<TeacherSummary[]>([])
const classrooms = ref<ClassroomSummary[]>([])
const timeSlots = ref<TimeSlotSummary[]>([])

const classId = ref<string | null>(null)
const lessonDate = ref(todayValue())
const weekStart = ref(startOfWeekValue())
const weekEnd = ref(endOfWeekValue(weekStart.value))
const activeTab = ref('management')
const timetableTab = ref('day')

const statsVisible = ref(false)

const lessons = ref<ScheduleLessonRecord[]>([])
const dayLessons = ref<ScheduleLessonRecord[]>([])
const weekLessons = ref<ScheduleLessonRecord[]>([])
const pendingMakeupLessons = ref<ScheduleLessonRecord[]>([])

const pageError = ref('')
const formError = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingLessonId = ref<string | null>(null)
const lookupLoading = ref(false)
const lessonsLoading = ref(false)
const dayLoading = ref(false)
const weekLoading = ref(false)
const submitting = ref(false)
const deletingLessonId = ref<string | null>(null)
const conflictResult = ref<ScheduleConflictCheckResult | null>(null)

const attendanceDialogVisible = ref(false)
const attendanceLoading = ref(false)
const attendanceSubmitting = ref(false)
const attendanceError = ref('')
const attendancePage = ref<AttendanceLessonPage | null>(null)
const attendanceRows = ref<AttendanceEditorRow[]>([])
const completingLesson = ref(false)
const dayAttendancePages = ref<Record<string, AttendanceLessonPage>>({})
const executionDialogVisible = ref(false)
const executionSubmitting = ref(false)
const executionFormError = ref('')
const executionConflictResult = ref<ScheduleConflictCheckResult | null>(null)
const executionTargetLesson = ref<ScheduleLessonRecord | null>(null)

type ExecutionAction = 'rescheduled' | 'cancelled' | 'makeup'

const form = reactive({
  institutionId: '',
  campusId: '',
  termId: '',
  classId: '',
  classroomId: '',
  timeSlotId: '',
  lessonDate: todayValue(),
  lessonTitle: '',
  lessonStatus: 'scheduled',
  teacherIds: [] as string[],
  remark: '',
})

const executionForm = reactive({
  action: 'rescheduled' as ExecutionAction,
  lessonStatus: 'rescheduled',
  classroomId: '',
  timeSlotId: '',
  lessonDate: todayValue(),
  lessonTitle: '',
  teacherIds: [] as string[],
  relatedLessonId: '',
  remark: '',
})

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增排课' : '编辑排课'))
const executionDialogTitle = computed(() => {
  switch (executionForm.action) {
    case 'cancelled':
      return '课次停课'
    case 'makeup':
      return '课次补课'
    case 'rescheduled':
    default:
      return '课次调课'
  }
})
const executionRemarkPlaceholder = computed(() => {
  switch (executionForm.action) {
    case 'cancelled':
      return '填写停课原因和后续安排'
    case 'makeup':
      return '填写补课原因、补课来源或执行说明'
    case 'rescheduled':
    default:
      return '填写调课原因和同步说明'
  }
})

const institutionMap = computed(() => {
  return new Map(
    institutions.value.map((item) => [item.id, item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`]),
  )
})

const campusMap = computed(() => {
  return new Map(campuses.value.map((item) => [item.id, item.campusName || item.campusCode || `校区 #${item.id}`]))
})

const classMap = computed(() => {
  return new Map(classes.value.map((item) => [item.id, item.className || item.classCode || `班级 #${item.id}`]))
})

const classroomMap = computed(() => {
  return new Map(
    classrooms.value.map((item) => [item.id, item.classroomName || item.classroomCode || `教室 #${item.id}`]),
  )
})

const teacherMap = computed(() => {
  return new Map(teachers.value.map((item) => [item.id, item.teacherName || item.teacherCode || `教师 #${item.id}`]))
})

const timeSlotMap = computed(() => {
  return new Map(timeSlots.value.map((item) => [item.id, item]))
})

const normalizedConflictMessages = computed(() => {
  if (!conflictResult.value?.conflict) {
    return []
  }

  if (conflictResult.value.messages && conflictResult.value.messages.length > 0) {
    return conflictResult.value.messages
  }

  return (conflictResult.value.conflictTypes || []).map((item) => `${conflictTypeLabel(item)}冲突`)
})

const normalizedExecutionConflictMessages = computed(() => {
  if (!executionConflictResult.value?.conflict) {
    return []
  }

  if (executionConflictResult.value.messages && executionConflictResult.value.messages.length > 0) {
    return executionConflictResult.value.messages
  }

  return (executionConflictResult.value.conflictTypes || []).map((item) => `${conflictTypeLabel(item)}冲突`)
})

const lessonMap = computed(() => {
  return new Map(sortedLessons([...lessons.value, ...dayLessons.value, ...weekLessons.value]).map((item) => [item.id, item]))
})

const makeupLessonsByRelatedId = computed(() => {
  const map = new Map<string, ScheduleLessonRecord[]>()
  for (const lesson of lessonMap.value.values()) {
    if (lesson.lessonStatus !== 'makeup' || lesson.changeType !== 'cancelled_makeup' || !lesson.relatedLessonId) {
      continue
    }
    const key = lesson.relatedLessonId
    const list = map.get(key) || []
    list.push(lesson)
    map.set(key, list)
  }
  return map
})

const cancelledLessonOptions = computed(() => {
  return sortedLessons(Array.from(lessonMap.value.values()).filter((lesson) => lesson.lessonStatus === 'cancelled'))
})

const weekTimetableGroups = computed(() => {
  const groups = new Map<string, ScheduleLessonRecord[]>()
  const dates = enumerateDates(weekStart.value, weekEnd.value)

  for (const date of dates) {
    groups.set(date, [])
  }

  for (const lesson of sortedLessons(weekLessons.value)) {
    const key = lesson.lessonDate || ''
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)?.push(lesson)
  }

  return Array.from(groups.entries()).map(([date, items]) => ({
    date,
    title: `${compactDateLabel(date)} ${weekdayLabel(weekdayFromDate(date))}`,
    lessons: items,
  }))
})

const focusedSection = computed(() => {
  const value = route.query.focus
  return typeof value === 'string' ? value : ''
})

const pendingAttendanceLessons = computed(() => {
  return sortedLessons(
    dayLessons.value.filter((lesson) => {
      if (!isAttendanceAllowedStatus(lesson.lessonStatus)) {
        return false
      }
      return attendanceMarkedCount(lesson) < attendanceExpectedCount(lesson)
    }),
  )
})

const readyToCompleteLessons = computed(() => {
  return sortedLessons(
    dayLessons.value.filter((lesson) => {
      if (!canCompleteLesson(lesson)) {
        return false
      }
      const expectedCount = attendanceExpectedCount(lesson)
      return expectedCount > 0 && attendanceMarkedCount(lesson) >= expectedCount
    }),
  )
})

const pendingMakeupLessonIds = computed(() => {
  return new Set(pendingMakeupLessons.value.map((lesson) => lesson.id))
})

const completedDayLessons = computed(() => {
  return sortedLessons(dayLessons.value.filter((lesson) => lesson.lessonStatus === 'completed'))
})

const scheduleChangeReminders = computed<ScheduleReminderItem[]>(() => {
  const items: ScheduleReminderItem[] = []

  for (const lesson of dayLessons.value) {
    if (isPendingMakeupLesson(lesson)) {
      items.push({
        key: `${lesson.id}-pending-makeup`,
        title: `${formatClass(lesson.classId)} 待补课`,
        text: '已停课，待安排补课，可直接进入补课动作。',
        lessonId: lesson.id,
      })
    }
    const page = dayAttendancePages.value[lesson.id]
    if (page?.message) {
      items.push({
        key: `${lesson.id}-attendance-message`,
        title: `${formatClass(lesson.classId)} ${compactTimeRange(lesson.startTime, lesson.endTime) || ''}`.trim(),
        text: page.message,
        lessonId: lesson.id,
      })
    }
    if (lesson.remark) {
      items.push({
        key: `${lesson.id}-remark`,
        title: `${formatClass(lesson.classId)} 备注`,
        text: lesson.remark,
        lessonId: lesson.id,
      })
    }
    if (lesson.lessonStatus === 'rescheduled') {
      items.push({
        key: `${lesson.id}-rescheduled`,
        title: `${formatClass(lesson.classId)} 已调课`,
        text: '该课次已调课，请确认新时段、新教室和授课教师安排。',
        lessonId: lesson.id,
      })
    }
    if (lesson.lessonStatus === 'makeup') {
      items.push({
        key: `${lesson.id}-makeup`,
        title: `${formatClass(lesson.classId)} 已补课`,
        text: '该课次已作为补课执行，请关注点名和后续完成情况。',
        lessonId: lesson.id,
      })
    }
    if (lesson.lessonStatus === 'cancelled') {
      items.push({
        key: `${lesson.id}-cancelled`,
        title: `${formatClass(lesson.classId)} 已停课`,
        text: isPendingMakeupLesson(lesson)
          ? '该课次已停课，待安排补课，请尽快处理。'
          : '该课次已停课，请同步检查补课与通知安排。',
        lessonId: lesson.id,
      })
    }
  }

  return items
})

const attendanceSummary = computed(() => {
  const rows = attendanceRows.value
  let markedCount = 0
  let presentCount = 0
  let absentCount = 0
  let leaveCount = 0
  let lateCount = 0

  for (const row of rows) {
    if (!row.draftStatus) {
      continue
    }
    markedCount++
    switch (row.draftStatus) {
      case 'present':
        presentCount++
        break
      case 'absent':
        absentCount++
        break
      case 'leave':
        leaveCount++
        break
      case 'late':
        lateCount++
        break
      default:
        break
    }
  }

  return {
    expectedCount: rows.length,
    markedCount,
    presentCount,
    absentCount,
    leaveCount,
    lateCount,
  }
})

const attendanceLessonRecord = computed(() => {
  const lessonId = attendancePage.value?.lesson.lessonId
  return lessonId ? lessonMap.value.get(lessonId) || null : null
})

function todayValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function startOfWeekValue() {
  return startOfWeek(todayValue())
}

function endOfWeekValue(baseDate: string) {
  return addDays(baseDate, 6)
}

function startOfWeek(value: string) {
  const date = new Date(`${value}T00:00:00`)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  return formatDate(date)
}

function addDays(value: string, days: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

function formatDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

function enumerateDates(start: string, end: string) {
  if (!start || !end || start > end) {
    return []
  }

  const values: string[] = []
  const cursor = new Date(`${start}T00:00:00`)
  const endDate = new Date(`${end}T00:00:00`)

  while (cursor <= endDate) {
    values.push(formatDate(cursor))
    cursor.setDate(cursor.getDate() + 1)
  }

  return values
}

function weekdayFromDate(value?: string | null) {
  if (!value) {
    return null
  }
  const weekday = new Date(`${value}T00:00:00`).getDay()
  return weekday === 0 ? 7 : weekday
}

function compactDateLabel(value?: string | null) {
  if (!value) {
    return '未命名'
  }
  return value.length >= 10 ? value.slice(5) : value
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeTeacherIds(values?: Array<string | number> | null) {
  return Array.from(new Set((values || []).map((item) => String(item))))
}

function timeSlotOptionLabel(name?: string | null, startTime?: string | null, endTime?: string | null, weekday?: number | null) {
  const label = name || '时间段'
  const timeRange = compactTimeRange(startTime, endTime)
  const week = weekdayLabel(weekday)
  return [label, timeRange, week === '未知' ? '' : week].filter(Boolean).join(' / ')
}

function compactTimeRange(startTime?: string | null, endTime?: string | null) {
  if (startTime && endTime) {
    return `${startTime.slice(0, 5)}-${endTime.slice(0, 5)}`
  }
  return startTime?.slice(0, 5) || endTime?.slice(0, 5) || ''
}

function attendancePageFor(lessonId: string) {
  return dayAttendancePages.value[lessonId]
}

function attendanceExpectedCount(lesson: ScheduleLessonRecord) {
  return attendancePageFor(lesson.id)?.summary.expectedCount || 0
}

function attendanceMarkedCount(lesson: ScheduleLessonRecord) {
  return attendancePageFor(lesson.id)?.summary.markedCount || 0
}

function attendanceProgressText(lesson: ScheduleLessonRecord) {
  const page = attendancePageFor(lesson.id)
  if (!page) {
    return lesson.lessonStatus === 'completed' ? '已结课，待回查点名' : '待加载点名进度'
  }
  return `已点 ${page.summary.markedCount} / 应到 ${page.summary.expectedCount}`
}

function isPendingMakeupLesson(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return false
  }
  return lesson.pendingMakeup === true || pendingMakeupLessonIds.value.has(lesson.id)
}

// 调课/补课课次仍要实际上课，可以点名；停课课次不产生考勤，已结课仅回查
function isAttendanceAllowedStatus(status?: string | null) {
  return status === 'scheduled' || status === 'rescheduled' || status === 'makeup' || status === 'completed'
}

function canCompleteLesson(lesson: Pick<ScheduleLessonRecord, 'lessonStatus'>) {
  return lesson.lessonStatus === 'scheduled' || lesson.lessonStatus === 'rescheduled' || lesson.lessonStatus === 'makeup'
}

function cancelledLessonOptionLabel(lesson: ScheduleLessonRecord) {
  return `${lesson.lessonDate || '未设置日期'} / ${formatClass(lesson.classId)} / ${compactTimeRange(lesson.startTime, lesson.endTime) || '待定时段'}`
}

function relatedCancelledLesson(lesson?: ScheduleLessonRecord | null) {
  if (!lesson?.relatedLessonId) {
    return null
  }
  return lessonMap.value.get(lesson.relatedLessonId) || null
}

function relatedMakeupLessons(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return []
  }
  return sortedLessons(makeupLessonsByRelatedId.value.get(lesson.id) || [])
}

function lessonRelationSummary(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return '无关联'
  }
  if (lesson.lessonStatus === 'cancelled') {
    const related = relatedMakeupLessons(lesson)
    return related.length === 0 ? '未关联补课' : `已关联 ${related.length} 节补课`
  }
  if (lesson.lessonStatus === 'makeup' && lesson.changeType === 'cancelled_makeup') {
    const related = relatedCancelledLesson(lesson)
    if (!related) {
      return '补课原课未加载'
    }
    return `补 ${related.lessonDate || '未设置日期'} ${formatClass(related.classId)}`
  }
  return '无关联'
}

function relationJumpLabel(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return ''
  }
  if (lesson.lessonStatus === 'cancelled' && relatedMakeupLessons(lesson).length > 0) {
    return '查看补课'
  }
  if (lesson.lessonStatus === 'makeup' && lesson.changeType === 'cancelled_makeup' && lesson.relatedLessonId) {
    return '查看原停课'
  }
  return ''
}

function jumpToRelatedLesson(lesson?: ScheduleLessonRecord | null) {
  if (!lesson) {
    return
  }
  let target: ScheduleLessonRecord | null = null
  if (lesson.lessonStatus === 'cancelled') {
    target = relatedMakeupLessons(lesson)[0] || null
  } else if (lesson.lessonStatus === 'makeup') {
    target = relatedCancelledLesson(lesson)
  }
  if (!target) {
    ElMessage.warning('未找到关联课次')
    return
  }
  lessonDate.value = target.lessonDate || lessonDate.value
  activeTab.value = 'management'
  refreshLessonsAndDay().then(() => {
    ElMessage.success(`已定位到 ${formatClass(target.classId)} 的关联课次`)
  })
}

function sortedLessons(values: ScheduleLessonRecord[]) {
  return [...values].sort((left, right) => {
    const leftKey = `${left.lessonDate || ''} ${left.startTime || ''} ${left.id || ''}`
    const rightKey = `${right.lessonDate || ''} ${right.startTime || ''} ${right.id || ''}`
    return leftKey.localeCompare(rightKey)
  })
}

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return institutionMap.value.get(id) || `机构 #${id}`
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function formatClass(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classMap.value.get(id) || `班级 #${id}`
}

function formatClassroom(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  return classroomMap.value.get(id) || `教室 #${id}`
}

function formatTimeSlot(id?: string | null, startTime?: string | null, endTime?: string | null) {
  if (id) {
    const slot = timeSlotMap.value.get(id)
    if (slot) {
      return timeSlotOptionLabel(slot.slotName, slot.startTime, slot.endTime, slot.weekday)
    }
  }

  const fallbackRange = compactTimeRange(startTime, endTime)
  return fallbackRange || (id ? `时间段 #${id}` : '未选择')
}

function formatTeacherNames(values?: Array<string | number> | null) {
  const ids = normalizeTeacherIds(values)
  if (ids.length === 0) {
    return '未选择'
  }
  return ids.map((id) => teacherMap.value.get(id) || `教师 #${id}`).join('、')
}

function weekdayLabel(weekday?: number | null) {
  switch (weekday) {
    case 1:
      return '周一'
    case 2:
      return '周二'
    case 3:
      return '周三'
    case 4:
      return '周四'
    case 5:
      return '周五'
    case 6:
      return '周六'
    case 7:
      return '周日'
    default:
      return '未知'
  }
}

function lessonStatusLabel(status?: string | null) {
  switch (status) {
    case 'completed':
      return '已完成'
    case 'rescheduled':
      return '已调课'
    case 'makeup':
      return '已补课'
    case 'cancelled':
      return '已停课'
    case 'scheduled':
    default:
      return '已排课'
  }
}

// 已排课/已调课为常态用中性展示;黄色只留给已停课,红色留给待补课
function lessonStatusTagType(status?: string | null) {
  switch (status) {
    case 'completed':
      return 'success'
    case 'makeup':
      return 'danger'
    case 'cancelled':
      return 'warning'
    case 'rescheduled':
    case 'scheduled':
    default:
      return 'info'
  }
}

function attendanceStatusLabel(status?: string | null) {
  switch (status) {
    case 'present':
      return '到课'
    case 'absent':
      return '缺勤'
    case 'leave':
      return '请假'
    case 'late':
      return '迟到'
    default:
      return '未点名'
  }
}

function attendanceStatusTagType(status?: string | null) {
  switch (status) {
    case 'present':
      return 'success'
    case 'absent':
      return 'danger'
    case 'leave':
      return 'info'
    case 'late':
      return 'warning'
    default:
      return ''
  }
}

function conflictTypeLabel(type?: string | null) {
  switch (type) {
    case 'class':
      return '班级'
    case 'classroom':
      return '教室'
    case 'teacher':
      return '教师'
    default:
      return type || '未知'
  }
}

function resetForm() {
  form.institutionId = institutionId.value || ''
  form.campusId = campusId.value || ''
  form.termId = termId.value || ''
  form.classId = classId.value || ''
  form.classroomId = ''
  form.timeSlotId = ''
  form.lessonDate = lessonDate.value || todayValue()
  form.lessonTitle = ''
  form.lessonStatus = 'scheduled'
  form.teacherIds = []
  form.remark = ''
  editingLessonId.value = null
  conflictResult.value = null
  formError.value = ''
}

function populateForm(lesson: ScheduleLessonRecord) {
  form.institutionId = lesson.institutionId || institutionId.value || ''
  form.campusId = lesson.campusId || campusId.value || ''
  form.termId = lesson.termId || termId.value || ''
  form.classId = lesson.classId || ''
  form.classroomId = lesson.classroomId || ''
  form.timeSlotId = lesson.timeSlotId || ''
  form.lessonDate = lesson.lessonDate || lessonDate.value || todayValue()
  form.lessonTitle = lesson.lessonTitle || ''
  form.lessonStatus = 'scheduled'
  form.teacherIds = normalizeTeacherIds(lesson.teacherIds)
  form.remark = lesson.remark || ''
  conflictResult.value = null
  formError.value = ''
}

function resetExecutionForm() {
  executionForm.action = 'rescheduled'
  executionForm.lessonStatus = 'rescheduled'
  executionForm.classroomId = ''
  executionForm.timeSlotId = ''
  executionForm.lessonDate = lessonDate.value || todayValue()
  executionForm.lessonTitle = ''
  executionForm.teacherIds = []
  executionForm.relatedLessonId = ''
  executionForm.remark = ''
  executionFormError.value = ''
  executionConflictResult.value = null
}

function populateExecutionForm(action: ExecutionAction, lesson: ScheduleLessonRecord) {
  executionForm.action = action
  executionForm.lessonStatus = action
  executionForm.classroomId = lesson.classroomId || ''
  executionForm.timeSlotId = lesson.timeSlotId || ''
  executionForm.lessonDate = lesson.lessonDate || lessonDate.value || todayValue()
  executionForm.lessonTitle = lesson.lessonTitle || ''
  executionForm.teacherIds = normalizeTeacherIds(lesson.teacherIds)
  executionForm.relatedLessonId = action === 'makeup' && lesson.lessonStatus === 'cancelled'
    ? lesson.id
    : (lesson.relatedLessonId || '')
  executionForm.remark = lesson.remark || ''
  executionFormError.value = ''
  executionConflictResult.value = null
}

// 弹窗内切换校区后，教师与教室选项按新校区重拉，避免出现与所选校区不符的选项
async function handleFormCampusChange() {
  form.classroomId = ''
  if (!institutionId.value) {
    return
  }
  try {
    const [teacherList, classroomList] = await Promise.all([
      fetchTeachers({ institutionId: institutionId.value, campusId: form.campusId || null }),
      fetchClassrooms(form.campusId || null),
    ])
    teachers.value = teacherList
    classrooms.value = classroomList
  } catch (error) {
    ElMessage.warning(toMessage(error, '按校区刷新教师与教室选项失败'))
  }
}

function validateForm() {
  if (!form.institutionId) {
    return '缺少机构信息'
  }
  if (!form.campusId) {
    return '缺少校区信息'
  }
  if (!form.termId) {
    return '缺少学期信息'
  }
  if (!form.classId) {
    return '请选择班级'
  }
  if (!form.classroomId) {
    return '请选择教室'
  }
  if (!form.timeSlotId) {
    return '请选择时间段'
  }
  if (!form.lessonDate) {
    return '请选择上课日期'
  }
  if (form.teacherIds.length === 0) {
    return '请至少选择一位教师'
  }
  return ''
}

function buildLessonPayload(): ScheduleLessonPayload {
  return {
    institutionId: form.institutionId,
    campusId: form.campusId,
    termId: form.termId,
    classId: form.classId,
    classroomId: form.classroomId,
    timeSlotId: form.timeSlotId,
    lessonDate: form.lessonDate,
    lessonTitle: trimToUndefined(form.lessonTitle),
    lessonStatus: 'scheduled',
    teacherIds: normalizeTeacherIds(form.teacherIds),
    remark: trimToUndefined(form.remark),
  }
}

function validateExecutionForm() {
  if (!executionTargetLesson.value) {
    return '缺少课次信息'
  }
  if (!executionForm.remark.trim()) {
    return '请填写执行变更备注'
  }
  if (executionForm.action === 'cancelled') {
    return ''
  }
  if (!executionForm.lessonDate) {
    return '请选择上课日期'
  }
  if (!executionForm.timeSlotId) {
    return '请选择时间段'
  }
  if (!executionForm.classroomId) {
    return '请选择教室'
  }
  if (executionForm.teacherIds.length === 0) {
    return '请至少选择一位授课教师'
  }
  if (executionForm.action === 'makeup' && !executionForm.relatedLessonId) {
    return '请选择关联的停课原课'
  }
  return ''
}

function buildExecutionPayload(detail: ScheduleLessonRecord): ScheduleLessonPayload {
  return {
    institutionId: detail.institutionId || '',
    campusId: detail.campusId || '',
    termId: detail.termId || '',
    classId: detail.classId || '',
    classroomId: executionForm.action === 'cancelled' ? detail.classroomId || '' : executionForm.classroomId,
    timeSlotId: executionForm.action === 'cancelled' ? detail.timeSlotId || '' : executionForm.timeSlotId,
    lessonDate: executionForm.action === 'cancelled' ? detail.lessonDate || '' : executionForm.lessonDate,
    lessonTitle: trimToUndefined(executionForm.lessonTitle) ?? detail.lessonTitle ?? undefined,
    lessonStatus: executionForm.lessonStatus,
    changeType: executionForm.action === 'makeup' ? 'cancelled_makeup' : null,
    relatedLessonId: executionForm.action === 'makeup' ? executionForm.relatedLessonId : null,
    teacherIds: executionForm.action === 'cancelled' ? normalizeTeacherIds(detail.teacherIds) : normalizeTeacherIds(executionForm.teacherIds),
    remark: trimToUndefined(executionForm.remark),
  }
}

function populateAttendanceRows(page: AttendanceLessonPage) {
  attendanceRows.value = page.students.map((item) => ({
    ...item,
    draftStatus: item.attendanceStatus || '',
    draftRemark: item.remark || '',
  }))
}

async function loadSupportingOptions() {
  if (!institutionId.value) {
    teachers.value = []
    classrooms.value = []
    timeSlots.value = []
    return
  }

  const [teacherList, classroomList, timeSlotList] = await Promise.all([
    fetchTeachers({ institutionId: institutionId.value, campusId: campusId.value }),
    // 不传校区时后端返回全部教室，保证未选校区时列表/课表仍能显示教室名称
    fetchClassrooms(campusId.value),
    fetchTimeSlots(institutionId.value),
  ])

  teachers.value = teacherList
  classrooms.value = classroomList
  timeSlots.value = timeSlotList
}

async function refreshScopeData() {
  if (!institutionId.value) {
    campuses.value = []
    terms.value = []
    classes.value = []
    teachers.value = []
    classrooms.value = []
    timeSlots.value = []
    classId.value = null
    termId.value = null
    return
  }

  lookupLoading.value = true
  pageError.value = ''
  try {
    await loadScope(campusId.value, termId.value)
    await loadSupportingOptions()

    if (campusId.value && !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = null
      await loadSupportingOptions()
    }
    if (termId.value && !terms.value.some((item) => item.id === termId.value)) {
      termId.value = null
      await loadScope(campusId.value, termId.value)
    }
    if (classId.value && !classes.value.some((item) => item.id === classId.value)) {
      classId.value = null
    }
  } catch (error) {
    pageError.value = toMessage(error, '范围数据加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function refreshLessons() {
  if (!institutionId.value) {
    lessons.value = []
    return
  }

  lessonsLoading.value = true
  pageError.value = ''
  try {
    lessons.value = sortedLessons(
      await fetchScheduleLessons({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        lessonDate: lessonDate.value,
      }),
    )
  } catch (error) {
    lessons.value = []
    pageError.value = toMessage(error, '排课列表加载失败')
  } finally {
    lessonsLoading.value = false
  }
}

async function refreshPendingMakeupLessons() {
  if (!institutionId.value || !lessonDate.value) {
    pendingMakeupLessons.value = []
    return
  }

  try {
    pendingMakeupLessons.value = sortedLessons(
      await fetchScheduleLessons({
        institutionId: institutionId.value,
        campusId: campusId.value,
        classId: classId.value,
        lessonDate: lessonDate.value,
        pendingMakeupOnly: true,
      }),
    )
  } catch (error) {
    pendingMakeupLessons.value = []
    pageError.value = toMessage(error, '待补课聚合加载失败')
  }
}

async function refreshDayTimetable() {
  if (!lessonDate.value) {
    dayLessons.value = []
    dayAttendancePages.value = {}
    return
  }

  dayLoading.value = true
  pageError.value = ''
  try {
    dayLessons.value = sortedLessons(
      await fetchDayTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        lessonDate: lessonDate.value,
      }),
    )
    await loadDayAttendancePages(dayLessons.value)
  } catch (error) {
    dayLessons.value = []
    dayAttendancePages.value = {}
    pageError.value = toMessage(error, '日课表加载失败')
  } finally {
    dayLoading.value = false
  }
}

async function refreshWeekTimetable() {
  if (!weekStart.value || !weekEnd.value || weekStart.value > weekEnd.value) {
    weekLessons.value = []
    return
  }

  weekLoading.value = true
  pageError.value = ''
  try {
    weekLessons.value = sortedLessons(
      await fetchWeekTimetable({
        institutionId: institutionId.value,
        campusId: campusId.value,
        weekStart: weekStart.value,
        weekEnd: weekEnd.value,
      }),
    )
  } catch (error) {
    weekLessons.value = []
    pageError.value = toMessage(error, '周课表加载失败')
  } finally {
    weekLoading.value = false
  }
}

async function refreshLessonsAndDay() {
  await Promise.all([refreshLessons(), refreshDayTimetable(), refreshPendingMakeupLessons()])
}

async function refreshAll() {
  await Promise.all([refreshLessons(), refreshDayTimetable(), refreshWeekTimetable(), refreshPendingMakeupLessons()])
}

async function handleCampusChange() {
  classId.value = null
  await refreshScopeData()
  await refreshAll()
}

async function handleTermChange() {
  classId.value = null
  await refreshScopeData()
  await refreshAll()
}

async function handleWeekStartChange() {
  if (weekStart.value) {
    weekEnd.value = endOfWeekValue(weekStart.value)
  }
  await refreshWeekTimetable()
}

function applyRoutePreferences() {
  const date = route.query.date
  if (typeof date === 'string' && date) {
    lessonDate.value = date
    weekStart.value = startOfWeek(date)
    weekEnd.value = endOfWeekValue(weekStart.value)
  }

  const tab = route.query.tab
  if (tab === 'management' || tab === 'timetable') {
    activeTab.value = tab
  }

  if (route.query.focus === 'day') {
    activeTab.value = 'timetable'
    timetableTab.value = 'day'
  }
}

function openDayBoard() {
  activeTab.value = 'timetable'
  timetableTab.value = 'day'
}

async function jumpToToday() {
  lessonDate.value = todayValue()
  weekStart.value = startOfWeekValue()
  weekEnd.value = endOfWeekValue(weekStart.value)
  await refreshAll()
}

function openCreateDialog() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

async function openEditDialog(lesson: ScheduleLessonRecord) {
  dialogMode.value = 'edit'
  editingLessonId.value = lesson.id
  formError.value = ''
  conflictResult.value = null

  try {
    const detail = await fetchScheduleLesson(lesson.id)
    populateForm(detail)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(toMessage(error, '排课详情加载失败'))
  }
}

function openExecutionChangeDialog(action: ExecutionAction, lesson: ScheduleLessonRecord) {
  executionTargetLesson.value = lesson
  resetExecutionForm()
  populateExecutionForm(action, lesson)
  executionDialogVisible.value = true
}

async function openAttendanceDialog(lesson: ScheduleLessonRecord) {
  attendanceDialogVisible.value = true
  attendanceLoading.value = true
  attendanceError.value = ''
  attendancePage.value = null
  attendanceRows.value = []

  try {
    const page = await fetchLessonAttendancePage(lesson.id)
    attendancePage.value = page
    populateAttendanceRows(page)
  } catch (error) {
    attendanceError.value = toMessage(error, '课次点名页加载失败')
  } finally {
    attendanceLoading.value = false
  }
}

function handleReminderClick(lessonId: string) {
  const lesson = dayLessons.value.find((item) => item.id === lessonId) || lessons.value.find((item) => item.id === lessonId)
  if (lesson) {
    if (isPendingMakeupLesson(lesson) || lesson.lessonStatus === 'cancelled') {
      openExecutionChangeDialog('makeup', lesson)
      return
    }
    openAttendanceDialog(lesson)
  }
}

function applyAttendanceStatusToAll(status: string) {
  attendanceRows.value.forEach((row) => {
    row.draftStatus = status
  })
}

function clearAttendanceStatuses() {
  attendanceRows.value.forEach((row) => {
    row.draftStatus = ''
  })
}

async function saveAttendance() {
  if (!attendancePage.value?.lesson.lessonId) {
    attendanceError.value = '缺少课次信息，无法保存点名'
    return
  }

  const items = attendanceRows.value
    .filter((row) => row.draftStatus)
    .map((row) => ({
      studentId: row.studentId,
      attendanceStatus: row.draftStatus,
      source: row.source || 'manual',
      remark: trimToUndefined(row.draftRemark),
    }))

  if (items.length === 0) {
    attendanceError.value = '请至少为一位学员选择点名状态'
    return
  }

  attendanceSubmitting.value = true
  attendanceError.value = ''
  try {
    const page = await saveLessonAttendanceBatch({
      lessonId: attendancePage.value.lesson.lessonId,
      items,
    })
    attendancePage.value = page
    populateAttendanceRows(page)
    ElMessage.success('课次点名已保存')
  } catch (error) {
    attendanceError.value = toMessage(error, '课次点名保存失败')
  } finally {
    attendanceSubmitting.value = false
  }
}

async function loadDayAttendancePages(values: ScheduleLessonRecord[]) {
  if (values.length === 0) {
    dayAttendancePages.value = {}
    return
  }

  const items = await Promise.all(
    values.map(async (lesson) => {
      try {
        return [lesson.id, await fetchLessonAttendancePage(lesson.id)] as const
      } catch {
        return null
      }
    }),
  )

  dayAttendancePages.value = Object.fromEntries(
    items.filter((item): item is readonly [string, AttendanceLessonPage] => Boolean(item)),
  )
}

async function updateLessonStatus(id: string, status: 'completed' | 'scheduled', remarkOverride?: string) {
  const detail = await fetchScheduleLesson(id)
  await updateScheduleLesson(id, {
    institutionId: detail.institutionId || '',
    campusId: detail.campusId || '',
    termId: detail.termId || '',
    classId: detail.classId || '',
    classroomId: detail.classroomId || '',
    timeSlotId: detail.timeSlotId || '',
    lessonDate: detail.lessonDate || '',
    lessonTitle: detail.lessonTitle || undefined,
    lessonStatus: status,
    teacherIds: normalizeTeacherIds(detail.teacherIds),
    remark: remarkOverride ?? (detail.remark || undefined),
  })
}

async function completeLesson(lesson: ScheduleLessonRecord) {
  if (!canCompleteLesson(lesson)) {
    ElMessage.warning('已停课或已结课的课次不支持结课')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认将 ${lesson.lessonDate || ''} ${formatClass(lesson.classId)} 标记为已完成吗？`,
      '课次结课',
      { type: 'warning' },
    )
  } catch {
    return
  }

  completingLesson.value = true
  try {
    await updateLessonStatus(lesson.id, 'completed')
    ElMessage.success('课次已结课')
    await refreshAll()
    if (attendancePage.value?.lesson.lessonId === lesson.id) {
      attendanceDialogVisible.value = false
    }
  } catch (error) {
    ElMessage.error(toMessage(error, '课次结课失败'))
  } finally {
    completingLesson.value = false
  }
}

async function saveAttendanceAndComplete() {
  if (!attendancePage.value) {
    attendanceError.value = '当前未加载课次点名页'
    return
  }

  await saveAttendance()
  if (attendanceError.value) {
    return
  }

  completingLesson.value = true
  try {
    const lessonId = attendancePage.value.lesson.lessonId
    await updateLessonStatus(lessonId, 'completed')
    attendanceDialogVisible.value = false
    ElMessage.success('点名已保存并结课')
    await refreshAll()
  } catch (error) {
    attendanceError.value = toMessage(error, '点名已保存，但结课失败')
  } finally {
    completingLesson.value = false
  }
}

async function runConflictCheck() {
  const payload = buildLessonPayload()
  conflictResult.value = await checkScheduleConflicts({
    excludeLessonId: dialogMode.value === 'edit' ? editingLessonId.value || undefined : undefined,
    institutionId: payload.institutionId,
    campusId: payload.campusId,
    termId: payload.termId,
    classId: payload.classId,
    classroomId: payload.classroomId,
    timeSlotId: payload.timeSlotId,
    lessonDate: payload.lessonDate,
    teacherIds: payload.teacherIds,
  })

  if (conflictResult.value.conflict) {
    formError.value = '存在排课冲突，请调整后重试'
    return false
  }

  return true
}

async function runExecutionConflictCheck(detail: ScheduleLessonRecord) {
  if (executionForm.action === 'cancelled') {
    executionConflictResult.value = null
    return true
  }

  const payload = buildExecutionPayload(detail)
  executionConflictResult.value = await checkScheduleConflicts({
    excludeLessonId: detail.id,
    institutionId: payload.institutionId,
    campusId: payload.campusId,
    termId: payload.termId,
    classId: payload.classId,
    classroomId: payload.classroomId,
    timeSlotId: payload.timeSlotId,
    lessonDate: payload.lessonDate,
    teacherIds: payload.teacherIds,
  })

  if (executionConflictResult.value.conflict) {
    executionFormError.value = '存在执行冲突，请调整后重试'
    return false
  }

  return true
}

async function submitLesson() {
  const validationError = validateForm()
  if (validationError) {
    formError.value = validationError
    return
  }

  submitting.value = true
  formError.value = ''
  conflictResult.value = null

  try {
    const passed = await runConflictCheck()
    if (!passed) {
      return
    }

    const payload = buildLessonPayload()
    if (dialogMode.value === 'create') {
      await createScheduleLesson(payload)
      ElMessage.success('排课已创建')
    } else if (editingLessonId.value) {
      await updateScheduleLesson(editingLessonId.value, payload)
      ElMessage.success('排课已更新')
    }

    dialogVisible.value = false
    await refreshAll()
  } catch (error) {
    formError.value = toMessage(error, dialogMode.value === 'create' ? '新增排课失败' : '更新排课失败')
  } finally {
    submitting.value = false
  }
}

async function submitExecutionChange() {
  const validationError = validateExecutionForm()
  if (validationError) {
    executionFormError.value = validationError
    return
  }
  if (!executionTargetLesson.value) {
    executionFormError.value = '缺少课次信息'
    return
  }

  executionSubmitting.value = true
  executionFormError.value = ''
  executionConflictResult.value = null

  try {
    const detail = await fetchScheduleLesson(executionTargetLesson.value.id)
    const passed = await runExecutionConflictCheck(detail)
    if (!passed) {
      return
    }

    await updateScheduleLesson(detail.id, buildExecutionPayload(detail))
    executionDialogVisible.value = false
    ElMessage.success(`${executionDialogTitle.value}已保存`)
    await refreshAll()
  } catch (error) {
    executionFormError.value = toMessage(error, `${executionDialogTitle.value}失败`)
  } finally {
    executionSubmitting.value = false
  }
}

async function removeLesson(lesson: ScheduleLessonRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${lesson.lessonDate || ''} ${formatClass(lesson.classId)} 的排课吗？`,
      '删除排课',
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingLessonId.value = lesson.id
  try {
    await deleteScheduleLesson(lesson.id)
    ElMessage.success('排课已删除')
    await refreshAll()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除排课失败'))
  } finally {
    deletingLessonId.value = null
  }
}

async function initializePage() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    applyRoutePreferences()
    await institutionStore.loadInstitutions()
    if (institutionId.value) {
      await refreshScopeData()
    }
    await refreshAll()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  } finally {
    lookupLoading.value = false
  }
}

watch(() => institutionStore.currentInstitutionId, async () => {
  campusId.value = null
  termId.value = null
  classId.value = null
  await refreshScopeData()
  await refreshAll()
})

onMounted(() => {
  initializePage()
})
</script>

<style scoped>
.table-actions {
  display: flex;
  gap: 8px;
}

.relation-cell {
  display: grid;
  gap: 4px;
}

.schedule-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.schedule-conflict-list {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(250, 173, 20, 0.12);
  color: #8f5f00;
}

.schedule-conflict-item + .schedule-conflict-item {
  margin-top: 6px;
}

.daily-center-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.daily-center-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.daily-center-block {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.92);
}

.daily-center-block.is-wide {
  grid-column: span 2;
}

.daily-center-block.is-highlight {
  border-color: rgba(249, 199, 79, 0.9);
  box-shadow: 0 0 0 3px rgba(249, 199, 79, 0.18);
}

.daily-center-block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.daily-center-title {
  font-size: 18px;
  font-weight: 700;
  color: #14213d;
}

.daily-center-inline-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  color: #8f5f00;
  background: rgba(249, 199, 79, 0.16);
}

.daily-lesson-list,
.daily-mini-list {
  display: grid;
  gap: 12px;
}

.daily-lesson-item,
.daily-mini-item.is-button {
  width: 100%;
  border: 0;
  cursor: pointer;
  text-align: left;
}

.daily-lesson-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
}

.daily-lesson-item-main,
.daily-mini-item {
  display: grid;
  gap: 6px;
}

.daily-lesson-item-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-weight: 700;
  color: #14213d;
}

.daily-lesson-item-text,
.daily-mini-item span {
  color: #5c6b82;
  font-size: 13px;
}

.daily-lesson-item-meta {
  align-self: center;
  white-space: nowrap;
  font-weight: 700;
  color: #234c77;
}

.daily-mini-item {
  padding: 12px 14px;
  border-radius: 10px;
  color: #234c77;
  background: #ffffff;
}

.daily-mini-item strong {
  color: #14213d;
}

.daily-mini-item-actions {
  display: flex;
  justify-content: flex-end;
}

.lesson-status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.week-board {
  display: grid;
  /* 固定 7 列，一行正好放下周一到周日；容器不够宽时横向滚动而不是换行 */
  grid-template-columns: repeat(7, minmax(132px, 1fr));
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.week-board-day {
  min-width: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  padding: 12px;
  background: rgba(248, 250, 252, 0.92);
}

.week-board-title {
  margin-bottom: 10px;
  font-weight: 700;
  font-size: 13px;
  color: #0f172a;
  white-space: nowrap;
}

.week-board-empty {
  color: #64748b;
  font-size: 12px;
}

.week-board-card {
  padding: 10px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
  color: #334155;
  font-size: 12px;
}

.week-board-card + .week-board-card {
  margin-top: 8px;
}

.week-board-main {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 8px;
  margin-bottom: 6px;
  align-items: center;
  font-size: 13px;
}

.week-board-actions {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
}

.week-board-actions .el-button + .el-button {
  margin-left: 0;
}

.week-board-relation {
  margin-top: 6px;
  color: #5c6b82;
}

.attendance-shell {
  display: grid;
  gap: 16px;
}

.attendance-stat-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.attendance-multi-stat {
  font-size: 24px;
}

.attendance-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.attendance-toolbar-actions {
  display: flex;
  gap: 8px;
}

.attendance-status-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.attendance-empty-text {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .schedule-form-grid {
    grid-template-columns: 1fr;
  }

  .daily-center-head,
  .daily-center-block-head,
  .daily-center-inline-tip,
  .daily-lesson-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .daily-center-grid {
    grid-template-columns: 1fr;
  }

  .daily-center-block.is-wide {
    grid-column: span 1;
  }

  .attendance-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .attendance-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
