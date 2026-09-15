# 微信小程序端（miniapp）

本项目新增的微信小程序端 **「教培学堂」**,基于 **uni-app(Vue 3 + TypeScript + Vite)** 构建,定位
**家长/学生移动端服务**;与管理后台(frontend,「教培管理平台」)并行,复用同一套 Spring Boot 后端接口。

> 小程序名称在 `miniapp/src/manifest.json`(name)与 `miniapp/src/pages.json`(navigationBarTitleText)统一维护。

## 目录结构

```
jiaopei-platform/
├── backend/    # Spring Boot 后端(新增微信登录链路)
├── frontend/   # 管理后台(Vue 3 + Element Plus,不改动)
├── miniapp/    # 微信小程序端(uni-app,本目录)
└── docs/
```

miniapp 内部结构:

```
miniapp/src/
├── config.ts               # API 基地址(可用 VITE_API_BASE_URL 环境变量覆盖)
├── utils/
│   ├── auth-session.ts     # 登录态存取(uni storage,key: jiaopei.mini.auth.session)
│   └── request.ts          # uni.request 封装:ApiResponse 信封解包 + 雪花 ID 精度保护 + 401 统一处理
├── api/auth.ts             # 微信登录 / 自助注册 / 注销 / 当前用户
├── api/miniapp.ts          # 学员档案关联 + 课表查询
├── stores/auth.ts          # Pinia 登录态
└── pages/
    ├── login/index.vue     # 微信一键登录(启动页)
    ├── register/index.vue  # 首次使用选择身份(学生/家长)自动注册
    ├── index/index.vue     # 首页 tab(业务占位)
    ├── bind-student/index.vue  # 绑定学员档案(按学号/监护人手机号)
    ├── timetable/index.vue     # 课程表(周视图)
    └── mine/index.vue      # 我的 tab(学员管理 + 退出/注销)
```

## 后端新增内容

| 项 | 说明 |
| --- | --- |
| `V26__create_wechat_binding_table.sql` | `jp_wechat_binding` 表:openid ↔ 系统用户绑定关系 |
| `V27__wechat_self_register.sql` | `password_hash` 改可空、`register_source` 来源列、预置 `MINIAPP_STUDENT`/`MINIAPP_PARENT` 两个角色 |
| `POST /api/auth/wechat/login` | code 换 openid;已注册签发 JWT,未注册返回 `registered=false` |
| `POST /api/auth/wechat/register` | 自助注册:选身份(学生/家长)+昵称自动建档、分配角色并签发 JWT;重复注册幂等 |
| `POST /api/auth/wechat/deregister` | 账号注销:删除当前微信注册的账号(含身份角色与绑定),令牌立即失效;注销后同一微信重新登录为未注册状态,可重新注册 |
| `UserAccessTokenService` | 从 `AuthApplicationServiceImpl` 抽出的统一令牌签发组件,账号密码登录与微信登录共用 |
| `wx.miniapp.*` 配置 | `application.yml`,通过环境变量 `WECHAT_MINIAPP_APPID/SECRET` 注入 |

自助注册的用户不设密码(`password_hash` 为空),不能走账号密码登录,只能微信免密登录;
机构归属取系统配置 `auth.register.default_institution_id`(默认 1,可在后台系统配置里调整)。

微信登录复用现有 JWT + Redis 会话体系,签发的令牌与管理后台完全同构,
小程序端可直接调用受 `@RequiresPermission` 保护的接口。

## 开发环境准备

