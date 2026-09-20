package com.cornerstone.core.auth.event;

/**
 * 账号自助注销事件。
 *
 * <p>底座在物理删除账号前发布该事件，业务模块通过监听器清理各自域内的关联数据
 * （如学员档案归属、用户-学员关联），从而避免 auth 包直接依赖业务包。</p>
 *
 * <p>监听器默认同步执行，运行在发布方（注销流程）的事务内，清理失败会整体回滚。</p>
 *
 * @param userId 被注销的账号 ID
 */
public record UserAccountDeregisteredEvent(Long userId) {
}
