#!/usr/bin/env bash
# 实验 1：静态服务发现 + 客户端负载均衡 + 健康检查剔除 + 网关 GET 重试
set -uo pipefail
source "$(dirname "$0")/lib.sh"
T=$(token admin)

served_by() { curl -s -D - -o /dev/null "$G/api/inventory/6" -H "Authorization: Bearer $T" -w 'HTTP %{http_code} %{time_total}s\n' | grep -E '^X-Served-By|^HTTP' | tr -d '\r' | paste -sd' ' -; }

step "启动 inventory-service 的第二个实例（9094，静态服务列表里早就登记了它）"
"$MS_ROOT/scripts/start-instance.sh" inventory-service 9094 && wait_healthy 9094 && echo "9094 已就绪"
step "等一个健康检查周期（5 秒），让网关发现它"
sleep 6
step "经网关请求 10 次 GET /api/inventory/6，看是哪个实例处理的"
for i in $(seq 1 10); do served_by; done | awk '{print}' | sort | uniq -c | sed 's/^/  /'
step "打开网关重试过滤器的 TRACE 日志（运行时改日志级别，不用重启）"
curl -s -X POST localhost:9080/actuator/loggers/org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory \
  -H 'Content-Type: application/json' -d '{"configuredLevel":"TRACE"}' -o /dev/null -w '  HTTP %{http_code}\n'
step "kill -9 停掉 9094（模拟宕机），立刻再请求 10 次"
log_start=$(( $(wc -l < "$LOG_DIR/gateway-9080.log") + 1 ))   # macOS 的 wc 输出带前导空格，用算术展开去掉
kill -9 "$(pid_of inventory-service-9094)"; rm -f "$PID_DIR/inventory-service-9094.pid"
for i in $(seq 1 10); do served_by; done | sed 's/^/  /'
step "网关日志里的重试痕迹（连接被拒绝 → 判定可重试 → 换实例重发）"
tail -n +"$log_start" "$LOG_DIR/gateway-9080.log" | grep -E "retryable|setting new iteration" | tail -4 | sed -E 's/.*RetryGatewayFilterFactory *: /  /' | cut -c1-160
echo "  这 10 次请求里，被分到已宕机实例、由网关重试到 9084 的有 $(tail -n +"$log_start" "$LOG_DIR/gateway-9080.log" | grep -c 'setting new iteration in attr 1') 次"
curl -s -X POST localhost:9080/actuator/loggers/org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory \
  -H 'Content-Type: application/json' -d '{"configuredLevel":null}' -o /dev/null
step "等健康检查剔除（再过一个周期），再请求 5 次"
sleep 6
for i in $(seq 1 5); do served_by; done | sed 's/^/  /'
