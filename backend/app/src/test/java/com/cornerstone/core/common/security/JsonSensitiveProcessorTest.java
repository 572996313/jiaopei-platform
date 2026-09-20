package com.cornerstone.core.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSensitiveProcessorTest {

    private final JsonSensitiveProcessor processor =
        new JsonSensitiveProcessor(new SensitiveCipher("test-key-for-processor"));

    @Test
    void masksByFieldNameKeywords() {
        Map<String, Object> node = Map.of(
            "phone", "13812341234",
            "idCard", "110101199003074512",
            "password", "abc123",
            "email", "a@example.com",
            "realName", "张伟",
            "parentName", "张明",
            "address", "北京市朝阳区建国路88号"
        );

        Object masked = processor.process(node, SensitiveStrategy.MASK);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) masked;
        assertThat(result.get("phone")).isEqualTo("138****1234");
        // 字段名含大小写规范化:<camelCase idCard 转小写下划线后仍命中 id_card
        assertThat(result.get("idCard")).isEqualTo("110***********4512");
        // 字段名是驼峰 realName → real_name,name 规则命中
        assertThat(result.get("realName")).isEqualTo("张*");
        // 驼峰 parentName → parent_name,规则命中
        assertThat(result.get("parentName")).isEqualTo("张*");
        assertThat(result.get("password")).isEqualTo("******");
        // 字段名 email 命中邮箱规则
        assertThat(result.get("email")).isEqualTo("a***@example.com");
        assertThat(String.valueOf(result.get("address"))).contains("*");
    }

    @Test
    void masksByValueFormatWhenFieldNameMisses() {
        Map<String, Object> node = Map.of(
            "remark", "13612345678",     // 字段名不敏感,值命中手机号
            "note", "110101199003074512100101101911" // 长数字不匹配卡号/身份证,保持原样
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processor.process(node, SensitiveStrategy.MASK);
        assertThat(result.get("remark")).isEqualTo("136****5678");
        assertThat(result.get("note")).isEqualTo("110101199003074512100101101911");
    }

    @Test
    void masksNestedListsAndMaps() {
        Map<String, Object> node = Map.of(
            "students", List.of(
                Map.of("studentName", "李雷", "phone", "13900001111"),
                Map.of("studentName", "韩梅梅", "phone", "13700002222")
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processor.process(node, SensitiveStrategy.MASK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) result.get("students");
        assertThat(students.get(0).get("studentName")).isEqualTo("李*");
        assertThat(students.get(1).get("phone")).isEqualTo("137****2222");
    }

    @Test
    void plainStrategyKeepsOriginal() {
        Map<String, Object> node = Map.of("phone", "13812341234");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processor.process(node, SensitiveStrategy.PLAIN);
        assertThat(result.get("phone")).isEqualTo("13812341234");
    }

    @Test
    void masksApplicationJsonTreeWithCamelCaseKeys() {
        // 模拟切面把 record 序列化成 JSON 树后的结构(键为驼峰)
        Map<String, Object> node = Map.of(
            "initialPassword", "Init@123456",
            "phone", "13800138000",
            "displayName", "留痕测试",
            "institutionId", 1
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processor.process(node, SensitiveStrategy.MASK);
        assertThat(result.get("initialPassword")).isEqualTo("******");
        assertThat(result.get("phone")).isEqualTo("138****8000");
        // 业务名称与 ID 保持原样
        assertThat(result.get("displayName")).isEqualTo("留痕测试");
        assertThat(result.get("institutionId")).isEqualTo(1);
    }

    @Test
    void nonSensitiveValuesUnchanged() {
        Map<String, Object> node = Map.of(
            "courseName", "少儿英语启蒙班",
            "pageSize", 10,
            "flag", true
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processor.process(node, SensitiveStrategy.MASK);
        assertThat(result.get("courseName")).isEqualTo("少儿英语启蒙班");
        assertThat(result.get("pageSize")).isEqualTo(10);
        assertThat(result.get("flag")).isEqualTo(true);
    }
}