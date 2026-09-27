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
     * 按机构取全部档案，**含已软删行**，只取导入判重需要的列。
     *
     * <p>必须绕过 {@code @TableLogic} 的 deleted=0 过滤：uk_jp_teacher_code 与 uk_jp_teacher_phone
     * 都不含 deleted，软删档案依旧永久占着这两个值。预览看不见它，就会出现"整批校验全绿、
     * 提交时撞 1062 整批回滚"。</p>
     */
    @Select("SELECT id, institution_id, teacher_code, phone, deleted "
        + "FROM jp_teacher WHERE institution_id = #{institutionId}")
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
