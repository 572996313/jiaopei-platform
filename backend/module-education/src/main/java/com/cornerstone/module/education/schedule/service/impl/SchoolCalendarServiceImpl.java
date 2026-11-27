package com.cornerstone.module.education.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.SchoolCalendarDayEntity;
import com.cornerstone.module.education.mapper.SchoolCalendarDayMapper;
import com.cornerstone.module.education.schedule.dto.SchoolCalendarRequest;
import com.cornerstone.module.education.schedule.dto.SchoolCalendarView;
import com.cornerstone.module.education.schedule.service.SchoolCalendarService;
import com.cornerstone.module.education.schedule.support.ScheduleInstitutionGuard;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolCalendarServiceImpl implements SchoolCalendarService {

    /** 机构级停排（全校区生效）用 0 而不是 NULL 存：唯一键里的 NULL 互不相等，同一天能插无数条。 */
    private static final long CAMPUS_ALL = 0L;

    /** 跨机构访问被拒时的提示对象名：「无权访问其他机构的校区日历」 */
    private static final String SUBJECT = "校区日历";

    private final SchoolCalendarDayMapper calendarMapper;
    private final CampusMapper campusMapper;

    public SchoolCalendarServiceImpl(SchoolCalendarDayMapper calendarMapper, CampusMapper campusMapper) {
        this.calendarMapper = calendarMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public List<SchoolCalendarView> listDays(Long institutionId, Long campusId, Integer year) {
        Long scopedInstitution = ScheduleInstitutionGuard.resolveForQuery(institutionId, SUBJECT);
        LambdaQueryWrapper<SchoolCalendarDayEntity> wrapper = new LambdaQueryWrapper<SchoolCalendarDayEntity>()
            .eq(SchoolCalendarDayEntity::getInstitutionId, scopedInstitution)
            .orderByAsc(SchoolCalendarDayEntity::getDayDate);
        if (campusId != null) {
            CampusScopeGuard.assertCampus(campusId, "校区日历");
            // 与 closedDays 同口径：选了校区也要把机构级(0)那批一起列出。
            // 写成 eq 的话，面板按校区筛选就看不见"全校区"的记录，而排课时它们照样提示——
            // 操作员会以为没登记上，再登记一次还会因为 campus_id 不同而真插出一条重复日子。
            wrapper.in(SchoolCalendarDayEntity::getCampusId, List.of(CAMPUS_ALL, campusId));
        }
        // 受限角色：全校区记录 + 授权校区记录；空白名单时是 1=0
        CampusScopeGuard.applyWithAllSentinel(wrapper, SchoolCalendarDayEntity::getCampusId, CAMPUS_ALL);
        if (year != null) {
            wrapper.between(SchoolCalendarDayEntity::getDayDate, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        return calendarMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public SchoolCalendarView createDay(SchoolCalendarRequest request) {
        SchoolCalendarDayEntity entity = new SchoolCalendarDayEntity();
        applyDefinition(entity, request);
        entity.setDeleted(Boolean.FALSE);
        try {
            calendarMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该日期已登记停排：" + request.dayDate() + "，请直接修改那一条");
        }
        return toView(requireDay(entity.getId()));
    }

    @Override
    @Transactional
    public SchoolCalendarView updateDay(Long id, SchoolCalendarRequest request) {
        SchoolCalendarDayEntity entity = requireDay(id);
        applyDefinition(entity, request);
        try {
            calendarMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该日期已登记停排：" + request.dayDate() + "，同一天不能重复登记");
        }
        return toView(requireDay(id));
    }

    @Override
    @Transactional
    public void deleteDay(Long id) {
        requireDay(id);
        calendarMapper.deletePhysicallyById(id);
    }

    @Override
    public String closedDayNotice(Long institutionId, Long campusId, LocalDate dayDate) {
        if (dayDate == null) {
            return null;
        }
        Map<LocalDate, String> closed = closedDays(institutionId, campusId, dayDate, dayDate);
        return closed.get(dayDate);
    }

    @Override
    public Map<LocalDate, String> closedDays(Long institutionId, Long campusId, LocalDate startDate, LocalDate endDate) {
        if (institutionId == null || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return Map.of();
        }
        if (campusId != null) {
            CampusScopeGuard.assertCampus(campusId, "校区日历");
        }
        // 只认机构级(0)与本校区的记录：别的校区的停排日本校区照常上课
        List<Long> campuses = campusId == null ? List.of(CAMPUS_ALL) : List.of(CAMPUS_ALL, campusId);
        List<SchoolCalendarDayEntity> rows = calendarMapper.selectList(
            new LambdaQueryWrapper<SchoolCalendarDayEntity>()
                .eq(SchoolCalendarDayEntity::getInstitutionId, institutionId)
                .in(SchoolCalendarDayEntity::getCampusId, campuses)
                .between(SchoolCalendarDayEntity::getDayDate, startDate, endDate)
                // 校区级记录排在机构级前面：同一天两处都登记时，以范围更窄的那条说明为准
                .orderByDesc(SchoolCalendarDayEntity::getCampusId));
        Map<LocalDate, String> notices = new LinkedHashMap<>();
        for (SchoolCalendarDayEntity row : rows) {
            notices.putIfAbsent(row.getDayDate(), noticeText(row));
        }
        return notices;
    }

    /** 提示语：停排日 + 名称 + 范围 + 批注，操作员要能一眼看出是谁在哪天登记的什么。 */
    private String noticeText(SchoolCalendarDayEntity row) {
        String scope = CAMPUS_ALL == row.getCampusId() ? "全校区" : "本校区";
        return "停排日「" + row.getDayName() + "」(" + scope + "，只提示不拦)"
            + (row.getRemark() == null || row.getRemark().isBlank() ? "" : "：" + row.getRemark());
    }

    private SchoolCalendarDayEntity requireDay(Long id) {
        SchoolCalendarDayEntity entity = calendarMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区日历记录不存在：" + id);
        }
        ScheduleInstitutionGuard.assertOwn(entity.getInstitutionId(), SUBJECT);
        if (entity.getCampusId() == CAMPUS_ALL) {
            CampusScopeGuard.assertUnrestricted("维护全校区停排日");
        } else {
            CampusScopeGuard.assertCampus(entity.getCampusId(), "校区日历");
        }
        return entity;
    }

    private void applyDefinition(SchoolCalendarDayEntity entity, SchoolCalendarRequest request) {
        ScheduleInstitutionGuard.assertOwn(request.institutionId(), SUBJECT);
        Long campusId = request.campusId() == null ? CAMPUS_ALL : request.campusId();
        if (campusId != CAMPUS_ALL) {
            CampusEntity campus = campusMapper.selectById(campusId);
            if (campus == null || !Objects.equals(campus.getInstitutionId(), request.institutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "校区不存在或不属于该机构：" + campusId);
            }
            CampusScopeGuard.assertCampus(campusId, "校区日历");
        } else {
            CampusScopeGuard.assertUnrestricted("维护全校区停排日");
        }
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(campusId);
        entity.setDayDate(request.dayDate());
        entity.setDayName(request.dayName().trim());
        entity.setRemark(request.remark());
    }

    private SchoolCalendarView toView(SchoolCalendarDayEntity entity) {
        return new SchoolCalendarView(entity.getId(), entity.getInstitutionId(), entity.getCampusId(),
            entity.getDayDate(), entity.getDayName(), entity.getRemark());
    }
}
