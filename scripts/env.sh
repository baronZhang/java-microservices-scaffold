#!/usr/bin/env bash
# 公共变量和函数：被其他脚本 source，不单独执行。

# 项目根目录（脚本可以从任何目录调用）
MS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$MS_ROOT/.run"
LOG_DIR="$RUN_DIR/logs"
PID_DIR="$RUN_DIR/pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

# 服务名和端口：网关 9080，各服务 9081 起（8080 留给单体版）
SERVICES=(auth-service user-service product-service inventory-service order-service gateway)
port_of() {
  case "$1" in
    gateway) echo 9080 ;;
    auth-service) echo 9081 ;;
    user-service) echo 9082 ;;
    product-service) echo 9083 ;;
    inventory-service) echo 9084 ;;
    order-service) echo 9085 ;;
    *) echo "未知服务：$1" >&2; return 1 ;;
  esac
}

# 找一个 JDK 25+：优先用已经设置的 JAVA_HOME，其次 Homebrew 的 openjdk
resolve_java() {
  local candidates=("${JAVA_HOME:-}" /opt/homebrew/opt/openjdk /usr/local/opt/openjdk)
  for home in "${candidates[@]}"; do
    [[ -n "$home" && -x "$home/bin/java" ]] || continue
    local major
    major=$("$home/bin/java" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
    if [[ "${major%%.*}" -ge 25 ]]; then
      export JAVA_HOME="$home"
      return 0
    fi
  done
  echo "找不到 JDK 25+，请先安装（brew install openjdk）或设置 JAVA_HOME" >&2
  return 1
}

# 演示密码只写在种子数据里（auth-service 的 Flyway Java 迁移），脚本从那里读，不另外硬编码
demo_password() {
  grep -oE 'DEMO_PASSWORD = "[^"]+"' "$MS_ROOT/auth-service/src/main/java/db/migration/V2__SeedDemoAccounts.java" \
    | sed -E 's/.*"(.*)"/\1/'
}

# 等待某个端口的健康检查通过：wait_healthy <端口> [最长秒数]
wait_healthy() {
  local port=$1 max=${2:-90} i
  for ((i = 0; i < max; i++)); do
    if curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '^200$'; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# 进程是否在跑：is_running <pid 文件>
is_running() {
  [[ -f "$1" ]] && kill -0 "$(cat "$1")" 2>/dev/null
}
