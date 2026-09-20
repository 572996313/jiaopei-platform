package com.cornerstone.core.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.system.domain.SystemRoleMenuEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SystemRoleMenuMapper extends BaseMapper<SystemRoleMenuEntity> {

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int physicalDeleteByRoleId(@Param("roleId") Long roleId);
}
