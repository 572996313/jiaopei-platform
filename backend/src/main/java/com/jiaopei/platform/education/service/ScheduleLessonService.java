package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.dto.ScheduleConflictCheckRequest;
import com.jiaopei.platform.education.dto.ScheduleConflictCheckResponse;
import com.jiaopei.platform.education.dto.ScheduleLessonRequest;
import com.jiaopei.platform.education.dto.ScheduleLessonView;
import java.time.LocalDate;
import java.util.List;

public interface ScheduleLessonService {

    List<ScheduleLessonView> listLessons(Long institutionId, Long campusId, Long classId, LocalDate lessonDate,
                                         Boolean pendingMakeupOnly);

    ScheduleLessonView getLesson(Long id);

    ScheduleLessonView createLesson(ScheduleLessonRequest request);

    ScheduleLessonView updateLesson(Long id, ScheduleLessonRequest request);

    void deleteLesson(Long id);

    List<ScheduleLessonView> dayTimetable(Long institutionId, Long campusId, LocalDate lessonDate,
                                          Boolean pendingMakeupOnly);

    List<ScheduleLessonView> weekTimetable(Long institutionId, Long campusId, LocalDate weekStart, LocalDate weekEnd,
                                           Boolean pendingMakeupOnly);

    ScheduleConflictCheckResponse checkConflicts(ScheduleConflictCheckRequest request);
}
