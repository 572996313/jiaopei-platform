package com.cornerstone.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 迁移脚本门禁：新增迁移必须满足生产兼容与中文注释规范。
 *
 * <p>守的是「以后新加的脚本」，不是全部存量。写这道门禁时 app 侧最大版本是 V118、core 侧是 V120，
 * 存量脚本里还有 36 处 {@code ADD COLUMN} 没写 COMMENT（多为 V103~V105 的唯一键守卫列）。
 * 已上生产的脚本改一个字符就变 checksum、生产会拒绝启动，所以存量不回改，门禁从下一个版本起生效。</p>
 *
 * <p>覆盖的规则见 AGENTS.md「迁移脚本生产兼容检查」与「数据库表 / 字段中文注释规范」：
 * MySQL 8.x 普通 DDL、建表显式排序规则、表/列中文注释、字符列显式 COLLATE、
 * 禁用 MariaDB 的 {@code IF [NOT] EXISTS} 方言。检查全部是纯文本，不连数据库。</p>
 */
class MigrationScriptGuardTest {

    /** app 侧（教培业务表）门禁起始版本：写门禁时最新是 V118。 */
    private static final int APP_ENFORCED_FROM = 119;
    /** core 侧（底座基线表）门禁起始版本：写门禁时最新是 V120。 */
    private static final int CORE_ENFORCED_FROM = 121;

    private static final Pattern FILE_NAME = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern CTE = Pattern.compile("(?is)\\bWITH\\s+[A-Za-z0-9_]+\\s+AS\\s*\\(");
    private static final Pattern WINDOW_FUNCTION = Pattern.compile("(?is)\\bOVER\\s*\\(");
    private static final Pattern IF_FUNCTION = Pattern.compile("(?is)\\bIF\\s*\\(");
    private static final Pattern MARIADB_DDL = Pattern.compile(
        "(?is)\\b(ADD|DROP|MODIFY)\\s+(COLUMN\\s+|INDEX\\s+)?IF\\s+(NOT\\s+)?EXISTS\\b");
    private static final Pattern CHARACTER_TYPE = Pattern.compile(
        "(?is)\\b(VARCHAR|CHAR|TEXT|TINYTEXT|MEDIUMTEXT|LONGTEXT|ENUM|SET)\\b");
    /**
     * 排序规则的两种合法写法都要认：表选项里的 {@code COLLATE=utf8mb4_unicode_ci}
     * 与列定义里的 {@code COLLATE utf8mb4_unicode_ci}。早先只匹配空格写法，
     * 表级显式排序规则（等号写法）会被误判成「只写了 CHARSET」。
     */
    private static final Pattern UNICODE_COLLATE = Pattern.compile(
        "(?is)\\bCOLLATE\\s*=?\\s*UTF8MB4_UNICODE_CI\\b");

    @Test
    void newMigrationsMustFollowProductionRules() throws IOException {
        List<String> violations = new ArrayList<>();
        violations.addAll(scan(appMigrationDir(), APP_ENFORCED_FROM));
        violations.addAll(scan(coreMigrationDir(), CORE_ENFORCED_FROM));
        assertThat(violations)
            .as("新增迁移脚本不满足规范，先读 docs/新增字段操作手册.md 与 AGENTS.md「迁移脚本生产兼容检查」")
            .isEmpty();
    }

    @Test
    void happyPathScriptIsAccepted() {
        String sql = """
            -- 一条合规的新迁移
            CREATE TABLE `demo_table` (
              `id` bigint NOT NULL COMMENT '主键ID',
              `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
              `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0=未删除,1=已删除)',
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_demo_name` (`name`,`deleted`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示表(括号不能干扰表注释判断)';

            ALTER TABLE `demo_table`
              ADD COLUMN `code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '编码',
              ADD COLUMN `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号';
            """;

        assertThat(check(sql)).isEmpty();
    }

