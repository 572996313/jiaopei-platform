package com.jiaopei.platform.auth.service;

import com.jiaopei.platform.auth.dto.BindStudentRequest;
import com.jiaopei.platform.auth.dto.MiniappLessonView;
import com.jiaopei.platform.auth.dto.StudentBindSelfRequest;
import com.jiaopei.platform.auth.dto.StudentBriefView;
import com.jiaopei.platform.auth.dto.StudentOwnProfileView;
import com.jiaopei.platform.auth.dto.StudentProfileSaveRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * 小程序端服务:用户-学员档案关联与课程表查询(严格按当前登录用户的数据隔离)。
 */
public interface MiniappService {

    /** 按学员编号或监护人手机号绑定一个学员档案到当前用户 */
    StudentBriefView bindStudent(BindStudentRequest request);

    /** 解绑当前用户与某学员的档案关联,解绑后可重新绑定 */
    void unbindStudent(Long studentId);

    /** 当前用户已关联的学员列表 */
    List<StudentBriefView> listMyStudents();

    /** 当前用户某学员在指定日期范围内的课表(联查班级/课程/教室/教师) */
    List<MiniappLessonView> studentLessons(Long studentId, LocalDate start, LocalDate end);

    /** 学生端:查询自己的档案(未归属时 bound=false) */
    StudentOwnProfileView myStudentProfile();

    /** 学生端:按学号+姓名绑定自己的档案(绑定后不可换绑,重复调用幂等) */
    StudentOwnProfileView bindSelfStudent(StudentBindSelfRequest request);

    /** 学生端:无档案时新建并归属自己;已归属时编辑本人档案(仅姓名/性别/生日) */
    StudentOwnProfileView saveStudentProfile(StudentProfileSaveRequest request);
}