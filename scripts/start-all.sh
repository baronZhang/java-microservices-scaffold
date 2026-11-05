#!/usr/bin/env bash
# 一键启动全部服务（默认 profile = demo，不依赖任何外部中间件）。
# 用法：scripts/start-all.sh [--build]
#       MS_PROFILES=demo,redis scripts/start-all.sh      # 叠加中间件 profile
set -euo pipefail
source "$(dirname "$0")/env.sh"
resolve_java

if [[ "${1:-}" == "--build" ]] || ! ls "$MS_ROOT"/gateway/target/gateway-*.jar >/dev/null 2>&1; then
  echo "== 打包（跳过测试）"
  (cd "$MS_ROOT" && ./mvnw -B -q -DskipTests -Dspotbugs.skip=true -Dspotless.check.skip=true package)
fi

echo "== 启动服务（profiles=${MS_PROFILES:-demo}）"
for service in "${SERVICES[@]}"; do
  "$MS_ROOT/scripts/start-instance.sh" "$service"
done

echo "== 等待健康检查"
failed=0
for service in "${SERVICES[@]}"; do
  port=$(port_of "$service")
  if wait_healthy "$port" 120; then
    echo "  ✓ $service :$port"
  else
    echo "  ✗ $service :$port 启动失败，看日志 .run/logs/$service-$port.log"
    failed=1
  fi
done
[[ $failed -eq 0 ]] && echo "全部就绪：网关 http://localhost:9080" || exit 1
