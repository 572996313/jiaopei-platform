package com.cornerstone.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.domain.AuthInvitationEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AuthInvitationMapper extends BaseMapper<AuthInvitationEntity> {

    /**
     * 核销一次凭证用量。状态、有效期、剩余次数全部写进 WHERE 条件并原子递增 used_count，
     * 返回 0 表示凭证不可用——先查后扣的并发窗口由此兜住,不会超发。
     */
    @Update("UPDATE sys_auth_invitation SET used_count = used_count + 1 "
        + "WHERE id = #{id} AND status = 'active' AND deleted = 0 "
        + "AND (expires_at IS NULL OR expires_at > NOW()) "
        + "AND (max_uses IS NULL OR used_count < max_uses)")
    int tryConsume(@Param("id") Long id);

    /**
     * 物理删除某账号名下的注册凭证（激活码的 user_id 外键指向 sys_user_account）。
     * 账号注销时必须先清这些行，否则删账号会被外键 1451 拒掉。
     */
    @Delete("DELETE FROM sys_auth_invitation WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}
