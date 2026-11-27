package com.cornerstone.module.education.schedule.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.schedule.dto.ScheduleConflictCheckRequest;
import com.cornerstone.module.education.schedule.dto.ScheduleConflictCheckResponse;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonPageQuery;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonRequest;
import com.cornerstone.module.education.schedule.dto.ScheduleLessonView;
import java.time.LocalDate;
import java.util.List;

public interface ScheduleLessonService {

    List<ScheduleLessonView> listLessons(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                         Boolean pendingMakeupOnly);

    PageResult<ScheduleLessonView> pageLessons(ScheduleLessonPageQuery query);

    PageResult<ScheduleLessonView> pagePendingMakeupLessons(ScheduleLessonPageQuery query);

    ScheduleLessonView getLesson(Long id);

    ScheduleLessonView createLesson(ScheduleLessonRequest request);

    ScheduleLessonView updateLesson(Long id, ScheduleLessonRequest request);

    void deleteLesson(Long id);

    List<ScheduleLessonView> dayTimetable(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                          Boolean pendingMakeupOnly);

    List<ScheduleLessonView> weekTimetable(Long institutionId, Long campusId, Long classId, LocalDate weekStart,
                                           LocalDate weekEnd, Boolean pendingMakeupOnly);

    ScheduleConflictCheckResponse checkConflicts(ScheduleConflictCheckRequest request);
}
