package com.cornerstone.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.domain.AuthInvitationEntity;
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
}
