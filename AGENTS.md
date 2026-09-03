# 项目开发约定

## 实体 / DTO 字段说明规范(软规范)

本规范为**软规范**,不阻塞构建,但作为团队约定在新字段添加、存量补齐时遵循。

### 背景

项目已集成 `springdoc-openapi-starter-webmvc-ui 2.8.13`,Controller 已使用 `@Tag`,但
实体与 DTO 的字段缺少中文说明,导致:

- IDEA 鼠标悬停看不到字段语义,新成员上手成本高。
- Swagger UI 接口文档里字段名是英文,前端联调时反复询问。
- 枚举型字段(如 `status`、`gender`、`attendanceStatus`)没有注释,调用方不知道取值。

### 约定内容

1. 所有 `domain/entity/*` 与 `dto/*`(Request/Response/View)的**公共字段必须添加**
   `@Schema(description="中文说明")`,IDE 悬浮与 Swagger UI 直接展示中文。

2. **枚举型字段**额外使用 `allowableValues` 列出码值,并在 `description` 中给出
   "码=含义"对照,示例:

   ```java
   @Schema(description = "出勤状态(PRESENT=出勤,ABSENT=缺勤,LATE=迟到,LEAVE=请假)",
           allowableValues = {"PRESENT", "ABSENT", "LATE", "LEAVE"})
   private String attendanceStatus;
   ```

3. **继承自 `BaseEntity`** 的公共字段(`id`/`createdAt`/`createdBy`/`updatedAt`/
   `updatedBy`/`deleted`)由 `BaseEntity` 统一定义一次,子类不重复声明。

4. **必填字段**额外标注 `requiredMode = Schema.RequiredMode.REQUIRED`,便于
   Swagger UI 显示星号。

5. **Java record 类型 DTO**(`dto/*Request` / `dto/*Response`)的 `@Schema` 直接写在
   record 组件上,与其他注解并列。

### 适用注解

```java
import io.swagger.v3.oas.annotations.media.Schema;
```

项目已通过 springdoc 引入该依赖,无需新增 pom 依赖。

### 落地节奏

- 新建实体 / DTO 时同步添加,不留存量。
- 存量字段按域逐步补齐(教育域已首批补齐)。
- 后续可考虑引入 Checkstyle / ArchUnit 规则强制扫描,但目前不强制。

### 验证

启动后端访问 `http://localhost:8080/swagger-ui.html`,字段说明应展示中文。
也可访问 `/v3/api-docs` 查看 OpenAPI JSON,字段 `description` 已写入。
