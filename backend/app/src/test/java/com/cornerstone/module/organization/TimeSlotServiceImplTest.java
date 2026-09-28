package com.cornerstone.module.organization;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.module.organization.service.impl.TimeSlotServiceImpl;
import com.cornerstone.module.organization.service.support.DictValueValidator;
import com.cornerstone.support.CodeRuleTestSupport;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceImplTest {

    @Mock
    private TimeSlotMapper timeSlotMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private SystemDictService systemDictService;

    private TimeSlotServiceImpl service() {
        return new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper,
            new DictValueValidator(systemDictService), CodeRuleTestSupport.unconfiguredRules());
    }

    private TimeSlotEntity timeSlot(Long id, Long institutionId) {
        TimeSlotEntity entity = new TimeSlotEntity();
        entity.setId(id);
        entity.setInstitutionId(institutionId);
        entity.setCampusId(1L);
        entity.setSlotCode("AM1");
        entity.setSlotName("上1");
        entity.setWeekday(1);
        entity.setStartTime(LocalTime.of(8, 0));
        entity.setEndTime(LocalTime.of(8, 45));
        entity.setDurationMinutes(45);
        entity.setStatus("active");
        return entity;
    }

    private BusinessException deleteFailure(long totalCount, long liveCount) {
        when(timeSlotMapper.selectById(9L)).thenReturn(timeSlot(9L, 1L));
        when(timeSlotMapper.countLessonRefs(9L)).thenReturn(totalCount);
        // 未删除的引用数只在总数非 0 时才查，所以零引用的用例不会走到这条桩
        when(timeSlotMapper.countLiveLessonRefs(9L)).thenReturn(liveCount);
        return assertThrows(BusinessException.class, () -> service().deleteTimeSlot(1L, 9L));
    }

    @Test
    void deleteRejectsAndCountsLiveRefs() {
        BusinessException exception = deleteFailure(3, 3);

        assertEquals(ResultCode.BAD_REQUEST, exception.getResultCode());
        assertTrue(exception.getMessage().contains("被 3 个课次引用"), exception.getMessage());
        assertTrue(exception.getMessage().contains("请改为停用"), exception.getMessage());
        // 全是在排课中的课次时不该提"已删除"，操作员会以为清理一下就能删
        assertTrue(!exception.getMessage().contains("已删除的课次"), exception.getMessage());
        verify(timeSlotMapper, never()).deletePhysically(9L);
    }

    @Test
    void deleteRejectsAndSplitsTombstoneRefs() {
        BusinessException exception = deleteFailure(3, 2);

        assertTrue(exception.getMessage().contains("其中 1 个是已删除的课次"), exception.getMessage());
        assertTrue(exception.getMessage().contains("请改为停用"), exception.getMessage());
        verify(timeSlotMapper, never()).deletePhysically(9L);
    }

    @Test
    void deleteRejectsAndPointsToCleanupWhenOnlyTombstonesRemain() {
        BusinessException exception = deleteFailure(2, 0);

        assertTrue(exception.getMessage().contains("只被 2 个已删除的课次引用"), exception.getMessage());
        // 这类引用在课表页一行都不显示，必须给出清理入口，否则操作员只能永远停用它
        assertTrue(exception.getMessage().contains("查看引用"), exception.getMessage());
        verify(timeSlotMapper, never()).deletePhysically(9L);
    }

    @Test
    void deletePhysicallyWhenNothingReferencesIt() {
        when(timeSlotMapper.selectById(9L)).thenReturn(timeSlot(9L, 1L));
        when(timeSlotMapper.countLessonRefs(9L)).thenReturn(0L);

        service().deleteTimeSlot(1L, 9L);

        verify(timeSlotMapper).deletePhysically(9L);
    }
}
