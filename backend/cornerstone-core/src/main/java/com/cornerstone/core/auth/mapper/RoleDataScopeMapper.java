package com.cornerstone.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.domain.RoleDataScopeEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RoleDataScopeMapper extends BaseMapper<RoleDataScopeEntity> {

    /** 保存时整组替换：先物理删除该角色在该机构该维度下的旧行，再插入新行。 */
    @Delete("DELETE FROM sys_role_data_scope"
        + " WHERE role_id = #{roleId} AND institution_id = #{institutionId} AND scope_type = #{scopeType}")
    int physicalDelete(@Param("roleId") Long roleId,
                       @Param("institutionId") Long institutionId,
                       @Param("scopeType") String scopeType);
}
