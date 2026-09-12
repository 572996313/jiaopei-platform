# 页面可配置化 — 配置契约

> 本文档是「配置驱动 CRUD 页面」的**配置结构规范**。阶段 A(纯前端)已让前端能读取
> 下列字段且后端未加列时走默认行为;阶段 B 已补齐后端存储与透传,**`page_json` /
> `dialog_json` 已可配置落库并在「列表配置」管理页编辑生效**。
> 后端缺列、前端走默认的兼容期已结束;字段置空仍回到默认行为,不影响现有页面。

## 目标

把「每个页面写死的部分」收敛成前端可读、后端可存的配置。前端的 `ConfigurableCrudPage`
组件消费 `sys_list_config` 的一条资源配置,并按 `resourceCode` 装配:

- 列表列 / 查询区 / 按钮渲染 —— 已有(`columns_json` / `query_json` / `actions_json`)
- 弹窗表单 —— 已有(`form_json`)
- **页面壳(标题 / 计数 / 空态 / 描述)** —— 新增 `page_json`
- **弹窗壳(宽度 / 标题 / 按钮文案 / 成功提示 / 提交后刷新)** —— 新增 `dialog_json`
- **按钮动作类型(决定默认点击行为)** —— 新增 `actions[].actionType`

已配置化能力的详细说明见前端组件的类型定义:
`frontend/src/components/ConfigurableCrudPage/types.ts`。

---

## 1. `page_json`(页面壳)

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| title | string | 否 | 列表配置 resourceName | 页面主标题 |
| description | string | 否 | 空 | 标题右侧描述文案 |
| showCount | boolean | 否 | true | 是否显示「共 X 条」 |
| countFormat | string | 否 | `共 {total} 条` | 计数模板,`{total}` 为占位符 |
| emptyText | string | 否 | 暂无数据 | 表格空态文案 |

示例:

```json
{
  "title": "系统参数",
  "description": "维护全局键值参数(token 有效期等),支持新增、编辑与删除。",
  "showCount": true,
  "countFormat": "共 {total} 条",
  "emptyText": "暂无数据"
}
```

## 2. `dialog_json`(弹窗壳)

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| width | string | 否 | `640px` | 弹窗宽度 |
| createTitle | string | 否 | `新增{name}` | 新增标题,`{name}`=资源名 |
| editTitle | string | 否 | `编辑{name}` | 编辑标题 |
| submitText | string | 否 | `保存` | 提交按钮文案 |
| cancelText | string | 否 | `取消` | 取消按钮文案 |
| createMessage | string | 否 | `{name}已创建` | 新增成功 toast |
| updateMessage | string | 否 | `{name}已更新` | 编辑成功 toast |
| reloadAfterSubmit | boolean | 否 | true | 提交成功后是否重拉列表 |

示例:

```json
{
  "width": "680px",
  "createTitle": "新增角色",
  "editTitle": "编辑角色",
  "submitText": "保存",
  "createMessage": "角色已创建",
  "updateMessage": "角色已更新",
  "reloadAfterSubmit": true
}
```

## 3. `actions_json[].actionType`(按钮动作类型)

在既有按钮配置(`action` / `label` / `position` / `buttonType` / `permission`)基础上,
新增枚举字段 `actionType`,决定点击按钮的**默认行为**:

| 值 | 默认行为 |
|---|---|
| `create` | 打开内置新增弹窗(调 `buildInitial` create 分支 + `createApi`) |
| `edit` | 打开内置编辑弹窗(回填选中行 + `updateApi`) |
| `delete` | 确认框 → `deleteApi` → reload |
| `toggle-status` | 确认框(文案随状态切换)→ `toggleStatusApi` → reload |
| `reset-password` | 宿主 `extraActions` 实现(需展示新密码 alert) |
| `assign-roles` | 宿主 `extraActions` 实现(业务专属弹窗) |
| `custom` | 默认;完全由宿主 `extraActions` 提供 handler |

> 阶段 A 兜底:后端未把 `actionType` 入配置时,前端按动作名/文案**推断**
> (见 `types.ts` 的 `inferActionType`),保证零配置也能正确接线;后端加列后
> 该推断仅作为未显式声明时的兜底。宿主 `extraActions` 始终优先于任何默认行为。

示例:

