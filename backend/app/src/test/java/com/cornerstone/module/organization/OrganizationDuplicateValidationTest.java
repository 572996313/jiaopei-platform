package com.cornerstone.module.organization;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.module.organization.dto.CampusCreateRequest;
import com.cornerstone.core.tenant.dto.InstitutionCreateRequest;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.module.organization.service.impl.CampusServiceImpl;
import com.cornerstone.core.tenant.service.impl.InstitutionServiceImpl;
import java.time.LocalDate;
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
class OrganizationDuplicateValidationTest {

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private CampusMapper campusMapper;

    @Test
    void createInstitutionRejectsDuplicateOrgCode() {
        InstitutionServiceImpl service = new InstitutionServiceImpl(institutionMapper);
        when(institutionMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createInstitution(institutionRequest("ORG001", "测试机构")));

        verify(institutionMapper, never()).insert(any(InstitutionEntity.class));
    }

    @Test
    void createInstitutionRejectsDuplicateOrgName() {
        InstitutionServiceImpl service = new InstitutionServiceImpl(institutionMapper);
        when(institutionMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createInstitution(institutionRequest("ORG002", "测试机构")));

        verify(institutionMapper, never()).insert(any(InstitutionEntity.class));
    }

    @Test
    void createCampusRejectsDuplicateCampusCodeWithinInstitution() {
        CampusServiceImpl service = new CampusServiceImpl(campusMapper, institutionMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createCampus(campusRequest("CMP001", "东区校区")));

        verify(campusMapper, never()).insert(any(CampusEntity.class));
    }

    @Test
    void createCampusRejectsDuplicateCampusNameWithinInstitution() {
        CampusServiceImpl service = new CampusServiceImpl(campusMapper, institutionMapper);
        when(institutionMapper.selectById(1L)).thenReturn(new InstitutionEntity());
        when(campusMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThrows(BusinessException.class, () -> service.createCampus(campusRequest("CMP002", "东区校区")));

        verify(campusMapper, never()).insert(any(CampusEntity.class));
    }

    private InstitutionCreateRequest institutionRequest(String orgCode, String orgName) {
        return new InstitutionCreateRequest(
            orgCode,
            orgName,
            null,
            null,
            null,
            null,
            null,
            null,
            "测试地址",
            null,
            null,
            LocalDate.now(),
            null
        );
    }

    private CampusCreateRequest campusRequest(String campusCode, String campusName) {
        return new CampusCreateRequest(
            1L,
            campusCode,
            campusName,
            null,
            null,
            null,
            null,
            null,
            null,
            "测试地址",
            null,
            null,
            LocalDate.now(),
            null,
            null
        );
    }
}
