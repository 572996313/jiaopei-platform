package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TeacherMapper extends BaseMapper<TeacherEntity> {

    /**
     * 排课写入前锁住本次涉及的教师档案行。
     *
     * <p>班级与教室占用有 V72 的生成列唯一键兜底，教师占用要跨 schedule_teacher_rel 与课次两张表、
     * 建不了等价约束，于是按「先查后插必须有兜底」的显式锁这一支：并发第二个事务会在这里排队，
     * 等第一个提交后再读占用，重复占用才判得出来。ORDER BY id 不是给看数据的，是让多方同时排课时
     * 的加锁顺序一致，避免互相占着对方要的行而死锁。只能在校验与写入同一事务里调用。</p>
     */
    @Select("""
        <script>
        SELECT id FROM jp_teacher
        WHERE id IN
        <foreach collection="teacherIds" item="teacherId" open="(" separator="," close=")">#{teacherId}</foreach>
        ORDER BY id
        FOR UPDATE
        </script>
        """)
    List<Long> lockByIdsForUpdate(@Param("teacherIds") List<Long> teacherIds);

    /**
     * 按机构取未删除档案，只取导入判重需要的列。
     *
     * <p>V106 起 {@code uk_jp_teacher_code} / {@code uk_jp_teacher_phone} 换成只约束未删除行的
     * 守卫键（`*_guard`），软删档案不再永久占住编码与手机号。这里必须与数据库口径一致地过滤
     * {@code deleted = 0}：再把墓碑算成占用，就会误报「被已删除的历史档案占用」，而提交时
     * 其实能正常插入。</p>
     */
    @Select("SELECT id, institution_id, teacher_code, phone "
        + "FROM jp_teacher WHERE institution_id = #{institutionId} AND deleted = 0")
    List<TeacherEntity> selectOccupations(@Param("institutionId") Long institutionId);

    /**
     * 账号注销时解除该账号与教师档案的关联(user_id 置空),档案本身保留(可能挂着班级/考勤)。
     */
    @Update("UPDATE jp_teacher SET user_id = NULL WHERE user_id = #{userId}")
    int releaseOwnerByUserId(@Param("userId") Long userId);

    /**
     * 软删档案并在同一条语句里解绑。
     *
     * <p>不能只靠 {@code deleteById}:uk_jp_teacher_user_id 是全表唯一键,软删行若留着 user_id,
     * 这个账号ID就被一条看不见的档案永久占住,后来人再绑同号会撞 1062,而页面上查不到占着它的那行。</p>
     */
    @Update("UPDATE jp_teacher SET user_id = NULL, deleted = 1 WHERE id = #{id} AND deleted = 0")
    int softDeleteAndUnlink(@Param("id") Long id);
}