1. **注册微信小程序账号**:到 [mp.weixin.qq.com](https://mp.weixin.qq.com) 注册,
   获取 **AppID** 与 **AppSecret**(个人主体即可用于开发验证)。
2. **安装微信开发者工具**:微信官方 IDE,用于运行和调试小程序。
3. **后端配置**:

   ```bash
   # 启动后端前设置环境变量(或在 IDE 运行配置中添加)
   WECHAT_MINIAPP_APPID=wx你的appid
   WECHAT_MINIAPP_SECRET=你的secret
   ```

   未配置时后端可正常启动,调用微信登录接口会返回明确错误提示。

   生产环境(docker 部署):把同样两个变量写入 `docker/prod.env`(该文件已 gitignore,只留服务器),
   `docker-compose.prod.yml` 的 backend 服务已透传这两个变量,`docker/prod.env.example` 有对应模板。

## 小程序本地运行

```bash
cd miniapp
pnpm install
pnpm dev:mp-weixin     # 开发模式,产物输出到 dist/dev/mp-weixin,监听文件变更
# 或
pnpm build:mp-weixin   # 生产构建,产物输出到 dist/build/mp-weixin
```

然后:

1. 打开**微信开发者工具** → 导入项目 → 目录选择 `miniapp/dist/dev/mp-weixin`
   → AppID 填自己的小程序 AppID(或选"测试号")。
2. 在 `src/manifest.json` 的 `mp-weixin.appid` 填入 AppID。
3. 开发者工具右上角 **详情 → 本地设置 → 勾选"不校验合法域名…"**
   (开发阶段直连 `http://127.0.0.1:8080`,正式发布必须 HTTPS 域名)。
4. 确认本机 MySQL/Redis/后端已启动,模拟器中即可走通登录流程。

| `V28__user_student_rel.sql` | 用户-学员关联表 `jp_user_student_rel`;小程序角色追加 `miniapp:access` 权限码 |
| `GET/POST /api/miniapp/students` | 绑定学员档案(按学号/监护人手机号)与当前用户已绑学员列表 |
| `DELETE /api/miniapp/students/{id}` | 解绑学员档案(物理删除关联,解绑后可重新绑定) |
| `GET /api/miniapp/student/profile` | 学生端-我的档案(未绑定 bound=false) |
| `POST /api/miniapp/student/bind-self` | 学生端-按学号+姓名绑定自己的档案(双因子,绑定后不可换绑) |
| `POST /api/miniapp/student/profile` | 学生端-新建(无档案时自动建档并归属)或编辑本人档案(仅姓名/性别/生日) |
| `GET /api/miniapp/students/{id}/lessons` | 学员周课表(联查班级/课程/教室/教师),仅限已绑定的当前用户 |

## 档案关联与课程表

- **绑定学员**:「我的」→「绑定学员档案」,支持按**学员编号**或**监护人手机号**两种方式;手机号方式通过 `jp_student_parent.parent_phone` 匹配。**双因子校验**:除编号/手机号外必须填写**学员姓名**,字段与档案不一致(或该手机号下无此姓名)时拒绝绑定,防止陌生人仅凭传播的学号/手机号绑定查看学员信息。一个账号可绑多个学员,一个学员可被多个家长绑定。
- **绑定的学员**:家长端「我的」→「我的学员」列表,点学员进课表,点「解绑」解除关联(有二次确认,解绑后可重新绑定)。
- **学生端「我的」**:按是否绑定档案展示——未绑定引导「按学号绑定已有档案」或「自助创建档案」;已绑定展示本人档案(姓名/性别/生日/班级)+「我的课表」「编辑档案」入口。学生档案**绑定后不可换绑**,注销账号时自动清除档案归属(user_id 置空,档案本身保留可由后续绑定)。
- **数据隔离**:所有 `/api/miniapp` 接口都经 `@RequiresPermission("miniapp:access")` 且按**当前登录用户**过滤——只能查到自己绑定的学员,不能越权看别人。
- **课表**:按当前用户所选学员,取其在班班级(`jp_class_student` ACTIVE)→ 查询该班课次(`schedule_lesson`)并按日期范围返回,联查出班级名、课程名、教室名、教师姓名电话。停课(cancelled)课次在页面置灰标识。

## 登录与注册流程

```
启动 → 登录页 → uni.login 取 code
     → POST /api/auth/wechat/login
         ├─ registered=true  → 保存令牌 → 进入首页(微信免密登录)
         └─ registered=false → 注册页:选身份(学生/家长)+填昵称
                             → POST /api/auth/wechat/register → 自动建档+分配角色 → 进入首页
```

- 首次使用自动注册进系统(分配 MINIAPP_STUDENT / MINIAPP_PARENT 角色),之后微信免密登录。
- 一个微信对应一个账号,重复提交注册时按已注册用户直接登录(幂等)。
- 注册用户无密码,不能走账号密码登录;注册身份不可自行更改,需要调整找管理员。
- 退出登录调用 `/api/auth/logout`,服务端令牌(Redis)立即失效。
- 「我的」页提供**注销账号**:删除当前微信注册的账号及绑定关系,注销后同一微信重新登录即为全新未注册用户,可重新选择身份注册。注销操作不可恢复,仅小程序自助注册的账号可自助注销。

## 关键约定(与 frontend 保持一致)

- **ApiResponse 信封**:统一解包 `success/message/data`,`success=false` 抛 `ApiError`
  并展示中文 message。
- **雪花 ID 精度保护**:后端 19 位 Long 以 JSON 数字传输会超出 JS 安全整数范围,
  `utils/request.ts` 关闭自动 JSON.parse,先用与 `frontend/src/api/http.ts` 相同的
  正则把 `id/*Id/*Ids/*By` 字段转字符串再解析。
- **401 处理**:任何接口返回 401 时清除本地会话并跳回登录页。

## 常见问题

- **真机预览连不上后端**:手机无法访问电脑的 `127.0.0.1`,需把基地址改为电脑局域网 IP
  (通过 `VITE_API_BASE_URL` 环境变量或 `src/config.ts`),且手机与电脑同一网络;
  正式环境必须配置 HTTPS 域名并在小程序后台加入 request 合法域名。
- **code 无效/已被使用**:`wx.login` 的 code 一次性有效,报"微信登录失败"时重试即可,
  勿在前后端重复使用同一 code。
- **jwt 已过期**:默认 12 小时(系统配置 `auth.token.expire_hours`),过期后请求层会
  自动退回登录页。

## 后续迭代方向

- 按身份开放功能:学生端/家长端各自的功能页,后端配套权限码(`miniapp:student:*` / `miniapp:parent:*`,在角色管理里追加即可)。
- 注册用户与教育域档案关联:家长可按 `jp_student_parent.parent_phone / wechat_no` 匹配孩子;学员需给 `jp_student` 加 `user_id` 列。
- 手机号一键获取(需企业认证小程序)。
- 后台用户管理按 `register_source` 筛选小程序注册用户。
- tabBar 图标(当前为纯文字 tab,可在 `pages.json` 的 `tabBar.list` 中补充 iconPath)。
- 需要输出 H5/App 时,uni-app 一套代码直接切换编译目标;届时 H5 端需后端补 CORS 配置。
