# 生产环境 MySQL 重建与安全加固方案

> 适用场景：数据库遭受攻击后的紧急重建，基于 `docker-compose.prod.yml` 与 `prod.env` 的运行环境。
>
> 请在生产服务器上严格按此步骤执行。

---

## 一、当前配置存在的严重安全风险

根据您项目中的 `docker-compose.prod.yml` 和 `prod.env`，发现以下高危问题，**这是导致被攻击的直接原因**：

| 风险项 | 当前情况 | 危险等级 |
|---|---|---|
| MySQL root 密码 | `1234`（prod.env 第 6 行） | 🔴 **极高** |
| 应用账号密码 | `123456`（prod.env 第 5 行） | 🔴 **极高** |
| MySQL 端口暴露 | `3306` 直接映射到宿主机公网 | 🔴 **极高** |
| Redis 密码 | 空密码（prod.env 第 11 行） | 🟠 **高** |
| Redis 端口暴露 | `6379` 直接映射到宿主机 | 🟠 **高** |
| 默认数据库名 | `jiaopei_platform` 可预测 | 🟡 **中** |

**结论**：密码为 `1234`/`123456` 且 3306 端口暴露在公网，等于把数据库大门敞开，这是典型被扫描爆破攻击的原因。

---

## 二、重建前准备（在数据库所在服务器执行）

### 2.1 停止并备份现有数据（如有残留）

如果旧容器还在运行，先停掉，**但不要立即删除 volume**，防止还有可用数据：

```bash
# 进入项目目录
cd /path/to/jiaopei-platform/docker

# 停止生产环境
docker compose -f docker-compose.prod.yml down

# 查看现有数据卷
docker volume ls
```

如果确定数据已被加密/破坏，无需备份；如果还有未损坏的数据，先导出：

```bash
# 临时启动旧容器（不映射端口，避免再次暴露）
docker run -d --name mysql-recover \
  -v jiaopei-platform_mysql_data:/var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD=1234 \
  mysql:8.4

# 导出数据（如果有）
docker exec mysql-recover mysqldump -uroot -p1234 jiaopei_platform > /backup/recover_$(date +%F_%H%M).sql

# 停止临时容器
docker stop mysql-recover && docker rm mysql-recover
```

### 2.2 彻底删除旧数据（确认无需求后再执行）

```bash
# 删除生产环境容器和网络
docker compose -f docker-compose.prod.yml down -v

# 删除旧的数据卷（此操作不可逆，确认已备份或数据已废弃）
docker volume rm jiaopei-platform_mysql_data

# 删除旧的镜像（可选，重建时会自动拉取/构建）
docker image rm mysql:8.4
```

---

## 三、生成高强度密码

**严禁再使用 `123456`、`1234`、`jiaopei` 等弱密码！**

在服务器上生成强密码，替换 `prod.env`：

```bash
# 生成 32 位随机密码（包含大小写+数字+符号）
openssl rand -base64 32

# 示例输出（每次不同）：
# dGh9#kL2$vBqWz1@P8xR3mN7cF4aJ6s
```

**必须替换的字段**：
- `DB_PASSWORD`：应用连接账号密码（当前是 `123456`）
- `MYSQL_ROOT_PASSWORD`：root 超级管理员密码（当前是 `1234`）
- `REDIS_PASSWORD`：Redis 密码（当前为空）

---

## 四、重写 `prod.env`（核心步骤）

将以下 secure 版本替换原文件，**密码请使用你自己生成的**：

