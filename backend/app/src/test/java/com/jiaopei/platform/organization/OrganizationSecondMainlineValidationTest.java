package com.jiaopei.platform.organization;

import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.tenant.domain.InstitutionEntity;
import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.dto.ClassroomCreateRequest;
import com.jiaopei.platform.organization.dto.TermCreateRequest;
import com.jiaopei.platform.organization.dto.TimeSlotCreateRequest;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
import com.jiaopei.platform.tenant.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.mapper.TermMapper;
import com.jiaopei.platform.organization.mapper.TimeSlotMapper;
import com.jiaopei.platform.organization.service.impl.ClassroomServiceImpl;
import com.jiaopei.platform.organization.service.impl.TermServiceImpl;
import com.jiaopei.platform.organization.service.impl.TimeSlotServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationSecondMainlineValidationTest {

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private TermMapper termMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private TimeSlotMapper timeSlotMapper;

    @Test
    void createClassroomRejectsDuplicateCodeInCampus() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper);
        when(campusMapper.selectById(1L)).thenReturn(new CampusEntity());
        when(classroomMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createClassroom(classroomRequest("ROOM001", "一号教室")));

        verify(classroomMapper, never()).insert(any(ClassroomEntity.class));
    }

    @Test
    void createClassroomRejectsNegativeArea() {
        ClassroomServiceImpl service = new ClassroomServiceImpl(classroomMapper, campusMapper);
        when(campusMapper.selectById(1L)).thenReturn(new CampusEntity());
        when(classroomMapper.selectCount(any())).thenReturn(0L, 0L);

        ClassroomCreateRequest request = new ClassroomCreateRequest(
            1L,
            "ROOM002",
            "二号教室",
            null,
            null,
            30,
            BigDecimal.valueOf(-1),
            null,
            null,
            null,
            null
        );
        assertThrows(BusinessException.class, () -> service.createClassroom(request));

        verify(classroomMapper, never()).insert(any(ClassroomEntity.class));
    }

    @Test
    void createTermRejectsDuplicateNameInInstitution() {
        TermServiceImpl service = new TermServiceImpl(termMapper, institutionMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(termMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createTerm(termRequest(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 1))));

        verify(termMapper, never()).insert(any(TermEntity.class));
    }

    @Test
    void createTermRejectsInvalidDateRange() {
        TermServiceImpl service = new TermServiceImpl(termMapper, institutionMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        assertThrows(BusinessException.class, () -> service.createTerm(termRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 7, 1))));

        verify(termMapper, never()).insert(any(TermEntity.class));
    }

    @Test
    void createTimeSlotRejectsDurationMismatch() {
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());

        TimeSlotCreateRequest request = timeSlotRequest(45);
        assertThrows(BusinessException.class, () -> service.createTimeSlot(1L, request));

        verify(timeSlotMapper, never()).insert(any(TimeSlotEntity.class));
    }

    @Test
    void createTimeSlotRejectsDuplicateSlotCodeInInstitution() {
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(timeSlotMapper, institutionMapper, campusMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(timeSlotMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createTimeSlot(1L, timeSlotRequest(60)));

        verify(timeSlotMapper, never()).insert(any(TimeSlotEntity.class));
    }

    private ClassroomCreateRequest classroomRequest(String code, String name) {
        return new ClassroomCreateRequest(
            1L,
            code,
            name,
            null,
            null,
            30,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null
        );
    }

    private TermCreateRequest termRequest(LocalDate startDate, LocalDate endDate) {
        return new TermCreateRequest(
            1L,
            "TERM2026S",
            "2026 春季班",
            "2026",
            null,
            startDate,
            endDate,
            null,
            null,
            null,
            null,
            null
        );
    }

    private TimeSlotCreateRequest timeSlotRequest(Integer durationMinutes) {
        return new TimeSlotCreateRequest(
            1L,
            null,
            "SLOT001",
            "周一晚间",
            1,
            LocalTime.of(19, 0),
            LocalTime.of(20, 0),
            durationMinutes,
            null,
            null,
            null,
            null,
            null
        );
    }
}
