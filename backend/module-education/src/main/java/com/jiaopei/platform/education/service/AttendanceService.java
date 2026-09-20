package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.AttendanceEntity;
import com.jiaopei.platform.education.dto.AttendanceCreateRequest;
import com.jiaopei.platform.education.dto.AttendanceLessonBatchSaveRequest;
import com.jiaopei.platform.education.dto.AttendanceLessonPageView;
import com.jiaopei.platform.education.dto.AttendanceLessonProgressView;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    List<AttendanceEntity> listAttendances(Long lessonId, Long classId, Long studentId, LocalDate attendanceDate);

    List<AttendanceLessonProgressView> listLessonProgress(List<Long> lessonIds);

    AttendanceEntity createAttendance(AttendanceCreateRequest request);

    AttendanceLessonPageView getLessonAttendancePage(Long lessonId);

    AttendanceLessonPageView saveLessonAttendance(AttendanceLessonBatchSaveRequest request);
}
