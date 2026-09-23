package com.cornerstone.module.education.service.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 通用字典值校验:写入前把"用户可选的业务枚举"(性别/家长关系/报名渠道/用工类型/教师角色等)
 * 统一按系统字典校验,空值一律落 NULL(选填语义),非空值必须命中该字典类型的启用条目。
 * 见 AGENTS「枚举值/字典规范」:各端不再各自写死选项,取值以字典 item_value 为准;
 * 由代码逻辑判定的状态值(启用/停用、课次状态等)不走这里。
 * 与 module-organization 的同名类各自持有一份:共享会上沉到 cornerstone-core,
 * 而底座不许带业务字典类型,education 也不能反向供给 organization。
 * bean 名必须带模块前缀:两个模块同名类默认都叫 dictValueValidator,一起装配时容器会直接
 * ConflictingBeanDefinitionException 起不来(单元测试是 Mockito 手工构造,发现不了)。
 */
@Component("educationDictValueValidator")
public class DictValueValidator {

    private final SystemDictService systemDictService;

    public DictValueValidator(SystemDictService systemDictService) {
        this.systemDictService = systemDictService;
    }

    /**
     * @param dictType   字典类型编码(如 gender、parent_relation)
     * @param fieldLabel 字段中文名,用于异常文案(如"性别")
     * @param value      原始输入,可为 null/空串
     * @return 空值返回 null;非空且命中字典返回去空格后的原值
     * @throws BusinessException 非空值不在字典启用条目内
     */
    public String validate(String dictType, String fieldLabel, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> allowed = systemDictService.listItems(dictType).stream()
            .filter(item -> "active".equals(item.status()))
            .map(DictItemView::itemValue)
            .toList();
        if (!allowed.contains(trimmed)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                fieldLabel + "取值无效，可选值：" + String.join("/", allowed));
        }
        return trimmed;
    }
}
