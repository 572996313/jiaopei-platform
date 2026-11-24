# CI 构建上线操作清单（GitHub Actions）

本文是一份**照做即可**的手册，把生产部署从「在服务器上编译」切换成
「GitHub Actions 构建 + 服务器零编译部署」。

> 背景：生产服务器是突发性能型（CPU 积分制）实例。在服务器上跑 `mvn` / `pnpm` /
> `docker build` 会打满 CPU、耗尽积分甚至触发关机。切换后服务器只负责接收产物和重建容器，
> 部署期间 CPU 峰值应该是个位数。

相关原理与日常流程见 [生产部署说明.md](生产部署说明.md)；本文只讲**一次性上线操作**。

---

## 0. 总览：每一步在哪做

| 步骤 | 在哪做 | 大概耗时 |
| --- | --- | --- |
| 1. 服务器建 `deploy` 用户与 `releases/` 目录 | 服务器 | 2 分钟 |
| 2. 生成本地密钥对 | 你的电脑 | 1 分钟 |
| 3. 公钥装进服务器 `authorized_keys` | 服务器（或从电脑推） | 2 分钟 |
| 4. 本地验证能否用这把 key 连服务器 | 你的电脑 | 1 分钟 |
| 5. 加 5 个 GitHub Secrets | GitHub 网页 | 5 分钟 |
| 6. 改服务器私有 compose（去掉 `build:`） | 服务器 | 5 分钟 |
| 7. 服务器 origin 改到 GitHub | 服务器 | 1 分钟 |
| 8. 触发第一次构建 | GitHub 网页 | 10 分钟（构建）+ 1 分钟（投递） |
| 9. 服务器首次零编译部署 | 服务器 | 2 分钟 |

> 顺序不要乱：**第 3、6、7 步必须在第 8 步之前完成**，否则第一次构建的投递一定会失败。

---

## 1. 服务器：建接收用户与目录

SSH 登进服务器（用你平时的运维账号，**不要用 root 跑后面的脚本**）。

### 1.1 建 `deploy` 用户

```bash
sudo useradd -m -s /bin/bash deploy
```

> 如果提示 `user already exists`，说明已经建过，忽略即可。

这个用户只负责**接收 CI 推上来的产物**，不需要密码登录，也不需要 sudo 权限。

### 1.2 建产物根目录（这就是 GitHub Secret `DEPLOY_RELEASES_DIR` 的值）

```bash
sudo mkdir -p /mnt/software/jiaopei/releases
sudo chown -R deploy:deploy /mnt/software/jiaopei/releases
sudo chmod -R g+rwX /mnt/software/jiaopei/releases
sudo chmod g+s /mnt/software/jiaopei/releases
```

逐条解释：

| 命令 | 作用 |
| --- | --- |
| `chown -R deploy:deploy` | CI 以 `deploy` 身份写文件，必须能写 |
| `chmod -R g+rwX` | 组内成员可读写（你的运维账号要能清理旧版本） |
| `chmod g+s` | 新建的子目录自动继承 `deploy` 组，权限不会掉 |

### 1.3 把你自己的运维账号加进 `deploy` 组

`deploy.sh` 部署完会清理过期产物，需要写权限：

```bash
sudo usermod -aG deploy <你的运维账号>
```

⚠️ **执行后必须退出 SSH 重新登录**，组权限才生效。验证：

```bash
groups            # 输出里应该出现 deploy
```

### 1.4 确认 rsync 存在

CI 用 rsync 投递，精简系统可能没装：

```bash
rsync --version | head -1
# 没有输出就先装：sudo apt install -y rsync    （Debian/Ubuntu）
#                 sudo dnf install -y rsync     （RHEL/CentOS/Alma）
```

### 1.5 确认磁盘余量

`releases/` 默认保留最近 5 个版本，每个约 100MB：

```bash
df -h /mnt/software/jiaopei
# Available 至少留 1G
```

---

## 2. 本地：密钥对是什么、怎么创建

### 2.1 一句话原理

SSH 登录用的是**一对钥匙**：

