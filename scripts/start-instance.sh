#!/usr/bin/env bash
# 启动一个服务实例（后台运行）。
# 用法：scripts/start-instance.sh <服务名> [端口] [额外的 Spring 参数...]
# 例子：MS_INSTANCE_VERSION=v2 scripts/start-instance.sh product-service 9093   # 灰度版本的第二个实例
set -euo pipefail
source "$(dirname "$0")/env.sh"
resolve_java

service=$1
port=${2:-$(port_of "$service")}
shift $(( $# >= 2 ? 2 : 1 ))
jar=$(ls "$MS_ROOT/$service"/target/"$service"-*.jar 2>/dev/null | grep -v original | head -1 || true)
if [[ -z "$jar" ]]; then
  echo "没有找到 $service 的 jar，先执行 ./mvnw -DskipTests package" >&2
  exit 1
fi
name="$service-$port"
if is_running "$PID_DIR/$name.pid"; then
  echo "$name 已经在运行（pid $(cat "$PID_DIR/$name.pid")）"
  exit 0
fi
profiles=${MS_PROFILES:-demo}
# 从项目根目录启动：config/ms-common.yml、.data/ 都按相对路径找
# --enable-native-access：JDK 24+ 对 JNI 调用（Netty 加载原生库）会打警告，显式允许
cd "$MS_ROOT"
PORT=$port nohup "$JAVA_HOME/bin/java" -Xms64m -Xmx384m -XX:+UseSerialGC --enable-native-access=ALL-UNNAMED \
  -jar "$jar" --spring.profiles.active="$profiles" "$@" > "$LOG_DIR/$name.log" 2>&1 &
echo $! > "$PID_DIR/$name.pid"
echo "启动 ${name}（profiles=${profiles}，pid $!，日志 .run/logs/$name.log）"
