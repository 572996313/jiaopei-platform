package com.cornerstone.module.education.service.impl;

import com.cornerstone.core.auth.service.PortalResolver;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 小程序端归属判定（教务模块提供的底座 {@link PortalResolver} 实现）。
 *
 * <p>微信自助注册的账号按所持小程序角色落到学生端/家长端；其余账号一律返回 null，
 * 由底座落回管理后台端。关闭 {@code cornerstone.modules.enabled.education} 后本实现不注册，
 * 小程序角色用户退化为 admin 端（该场景下小程序本身也不可用）。</p>
 *
 * <p>本类同时是小程序身份标识（注册来源、端、角色编码）的唯一出处，
 * 注册与判端两处都从这里取值，避免字符串各写一份。</p>
 */
@Component
public class MiniappPortalResolver implements PortalResolver {

    /** 小程序自助注册的账号来源标识，写入 sys_user.register_source */
    public static final String REGISTER_SOURCE_MINIAPP = "WECHAT_MINIAPP";

    /** 学生端标识，与 sys_menu.portal 的取值一致 */
    public static final String PORTAL_STUDENT = "student";

    /** 家长端标识，与 sys_menu.portal 的取值一致 */
    public static final String PORTAL_PARENT = "parent";

    /** 学生端账号角色 */
    public static final String ROLE_CODE_STUDENT = "MINIAPP_STUDENT";

    /** 家长端账号角色 */
    public static final String ROLE_CODE_PARENT = "MINIAPP_PARENT";

    @Override
    public String resolve(String registerSource, Set<String> roleCodes) {
        if (!REGISTER_SOURCE_MINIAPP.equals(registerSource)) {
            return null;
        }
        if (roleCodes.contains(ROLE_CODE_STUDENT)) {
            return PORTAL_STUDENT;
        }
        if (roleCodes.contains(ROLE_CODE_PARENT)) {
            return PORTAL_PARENT;
        }
        return null;
    }
}