| 文件 | 叫什么 | 放在哪 | 能不能给别人看 |
| --- | --- | --- | --- |
| `gha_deploy` | **私钥** | 你的电脑 + GitHub Secret | ❌ 绝对不能公开 |
| `gha_deploy.pub` | **公钥** | 服务器 `authorized_keys` | ✅ 可以公开 |

流程是：GitHub 拿着私钥来敲门 → 服务器用公钥验证「这把私钥确实配对」→ 放行。
所以**公钥给服务器，私钥给 GitHub**，任何一边都不能放反。

### 2.2 生成（已经生成过可跳过）

在**你的电脑**上（Windows 用 Git Bash 或 PowerShell 均可）：

```bash
ssh-keygen -t ed25519 -f ~/.ssh/gha_deploy -N "" -C "github-actions-deploy"
```

参数说明：

- `-t ed25519`：密钥类型，比 RSA 短且更安全
- `-f ~/.ssh/gha_deploy`：保存路径
- `-N ""`：**空密码**。CI 无人值守，有密码就没法自动登录
- `-C "..."`：备注，方便以后在服务器上认出这把 key 是谁

生成后目录里会有两个文件：

```
C:\Users\<你>\.ssh\gha_deploy       ← 私钥（无扩展名）
C:\Users\<你>\.ssh\gha_deploy.pub   ← 公钥
```

### 2.3 核对（强烈建议做）

```bash
# 查看指纹
ssh-keygen -lf ~/.ssh/gha_deploy.pub

# 验证公私钥确实配对：能输出版 SSH 公钥就说明没问题
ssh-keygen -y -f ~/.ssh/gha_deploy
```

> ⚠️ **不要复用你平时的 `id_rsa`**。CI 权限应该最小化：这把 key 泄露了，只影响服务器上
> `deploy` 这个受限用户；复用个人 key 则会连带你所有 GitHub 权限一起泄露。

### 2.4 如果重新生成过 key

**公钥和私钥必须成对使用。** 重新生成后：

1. 服务器上要**替换** `authorized_keys` 里旧的那一行（不是追加）
2. GitHub 的 `SSH_PRIVATE_KEY` secret 要**重新粘贴**新私钥

否则会一直 `Permission denied (publickey)`。

---

## 3. 把公钥装到服务器

有两种方式，任选其一。

### 方式 A：从你的电脑一条命令推上去（推荐）

把 `<运维账号>` 和 `<服务器IP>` 换成实际值。第一条建目录，第二条把公钥写进去：

```bash
# 1) 建目录并设权限
ssh <运维账号>@<服务器IP> \
  'sudo -u deploy mkdir -p /home/deploy/.ssh && sudo -u deploy chmod 700 /home/deploy/.ssh'

# 2) 把本地公钥加上 restrict 前缀后追加
( echo -n 'restrict '; cat ~/.ssh/gha_deploy.pub ) | \
  ssh <运维账号>@<服务器IP> \
  'sudo tee -a /home/deploy/.ssh/authorized_keys >/dev/null && \
   sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys && \
   sudo chmod 600 /home/deploy/.ssh/authorized_keys'
```

`restrict` 的作用：禁掉端口转发、agent 转发、PTY 等能力，只保留最基本的登录。
即使私钥泄露，攻击面也小很多。

### 方式 B：手动复制粘贴

先在本地打印公钥：

```bash
cat ~/.ssh/gha_deploy.pub
```

然后在服务器上（把输出整行贴进去，**前面加 `restrict ` 和一个空格**）：