```ini
# ========================================
# 生产环境配置 - 安全版（重建后启用）
# ========================================

# ---- 数据库配置 ----
DB_NAME=jiaopei_platform
DB_USERNAME=jiaopei_app
DB_PASSWORD=<生成32位强密码>
MYSQL_ROOT_PASSWORD=<生成32位强密码，与上面不同>
MYSQL_PORT=3306

# ---- Redis 配置 ----
REDIS_PORT=6379
REDIS_DATABASE=0
REDIS_PASSWORD=<生成32位强密码>

# ---- 后端配置 ----
BACKEND_PORT=8181
BACKEND_BUILD_MAVEN_OPTS=-Xmx512m -XX:MaxMetaspaceSize=256m -Djava.awt.headless=true
BACKEND_JAVA_OPTS=-Xms256m -Xmx768m

# JWT 签名密钥(必填,至少 32 字节随机字符串,例如: openssl rand -base64 48)
AUTH_JWT_SECRET=<生成至少32字节的随机密钥>

# ---- 前端配置 ----
FRONTEND_PORT=5757

# 登录态由前端保存登录接口返回的 JWT 并自动携带,留空即可
VITE_API_AUTHORIZATION=
```

---

## 五、安全加固：修改 `docker-compose.prod.yml`

当前 `mysql` 服务直接映射了 `3306:3306`，这是公网暴露的根源。**建议修改为不暴露端口，或仅绑定本地回环地址**：

### 方案 A（推荐）：MySQL 不暴露端口，只允许内网 Docker 网络访问

由于 `backend` 服务已经通过 Docker 内部网络 `mysql:3306` 连接，**宿主机和外网根本不需要访问 3306**。

```yaml
  mysql:
    image: mysql:8.4
    container_name: jiaopei-prod-mysql
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: ${DB_NAME:-jiaopei_platform}
      MYSQL_USER: ${DB_USERNAME:-jiaopei_app}
      MYSQL_PASSWORD: ${DB_PASSWORD:-change_me_immediately}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-change_me_immediately}
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
      MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      # 新增：绑定仅本地（Docker 网络仍可用）
      - --bind-address=0.0.0.0
    # 删除 ports 映射，或改为仅本地访问
    # ports:
    #   - "127.0.0.1:3306:3306"   # 如必须外部管理，用这条
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$${MYSQL_ROOT_PASSWORD} --silent"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
```

> **操作**：注释掉或删除 `mysql.ports` 和 `redis.ports` 下的公网映射行。

### 方案 B（过渡方案）：如需远程管理，仅允许跳板机/办公网 IP

如果必须用 Navicat、DBeaver 等工具远程连数据库，不要开放 3306 给 `0.0.0.0`，而是用 **SSH 隧道** 或 **防火墙白名单**：

```bash
# 如有 UFW 防火墙（Ubuntu/Debian）
ufw deny 3306
ufw allow from 你的办公IP/32 to any port 3306

# 或者仅绑定本地 + SSH 隧道
ports:
  - "127.0.0.1:3306:3306"
```

远程连接时通过 SSH 隧道：
```bash
ssh -L 3307:localhost:3306 user@生产服务器IP
# 然后在本地用 localhost:3307 连接
```

---

## 六、重建并启动（按顺序执行）

在数据库所在服务器执行：

```bash
# 1. 进入目录
cd /path/to/jiaopei-platform/docker

# 2. 确保 prod.env 已写入强密码
cat prod.env

# 3. 拉取镜像（防止本地缓存被篡改）
docker compose -f docker-compose.prod.yml pull

# 4. 构建并启动（-d 后台运行）
docker compose -f docker-compose.prod.yml up -d --build

# 5. 查看日志确认 MySQL 正常启动
docker compose -f docker-compose.prod.yml logs -f mysql
```

等待出现 `ready for connections` 后再继续。

---

## 七、数据库权限最小化（进入 MySQL 执行）

```bash
# 进入 MySQL 容器
docker exec -it jiaopei-prod-mysql mysql -uroot -p
```

在 MySQL 内部执行：

```sql
-- 1. 查看当前用户（确认无异常账号）
SELECT user, host FROM mysql.user;

-- 2. 删除多余用户（如存在空用户、测试用户）
DROP USER IF EXISTS ''@'%';
DROP USER IF EXISTS 'root'@'%';          -- root 只允许本地登录
DROP USER IF EXISTS 'test'@'%';

-- 3. 确保 root 只能从本地访问
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '<你的root强密码>';
CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '<你的root强密码>';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;

-- 4. 应用账号只授必要权限（推荐）
CREATE USER IF NOT EXISTS 'jiaopei_app'@'%' IDENTIFIED BY '<你的应用强密码>';
GRANT SELECT, INSERT, UPDATE, DELETE ON jiaopei_platform.* TO 'jiaopei_app'@'%';

FLUSH PRIVILEGES;
```

