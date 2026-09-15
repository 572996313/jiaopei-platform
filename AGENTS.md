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

## 接口中文说明规范(@Operation,软规范)

本规范为**软规范**,不阻塞构建,但作为团队约定在新接口添加、存量补齐时遵循。

### 背景

Controller 的接口方法缺少中文说明,导致:

- Swagger UI 与 `/v3/api-docs` 里的接口没有语义描述,前端联调时反复询问。
- **功能权限总览页**(系统设置 → 功能权限,后端 `PermissionOverviewController`)
  展示"权限码 → 受控接口"时,读取的是 `@Operation(summary)`;"说明"列为空的接口
  基本都是漏写 `@Operation` 的存量接口。

### 约定内容

1. 所有 Controller 的**每个接口方法**(`@GetMapping/@PostMapping/@PutMapping/@DeleteMapping`
   等)必须添加:

   ```java
   @Operation(summary = "获取教师列表", description = "按机构与校区筛选,返回教师列表")
   @GetMapping
   @RequiresPermission("education:read")
   public ApiResponse<List<TeacherEntity>> list(...) { ... }
   ```

2. `summary` 用简洁中文短语(如 "获取系统参数列表"),在总览页与 Swagger 列表直接展示;
   `description` 可选,补充入参含义、筛选字段等细节。**两者都使用中文**。

3. 若方法同时有 `@RequiresPermission`,`@Operation` 与其并列;不带权限的公开接口
   (如 `HealthController`)同样建议补 `@Operation`,保持文档完整。

### 验证与覆盖度自查

启动后访问 `http://localhost:8080/swagger-ui.html`,接口应显示中文 summary;
功能权限总览页"受控接口"表格的"说明"列应非空。

存量覆盖度检查可用命令行比对(统计每个 Controller 的 `@Operation` 与
`@(Get|Post|Put|Delete)Mapping` 数量):

```bash
grep -c "@Operation" **/*Controller.java
grep -cE "@(Get|Post|Put|Delete|Request)Mapping" **/*Controller.java
```

存量接口已按域分批补齐(系统域已首批,教务/组织域已第二批);新增接口必须同步写。

### 落地节奏

- 新建接口时同步添加,不留存量。
- 存量缺失按域逐步补齐;发现总览页"说明"列为空即为漏写,应补。

## 数据库表 / 字段中文注释规范(必填)

本规范为**必填**,建新表或加新列时同步落地,存量按域补齐;缺注释的表/字段视为未完成。

### 背景

小程序端与教育域间新增的关联表(`jp_wechat_binding`、`jp_user_student_rel` 等)表名与
字段名多为英文拼接(如 `user_id`、`last_login_at`),无自述性;数据库运维、新成员看表结构、
以及用数据库工具(如 DBX/DataGrip)联查时,没有中文注释就不知道该字段是什么含义,也不
知道码值(如 `status`、`deleted`)怎么取。

### 约定内容

1. **每张新表必须带表注释**,建表语句末尾 `COMMENT='中文说明'`,或对存量表用
   `ALTER TABLE \`表名\` COMMENT = '中文说明';`。

2. **每个字段必须带字段注释**,建表 DDL 的列定义后跟 `COMMENT '中文说明'`;
   对存量表用 `MODIFY COLUMN` 补注释(**必须与原列定义完全一致**:类型、NULL/默认值、
   DEFAULT,否则 MySQL 会隐式改列)。

3. 表注释说明这张表的**业务用途**;字段注释说明含义,**枚举/布尔类型额外给码值对照**:

   ```sql
   CREATE TABLE jp_user_student_rel (
       ...
       status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
       deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0=未删除,1=已删除)',
       ...
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-学员档案关联(学生/家长绑定学员)';
   ```

4. **通用审计字段**(`id`/`created_at`/`created_by`/`updated_at`/`updated_by`/`deleted`)
   每个表都要有 COMMENT,文案与 `BaseEntity` 的 `@Schema` 保持一致(如
   `deleted` → 逻辑删除标识(0=未删除,1=已删除))。

5. 与代码侧联动:表/字段注释的中文与对应实体 `@Schema(description=...)` 保持一致,
   避免"表里一个说法、代码里另一个说法"。

### 落地节奏

- 新建表 / 新增列(SQL 迁移)时**必须**同步 COMMENT,不留存量。
- 存量表按域补齐;发现新表缺注释即为遗漏,应补。
- 覆盖度自查(仓库内):

  ```bash
  # 找出建表语句里没有末行 COMMENT 的表
  grep -rlE "ENGINE=InnoDB" backend/src/main/resources/db/migration | xargs grep -L "COMMENT"
  ```

### 验证

重启后端后 `SHOW CREATE TABLE` / 数据库客户端可见表注释与字段注释;`INFORMATION_SCHEMA.COLUMNS`
的 `COLUMN_COMMENT` 非空。