```bash
sudo -u deploy mkdir -p /home/deploy/.ssh
sudo -u deploy chmod 700 /home/deploy/.ssh

# 注意：整行粘贴，形如 restrict ssh-ed25519 AAAA... github-actions-deploy
echo 'restrict ssh-ed25519 AAAA...你的公钥... github-actions-deploy' | \
  sudo tee -a /home/deploy/.ssh/authorized_keys

sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

### 3.1 检查装对了没

```bash
sudo cat /home/deploy/.ssh/authorized_keys
sudo ls -ld /home/deploy/.ssh /home/deploy/.ssh/authorized_keys
```

期望看到：

- 内容是一行 `restrict ssh-ed25519 AAAA... github-actions-deploy`
- 目录权限 `drwx------ deploy deploy`
- 文件权限 `-rw------- deploy deploy`

**权限错是最常见的原因**：`.ssh` 不是 700 或 `authorized_keys` 不是 600，sshd 会直接忽略这把 key。

---

## 4. 本地验证这把 key 能用

在**你的电脑**上执行（这一步完全模拟 CI 的行为）：

```bash
ssh -i ~/.ssh/gha_deploy -p 22 deploy@<服务器IP> \
  "echo 连上了 && id && ls -ld /mnt/software/jiaopei/releases"
```

期望输出类似：

```
连上了
uid=1001(deploy) gid=1001(deploy) groups=1001(deploy)
drwxrwsr-x 2 deploy deploy 4096 ... /mnt/software/jiaopei/releases
```

看到 `连上了` 就说明公钥/私钥/权限三件事都对了，第 5 步可以放心做。

### 连不上怎么查

| 现象 | 原因 | 修法 |
| --- | --- | --- |
| `Permission denied (publickey)` | 公钥没进文件 / 用户不对 | 确认登录用户是 `deploy`；`sudo cat` 看内容在不在 |
| 同上，但内容明明在 | 权限太宽 | `.ssh` → 700，`authorized_keys` → 600，属主 → deploy |
| 同上，但权限也对 | 两把 key 没配对 | 重新执行 2.3 的核对；或重新生成并同时更新服务器与 GitHub |
| `Connection refused` | 端口不对 / 防火墙 | 确认端口；云厂商安全组放行 |
| 卡住无响应 | 安全组没放行来源 IP | GitHub 的 runner IP 不固定，投递端口需要对公网开放（或改用自托管 runner） |

---

## 5. GitHub Secrets：5 个名字和内容

打开仓库：

```
https://github.com/572996313/jiaopei-platform/settings/secrets/actions
```

点 **New repository secret**，逐个添加：

| # | Name | Value 填什么 | 怎么拿到 |
| --- | --- | --- | --- |
| 1 | `SSH_HOST` | 服务器公网 IP 或域名 | 你的云控制台；**不要带 `http://`、不要带端口** |
| 2 | `SSH_USER` | `deploy` | 第 1.1 步建的用户 |
| 3 | `SSH_PORT` | `22`（不是 22 就填实际端口） | 可省略，省略时默认 22 |
| 4 | `SSH_PRIVATE_KEY` | **私钥全文** | `cat ~/.ssh/gha_deploy` |
| 5 | `DEPLOY_RELEASES_DIR` | `/mnt/software/jiaopei/releases` | 第 1.2 步建的目录 |

### 5.1 `SSH_PRIVATE_KEY` 怎么填（最容易出错的一个）

**不要**用鼠标划选复制，容易漏掉最后的换行或混入多余空格。用命令直接复制到剪贴板：

```bash
cat ~/.ssh/gha_deploy | clip        # Windows Git Bash
# 或：Get-Content ~/.ssh/gha_deploy -Raw | Set-Clipboard    （PowerShell）
```

然后到网页 Value 框里 **Ctrl+V**。

正确的值应该长这样（首尾两行必须在）：

```text
（此处粘贴私钥全文：第一行以 5 个减号 + BEGIN 开头，最后一行以 5 个减号 + END 开头，
 中间是若干行 base64 字符，总行数通常 5~10 行）
```

❌ 常见错误：

- 只粘了中间内容，丢了 `-----BEGIN/END` 两行
- 粘成了 `.pub`（公钥）——公钥是公开的，放这里等于没配
- 首尾带了引号或反引号
- 粘贴后有人手动改了缩进

### 5.2 `SSH_HOST` 的取值

| 情况 | 填什么 |
| --- | --- |
| 直连公网 IP | `203.0.113.10` |
| 域名 | `deploy.example.com` |
| 非标准端口 | `SSH_HOST` 只填地址，端口单独填在 `SSH_PORT` |

