package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleRuleEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleRuleMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.schedule.dto.ScheduleConflictCheckRequest;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonPageQuery;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonRequest;
import com.cornerstone.module.education.schedule.service.SchoolCalendarService;
import com.cornerstone.module.education.schedule.service.impl.ScheduleLessonServiceImpl;
import com.cornerstone.module.education.schedule.support.ScheduleRuleSupport;
import com.cornerstone.module.education.schedule.support.ScheduleTeacherLock;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ScheduleLessonServiceImplTest {

    @BeforeAll
    static void initEntityTableInfo() {
        // 单测环境没有 MyBatis 启动流程，手动注册实体元数据，wrapper 的 lambda 才能解析出列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ScheduleLessonEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ScheduleRuleEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TimeSlotEntity.class);
        // 规则提示语要带出班级/教师名称，ScheduleRuleSupport 里也会按这两张表建 wrapper
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeachingClassEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeacherEntity.class);
        // 学员跨班撞课提示按这两张表建 wrapper
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ClassStudentEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StudentEntity.class);
        // 教室禁排规则的提示语要带出教室名，targetNames 会按教室表建 wrapper
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ClassroomEntity.class);
    }

    private final ScheduleLessonMapper lessonMapper = mock(ScheduleLessonMapper.class);
    private final ScheduleTeacherRelMapper teacherRelMapper = mock(ScheduleTeacherRelMapper.class);
    private final TeachingClassMapper classMapper = mock(TeachingClassMapper.class);
    private final TeacherMapper teacherMapper = mock(TeacherMapper.class);
    private final ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
    private final TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
    private final ScheduleRuleMapper ruleMapper = mock(ScheduleRuleMapper.class);
    private final ClassStudentMapper classStudentMapper = mock(ClassStudentMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final SchoolCalendarService schoolCalendarService = mock(SchoolCalendarService.class);
    private final ScheduleTeacherLock teacherLock = mock(ScheduleTeacherLock.class);
    private final ScheduleRuleSupport ruleSupport = new ScheduleRuleSupport(ruleMapper, classMapper, teacherMapper,
        timeSlotMapper, classroomMapper, new ObjectMapper());
    private final ScheduleLessonServiceImpl service = new ScheduleLessonServiceImpl(
        lessonMapper,
        teacherRelMapper,
        classMapper,
        teacherMapper,
        classroomMapper,
        timeSlotMapper,
        classStudentMapper,
        studentMapper,
        schoolCalendarService,
        ruleSupport,
        teacherLock
    );

    @BeforeEach
    void authenticateOperator() {
        // 课次接口的机构范围取自登录态（ScheduleInstitutionGuard），单测要先把它造出来
        authenticate(1L, 1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createLessonWritesTeacherRelations() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
        relation.setLessonId(900L);
        relation.setTeacherId(400L);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));

        assertEquals(List.of(400L), service.createLesson(request()).teacherIds());

        // 教师占用没有唯一键可兜，先查后插之前必须取行锁（锁在 ScheduleTeacherLock 里，这里验调用点）
        verify(teacherLock).lock(List.of(400L));
        verify(teacherRelMapper).physicalDeleteByLessonId(900L);
        verify(teacherRelMapper).insert(any(ScheduleTeacherRelEntity.class));
    }

    @Test
    void checkConflictsWarnsTeacherOverDailyLimitWithoutBlocking() {
        stubValidMaterial();
        TeacherEntity teacher = teacherMapper.selectById(400L);
        teacher.setMaxDailyLessons(1);
        teacher.setMaxWeeklyLessons(3);
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), any(), any(), any())).thenReturn(0L);
        // 2026-06-15 是周一，所以"当日"与"本周"两次统计的区间可区分
        when(teacherRelMapper.countTeacherLessonsInRange(400L, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null))
            .thenReturn(1L);
        when(teacherRelMapper.countTeacherLessonsInRange(400L, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), null))
            .thenReturn(2L);
        when(schoolCalendarService.closedDayNotice(1L, 2L, LocalDate.of(2026, 6, 15))).thenReturn(null);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var response = service.checkConflicts(conflictRequest());

        assertEquals(Boolean.FALSE, response.conflict());
        assertTrue(response.notices().stream().anyMatch(item -> item.contains("当日已排 1 节")
            && item.contains("超出每日上限 1")), String.valueOf(response.notices()));
        assertTrue(response.notices().stream().noneMatch(item -> item.contains("周课时上限")),
            "本周 2 节 + 本节 = 3 节，正好等于上限，不该提示：" + response.notices());
    }

    @Test
    void checkConflictsWarnsStudentAlsoHasLessonInAnotherClass() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), any(), any(), any())).thenReturn(0L);
        when(teacherRelMapper.countTeacherLessonsInRange(any(), any(), any(), any())).thenReturn(0L);
        // 第一次取本班在册学员，第二次取这些学员在别班的在册关系
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(classStudent(100L, 9001L)), List.of(classStudent(101L, 9001L)));
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(clashingLesson(930L, 101L, LocalDate.of(2026, 6, 15))));
        when(classMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            teachingClass(100L, "测试班级"), teachingClass(101L, "音乐欣赏班")));
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(student(9001L, "小明")));

        var response = service.checkConflicts(conflictRequest());

        assertEquals(Boolean.FALSE, response.conflict());
        assertTrue(response.notices().stream().anyMatch(item -> item.contains("学员「小明」")
            && item.contains("音乐欣赏班") && item.contains("只提示不拦")), String.valueOf(response.notices()));
    }

    @Test
    void checkConflictsWarnsClosedCalendarDayWithoutBlocking() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), any(), any(), any())).thenReturn(0L);
        when(teacherRelMapper.countTeacherLessonsInRange(any(), any(), any(), any())).thenReturn(0L);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(schoolCalendarService.closedDayNotice(1L, 2L, LocalDate.of(2026, 6, 15)))
            .thenReturn("停排日「端午节」(全校区，只提示不拦)：全校放假");

        var response = service.checkConflicts(conflictRequest());

        assertEquals(Boolean.FALSE, response.conflict());
        assertEquals(List.of("停排日「端午节」(全校区，只提示不拦)：全校放假"), response.notices());
    }

    @Test
    void checkConflictsBlocksClassroomForbiddenRule() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), any(), any(), any())).thenReturn(0L);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        ScheduleRuleEntity rule = forbiddenRule("排练占用");
        rule.setTargetType("classroom");
        rule.setTargetId(200L);
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));
        ClassroomEntity room = new ClassroomEntity();
        room.setId(200L);
        room.setClassroomName("3号音乐教室");
        when(classroomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(room));

        var response = service.checkConflicts(conflictRequest());

        // 教室禁排与班级/教师禁排同档：硬拦，不然是让操作员自己避开一间本来锁死的房
        assertEquals(Boolean.TRUE, response.conflict());
        assertTrue(response.conflictTypes().contains("rule"), String.valueOf(response.conflictTypes()));
        assertTrue(response.messages().stream().anyMatch(item -> item.contains("教室「3号音乐教室」")
            && item.contains("排练占用")), String.valueOf(response.messages()));
    }

    private ScheduleConflictCheckRequest conflictRequest() {
        ScheduleLessonRequest own = request();
        return new ScheduleConflictCheckRequest(null, own.institutionId(), own.campusId(), own.termId(),
            own.classId(), own.classroomId(), own.timeSlotId(), own.lessonDate(), own.teacherIds());
    }

    private ClassStudentEntity classStudent(Long classId, Long studentId) {
        ClassStudentEntity entity = new ClassStudentEntity();
        entity.setInstitutionId(1L);
        entity.setClassId(classId);
        entity.setStudentId(studentId);
        entity.setStatus("active");
        return entity;
    }

    private StudentEntity student(Long id, String name) {
        StudentEntity entity = new StudentEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setStudentName(name);
        return entity;
    }

    private TeachingClassEntity teachingClass(Long id, String name) {
        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setId(id);
        entity.setInstitutionId(1L);
        entity.setClassName(name);
        return entity;
    }


    @Test
    void createLessonRejectsClassConflict() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.createLesson(request()));
    }

    @Test
    void createLessonRejectsForbiddenRuleCell() {
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(forbiddenRule("教师没空")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.createLesson(request()));

        assertTrue(error.getMessage().contains("禁排"));
        assertTrue(error.getMessage().contains("班级「测试班级」"), error.getMessage());
        assertTrue(error.getMessage().contains("教师没空"));
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void lessonWritesReportUniqueKeyRaceAsChineseConflict() {
        // 兜底约束是 V72 的 class_slot_guard：撞键说明另一个会话已把同一格子占下，不能把裸 SQL 文案透给客户
        stubValidMaterial();
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doThrow(new DuplicateKeyException(
            "Duplicate entry '100#2026-06-15#500' for key 'schedule_lesson.uk_schedule_lesson_class_slot'"))
            .when(lessonMapper).insert(any(ScheduleLessonEntity.class));

        BusinessException createError = assertThrows(BusinessException.class, () -> service.createLesson(request()));
        assertEquals(ResultCode.BAD_REQUEST, createError.getResultCode());
        assertTrue(createError.getMessage().contains("并发写入"), createError.getMessage());

        doThrow(new DuplicateKeyException(
            "Duplicate entry '200#2026-06-15#500' for key 'schedule_lesson.uk_schedule_lesson_classroom_slot'"))
            .when(lessonMapper).updateById(any(ScheduleLessonEntity.class));
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        BusinessException updateError = assertThrows(BusinessException.class, () -> service.updateLesson(900L, request()));
        assertTrue(updateError.getMessage().contains("并发写入"), updateError.getMessage());
    }

    private ScheduleRuleEntity forbiddenRule(String remark) {
        ScheduleRuleEntity rule = new ScheduleRuleEntity();
        rule.setInstitutionId(1L);
        rule.setRuleType("forbidden");
        rule.setTargetType("class");
        rule.setTargetId(100L);
        rule.setSlotsJson("[{\"weekday\":1,\"timeSlotId\":500}]");
        rule.setStatus("active");
        rule.setRemark(remark);
        return rule;
    }

    private ScheduleLessonEntity scheduledLesson(Long id, LocalDate lessonDate) {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setId(id);
        lesson.setInstitutionId(1L);
        lesson.setCampusId(2L);
        lesson.setTermId(3L);
        lesson.setClassId(100L);
        lesson.setClassroomId(200L);
        lesson.setTimeSlotId(500L);
        lesson.setLessonDate(lessonDate);
        lesson.setLessonWeekday(lessonDate.getDayOfWeek().getValue());
        lesson.setStartTime(LocalTime.of(9, 0));
        lesson.setEndTime(LocalTime.of(10, 0));
        lesson.setLessonTitle("已排课");
        lesson.setLessonStatus("scheduled");
        return lesson;
    }

    /** 别班在同一日期同一时段已排的课次：学员跨班撞课的对照行。 */
    private ScheduleLessonEntity clashingLesson(Long id, Long classId, LocalDate lessonDate) {
        ScheduleLessonEntity lesson = scheduledLesson(id, lessonDate);
        lesson.setClassId(classId);
        return lesson;
    }

    private ScheduleTeacherRelEntity teacherRel(Long lessonId, Long teacherId) {        ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
        relation.setLessonId(lessonId);
        relation.setTeacherId(teacherId);
        return relation;
    }

    @Test
    void lessonViewsCarryForbiddenRuleMessages() {
        // 规则可以晚于课次建立，"不回改已生成课次"之后这一节就永久落在禁排窗口里。
        // 读侧必须把它标出来，否则课表上毫无痕迹，操作员只在保存被拒的那一刻才知道有问题。
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(scheduledLesson(910L, LocalDate.of(2026, 6, 15))));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(forbiddenRule("后立的规则")));

        var lessons = service.listLessons(1L, 2L, 100L, LocalDate.of(2026, 6, 15), false);

        assertEquals(1, lessons.size());
        assertTrue(lessons.getFirst().ruleConflictMessages().stream().anyMatch(item -> item.contains("禁排")),
            String.valueOf(lessons.getFirst().ruleConflictMessages()));
    }

    @Test
    void updateLessonKeepsLessonWhenRuleRelevantFieldsUnchanged() {
        stubValidMaterial();
        when(lessonMapper.selectById(900L)).thenReturn(scheduledLesson(900L, LocalDate.of(2026, 6, 15)));
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), eq(900L)))
            .thenReturn(0L);
        // 班级/日期/时段/教师四个字段原样不动，本次只改标题与备注：这种保存不该被历史禁排规则拦下
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(teacherRel(900L, 400L)));
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(forbiddenRule("后立的规则")));

        service.updateLesson(900L, new ScheduleLessonRequest(1L, 2L, 3L, 100L, 200L, 500L,
            LocalDate.of(2026, 6, 15), "改个标题", "scheduled", null, null, List.of(400L), "临时调课备注"));

        verify(lessonMapper).updateById(any(ScheduleLessonEntity.class));
    }

    @Test
    void updateLessonRejectsForbiddenRuleWhenDateMoved() {
        stubValidMaterial();
        when(lessonMapper.selectById(900L)).thenReturn(scheduledLesson(900L, LocalDate.of(2026, 6, 15)));
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 22)), eq(500L), eq(900L)))
            .thenReturn(0L);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(teacherRel(900L, 400L)));
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(forbiddenRule("后立的规则")));

        // 换个日期就是把这节课重新排进窗口，规则要重新判——"未改动才放行"不是整体绕过
        BusinessException error = assertThrows(BusinessException.class, () -> service.updateLesson(
            900L, new ScheduleLessonRequest(1L, 2L, 3L, 100L, 200L, 500L, LocalDate.of(2026, 6, 22),
                "挪到下周", "scheduled", null, null, List.of(400L), null)));

        assertTrue(error.getMessage().contains("禁排"), error.getMessage());
        verify(lessonMapper, never()).updateById(any(ScheduleLessonEntity.class));
    }

    @Test
    void createCancelledLessonSkipsConflictChecks() {
        stubValidMaterial();
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(901L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "停课",
            "cancelled",
            null,
            null,
            List.of(400L),
            "天气原因停课"
        ));

        verify(lessonMapper).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void updateMakeupLessonChecksConflictsWithExcludeId() {
        stubValidMaterial();
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), eq(900L))).thenReturn(0L);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.updateLesson(900L, new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            null,
            null,
            List.of(400L),
            "周末补课"
        ));

        verify(teacherRelMapper).countTeacherConflicts(400L, LocalDate.of(2026, 6, 15), 500L, 900L);
    }

    @Test
    void updateLessonRejectsStatusChangeWhenMakeupsDependOnIt() {
        stubValidMaterial();
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        // 改成结课不占格子、不进冲突检查，唯一拦它的是"有补课行指着本课次"
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateLesson(
            900L,
            new ScheduleLessonRequest(1L, 2L, 3L, 100L, 200L, 500L, LocalDate.of(2026, 6, 15),
                "已停课原课", "completed", null, null, List.of(400L), null)));

        assertTrue(error.getMessage().contains("补课课次关联本课次"), error.getMessage());
        assertTrue(error.getMessage().contains("2"), error.getMessage());
        verify(lessonMapper, never()).updateById(any(ScheduleLessonEntity.class));
    }

    @Test
    void deleteLessonRejectsLessonWithMakeupDependents() {
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteLesson(900L));

        assertTrue(error.getMessage().contains("请先处理这些补课课次"), error.getMessage());
        verify(lessonMapper, never()).deleteById(900L);
        verify(teacherRelMapper, never()).physicalDeleteByLessonId(900L);
    }

    @Test
    void updateLessonMarksLessonCompletedWithoutConflictChecks() {
        stubValidMaterial();
        ScheduleLessonEntity existing = new ScheduleLessonEntity();
        existing.setId(900L);
        existing.setInstitutionId(1L);
        when(lessonMapper.selectById(900L)).thenReturn(existing);
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.updateLesson(900L, new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "结课",
            "completed",
            null,
            null,
            List.of(400L),
            null
        ));

        ArgumentCaptor<ScheduleLessonEntity> captor = ArgumentCaptor.forClass(ScheduleLessonEntity.class);
        verify(lessonMapper).updateById(captor.capture());
        assertEquals("completed", captor.getValue().getLessonStatus());
        verify(teacherRelMapper, never()).countTeacherConflicts(any(), any(), any(), any());
    }

    @Test
    void createLessonRejectsUnsupportedStatus() {
        stubValidMaterial();

        assertThrows(BusinessException.class, () -> service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "异常状态",
            "paused",
            null,
            null,
            List.of(400L),
            "invalid"
        )));
    }

    private void stubValidMaterial() {
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setInstitutionId(1L);
        teachingClass.setCampusId(2L);
        teachingClass.setTermId(3L);
        teachingClass.setClassName("测试班级");
        when(classMapper.selectById(100L)).thenReturn(teachingClass);
        // 禁排提示语要带出班级名称，名称是按 selectList 批量取的（ScheduleRuleSupport#targetNames）
        when(classMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(teachingClass));

        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(200L);
        classroom.setCampusId(2L);
        when(classroomMapper.selectById(200L)).thenReturn(classroom);

        TimeSlotEntity timeSlot = new TimeSlotEntity();
        timeSlot.setId(500L);
        timeSlot.setInstitutionId(1L);
        timeSlot.setCampusId(2L);
        timeSlot.setWeekday(1);
        timeSlot.setStartTime(LocalTime.of(9, 0));
        timeSlot.setEndTime(LocalTime.of(10, 0));
        when(timeSlotMapper.selectById(500L)).thenReturn(timeSlot);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(400L);
        teacher.setInstitutionId(1L);
        teacher.setCampusId(2L);
        when(teacherMapper.selectById(400L)).thenReturn(teacher);
    }

    private ScheduleLessonRequest request() {
        return new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "Math",
            "scheduled",
            null,
            null,
            List.of(400L),
            "test"
        );
    }

    @Test
    void createMakeupLessonRequiresCancelledRelatedLesson() {
        stubValidMaterial();
        ScheduleLessonEntity relatedLesson = new ScheduleLessonEntity();
        relatedLesson.setId(880L);
        relatedLesson.setInstitutionId(1L);
        relatedLesson.setCampusId(2L);
        relatedLesson.setTermId(3L);
        relatedLesson.setClassId(100L);
        relatedLesson.setLessonStatus("cancelled");
        when(lessonMapper.selectById(880L)).thenReturn(relatedLesson);
        when(lessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(teacherRelMapper.countTeacherConflicts(eq(400L), eq(LocalDate.of(2026, 6, 15)), eq(500L), any())).thenReturn(0L);
        doAnswer(invocation -> {
            ScheduleLessonEntity entity = invocation.getArgument(0);
            entity.setId(902L);
            return 1;
        }).when(lessonMapper).insert(any(ScheduleLessonEntity.class));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var lesson = service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            "cancelled_makeup",
            880L,
            List.of(400L),
            "关联停课补课"
        ));

        assertEquals("cancelled_makeup", lesson.changeType());
        assertEquals(880L, lesson.relatedLessonId());
    }

    @Test
    void createMakeupLessonRejectsNonCancelledRelatedLesson() {
        stubValidMaterial();
        ScheduleLessonEntity relatedLesson = new ScheduleLessonEntity();
        relatedLesson.setId(881L);
        relatedLesson.setInstitutionId(1L);
        relatedLesson.setCampusId(2L);
        relatedLesson.setTermId(3L);
        relatedLesson.setClassId(100L);
        relatedLesson.setLessonStatus("scheduled");
        when(lessonMapper.selectById(881L)).thenReturn(relatedLesson);

        assertThrows(BusinessException.class, () -> service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            "cancelled_makeup",
            881L,
            List.of(400L),
            "非法来源"
        )));
    }

    @Test
    void createMakeupLessonRejectsRelatedLessonWithoutCancelledMakeupChangeType() {
        stubValidMaterial();

        assertThrows(BusinessException.class, () -> service.createLesson(new ScheduleLessonRequest(
            1L,
            2L,
            3L,
            100L,
            200L,
            500L,
            LocalDate.of(2026, 6, 15),
            "补课",
            "makeup",
            "manual_adjust",
            881L,
            List.of(400L),
            "非法变更类型"
        )));
    }

    @Test
    void listLessonsMarksCancelledLessonWithoutMakeupAsPendingMakeup() {
        ScheduleLessonEntity cancelledLesson = new ScheduleLessonEntity();
        cancelledLesson.setId(910L);
        cancelledLesson.setInstitutionId(1L);
        cancelledLesson.setCampusId(2L);
        cancelledLesson.setTermId(3L);
        cancelledLesson.setClassId(100L);
        cancelledLesson.setClassroomId(200L);
        cancelledLesson.setTimeSlotId(500L);
        cancelledLesson.setLessonDate(LocalDate.of(2026, 6, 15));
        cancelledLesson.setLessonWeekday(1);
        cancelledLesson.setStartTime(LocalTime.of(9, 0));
        cancelledLesson.setEndTime(LocalTime.of(10, 0));
        cancelledLesson.setLessonTitle("停课");
        cancelledLesson.setLessonStatus("cancelled");

        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cancelledLesson));
        when(lessonMapper.selectLinkedCancelledLessonIds(List.of(910L))).thenReturn(List.of());
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var lessons = service.listLessons(1L, 2L, 100L, LocalDate.of(2026, 6, 15), false);

        assertEquals(1, lessons.size());
        assertTrue(Boolean.TRUE.equals(lessons.getFirst().pendingMakeup()));
    }

    @Test
    void listLessonsCanFilterPendingMakeupOnly() {
        ScheduleLessonEntity cancelledPending = new ScheduleLessonEntity();
        cancelledPending.setId(910L);
        cancelledPending.setInstitutionId(1L);
        cancelledPending.setCampusId(2L);
        cancelledPending.setTermId(3L);
        cancelledPending.setClassId(100L);
        cancelledPending.setClassroomId(200L);
        cancelledPending.setTimeSlotId(500L);
        cancelledPending.setLessonDate(LocalDate.of(2026, 6, 15));
        cancelledPending.setLessonWeekday(1);
        cancelledPending.setStartTime(LocalTime.of(9, 0));
        cancelledPending.setEndTime(LocalTime.of(10, 0));
        cancelledPending.setLessonTitle("待补停课");
        cancelledPending.setLessonStatus("cancelled");

        ScheduleLessonEntity cancelledLinked = new ScheduleLessonEntity();
        cancelledLinked.setId(911L);
        cancelledLinked.setInstitutionId(1L);
        cancelledLinked.setCampusId(2L);
        cancelledLinked.setTermId(3L);
        cancelledLinked.setClassId(100L);
        cancelledLinked.setClassroomId(200L);
        cancelledLinked.setTimeSlotId(501L);
        cancelledLinked.setLessonDate(LocalDate.of(2026, 6, 16));
        cancelledLinked.setLessonWeekday(2);
        cancelledLinked.setStartTime(LocalTime.of(10, 0));
        cancelledLinked.setEndTime(LocalTime.of(11, 0));
        cancelledLinked.setLessonTitle("已补停课");
        cancelledLinked.setLessonStatus("cancelled");

        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cancelledPending, cancelledLinked));
        when(lessonMapper.selectLinkedCancelledLessonIds(List.of(910L, 911L))).thenReturn(List.of(911L));
        when(teacherRelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var lessons = service.listLessons(1L, 2L, 100L, null, true);

        assertEquals(1, lessons.size());
        assertEquals(910L, lessons.getFirst().id());
        assertTrue(Boolean.TRUE.equals(lessons.getFirst().pendingMakeup()));
    }

    @Test
    void dayTimetableAppliesClassIdFilter() {
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.dayTimetable(1L, 2L, 100L, LocalDate.of(2026, 6, 15), false);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        // MP 的条件参数在渲染 SQL 片段时才注册，需先触发渲染再断言
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(100L));
    }

    @Test
    void dayTimetableRequiresLessonDate() {
        assertThrows(BusinessException.class, () -> service.dayTimetable(1L, 2L, null, null, false));
    }

    @Test
    void weekTimetableAppliesClassIdAndDateRange() {
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.weekTimetable(1L, 2L, 100L, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), false);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        wrapper.getSqlSegment();
        var values = wrapper.getParamNameValuePairs().values();
        assertTrue(values.contains(100L));
        assertTrue(values.contains(LocalDate.of(2026, 6, 8)));
        assertTrue(values.contains(LocalDate.of(2026, 6, 14)));
    }

    @Test
    void weekTimetableRejectsInvalidRange() {
        assertThrows(BusinessException.class,
            () -> service.weekTimetable(1L, 2L, null, LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 8), false));
    }

    @Test
    void listLessonsAlwaysFiltersByLoginInstitution() {
        when(lessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listLessons(null, null, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonMapper).selectList(captor.capture());
        LambdaQueryWrapper wrapper = captor.getValue();
        wrapper.getSqlSegment();
        // 客户端不传机构不等于不限机构：范围一律收到登录机构上
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void queriesAndWritesRejectOtherInstitution() {
        BusinessException listError = assertThrows(BusinessException.class,
            () -> service.listLessons(2L, null, null, null, null));
        assertEquals(ResultCode.FORBIDDEN, listError.getResultCode());

        ScheduleLessonPageQuery query = new ScheduleLessonPageQuery();
        query.setInstitutionId(2L);
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.pageLessons(query)).getResultCode());
        assertEquals(ResultCode.FORBIDDEN,
            assertThrows(BusinessException.class, () -> service.pagePendingMakeupLessons(query)).getResultCode());

        assertEquals(ResultCode.FORBIDDEN, assertThrows(BusinessException.class,
            () -> service.createLesson(foreignRequest())).getResultCode());
        verify(lessonMapper, never()).insert(any(ScheduleLessonEntity.class));
    }

    @Test
    void getLessonRejectsLessonFromAnotherInstitution() {
        ScheduleLessonEntity foreign = new ScheduleLessonEntity();
        foreign.setId(900L);
        foreign.setInstitutionId(2L);
        foreign.setLessonStatus("scheduled");
        when(lessonMapper.selectById(900L)).thenReturn(foreign);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getLesson(900L));

        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        assertTrue(error.getMessage().contains("排课课次"));
    }

    private ScheduleLessonRequest foreignRequest() {
        ScheduleLessonRequest own = request();
        return new ScheduleLessonRequest(
            2L,
            own.campusId(),
            own.termId(),
            own.classId(),
            own.classroomId(),
            own.timeSlotId(),
            own.lessonDate(),
            own.lessonTitle(),
            own.lessonStatus(),
            own.changeType(),
            own.relatedLessonId(),
            own.teacherIds(),
            own.remark()
        );
    }

    /** 排课服务的机构范围来自登录态，单测要把 SecurityContext 里的用户造出来（参照 AnnouncementServiceImplTest） */
    private void authenticate(Long userId, Long institutionId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, institutionId, "admin",
            Set.of("ADMIN"), Set.of("education:read", "education:schedule:write"), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }
}
