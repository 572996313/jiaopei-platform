package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.dto.AuthProviderAdminView;
import com.cornerstone.core.auth.dto.AuthProviderSaveRequest;
import java.util.List;

/**
 * 第三方登录渠道配置的管理端服务（新增/编辑/删除）。
 *
 * <p>登录侧读取走 {@code AuthProviderRegistry}；这里只负责维护行数据，
 * 写入时把客户端密钥加密，读取时不回传密钥本身。</p>
 */
public interface AuthProviderAdminService {

    List<AuthProviderAdminView> listProviders();

    AuthProviderAdminView create(AuthProviderSaveRequest request);

    AuthProviderAdminView update(Long id, AuthProviderSaveRequest request);

    void delete(Long id);
}
