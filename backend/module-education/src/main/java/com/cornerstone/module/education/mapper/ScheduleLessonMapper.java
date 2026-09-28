package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ScheduleLessonMapper extends BaseMapper<ScheduleLessonEntity> {

    @Select("""
        <script>
        SELECT DISTINCT related_lesson_id
        FROM schedule_lesson
        WHERE deleted = 0
          AND lesson_status = 'makeup'
          AND change_type = 'cancelled_makeup'
          AND related_lesson_id IS NOT NULL
          AND related_lesson_id IN
          <foreach collection='lessonIds' item='lessonId' open='(' separator=',' close=')'>
            #{lessonId}
          </foreach>
        </script>
        """)
    List<Long> selectLinkedCancelledLessonIds(@Param("lessonIds") List<Long> lessonIds);

    /**
     * 按节次列出引用它的课次,**含已逻辑删除的行**(节次明细的「查看引用」)。
     * 必须直查:走 BaseMapper 的 wrapper 会被 @TableLogic 自动加上 deleted = 0,而"删不掉节次"恰恰多半是这些
     * 任何课表页都不显示的墓碑课次留下的外键。institution_id 由调用方从登录态取,不许拿任意入参直查他机构数据。
     */
    @Select("""
        SELECT * FROM schedule_lesson
        WHERE time_slot_id = #{timeSlotId}
          AND institution_id = #{institutionId}
        ORDER BY deleted, lesson_date, start_time
        """)
    List<ScheduleLessonEntity> selectRefsByTimeSlot(@Param("institutionId") Long institutionId,
                                                   @Param("timeSlotId") Long timeSlotId);

    /** 还有未删除的补课课次指着这节课,删了会让 related_lesson_id 悬空,清理时要跳过 */
    @Select("SELECT COUNT(1) FROM schedule_lesson WHERE related_lesson_id = #{lessonId} AND deleted = 0")
    long countLiveMakeupRefs(@Param("lessonId") Long lessonId);

    /** 已删除的补课课次指向它时先把引用置空,否则外键会拦住下面那次物理删除 */
    @Update("UPDATE schedule_lesson SET related_lesson_id = NULL WHERE related_lesson_id = #{lessonId}")
    int clearMakeupRefs(@Param("lessonId") Long lessonId);

    /**
     * 物理删除一条**已逻辑删除**的课次。deleted = 1 写死在语句里:这条路径只清墓碑,
     * 传进未删除的课次 ID 只会影响 0 行,不会把还在排课里的课次删掉。
     */
    @Delete("DELETE FROM schedule_lesson WHERE id = #{id} AND deleted = 1")
    int deleteTombstonePhysically(@Param("id") Long id);
}
