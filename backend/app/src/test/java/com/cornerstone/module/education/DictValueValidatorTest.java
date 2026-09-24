package com.cornerstone.module.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 字典值双向翻译：入库认 item_value，给人看的地方认 item_name。
 *
 * <p>导入模板的下拉、导出列、报错文案三处都给中文，只有落库值是 item_value；这里把三条口径钉住，
 * 免得哪一处悄悄改回英文代码，操作员对着中文表头不知道填什么。</p>
 */
class DictValueValidatorTest {

    private final SystemDictService systemDictService = mock(SystemDictService.class);

    private DictValueValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DictValueValidator(systemDictService);
        when(systemDictService.listItems("gender")).thenReturn(List.of(
            item("male", "男", "active"),
            item("female", "女", "active"),
            item("other", "其他", "disabled")));
    }

    @Test
    void acceptsOnlyItemValuesAndMapsBothDirections() {
        assertEquals("male", validator.validate("gender", "性别", " male "));
        assertNull(validator.validate("gender", "性别", "  "));
        assertEquals(Map.of("male", "男", "female", "女"), validator.labels("gender"));
        assertEquals(Map.of("男", "male", "女", "female"), validator.valueByLabel("gender"));
    }

    /** 停用的条目不出现在任何一张表里：改了值也进不了库 */
    @Test
    void disabledItemsAreInvisible() {
        assertThrows(BusinessException.class, () -> validator.validate("gender", "性别", "other"));
        assertTrue(validator.valueByLabel("gender").containsKey("男"));
        assertEquals(2, validator.labels("gender").size());
    }

    @Test
    void errorListsChineseNamesWithTheirCodes() {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> validator.validate("gender", "性别", "男生"));

        // 报错要能对着模板下拉看：只列 male/female 的话，填"男生"的人不知道自己哪儿错
        assertTrue(exception.getMessage().contains("性别取值无效"), exception.getMessage());
        assertTrue(exception.getMessage().contains("男(male)/女(female)"), exception.getMessage());
    }

    private DictItemView item(String value, String name, String status) {
        return new DictItemView(1L, 1L, value, name, value, 1, status, null);
    }
}
