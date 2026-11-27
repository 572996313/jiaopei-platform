package com.cornerstone.module.education.message;

import com.cornerstone.module.education.message.support.MessageTypeRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件流的代码判定口径：状态归类与深链白名单。
 *
 * <p>深链白名单是防注入面：模板里的 action 由视图数据拼出来，
 * 一旦让 http(s) 或协议相对地址通过，事件卡片就成了开放跳转。</p>
 */
class MessageTypeRulesTest {

    @Test
    void resolvedStatusCoversAutomaticAndManualTerminalStates() {
        assertTrue(MessageTypeRules.isResolvedStatus("resolved"));
        assertTrue(MessageTypeRules.isResolvedStatus("done"));
        assertTrue(MessageTypeRules.isResolvedStatus("closed"));
        assertTrue(MessageTypeRules.isResolvedStatus("expired"));
        assertFalse(MessageTypeRules.isResolvedStatus("active"));
        assertFalse(MessageTypeRules.isResolvedStatus("open"));
        assertFalse(MessageTypeRules.isResolvedStatus("in_progress"));
        assertFalse(MessageTypeRules.isResolvedStatus(null));
    }

    @Test
    void manualStatusesExcludeSystemStatus() {
        assertTrue(MessageTypeRules.isManualStatus("open"));
        assertTrue(MessageTypeRules.isManualStatus("in_progress"));
        assertTrue(MessageTypeRules.isManualStatus("done"));
        assertTrue(MessageTypeRules.isManualStatus("closed"));
        assertFalse(MessageTypeRules.isManualStatus("active"));
        assertFalse(MessageTypeRules.isManualStatus("resolved"));
    }

    @Test
    void actionUrlAcceptsOnlyWhitelistedInternalPaths() {
        assertEquals("/messages", MessageTypeRules.sanitizeActionUrl("/messages"));
        assertEquals("/schedule?date=2026-11-26&focus=pending",
            MessageTypeRules.sanitizeActionUrl("/schedule?date=2026-11-26&focus=pending"));
        assertEquals("/fee-ledger/1001", MessageTypeRules.sanitizeActionUrl("/fee-ledger/1001"));
        assertEquals("/schedule", MessageTypeRules.sanitizeActionUrl("  /schedule  "));
    }

    @Test
    void actionUrlRejectsExternalProtocolRelativeAndUnknownPaths() {
        // 开放跳转与协议绕过
        assertNull(MessageTypeRules.sanitizeActionUrl("https://evil.example.com"));
        assertNull(MessageTypeRules.sanitizeActionUrl("//evil.example.com/messages"));
        assertNull(MessageTypeRules.sanitizeActionUrl("/\\evil.example.com"));
        assertNull(MessageTypeRules.sanitizeActionUrl("javascript:alert(1)"));
        // 只有前缀命中才算：/schedule-x 不是 /schedule 的子路径
        assertNull(MessageTypeRules.sanitizeActionUrl("/scheduler"));
        assertNull(MessageTypeRules.sanitizeActionUrl("/system/users"));
        // 空值与超长
        assertNull(MessageTypeRules.sanitizeActionUrl(null));
        assertNull(MessageTypeRules.sanitizeActionUrl("   "));
        assertNull(MessageTypeRules.sanitizeActionUrl("/messages?" + "a".repeat(500)));
    }
}
