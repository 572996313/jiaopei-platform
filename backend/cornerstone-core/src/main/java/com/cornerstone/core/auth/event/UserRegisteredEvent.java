package com.cornerstone.core.auth.event;

/**
 * 账号注册完成事件。
 *
 * <p>底座在邀请码注册 / 预建账号激活流程末尾（建号、挂角色、核销凭证之后）发布该事件，
 * 业务模块通过监听器完成各自域内的关联动作（如把账号与业务档案绑定），
 * 从而避免 auth 包直接依赖业务包。</p>
 *
 * <p>监听器默认同步执行，运行在注册流程的事务内，监听失败会整体回滚（凭证核销一并回退）。</p>
 *
 * @param userId        新注册（或刚被激活）的账号 ID
 * @param institutionId 账号归属机构 ID
 * @param kind          注册凭证类型(INVITE=邀请码注册,ACTIVATE=预建账号激活)
 * @param roleCode      注册时自动分配的角色编码,激活流程为空
 * @param invitationId  使用的凭证 ID
 */
public record UserRegisteredEvent(
    Long userId,
    Long institutionId,
    String kind,
    String roleCode,
    Long invitationId
) {
}
