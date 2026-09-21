package com.cornerstone.architecture;

import com.cornerstone.core.common.web.ExternalIdFieldLabelContributor;
import com.cornerstone.core.common.web.ExternalIdParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报错词表边界守护（纯反射，不起上下文）。
 *
 * <p>底座把客户端传来的业务 id 翻成中文放进报错文案。那些中文词属于哪个行业，就该放在哪个模块：
 * 阶段五-0e 之前底座里躺着 30+ 条校区/班级/学员/课次，任何下游项目的用户都能在错误消息里
 * 看到教培名词（{@code parentId} 更糟——底座菜单的"上级"被写成"家长ID"）。</p>
 *
 * <p>除了词表本身，这里还扫底座产物（class 常量池 + 随 jar 发布的基线脚本）：名词漏进
 * {@code @Schema(example=...)} 或种子数据，效果和在词表里写死是一样的。</p>
 */
class ExternalIdVocabularyGuardTest {

    /** 教培专有名词：出现在底座词表里即为越界。 */
    private static final List<String> TRAINING_TERMS =
        List.of("校区", "班级", "学员", "教师", "课程", "课次", "教室", "学期", "时段", "家长",
            "排课", "考勤", "报名", "教培", "教务");

    /**
     * 已应用过的基线脚本不能回改：改一个字就换 Flyway checksum，跑过它的库下次启动直接
     * {@code Migration checksum mismatch}。所以 V101 里那条角色表单提示语只能留着，由 V102 中性化
     * （空库路径 V101 → V102 的终态是干净的）。豁免精确到"文件:名词"，新引进的名词照样拦。
     */
    private static final Set<String> APPLIED_BASELINE_EXEMPTIONS =
        Set.of("V101__cornerstone_core_seed.sql:校区");

    @Test
    void coreVocabularyMustOnlyCoverCornerstoneConcepts() {
        assertThat(ExternalIdParser.coreLabels().keySet())
            .as("底座词表只留机构（=租户）/角色/菜单")
            .containsExactlyInAnyOrder("institutionid", "roleid", "menuid");

        assertThat(ExternalIdParser.coreLabels().values())
            .as("底座不得认识教培名词")
            .noneMatch(label -> TRAINING_TERMS.stream().anyMatch(label::contains));
    }

    @Test
    void businessFieldLabelsMustComeFromModuleContributors() {
        Map<String, String> contributed = contributedLabels();

        assertThat(contributed).as("一个业务登记方都没有，本测试随之失效").isNotEmpty();
        // 业务侧漏登记了字段名不会报错，只是报错文案退回英文，所以在这里点名必须存在的几条。
        assertThat(contributed)
            .containsEntry("campusid", "校区ID")
            .containsEntry("classid", "班级ID")
            .containsEntry("studentid", "学员ID");
    }

    @Test
    void contributorsMustNotShadowCoreLabels() {
        Map<String, String> contributed = contributedLabels();

        assertThat(contributed.keySet())
            .as("底座同名 key 优先级更高，业务方重复登记属于误解契约")
            .doesNotContainAnyElementsOf(ExternalIdParser.coreLabels().keySet());
    }

    @Test
    void contributorKeysMustBeNormalizedCamelCase() {
        // 查找前会先归一化（小写、去下划线），所以同一个字段的驼峰与下划线两种写法是冗余条目。
        assertThat(contributedLabels().keySet())
            .as("key 直接写字段名的驼峰形式即可，下划线变体会被归一成同一条")
            .noneMatch(key -> key.contains("_"));
    }

    @Test
    void coreArtifactsMustNotCarryTrainingVocabulary() throws IOException {
        // 词表挪走只解决了一处。底座产物（class 常量池里的 @Schema example、随 jar 发布的基线脚本）
        // 才是下游真正拿到的东西：那里留着教培名词，新项目的第一天就到处冒出"教室/校区"。
        List<String> violations = new ArrayList<>();
        for (Class<?> type : coreClasses()) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            URL location = type.getResource(resource);
            if (location == null) {
                throw new IllegalStateException("读不到字节码:" + resource);
            }
            if (location.toString().contains("test-classes")) {
                continue;  // 测试类不进 jar，不参与产物口径
            }
            try (InputStream in = type.getResourceAsStream(resource)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                TRAINING_TERMS.stream().filter(text::contains).forEach(term ->
                    violations.add(type.getName() + " 含\"" + term + "\""));
            }
        }
        for (org.springframework.core.io.Resource resource
            : new PathMatchingResourcePatternResolver().getResources("classpath*:cornerstone/**/*.sql")) {
            String sql = dataLines(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            TRAINING_TERMS.stream()
                .filter(sql::contains)
                .map(term -> resource.getFilename() + ":" + term)
                .filter(entry -> !APPLIED_BASELINE_EXEMPTIONS.contains(entry))
                .forEach(entry -> violations.add(entry + "（基线脚本含教培名词）"));
        }

        assertThat(violations).as("底座产物不得带教培名词").isEmpty();
    }

    private Set<Class<?>> coreClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        Set<Class<?>> types = new LinkedHashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.cornerstone.core")) {
            try {
                types.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("底座类加载不到:" + candidate.getBeanClassName(), e);
            }
        }
        assertThat(types).as("扫不到底座类，词表守护随之失效").isNotEmpty();
        return types;
    }

    /** 去掉整行注释（SQL 的 {@code --}）：注释里提"教培"是在说明两条路径的关系，不算夹带。 */
    private String dataLines(String script) {
        StringBuilder sb = new StringBuilder();
        for (String line : script.split("\n")) {
            if (!line.trim().startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private Map<String, String> contributedLabels() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ExternalIdFieldLabelContributor.class));
        Set<Class<?>> types = new LinkedHashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.cornerstone")) {
            try {
                types.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("词表登记方扫不到:" + candidate.getBeanClassName(), e);
            }
        }

        Map<String, String> merged = new HashMap<>();
        for (Class<?> type : types) {
            try {
                ExternalIdFieldLabelContributor contributor =
                    (ExternalIdFieldLabelContributor) type.getDeclaredConstructor().newInstance();
                contributor.labels().forEach((field, label) ->
                    merged.put(ExternalIdParser.normalize(field), label));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("词表登记方必须是无参可实例化的:" + type.getName(), e);
            }
        }
        return merged;
    }
}
