package com.jiaopei.platform.auth.service;

/**
 * 校区归属校验器（底座 SPI）。
 *
 * <p>底座在创建/更新系统用户时需要校验"默认校区属于所选机构"，但校区是业务模块概念，
 * auth 包不得直接依赖。由业务模块（organization）提供实现；
 * 未挂载校区概念的项目无实现 Bean，底座自动跳过该校验。</p>
 */
public interface CampusBelongingValidator {

    /**
     * 校验校区归属于指定机构，不属于时抛出业务异常。
     *
     * @param campusId      待校验的校区 ID（可为 null，null 时直接通过）
     * @param institutionId 目标机构 ID
     */
    void assertCampusBelongsToInstitution(Long campusId, Long institutionId);
}
