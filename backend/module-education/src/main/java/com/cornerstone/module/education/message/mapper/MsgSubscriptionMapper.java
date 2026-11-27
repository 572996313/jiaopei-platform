package com.cornerstone.module.education.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.message.domain.entity.MsgSubscriptionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface MsgSubscriptionMapper extends BaseMapper<MsgSubscriptionEntity> {

    /**
     * 整表替换订阅时物理删除旧行：逻辑删除会让 (user_id, rule_type, rule_value, deleted)
     * 唯一键不断堆积历史行，改成物理删除最干净；表上没有子外键引用。
     */
    @Delete("DELETE FROM jp_msg_subscription WHERE user_id = #{userId}")
    int physicalDeleteByUser(@Param("userId") Long userId);
}
