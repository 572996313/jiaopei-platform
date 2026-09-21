package com.cornerstone.core.common.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 启动期把各模块登记的字段中文名并进底座词表，供静态的 {@link ExternalIdParser} 读取。
 *
 * <p>装配期一次性写入、之后只读，是因为报错文案要在 Jackson 反序列化里用，而那个反序列化器
 * 由 Jackson 自己 new 出来、拿不到容器。用 {@link ObjectProvider} 而不是 {@code List} 注入：
 * 干净的新项目一个业务模块都没有，零个候选时 {@code List} 注入会让启动直接失败。</p>
 */
@Component
public class ExternalIdLabelRegistry {

    public ExternalIdLabelRegistry(ObjectProvider<ExternalIdFieldLabelContributor> contributors) {
        Map<String, String> business = new LinkedHashMap<>();
        contributors.forEach(contributor -> contributor.labels()
            .forEach((field, label) -> business.put(ExternalIdParser.normalize(field), label)));
        ExternalIdParser.useLabels(business);
    }
}
