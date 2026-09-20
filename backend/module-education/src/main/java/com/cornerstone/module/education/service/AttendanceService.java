package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.dto.AttendanceLessonProgressView;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    List<AttendanceEntity> listAttendances(Long lessonId, Long classId, Long studentId, LocalDate attendanceDate);

    List<AttendanceLessonProgressView> listLessonProgress(List<Long> lessonIds);

    AttendanceEntity createAttendance(AttendanceCreateRequest request);

    AttendanceLessonPageView getLessonAttendancePage(Long lessonId);

    AttendanceLessonPageView saveLessonAttendance(AttendanceLessonBatchSaveRequest request);
}
