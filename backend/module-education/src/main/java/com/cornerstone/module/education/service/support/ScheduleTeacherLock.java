package com.cornerstone.module.education.service.support;

import com.cornerstone.module.education.mapper.TeacherMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 排课写入前锁住授课教师档案行。
 *
 * <p>教师占用没有数据库唯一键可兜：班级与教室有 V72 的生成列防重键，教师占用要跨两张表
 * （jp_schedule_lesson 存日期时段、jp_schedule_teacher_rel 存教师），把日期时段冗余进关系表一旦漏刷
 * 就会把老师锁在错的格子上——假冲突比没约束更糟。所以按 AGENTS 安全约定第 4 条的另一支：
 * 先查后插之前取行锁，让并发的第二个事务读到第一次的写入。</p>
 *
 * <p>按 ID 升序取锁：两位教师同时排课时加锁顺序不一致会死锁。
 * 必须在事务里调用，否则语句结束即释放、等于没锁。</p>
 */
@Component
public class ScheduleTeacherLock {

    private final TeacherMapper teacherMapper;

    public ScheduleTeacherLock(TeacherMapper teacherMapper) {
        this.teacherMapper = teacherMapper;
    }

    public void lock(List<Long> teacherIds) {
        List<Long> ids = teacherIds == null
            ? List.of()
            : teacherIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (!ids.isEmpty()) {
            teacherMapper.lockByIdsForUpdate(ids);
        }
    }
}