    @Test
    void addedColumnWithoutCommentIsRejected() {
        assertThat(check("ALTER TABLE `t` ADD COLUMN `code` int NOT NULL DEFAULT 0;"))
            .anyMatch(v -> v.contains("ADD COLUMN 缺少 COMMENT"));
    }

    @Test
    void addedCharacterColumnWithoutExplicitCollateIsRejected() {
        assertThat(check("ALTER TABLE `t` ADD COLUMN `code` varchar(32) NOT NULL COMMENT '编码';"))
            .anyMatch(v -> v.contains("字符列必须显式 COLLATE"));
    }

    @Test
    void createTableWithoutTableCommentIsRejected() {
        String sql = """
            CREATE TABLE `t` (
              `id` bigint NOT NULL COMMENT '主键ID'
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
        assertThat(check(sql)).anyMatch(v -> v.contains("表级 COMMENT"));
    }

    @Test
    void createTableWithoutExplicitCollateIsRejected() {
        String sql = """
            CREATE TABLE `t` (
              `id` bigint NOT NULL COMMENT '主键ID'
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试表';
            """;
        assertThat(check(sql)).anyMatch(v -> v.contains("COLLATE=utf8mb4_unicode_ci"));
    }

    @Test
    void createTableWithExplicitEqualsCollateIsAccepted() {
        // 表级等号写法是 MySQL 的常规写法，字符列继承表排序规则，不应被误判
        String sql = """
            CREATE TABLE `t` (
              `id` bigint NOT NULL COMMENT '主键ID',
              `name` varchar(64) NOT NULL COMMENT '名称'
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试表';
            """;
        assertThat(check(sql)).isEmpty();
    }

    @Test
    void createTableColumnWithoutCommentIsRejected() {
        String sql = """
            CREATE TABLE `t` (
              `id` bigint NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试表';
            """;
        assertThat(check(sql)).anyMatch(v -> v.contains("建表列缺少 COMMENT"));
    }

    @Test
    void bannedConstructsAreRejected() {
        assertThat(check("WITH x AS (SELECT 1) SELECT * FROM x;"))
            .anyMatch(v -> v.contains("CTE"));
        assertThat(check("SELECT ROW_NUMBER() OVER () FROM t;"))
            .anyMatch(v -> v.contains("窗口函数"));
        assertThat(check("SELECT IF(1 = 1, 'a', 'b');"))
            .anyMatch(v -> v.contains("IF()"));
        assertThat(check("ALTER TABLE `t` ADD COLUMN IF NOT EXISTS `c` int COMMENT '列';"))
            .anyMatch(v -> v.contains("MariaDB"));
        assertThat(check("ALTER TABLE `t` MODIFY COLUMN `c` varchar(8) COLLATE utf8mb4_0900_ai_ci COMMENT '列';"))
            .anyMatch(v -> v.contains("utf8mb4_0900"));
    }

    // ------------------------------------------------------------------ 扫描

    private static List<String> scan(Path dir, int enforcedFrom) throws IOException {
        assertThat(Files.isDirectory(dir))
            .as("迁移目录不存在：" + dir.toAbsolutePath())
            .isTrue();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                .sorted()
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    Matcher matcher = FILE_NAME.matcher(fileName);
                    if (!matcher.matches()) {
                        violations.add(fileName + "：文件名应为 V<版本号>__<中文或英文说明>.sql");
                        return;
                    }
                    if (Integer.parseInt(matcher.group(1)) < enforcedFrom) {
                        return;
                    }
                    String sql;
                    try {
                        sql = Files.readString(path, StandardCharsets.UTF_8);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                    for (String violation : check(sql)) {
                        violations.add(fileName + "：" + violation);
                    }
                });
        }
        return violations;
    }

    private static Path appMigrationDir() {
        return migrationsDir("db/migration", "src/main/resources/db/migration");
    }

    private static Path coreMigrationDir() {
        return migrationsDir("cornerstone/migration",
            "../cornerstone-core/src/main/resources/cornerstone/migration");
    }

    /**
     * 测试从 app 模块启动，首选 classpath（target/classes），
     * 依赖被打成 jar 时 classpath 不是 file 协议，退回模块相对路径。
     */
    private static Path migrationsDir(String classpathName, String fallback) {
        URL url = MigrationScriptGuardTest.class.getClassLoader().getResource(classpathName);
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException ignored) {
                // 退回相对路径
            }
        }
        return Paths.get(fallback);
    }

    // ------------------------------------------------------------------ 规则

    /**
     * 检查一个脚本的全部违规点。抽成静态方法是为了让上面的样例测试能直接喂合成 SQL，
     * 不必为了验证门禁本身再造一批迁移文件。
     */
    static List<String> check(String rawSql) {
        List<String> violations = new ArrayList<>();
        String sql = stripComments(rawSql);
        String code = stripStringLiterals(sql);
        String upperCode = code.toUpperCase(Locale.ROOT);

        if (upperCode.contains("UTF8MB4_0900")) {
            violations.add("禁止 8.0 专属排序规则 utf8mb4_0900_*，统一 utf8mb4_unicode_ci");
        }
        if (upperCode.contains("JSON_TABLE")) {
            violations.add("暂不使用 JSON_TABLE");
        }
        if (CTE.matcher(code).find()) {
            violations.add("暂不使用 CTE（WITH ... AS）");
        }
        if (WINDOW_FUNCTION.matcher(code).find()) {
            violations.add("暂不使用窗口函数（OVER(...)）");
        }
        if (IF_FUNCTION.matcher(code).find()) {
            violations.add("暂不使用 IF() 函数，条件请用 CASE WHEN ... END");
        }

        for (String rawStatement : split(sql, ';')) {
            String statement = rawStatement.trim();
            if (statement.isEmpty()) {
                continue;
            }
            String statementCode = stripStringLiterals(statement);
            String upper = statementCode.toUpperCase(Locale.ROOT);
            if (upper.startsWith("CREATE TABLE")) {
                checkCreateTable(statement, violations);
            } else if (upper.startsWith("ALTER TABLE")) {
                if (MARIADB_DDL.matcher(statementCode).find()) {
                    violations.add("禁用 MariaDB 方言 IF [NOT] EXISTS DDL：" + oneLine(statement));
                }
                if (upper.contains("ADD COLUMN")) {
                    checkAddedColumns(statement, violations);
                }
            }
        }
        return violations;
    }

    private static void checkCreateTable(String statement, List<String> violations) {
        String code = stripStringLiterals(statement);
        String upper = code.toUpperCase(Locale.ROOT);
        if (!UNICODE_COLLATE.matcher(code).find()) {
            violations.add("建表必须显式 COLLATE=utf8mb4_unicode_ci（只写 CHARSET 会随服务器默认漂移）");
        }

        int close = closingParenIndex(statement);
        String tail = close < 0 ? "" : stripStringLiterals(statement.substring(close + 1));
        if (!tail.toUpperCase(Locale.ROOT).contains("COMMENT")) {
            violations.add("建表缺少表级 COMMENT='中文说明'");
        }

        boolean inColumns = false;
        for (String rawLine : statement.split("\\R")) {
            String line = rawLine.trim();
            if (!inColumns) {
                if (line.contains("(")) {
                    inColumns = true;
                }
                continue;
            }
            if (line.startsWith(")")) {
                break;
            }
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            // 本项目列定义统一写成一行、以反引号开头的列名起头；
            // PRIMARY/UNIQUE/KEY/CONSTRAINT 这些行不是列，自然不以反引号开头。
            if (line.startsWith("`") && !stripStringLiterals(line).toUpperCase(Locale.ROOT).contains("COMMENT")) {
                violations.add("建表列缺少 COMMENT：" + line);
            }
        }
    }

    private static void checkAddedColumns(String statement, List<String> violations) {
        for (String rawClause : split(statement, ',')) {
            String clause = rawClause.trim();
            String code = stripStringLiterals(clause);
            String upper = code.toUpperCase(Locale.ROOT);
            if (!upper.contains("ADD COLUMN")) {
                continue;
            }
            if (!upper.contains("COMMENT")) {
                violations.add("ADD COLUMN 缺少 COMMENT：" + oneLine(clause));
            }
            boolean characterColumn = CHARACTER_TYPE.matcher(code).find();
            boolean hasUnicodeCollate = UNICODE_COLLATE.matcher(code).find();
            if (characterColumn && !hasUnicodeCollate) {
                violations.add("字符列必须显式 COLLATE=utf8mb4_unicode_ci：" + oneLine(clause));
            }
            if (!characterColumn && upper.contains("COLLATE") && !hasUnicodeCollate) {
                violations.add("排序规则必须是 utf8mb4_unicode_ci：" + oneLine(clause));
            }
        }
    }

    // ------------------------------------------------------------------ 文本处理

    /** 去掉 {@code --} 行注释与 {@code /* *}{@code /} 块注释，保留字符串字面量。 */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '-' && index + 1 < sql.length() && sql.charAt(index + 1) == '-') {
                while (index < sql.length() && sql.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (current == '/' && index + 1 < sql.length() && sql.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < sql.length()
                    && !(sql.charAt(index) == '*' && sql.charAt(index + 1) == '/')) {
                    index++;
                }
                index = Math.min(index + 2, sql.length());
                continue;
            }
            if (current == '\'') {
                index = copyQuoted(sql, index, out);
                continue;
            }
            if (current == '"') {
                index = copyQuoted(sql, index, out);
                continue;
            }
            if (current == '`') {
                index = copyQuoted(sql, index, out);
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    /** 复制一段带引号的内容（含首尾引号），返回下一个未处理位置。 */
    private static int copyQuoted(String sql, int start, StringBuilder out) {
        char quote = sql.charAt(start);
        out.append(quote);
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\\' && quote == '\'' && index + 1 < sql.length()) {
                out.append(current).append(sql.charAt(index + 1));
                index += 2;
                continue;
            }
            out.append(current);
            if (current == quote) {
                // SQL 里 '' / "" / `` 是转义后的同字符
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    out.append(quote);
                    index += 2;
                    continue;
                }
                return index + 1;
            }
            index++;
        }
        return index;
    }

    /** 去掉单引号字符串的内容（保留引号），用于排除注释文案对语法检查的干扰。 */
    private static String stripStringLiterals(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inString = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (!inString) {
                out.append(current);
                if (current == '\'') {
                    inString = true;
                }
                continue;
            }
            if (current == '\\' && index + 1 < sql.length()) {
                index++;
                continue;
            }
            if (current == '\'') {
                inString = false;
                out.append(current);
            }
        }
        return out.toString();
    }

    /** 按分隔符切分，忽略括号内与引号内的分隔符。 */
    private static List<String> split(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == '\\' && quote == '\'' && index + 1 < text.length()) {
                    current.append(text.charAt(++index));
                    continue;
                }
                if (character == quote) {
                    if (index + 1 < text.length() && text.charAt(index + 1) == quote) {
                        current.append(text.charAt(++index));
                        continue;
                    }
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"' || character == '`') {
                quote = character;
                current.append(character);
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (character == delimiter && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    /** 表定义列清单的收尾右括号（跳过字符串/反引号里的括号）。 */
    private static int closingParenIndex(String statement) {
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < statement.length(); index++) {
            char character = statement.charAt(index);
            if (quote != 0) {
                if (character == '\\' && quote == '\'' && index + 1 < statement.length()) {
                    index++;
                    continue;
                }
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"' || character == '`') {
                quote = character;
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
