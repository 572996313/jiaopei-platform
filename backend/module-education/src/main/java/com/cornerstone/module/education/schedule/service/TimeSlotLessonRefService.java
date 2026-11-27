package com.cornerstone.module.education.schedule.service;

import com.cornerstone.module.education.schedule.dto.TimeSlotLessonCleanupResponse;
import com.cornerstone.module.education.schedule.dto.TimeSlotLessonRefView;
import java.util.List;

/**
 * 节次（时间段）的排课引用查看与清理。
 *
 * <p>节次被课次以外键指着时删不掉，而最常见的挡路者是**已删除的课次**：它们在课表页一行都不显示，
 * 操作员在界面上找不到"到底是谁引用了"。这两个方法就是给节次明细页用的：先列出来，再把墓碑清掉。
 */
public interface TimeSlotLessonRefService {

    /** 列出引用该节次的全部课次，含已逻辑删除的（deleted=true），未删除的排在前面 */
    List<TimeSlotLessonRefView> listRefsByTimeSlot(Long operatorInstitutionId, Long timeSlotId);

    /** 物理清理引用该节次的已删除课次（连同它的教师关系与考勤行）；未删除的课次一律不碰 */
    TimeSlotLessonCleanupResponse purgeTombstoneLessons(Long operatorInstitutionId, Long timeSlotId);
}
