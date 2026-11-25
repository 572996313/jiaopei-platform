package com.cornerstone.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.domain.ThirdPartyAuthProviderEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ThirdPartyAuthProviderMapper extends BaseMapper<ThirdPartyAuthProviderEntity> {

    /**
     * 删除渠道走物理删：这张表没有外键引用（身份绑定按 provider_code 字符串关联），
     * 留逻辑删墓碑没有留存价值，反而会让 (provider_code, deleted) 唯一键只容得下一次重建。
     */
    @Delete("DELETE FROM sys_auth_provider WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
