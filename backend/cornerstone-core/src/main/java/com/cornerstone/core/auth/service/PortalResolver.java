package com.cornerstone.core.auth.service;

import java.util.Set;

/**
 * 所属端解析器（底座 SPI）。
 *
 * <p>"端"是比角色高一级的维度，决定该用户看到哪一套功能清单（菜单树）。底座只内置
 * {@link #ADMIN}（管理后台）；行业专属端（如小程序的学生端/家长端）由对应业务模块判定，
 * 底座不认识这些取值。多个实现按装配顺序取第一个非空结果，全部返回 {@code null} 时落回 admin。</p>
 */
public interface PortalResolver {

    /** 管理后台端标识（平台账号默认端） */
    String ADMIN = "admin";

    /**
     * 判定用户所属端。
     *
     * @param registerSource 账号注册来源（如后台创建、小程序自助注册）
     * @param roleCodes      用户的角色编码集合
     * @return 端标识；不归本模块管时返回 {@code null}，交给下一个解析器
     */
    String resolve(String registerSource, Set<String> roleCodes);
}
