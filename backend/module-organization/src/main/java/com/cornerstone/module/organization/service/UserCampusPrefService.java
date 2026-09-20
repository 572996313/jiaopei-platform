package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.dto.UserCampusPrefView;
import java.util.List;

/**
 * 用户默认校区偏好服务。
 *
 * <p>"某个账号默认看哪个校区"是教务概念，底座用户表不再有这一列，改由本模块维护。</p>
 */
public interface UserCampusPrefService {

    /** 按校区列出默认校区为它的账号;campusId 为空时返回机构内全部已设置的偏好。 */
    List<UserCampusPrefView> listByCampus(Long institutionId, Long campusId);

    /** 读取指定用户的默认校区偏好,未设置时 campusId 为 null。 */
    UserCampusPrefView preference(Long institutionId, Long userId);

    /** 设置或清除指定用户的默认校区。 */
    UserCampusPrefView save(Long institutionId, Long userId, Long campusId);
}
