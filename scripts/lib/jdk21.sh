#!/usr/bin/env bash
# JDK 21 定位工具：被 scripts/ci-check.sh 与 scripts/restart-backend.sh 共用（source 使用）。
#
# 背景：本机 JAVA_HOME 默认指向 JDK 8，而项目要求 JDK 21。旧写法 `export JAVA_HOME="${JAVA_HOME:-...}"`
# 只在 JAVA_HOME 为空时才回落，JAVA_HOME 非空（但指向 8）时照旧用 8，报错是 Maven 的
# `class file version 65.0` 或运行时的 `UnsupportedClassVersionError`，都不提示是 JDK 版本问题。
#
# 其它 JDK 21 安装位置可用环境变量 JDK21_HOME 指定。

is_jdk21() {
  [ -n "${1:-}" ] && [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q 'version "21'
}

# 确保 JAVA_HOME 指向 JDK 21；找不到则报错退出（exit 1）
ensure_jdk21() {
  if ! is_jdk21 "${JAVA_HOME:-}"; then
    for candidate in "${JDK21_HOME:-}" "C:/Program Files/Java/jdk-21.0.12" "$HOME/.posh_sdk/java/21.0.9-oracle"; do
      if is_jdk21 "$candidate"; then
        export JAVA_HOME="$candidate"
        break
      fi
    done
  fi
  if ! is_jdk21 "${JAVA_HOME:-}"; then
    echo "未找到 JDK 21：请把 JAVA_HOME 或 JDK21_HOME 指向 JDK 21 安装目录后重试" >&2
    exit 1
  fi
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "JDK: $JAVA_HOME"
}
