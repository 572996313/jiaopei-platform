package com.jiaopei.platform.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.education.domain.entity.ScheduleLessonEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
