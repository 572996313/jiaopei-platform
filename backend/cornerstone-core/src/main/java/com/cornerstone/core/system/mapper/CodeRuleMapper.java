package com.cornerstone.core.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.system.domain.entity.CodeRuleEntity;

/**
 * 编号规则的读写。
 *
 * <p>不提供删除入口：资源清单在代码里，规则行只会被"停用"（enabled=0），
 * 所以这张表也不会出现软删墓碑占住 {@code uk_sys_code_rule_resource} 的问题。</p>
 */
public interface CodeRuleMapper extends BaseMapper<CodeRuleEntity> {
}
