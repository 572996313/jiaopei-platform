#!/bin/bash
# 创建操作留痕日志独立库 jiaopei_log,并将业务账号(若配置了 MYSQL_USER)授权到该库。
# 仅初始化首次创建数据卷时执行一次;镜像 MYSQL_DATABASE 只能建一个库,第二个库必须走 init 脚本。
set -e

mysql_root() {
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "$@"
}

echo "[init] 创建 jiaopei_log 数据库"
mysql_root <<'SQL'
CREATE DATABASE IF NOT EXISTS jiaopei_log DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL

# 生产环境配置了业务账号时,把日志库授权给它(应用用 DB_AUDIT_USERNAME=DB_USERNAME 访问)
if [ -n "${MYSQL_USER}" ]; then
  echo "[init] 授权账号 ${MYSQL_USER} 访问 jiaopei_log"
  mysql_root <<SQL
GRANT ALL PRIVILEGES ON jiaopei_log.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
SQL
fi

echo "[init] jiaopei_log 初始化完成"