package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeeAttendanceMonthEntity;
import com.cornerstone.module.education.fee.dto.FeeAttendanceDayRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeeAttendanceMonthMapper extends BaseMapper<FeeAttendanceMonthEntity> {

    /**
     * 按班按月汇总学员出勤天数。
     *
     * <p>只算 {@code present} 与 {@code late}：迟到当天仍然来了，伙食费等按天项目要收；
     * {@code leave}/{@code absent} 不计。{@code COUNT(DISTINCT attendance_date)} 而不是 count(*)：
     * 一个自然日可能有多节课（上午/下午），来了两节只算一天。</p>
     */
    @Select("""
        SELECT student_id AS studentId,
               COUNT(DISTINCT attendance_date) AS dayCount
        FROM jp_attendance
        WHERE deleted = 0
          AND institution_id = #{institutionId}
          AND class_id = #{classId}
          AND attendance_date >= #{startDate}
          AND attendance_date < #{endDateExclusive}
          AND attendance_status IN ('present', 'late')
        GROUP BY student_id
        """)
    List<FeeAttendanceDayRow> sumDaysByClassAndMonth(@Param("institutionId") Long institutionId,
                                                     @Param("classId") Long classId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDateExclusive") LocalDate endDateExclusive);

    /** 台账物理删除前清子行（外键要求）。 */
    @Delete("DELETE FROM jp_fee_attendance_month WHERE ledger_id = #{ledgerId}")
    int deletePhysicallyByLedgerId(@Param("ledgerId") Long ledgerId);
}
