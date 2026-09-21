package com.cornerstone.core.common.web;

import java.util.Map;

/**
 * 业务字段中文名登记：把"字段名 → 中文名"这类行业词汇交给业务模块，底座只留自己的。
 *
 * <p>底座在解析客户端传来的业务 id（见 {@link ExternalIdParser} 与
 * {@link FlexibleLongIdDeserializer}）时会把字段名翻成中文放进报错文案。那些名字属于哪个行业，
 * 词表就该放在哪个模块：写进底座等于每个下游项目都在报错消息里看到别人的业务名词
 * （阶段五-0e 之前的底座里躺着 30+ 条校区/班级/学员/排课）。</p>
 *
 * <p>实现类放自己模块的 config 包，随模块开关一起装配；模块没装配时它的字段名在底座侧
 * 查不到，报错文案回落成原始字段名，不影响功能。</p>
 *
 * <p>key 会被归一化（转小写并去掉下划线），所以 {@code campusId} 与 {@code campus_id}
 * 是同一条，只需写一种。</p>
 */
public interface ExternalIdFieldLabelContributor {

    /**
     * 本模块的字段中文名，key 用归一化后的字段名（小写、无下划线），value 是面向用户的中文。
     * 不要包含底座已有字段（institutionId/userId/roleId/menuId 等），重复时以业务登记为准。
     */
    Map<String, String> labels();
}
