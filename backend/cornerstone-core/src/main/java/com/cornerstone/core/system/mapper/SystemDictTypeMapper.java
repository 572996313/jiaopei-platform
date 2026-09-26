package com.cornerstone.core.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.system.domain.SystemDictTypeEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SystemDictTypeMapper extends BaseMapper<SystemDictTypeEntity> {

    /**
     * 物理删除:唯一键含 deleted 列,软删墓碑(deleted=1)会让同键"删除→重建→再删"的
     * 第二次删除撞唯一键(每键仅容一条墓碑)。删除动作的审计由操作日志承担。
     */
    @Delete("DELETE FROM sys_dict_type WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
