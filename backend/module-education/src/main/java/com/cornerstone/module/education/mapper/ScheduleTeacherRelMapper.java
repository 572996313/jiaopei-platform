package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ScheduleTeacherRelMapper extends BaseMapper<ScheduleTeacherRelEntity> {

    @Delete("DELETE FROM schedule_teacher_rel WHERE lesson_id = #{lessonId}")
    int physicalDeleteByLessonId(@Param("lessonId") Long lessonId);

    @Select("""
        SELECT COUNT(1)
        FROM schedule_teacher_rel rel
        JOIN schedule_lesson lesson ON lesson.id = rel.lesson_id
        WHERE rel.teacher_id = #{teacherId}
          AND lesson.lesson_date = #{lessonDate}
          AND lesson.time_slot_id = #{timeSlotId}
          AND lesson.lesson_status IN ('scheduled', 'rescheduled', 'makeup')
          AND lesson.deleted = 0
          AND rel.deleted = 0
          AND (#{excludeLessonId} IS NULL OR lesson.id <> #{excludeLessonId})
        """)
    Long countTeacherConflicts(@Param("teacherId") Long teacherId,
                               @Param("lessonDate") LocalDate lessonDate,
                               @Param("timeSlotId") Long timeSlotId,
                               @Param("excludeLessonId") Long excludeLessonId);

    /**
     * 该教师在一段日期区间内已占用的课节数，用于「周课时上限 / 每日授课节数上限」的提示。
     * 占用口径与 {@link #countTeacherConflicts} 一致：只算已排课/已调课/已补课，停课的不再占老师的时间。
     */
    @Select("""
        SELECT COUNT(DISTINCT rel.lesson_id)
        FROM schedule_teacher_rel rel
        JOIN schedule_lesson lesson ON lesson.id = rel.lesson_id
        WHERE rel.teacher_id = #{teacherId}
          AND lesson.lesson_date BETWEEN #{startDate} AND #{endDate}
          AND lesson.lesson_status IN ('scheduled', 'rescheduled', 'makeup')
          AND lesson.deleted = 0
          AND rel.deleted = 0
          AND (#{excludeLessonId} IS NULL OR lesson.id <> #{excludeLessonId})
        """)
    Long countTeacherLessonsInRange(@Param("teacherId") Long teacherId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate,
                                    @Param("excludeLessonId") Long excludeLessonId);

    @Select("""
        <script>
        SELECT DISTINCT rel.lesson_id
        FROM schedule_teacher_rel rel
        JOIN schedule_lesson lesson ON lesson.id = rel.lesson_id
        WHERE lesson.lesson_status IN ('scheduled', 'rescheduled', 'makeup')
          AND lesson.deleted = 0
          AND rel.deleted = 0
          AND lesson.lesson_date BETWEEN #{startDate} AND #{endDate}
          AND rel.teacher_id IN
          <foreach collection="teacherIds" item="teacherId" open="(" separator="," close=")">#{teacherId}</foreach>
        </script>
        """)
    List<Long> selectOccupiedLessonIdsByTeachers(@Param("teacherIds") List<Long> teacherIds,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);
}
