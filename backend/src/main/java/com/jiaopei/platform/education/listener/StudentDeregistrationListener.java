package com.jiaopei.platform.education.listener;

import com.jiaopei.platform.auth.event.UserAccountDeregisteredEvent;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.UserStudentRelMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号注销时的学员域数据清理监听器。
 *
 * <p>订阅底座发布的 {@link UserAccountDeregisteredEvent}：
 * 学生档案保留但清空归属（可被后续绑定），用户-学员关联行物理删除。
 * 与注销流程同事务，任一清理失败整体回滚。</p>
 */
@Component
public class StudentDeregistrationListener {

    private final StudentMapper studentMapper;
    private final UserStudentRelMapper userStudentRelMapper;

    public StudentDeregistrationListener(StudentMapper studentMapper,
                                         UserStudentRelMapper userStudentRelMapper) {
        this.studentMapper = studentMapper;
        this.userStudentRelMapper = userStudentRelMapper;
    }

    @EventListener
    public void onUserAccountDeregistered(UserAccountDeregisteredEvent event) {
        studentMapper.releaseOwnerByUserId(event.userId());
        userStudentRelMapper.physicalDeleteByUserId(event.userId());
    }
}