---

## 八、Redis 安全加固

在 `docker-compose.prod.yml` 中，`redis` 没有设置密码。请按以下方式加固：

```yaml
  redis:
    image: redis:7.4-alpine
    container_name: jiaopei-prod-redis
    restart: unless-stopped
    command:
      - redis-server
      - --appendonly yes
      - --requirepass ${REDIS_PASSWORD}    # <-- 添加密码
      - --bind 0.0.0.0
      - --protected-mode yes               # <-- 开启保护模式
    # 删除端口暴露，如果不需外部访问
    # ports:
    #   - "${REDIS_PORT:-6379}:6379"
```

> Redis 默认没有密码时，任何人连上 6379 都能执行 `FLUSHALL` 清空缓存甚至利用 Redis 写文件漏洞入侵宿主机，必须加密码。

---

## 九、宿主机防火墙加固（在服务器操作系统层面）

即使 Docker 内部不暴露端口，也请在宿主机加一道防火墙：

### iptables / ufw（Linux）

```bash
# 默认拒绝所有入站
ufw default deny incoming
ufw default allow outgoing

# 只开放必要的端口
ufw allow 22/tcp        # SSH（建议改为非 22 端口或仅允许跳板机）
ufw allow 80/tcp        # HTTP（如 Nginx 反向前端）
ufw allow 443/tcp       # HTTPS
ufw allow 5757/tcp      # 前端端口（如 nginx 不在本机）
ufw allow 8181/tcp      # 后端端口（仅允许负载均衡/Nginx 访问）
# ufw allow 3306/tcp    # ⛔ MySQL 绝不再暴露！
# ufw allow 6379/tcp    # ⛔ Redis 绝不再暴露！

ufw enable
ufw status verbose
```

### Windows Server 防火墙（如服务器是 Windows）

在 Windows Defender 防火墙中：
1. 打开"高级安全 Windows Defender 防火墙"
2. 入站规则 → 新建规则
3. 端口 → TCP，特定本地端口：`3306, 6379` → **阻止连接**
4. 勾选"域/专用/公用" → 命名为 `Block-Database-Ports`
5. 确保 `8181`、`5757`、`80`、`443` 是允许的

---

## 十、后续安全建议

1. **定期改密**：建议每 3 个月更换一次数据库密码
2. **关闭公网 IP**：如果服务器有独立公网 IP，尽量配合 CDN / 私有网络，避免直接暴露
3. **启用 binlog**：在 MySQL `command` 里加 `--log-bin=mysql-bin`，便于审计和恢复
4. **定时备份**：
   ```bash
   # 每天凌晨 3 点自动全量备份（加入服务器的 crontab）
   0 3 * * * docker exec jiaopei-prod-mysql mysqldump -uroot -p'<root密码>' jiaopei_platform > /backup/jiaopei_daily_$(date +\%F).sql 2>/dev/null
   ```
5. **监控告警**：使用 `docker compose ps` 检查状态，或接入 Prometheus + Grafana 监控异常连接数

---

## 十一、检查清单（重建完成后逐项确认）

- [ ] `prod.env` 中 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD` 已替换为强密码
- [ ] `mysql.ports` 已注释或改为 `127.0.0.1:3306:3306`
- [ ] `redis.ports` 已注释或改为仅本地
- [ ] `docker compose -f docker-compose.prod.yml up -d` 启动成功
- [ ] `docker compose logs mysql` 无异常报错
- [ ] 后端服务能正常连上数据库并运行
- [ ] MySQL 内已删除 `root@%'`，仅保留本地 root
- [ ] 宿主机防火墙已阻止 3306、6379 入站
- [ ] 旧数据卷 `jiaopei-platform_mysql_data` 已删除，老化容器已清理

---

**如有疑问，可联系我协助处理 `docker-compose.prod.yml` 的修改和 `prod.env` 的更新。**