---

## 6. 服务器：改私有 compose（去掉 `build:`）

`docker/docker-compose.prod.yml` 是**每台服务器私有、不进 git** 的文件，`git pull` 不会帮你改。
不改它，`deploy.sh` 会直接拒绝执行（这是故意的防呆）。

### 6.1 先在服务器拉最新代码

```bash
cd /mnt/software/jiaopei
git fetch origin && git merge --ff-only origin/master
```

### 6.2 对比模板与本地实例

```bash
cd /mnt/software/jiaopei
diff -u docker/docker-compose.prod.yml.example docker/docker-compose.prod.yml
```

需要把 `backend` / `frontend` 两段改成和模板一致：

- **删掉** `build:` 整段
- backend：`image: eclipse-temurin:21-jre-alpine`，加 `./artifacts/app.jar:/app/app.jar:ro`、
  `working_dir: /app`、`command: ["java", "-jar", "/app/app.jar"]`，**保留 `SERVER_PORT: 8080`**
- frontend：`image: nginx:1.27-alpine`，加 `./artifacts/dist:/usr/share/nginx/html:ro`
- **删除** `backend-deps` / `frontend-deps` 两个服务块

> 如果本地实例没有做过特殊定制（没有复用面板 MySQL/Redis、没改端口），最省事的做法是
> 直接备份后用模板覆盖，再按需改回你的定制项。

### 6.3 自检（必做）

```bash
cd /mnt/software/jiaopei
docker compose -f docker/docker-compose.prod.yml --env-file docker/prod.env config \
  | grep -c 'build:'
```

**必须输出 `0`**。不是 0 就不要往下走。

---

## 7. 服务器：origin 改到 GitHub

```bash
cd /mnt/software/jiaopei
git remote set-url origin git@github.com:572996313/jiaopei-platform.git
git remote -v
```

仓库当前是**公开**的，所以服务器即使没有 GitHub key，也可以用 HTTPS 匿名拉取：

```bash
git remote set-url origin https://github.com/572996313/jiaopei-platform.git
```

但建议还是配一把**只读 deploy key**（防止哪天仓库转私有就断掉）：

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "server-readonly"
cat ~/.ssh/github_deploy.pub
```

把公钥加到 GitHub 仓库 **Settings → Deploy keys → Add deploy key**，**只勾读权限**（不勾 write）。
然后在服务器 `~/.ssh/config` 里加：

```
Host github.com
  IdentityFile ~/.ssh/github_deploy
```

验证：

```bash
ssh -T git@github.com          # 看到 "successfully authenticated" 即可
cd /mnt/software/jiaopei && git fetch origin && echo 拉取正常
```

---

## 8. 触发第一次构建

打开：

```
https://github.com/572996313/jiaopei-platform/actions/workflows/build-release.yml
```

点右侧 **Run workflow** → 分支选 `master` → **Run workflow**。

它会依次做：

1. 后端 `mvn clean package`（含 **665 个测试**）
2. 前端 `pnpm install --frozen-lockfile` + `pnpm lint` + `pnpm build`
3. 用 `SSH_PRIVATE_KEY` 登录服务器
4. 把 `app.jar` 和 `dist/` 推到 `/mnt/software/jiaopei/releases/<日期>-<时刻>-<sha>/`

> 本地已预先验证过：后端 665 测试全绿、前端 lint 与构建通过、lockfile 一致。
> 所以**如果 CI 在编译阶段失败，大概率是环境差异**，把失败步骤的日志贴出来即可。

### 8.1 成功后确认

服务器上执行：

```bash
ls -lh /mnt/software/jiaopei/releases/
```

应该看到类似：

```
drwxr-xr-x 3 deploy deploy 4.0K Nov 24 10:32 20261124-103000-abc1234
```

进去看：

```bash
ls -lh /mnt/software/jiaopei/releases/20261124-103000-abc1234/
# app.jar 约 96M
# dist/   约 2.8M
```

---

## 9. 服务器：第一次零编译部署

```bash
cd /mnt/software/jiaopei
./docker/deploy.sh --from-ci latest
```

**另开一个 SSH 窗口**同时观察 CPU：

```bash
docker stats
```

期望：

- 部署日志里**不出现**任何 `docker compose build` 输出
- CPU 峰值**个位数**
- 最后打印各服务 `healthy`

### 9.1 验收

```bash
cd /mnt/software/jiaopei

