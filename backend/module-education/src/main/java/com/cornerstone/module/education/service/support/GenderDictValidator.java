package com.cornerstone.module.education.service.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.service.SystemDictService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 性别字典校验：PC 与小程序共用 gender 字典（V51 收口）。
 * 历史两端各自写死选项（PC 小写 male/female、小程序大写 MALE/FEMALE/UNKNOWN），
 * 同一列混出两套值；写入一律经本校验器归一，空值落 NULL。
 */
@Component
public class GenderDictValidator {

    public static final String DICT_TYPE = "gender";

    private final SystemDictService systemDictService;

    public GenderDictValidator(SystemDictService systemDictService) {
        this.systemDictService = systemDictService;
    }

    /** 空值统一存 NULL（性别为选填）；非空值必须命中 gender 字典的启用条目。 */
    public String normalizeAndValidate(String gender) {
        String value = gender == null ? "" : gender.trim();
        if (value.isEmpty()) {
            return null;
        }
        List<String> allowed = systemDictService.listItems(DICT_TYPE).stream()
            .filter(item -> "active".equals(item.status()))
            .map(DictItemView::itemValue)
            .toList();
        if (!allowed.contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "性别取值无效，可选值：" + String.join("/", allowed));
        }
        return value;
    }
}
