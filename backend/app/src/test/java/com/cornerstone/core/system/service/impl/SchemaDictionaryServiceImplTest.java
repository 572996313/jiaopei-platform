package com.cornerstone.core.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * 数据字典的两条纯函数口径：字段名换算与模块归属。
 *
 * <p>这两处一旦漂移，页面会静默错位——列名对不上就整列 Java 信息消失，
 * 模块推错就把表分到不存在的组里，都不会报错，所以单独锁死。</p>
 */
class SchemaDictionaryServiceImplTest {

    private static final String BASE_PACKAGE = "com.cornerstone";

    @Test
    void camelToUnderlineMatchesMybatisPlusNaming() {
        assertThat(SchemaDictionaryServiceImpl.camelToUnderline("id")).isEqualTo("id");
        assertThat(SchemaDictionaryServiceImpl.camelToUnderline("institutionId")).isEqualTo("institution_id");
        assertThat(SchemaDictionaryServiceImpl.camelToUnderline("weeklyLessonsInput"))
            .isEqualTo("weekly_lessons_input");
        assertThat(SchemaDictionaryServiceImpl.camelToUnderline("deleted")).isEqualTo("deleted");
    }

    @Test
    void moduleKeyComesFromPackageShape() {
        assertThat(SchemaDictionaryServiceImpl.moduleKeyOf(
            "com.cornerstone.core.system.domain.entity.ListConfigEntity", BASE_PACKAGE))
            .isEqualTo("core");
        assertThat(SchemaDictionaryServiceImpl.moduleKeyOf(
            "com.cornerstone.module.education.domain.entity.TeachingClassEntity", BASE_PACKAGE))
            .isEqualTo("education");
        assertThat(SchemaDictionaryServiceImpl.moduleKeyOf(
            "com.cornerstone.module.organization.domain.entity.CampusEntity", BASE_PACKAGE))
            .isEqualTo("organization");
    }

    @Test
    void unknownPackageFallsBackToOther() {
        // 下游项目换成自己的包根时，扫描结果若不在基准包下，不能瞎猜模块
        assertThat(SchemaDictionaryServiceImpl.moduleKeyOf(
            "com.example.other.domain.entity.FooEntity", BASE_PACKAGE))
            .isEqualTo("other");
    }

    @Test
    void entityScanMapsTableColumnsToJavaFields() {
        // 反射扫描是字典页"Java 字段 / @Schema"那一半的唯一来源，扫不到不会报错、只会整列空白，
        // 所以这里用真实的实体类跑一遍：表找到了、模块推对了、列名换算对上了。
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            AutoConfigurationPackages.register(context, BASE_PACKAGE);
            context.refresh();
            SchemaDictionaryServiceImpl service = new SchemaDictionaryServiceImpl(
                noDatabase(), context, List.of());

            Map<String, SchemaDictionaryServiceImpl.EntityMeta> entities = service.scanEntities();
            assertThat(entities).containsKeys("jp_class", "jp_student", "sys_menu");

            SchemaDictionaryServiceImpl.EntityMeta classEntity = entities.get("jp_class");
            assertThat(classEntity.moduleKey()).isEqualTo("education");
            assertThat(classEntity.fields().get("weekly_lessons").javaField()).isEqualTo("weeklyLessons");
            assertThat(classEntity.fields().get("total_lessons").javaField()).isEqualTo("totalLessons");
            // BaseEntity 的公共字段要能顺着父类找到
            assertThat(classEntity.fields()).containsKeys("id", "created_at", "deleted");
            // 显式 @TableField("is_default") 的自定义列名不能被驼峰换算覆盖
            assertThat(entities.get("jp_term").fields()).containsKey("is_default");
        }
    }

    /** 只构造 JdbcTemplate，不建立连接：扫描逻辑与库无关 */
    private static DataSource noDatabase() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                throw new IllegalStateException("本测试不连数据库");
            }

            @Override
            public Connection getConnection(String username, String password) {
                throw new IllegalStateException("本测试不连数据库");
            }
        };
    }
}
