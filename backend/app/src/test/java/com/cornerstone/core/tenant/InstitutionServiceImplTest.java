package com.cornerstone.core.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.tenant.domain.InstitutionEntity;
import com.cornerstone.core.tenant.dto.CurrentInstitutionUpdateRequest;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import com.cornerstone.core.tenant.service.impl.InstitutionServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionServiceImplTest {

    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final InstitutionServiceImpl service = new InstitutionServiceImpl(institutionMapper);

    @Test
    void updateCurrentInstitutionWritesAllowedProfileFieldsOnly() {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setId(1L);
        entity.setOrgCode("SYS-DEFAULT");
        entity.setOrgName("系统默认机构");
        entity.setStatus("active");
        when(institutionMapper.selectById(1L)).thenReturn(entity);
        when(institutionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        var view = service.updateCurrentInstitution(1L, new CurrentInstitutionUpdateRequest(
            "新机构名称",
            "新简称",
            "张老师",
            "13900000000",
            "新地址",
            "active",
            "更新备注"
        ));

        assertEquals("SYS-DEFAULT", view.orgCode());
        assertEquals("新机构名称", view.name());
        assertEquals("新简称", view.shortName());
        assertEquals("张老师", view.contactName());
        verify(institutionMapper).updateById(entity);
    }

    @Test
    void updateCurrentInstitutionRejectsBlankName() {
        InstitutionEntity entity = new InstitutionEntity();
        entity.setId(1L);
        entity.setOrgCode("SYS-DEFAULT");
        entity.setOrgName("系统默认机构");
        when(institutionMapper.selectById(1L)).thenReturn(entity);

        assertThrows(BusinessException.class, () -> service.updateCurrentInstitution(1L,
            new CurrentInstitutionUpdateRequest(" ", null, null, null, null, null, null)));
    }

    @Test
    void getCurrentInstitutionRejectsMissingInstitution() {
        when(institutionMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getCurrentInstitution(99L));
    }
}