# 1) 容器状态
docker compose -f docker/docker-compose.prod.yml ps

# 2) 前端与后端
curl -s http://127.0.0.1:5757/healthz
curl -s http://127.0.0.1:5757/api/health

# 3) 容器里的 jar 与 releases 里的一致
docker exec jiaopei-prod-backend md5sum /app/app.jar
md5sum docker/artifacts/app.jar

# 4) 确认没有编译过
grep -c 'compose build' deploy.log    # 应该是 0
```

### 9.2 回滚演练（建议做一次）

```bash
ls -1t /mnt/software/jiaopei/releases | head      # 看有哪些版本
./docker/deploy.sh --rollback <上一个版本目录名>
docker exec jiaopei-prod-backend md5sum /app/app.jar   # 内容应该变回旧版本
```

---

## 10. 常见问题速查

### 10.1 CI 报错

| 报错 | 原因 | 修法 |
| --- | --- | --- |
| `未配置 DEPLOY_RELEASES_DIR secret` | 漏了第 5 个 secret | 补上 |
| `Permission denied (publickey)` | 公钥/权限/配对问题 | 回到第 4 步本地复现 |
| `Host key verification failed` | 服务器指纹变化 | 服务器重装过就重新 `ssh-keyscan` |
| `rsync: command not found` | 服务器没装 rsync | 第 1.4 步 |
| `mkdir: cannot create directory` | `deploy` 用户对上级目录没权限 | 检查 `/mnt/software/jiaopei` 的属主 |
| 编译阶段失败 | 环境差异 / 依赖问题 | 本地跑 `mvn test`、`pnpm build` 复现 |

### 10.2 部署脚本报错

| 报错 | 原因 | 修法 |
| --- | --- | --- |
| `找不到 CI 产物: latest` | workflow 没成功 / 目录不一致 | `ls -1t /mnt/software/jiaopei/releases` |
| `仍含 build 段，拒绝继续` | compose 没改 | 第 6 步 |
| `docker compose config 解析失败` | `prod.env` 缺必填变量 | 补 `AUTH_JWT_SECRET` 等，见报错提示 |
| `origin 仍指向 Gitee` | 没改 remote | 第 7 步 |
| `backend: unhealthy` | jar 起不来 | `docker compose logs backend \| tail -50` |
| 页面 502 | nginx 连不上 backend | backend 是否 healthy；`SERVER_PORT: 8080` 是否被删 |

### 10.3 需要彻底重来

```bash
# 清掉这把 key 在服务器上的授权
sudo sed -i '/github-actions-deploy/d' /home/deploy/.ssh/authorized_keys

# 本地重新生成
rm -f ~/.ssh/gha_deploy ~/.ssh/gha_deploy.pub
ssh-keygen -t ed25519 -f ~/.ssh/gha_deploy -N "" -C "github-actions-deploy"
```

然后重做第 3 步和第 5 步（注意是**替换** GitHub 里的 `SSH_PRIVATE_KEY`，不是新增）。

---

## 11. 安全提醒

1. **私钥只存在两个地方**：你的电脑、GitHub Secret。不要发群、不要提交进 git。
2. **不要用 root 配这把 key**。`deploy` 用户只对 `releases/` 有写权限，影响面最小。
3. `restrict` 前缀不要省。
4. 怀疑泄露时：删掉服务器 `authorized_keys` 里那一行 → 立即失效 → 重新生成并更新 GitHub。
5. 本机私钥文件建议确认权限（Windows 上通常由 ACL 控制）：
   ```bash
   ls -l ~/.ssh/gha_deploy    # 理想是 -rw-------，至少不要是所有人可读
   ```
