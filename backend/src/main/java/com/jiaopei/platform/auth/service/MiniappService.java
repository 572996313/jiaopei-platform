package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.dto.BindStudentRequest;
import com.jiaopei.platform.auth.dto.MiniappLessonView;
import com.jiaopei.platform.auth.dto.StudentBriefView;
import java.time.LocalDate;
import java.util.List;

/**
 * 小程序端服务:用户-学员档案关联与课程表查询(严格按当前登录用户的数据隔离)。
 */
public interface MiniappService {

    /** 按学员编号或监护人手机号绑定一个学员档案到当前用户 */
    StudentBriefView bindStudent(BindStudentRequest request);

    /** 当前用户已关联的学员列表 */
    List<StudentBriefView> listMyStudents();

    /** 当前用户某个学员在指定日期范围内的课表(联查班级/课程/教室/教师) */
    List<MiniappLessonView> studentLessons(Long studentId, LocalDate start, LocalDate end);
}