package com.cornerstone.core.system;

import com.cornerstone.core.system.dto.MenuCreateRequest;
import com.cornerstone.core.system.dto.NavigationNodeUpdateRequest;
import com.cornerstone.core.system.dto.RoleMenuUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * id 入参精度守护：前端把雪花 id 按字符串下发，后端必须无损接收。
 *
 * <p>这些字段挂了 {@code FlexibleLongIdDeserializer}，但漏挂不会编译失败，只会退回 Jackson 的
 * 隐式字符串转数字（能跑、契约不显式）。这里用裸 ObjectMapper 直接读 JSON，专门守"19 位字符串
 * 不丢精度"这条要求。</p>
 */
class RequestIdDeserializationTest {

    private static final long SNOWFLAKE_ID = 1900000000000000001L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void menuParentIdAcceptsSnowflakeStringWithoutPrecisionLoss() throws Exception {
        MenuCreateRequest request = objectMapper.readValue(
            "{\"parentId\":\"" + SNOWFLAKE_ID + "\",\"menuCode\":\"demo\",\"menuName\":\"演示\",\"menuType\":\"menu\"}",
            MenuCreateRequest.class
        );

        assertThat(request.parentId()).isEqualTo(SNOWFLAKE_ID);
    }

    @Test
    void menuParentIdAcceptsNumberAndNull() throws Exception {
        MenuCreateRequest fromNumber = objectMapper.readValue(
            "{\"parentId\":1,\"menuCode\":\"demo\",\"menuName\":\"演示\",\"menuType\":\"menu\"}",
            MenuCreateRequest.class
        );
        MenuCreateRequest fromNull = objectMapper.readValue(
            "{\"parentId\":null,\"menuCode\":\"demo\",\"menuName\":\"演示\",\"menuType\":\"menu\"}",
            MenuCreateRequest.class
        );

        assertThat(fromNumber.parentId()).isEqualTo(1L);
        assertThat(fromNull.parentId()).isNull();
    }

    @Test
    void navigationNodeIdsAcceptSnowflakeStrings() throws Exception {
        NavigationNodeUpdateRequest request = objectMapper.readValue(
            "{\"id\":\"" + SNOWFLAKE_ID + "\",\"parentId\":\"" + (SNOWFLAKE_ID + 1)
                + "\",\"menuName\":\"页面\",\"sortNo\":1,\"visible\":true}",
            NavigationNodeUpdateRequest.class
        );

        assertThat(request.id()).isEqualTo(SNOWFLAKE_ID);
        assertThat(request.parentId()).isEqualTo(SNOWFLAKE_ID + 1);
    }

    @Test
    void idListAcceptsSnowflakeStrings() throws Exception {
        RoleMenuUpdateRequest request = objectMapper.readValue(
            "{\"menuIds\":[\"" + SNOWFLAKE_ID + "\",\"" + (SNOWFLAKE_ID + 1) + "\"]}",
            RoleMenuUpdateRequest.class
        );

        assertThat(request.menuIds()).containsExactly(SNOWFLAKE_ID, SNOWFLAKE_ID + 1);
    }
}