```json
[
  { "action": "config-create", "label": "新增参数", "position": "toolbar", "buttonType": "primary", "actionType": "create" },
  { "action": "config-edit", "label": "编辑", "position": "row", "actionType": "edit" },
  { "action": "config-delete", "label": "删除", "position": "row", "buttonType": "danger", "actionType": "delete" }
]
```

## 4. 后端存储(已实现,迁移 V25)

`sys_list_config` 已增加两列并透传给前端:

- `page_json : TEXT NULL` — 页面壳配置 JSON(`AFTER form_json`)
- `dialog_json : TEXT NULL` — 弹窗壳配置 JSON(`AFTER page_json`)

对应后端改动:

- 迁移:`backend/src/main/resources/db/migration/V25__add_list_config_page_and_dialog_json.sql`
  (含三个内置资源 system-users / system-roles / system-configs 的种子壳配置)。
- 实体 / DTO:`ListConfigEntity`、`ListConfigView`、`ListConfigCreateRequest`、
  `ListConfigUpdateRequest` 均已加 `pageJson` / `dialogJson` 字段。
- Service:`ListConfigServiceImpl` 在 create/update 时对二者做「JSON 对象」轻校验
  (空值跳过),并赋值、`toView` 透传。
- 列表配置接口(`GET /api/system/list-configs/code/{resourceCode}`)原样返回
  `pageJson` / `dialogJson`,`actions_json` 数组元素也允许 `actionType` 字段。
- 管理页:「列表配置」编辑弹窗已新增 `页面壳配置 pageJson` / `弹窗壳配置 dialogJson`
  两个 JSON 编辑框(仿 formJson),保存即落库、立即生效。

> 注意:种子 UPDATE 仅对新装库自动执行;已有库需执行一次 V25 迁移后,
> `page_json` / `dialog_json` 字段才会补上。置空(NULL/空串)时前端回退默认壳,不影响现有页面。

---

## 5. 预留(本次不实现,仅定结构,阶段 B/C 再扩展)

### 5.1 数据源配置(`optionsConfig` 预留)
让 select/下拉的选项数据源从「代码注册表」逐步收敛为「配置声明」:

```json
{
  "field": "institutionId",
  "type": "select",
  "optionsFrom": { "source": "api", "endpoint": "/organization/institutions", "valueKey": "id", "labelKey": "orgName" }
}
```

### 5.2 联动规则(`onChange` 升级为声明式)
当前 `onChange` 只是「代码里函数名」的暗号对接,后续升级为声明式联动:

```json
{
  "field": "institutionId",
  "onChange": {
    "reloadField": "defaultCampusId",
    "whenEmptyClear": true
  }
}
```

### 5.3 校验规则扩展(`rules` 支持更多类型)
当前 `rules` 仅支持 `required`,后续扩展:

```json
{
  "field": "phone",
  "rules": [
    { "type": "length", "min": 5, "max": 20, "message": "长度需在 5-20 位" },
    { "type": "regex", "pattern": "^1[0-9]{10}$", "message": "手机号格式不正确" }
  ]
}
```

### 5.4 插件注册表(`renderers.ts` / `optionLoaders` / 动作引擎)
把「列渲染 / 选项加载 / 按钮动作」收口成可注册的插件表,任何新能力通过注册即用;
这是后续「通用插件」的地基,属**代码架构**,不入配置库。

---

## 6. 当前实现范围(阶段 A 前端 + 阶段 B 后端已闭环)

- 前端新增 `ConfigurableCrudPage`(页面壳 + 弹窗壳 + 内置动作 + 提交流程)与
  `useCrudModal`;三个系统页(系统参数/角色/用户)迁移为配置驱动消费,
  净减约 300 行重复样板,全部特有逻辑保持。
- 新增 `page_json` / `dialog_json` / `actionType` 前端 schema 与解析(向后兼容,
  后端未加字段时按默认行为运行)。
- 列表组件 `ConfigurableList` 新增 `emptyText` 空态支持;`ListConfigSummary`
  新增可选 `pageJson` / `dialogJson` 字段。
- **阶段 B 闭环**:后端迁移 V25 加列并为内置资源补种子;实体/DTO/Service 透传;
  「列表配置」管理页新增两个 JSON 编辑框,配置保存即生效(改标题/描述/弹窗文案/宽度
  不再需要改代码)。**下一步路线**:教务/组织域规模化迁移、配置可视化编辑器、
  路由/菜单配置化,属阶段 B~E,另见路线图。