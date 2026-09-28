package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.BindStudentRequest;
import com.cornerstone.module.education.dto.MiniappClassmateGroupView;
import com.cornerstone.module.education.dto.MiniappHomeOverviewView;
import com.cornerstone.module.education.dto.MiniappLessonView;
import com.cornerstone.module.education.dto.StudentBindSelfRequest;
import com.cornerstone.module.education.dto.StudentBriefView;
import com.cornerstone.module.education.dto.StudentOwnProfileView;
import com.cornerstone.module.education.dto.StudentProfileSaveRequest;
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

    /** 当前用户某学员在指定日期范围内的课表(联查班级/课程/教室,教师只给姓) */
    List<MiniappLessonView> studentLessons(Long studentId, LocalDate start, LocalDate end);

    /** 当前用户某学员的同班同学名单,按当前班级分组(只给姓名) */
    List<MiniappClassmateGroupView> studentClassmates(Long studentId);

    /** 小程序首页近况:当前账号名下全部绑定学员的今日课次、本周节数与下一节预告 */
    MiniappHomeOverviewView homeOverview();

    /** 学生端:查询自己的档案(未归属时 bound=false) */
    StudentOwnProfileView myStudentProfile();

    /** 学生端:按学号+姓名绑定自己的档案(绑定后不可换绑,重复调用幂等) */
    StudentOwnProfileView bindSelfStudent(StudentBindSelfRequest request);

    /** 学生端:无档案时新建并归属自己;已归属时编辑本人档案(仅姓名/性别/生日) */
    StudentOwnProfileView saveStudentProfile(StudentProfileSaveRequest request);
}