package com.cornerstone.module.education.service;

import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import com.cornerstone.module.education.dto.AttendanceCreateRequest;
import com.cornerstone.module.education.dto.AttendanceLessonBatchSaveRequest;
import com.cornerstone.module.education.dto.AttendanceLessonPageView;
import com.cornerstone.module.education.dto.AttendanceLessonProgressView;
import com.cornerstone.module.education.dto.AttendanceStudentDetailResponse;
import com.cornerstone.module.education.dto.AttendanceSummaryQuery;
import com.cornerstone.module.education.dto.AttendanceSummaryResponse;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    List<AttendanceEntity> listAttendances(Long lessonId, Long classId, Long studentId, LocalDate attendanceDate);

    List<AttendanceLessonProgressView> listLessonProgress(List<Long> lessonIds);

    AttendanceEntity createAttendance(AttendanceCreateRequest request);

    AttendanceLessonPageView getLessonAttendancePage(Long lessonId);

    AttendanceLessonPageView saveLessonAttendance(AttendanceLessonBatchSaveRequest request);

    /**
     * 考勤汇总:按日期区间统计点名进度与出勤人次,支持按班级或按学员分组。
     * 机构取登录用户所属机构,由调用方传入。
     */
    AttendanceSummaryResponse getSummary(Long institutionId, AttendanceSummaryQuery query);

    /**
     * 学员考勤明细:单个学员在日期区间内的应到课次与点名状态。
     */
    AttendanceStudentDetailResponse getStudentDetail(Long institutionId, Long studentId,
                                                     LocalDate dateFrom, LocalDate dateTo);
}
