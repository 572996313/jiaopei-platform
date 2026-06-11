package com.jiaopei.platform.education.service;

import com.jiaopei.platform.education.domain.entity.AttendanceEntity;
import com.jiaopei.platform.education.dto.AttendanceCreateRequest;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    List<AttendanceEntity> listAttendances(Long classId, Long studentId, LocalDate attendanceDate);

    AttendanceEntity createAttendance(AttendanceCreateRequest request);
}
