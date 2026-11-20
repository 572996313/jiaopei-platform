package com.cornerstone.core.auth;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 登录快照新增数据范围字段后的兼容性：旧代码路径与 Redis 里的历史 JSON 都不能因此炸掉。
 */
class RoleDataScopeSnapshotTest {

    @Test
    void legacySevenArgConstructorLeavesDataScopeUnset() {
        // 十几处测试与内部调用还在用 7 参构造，没显式给范围时必须等价于"不限制"
        AuthenticatedUser user = new AuthenticatedUser(1L, 2L, "admin",
            Set.of("ORG_ADMIN"), Set.of("system:role:read"), Boolean.FALSE, "admin");

        assertNull(user.dataScopeKey());
        assertNull(user.dataScopeIds());
    }

    @Test
    void redisJsonWrittenBeforeUpgradeDeserializesToUnrestricted() throws Exception {
        // 上线前签发的会话快照没有 dataScopeKey/dataScopeIds，引用类型缺失反序列化为 null
        String legacyJson = "{\"userId\":1,\"orgId\":2,\"username\":\"admin\","
            + "\"roles\":[\"ORG_ADMIN\"],\"permissions\":[\"system:role:read\"],"
            + "\"forceChangePassword\":false,\"portal\":\"admin\"}";

        AuthenticatedUser user = new ObjectMapper().readValue(legacyJson, AuthenticatedUser.class);

        assertEquals("admin", user.username());
        assertNull(user.dataScopeKey());
        assertNull(user.dataScopeIds());
    }

    @Test
    void scopedSnapshotRoundTripsThroughJackson() throws Exception {
        AuthenticatedUser source = new AuthenticatedUser(1L, 2L, "admin", Set.of("ORG_ADMIN"), Set.of(),
            Boolean.FALSE, "admin", "CAMPUS", Set.of(11L, 12L));

        ObjectMapper objectMapper = new ObjectMapper();
        AuthenticatedUser restored = objectMapper.readValue(objectMapper.writeValueAsString(source),
            AuthenticatedUser.class);

        assertEquals("CAMPUS", restored.dataScopeKey());
        assertEquals(Set.of(11L, 12L), restored.dataScopeIds());
    }
}
