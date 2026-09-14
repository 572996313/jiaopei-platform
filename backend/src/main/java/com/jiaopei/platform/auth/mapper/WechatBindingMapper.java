package com.jiaopei.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.auth.domain.WechatBindingEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface WechatBindingMapper extends BaseMapper<WechatBindingEntity> {

    /**
     * 物理删除某用户的微信绑定。注销时需真正删行,否则逻辑删除(deleted=1)的行仍占用
     * openid 唯一键,导致同一微信注销后无法重新注册。
     */
    @Delete("DELETE FROM jp_wechat_binding WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}