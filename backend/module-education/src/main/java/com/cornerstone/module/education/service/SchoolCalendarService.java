package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.SchoolCalendarRequest;
import com.cornerstone.module.education.dto.SchoolCalendarView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 校区日历（停排日）：法定假、校区检修这类"这天开不了课"的日子。
 *
 * <p>它是提示不是拦截——操作员认定某年国庆照常补课时可以照样保存，不必先把日历记录删掉。
 * 与 V58 的必排规则同一口径，与禁排规则（硬拦）不同层。</p>
 */
public interface SchoolCalendarService {

    /** 按机构（可选校区、可选年份）取停排日，日期升序。 */
    List<SchoolCalendarView> listDays(Long institutionId, Long campusId, Integer year);

    SchoolCalendarView createDay(SchoolCalendarRequest request);

    SchoolCalendarView updateDay(Long id, SchoolCalendarRequest request);

    void deleteDay(Long id);

    /** 这一天该校区是否停排，停排时返回要写进提示语的中文说明，不停排返回 null。 */
    String closedDayNotice(Long institutionId, Long campusId, LocalDate dayDate);

    /** 一段日期里所有停排的日期，值为中文说明；供批量展开一次取齐，避免逐条查库。 */
    Map<LocalDate, String> closedDays(Long institutionId, Long campusId, LocalDate startDate, LocalDate endDate);
}
