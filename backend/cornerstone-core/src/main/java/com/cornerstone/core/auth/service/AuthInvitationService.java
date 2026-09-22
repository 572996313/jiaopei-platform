package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.dto.AccountActivateRequest;
import com.cornerstone.core.auth.dto.ActivationCodeCreateRequest;
import com.cornerstone.core.auth.dto.InvitationAccountSpec;
import com.cornerstone.core.auth.dto.InvitationCreateRequest;
import com.cornerstone.core.auth.dto.InvitationPageQuery;
import com.cornerstone.core.auth.dto.InvitationPrecheckView;
import com.cornerstone.core.auth.dto.InvitationRegisterRequest;
import com.cornerstone.core.auth.dto.InvitationView;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import com.cornerstone.core.common.api.PageResult;

/**
 * 注册凭证（邀请码 / 激活码）服务。
 *
 * <p>底座只提供"一次性凭证 → 校验 → 核销 → 建号或激活"的通用机制：
 * INVITE 凭证决定新账号的机构与角色，ACTIVATE 凭证绑定一个预建的待激活账号；
 * 注册完成后发布 {@code UserRegisteredEvent}，业务语义（如与业务档案绑定）由业务模块监听处理。</p>
 */
public interface AuthInvitationService {

    String KIND_INVITE = "INVITE";
    String KIND_ACTIVATE = "ACTIVATE";
    /** 预建账号的初始状态,凭激活码自设密码后转 active */
    String STATUS_PENDING_ACTIVATION = "pending_activation";
    /** 邀请码注册账号的来源标记(sys_user_account.register_source) */
    String REGISTER_SOURCE_INVITATION = "INVITATION_CODE";

    InvitationView createInvite(InvitationCreateRequest request);

    InvitationView createActivationCode(ActivationCodeCreateRequest request);

    PageResult<InvitationView> pageInvitations(InvitationPageQuery query);

    void disable(Long id);

    InvitationPrecheckView precheck(String code);

    LoginResponse register(InvitationRegisterRequest request);

    /**
     * 邀请码无密码建号：供业务身份（如微信 openid）兑换邀请码开新账号复用。
     * 机构/角色由邀请码固定，账号建成无密码态，发布 UserRegisteredEvent 但不签发令牌，
     * 令牌由调用方按自己的登录态形态自行签发。
     */
    UserAccountEntity registerInvitedAccount(InvitationAccountSpec spec);

    LoginResponse activate(AccountActivateRequest request);
}
