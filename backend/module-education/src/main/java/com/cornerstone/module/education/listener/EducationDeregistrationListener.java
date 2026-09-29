package com.cornerstone.module.education.listener;

import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.UserStudentRelMapper;
import com.cornerstone.module.education.mapper.WechatBindingMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号注销时的教务域数据清理监听器。
 *
 * <p>订阅底座发布的 {@link UserAccountDeregisteredEvent}：
 * 学生/教师档案保留但清空归属（可被后续绑定），用户-学员关联与微信绑定行物理删除。
 * 与注销流程同事务，任一清理失败整体回滚。</p>
 *
 * <p>教师档案的 user_id 是全表唯一键（uk_jp_teacher_user_id），注销后不清关联的话，
 * 这个账号ID会一直占着唯一键位，导致后续档案再也绑不上任何账号。</p>
 *
 * <p>小程序自助注销与管理端代注销都走这一条，业务侧的清理只在这里维护一份。</p>
 */
@Component
public class EducationDeregistrationListener {

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final UserStudentRelMapper userStudentRelMapper;
    private final WechatBindingMapper wechatBindingMapper;

    public EducationDeregistrationListener(StudentMapper studentMapper,
                                           TeacherMapper teacherMapper,
                                           UserStudentRelMapper userStudentRelMapper,
                                           WechatBindingMapper wechatBindingMapper) {
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.userStudentRelMapper = userStudentRelMapper;
        this.wechatBindingMapper = wechatBindingMapper;
    }

    @EventListener
    public void onUserAccountDeregistered(UserAccountDeregisteredEvent event) {
        studentMapper.releaseOwnerByUserId(event.userId());
        teacherMapper.releaseOwnerByUserId(event.userId());
        userStudentRelMapper.physicalDeleteByUserId(event.userId());
        // openid 唯一键 + 外键指向账号行，不清则注销直接被 1451 拒掉
        wechatBindingMapper.physicalDeleteByUserId(event.userId());
    }
}
