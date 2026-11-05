#!/usr/bin/env bash
# 实验 8：链路追踪 —— 一次请求跨多个服务，日志里是同一个 traceId
set -uo pipefail
source "$(dirname "$0")/lib.sh"
T=$(token guest)

trace_of() { grep -i '^X-Trace-Id' "$1" | tr -d '\r' | cut -d' ' -f2; }
show() { # show <traceId>：按时间顺序列出所有服务里带这个 traceId 的日志
  grep -h "$1" "$LOG_DIR"/*.log | sed -E 's/^.{11}([0-9:.]{12}).*--- \[([a-z-]+)\].*\] ([^ ]+) *: /\1 [\2] /' | sort | cut -c1-150 | sed 's/^/  /'
}

step "1. 下单：网关 → 订单服务 →（商品服务、库存服务）"
curl -s -D "$RUN_DIR/trace-h.txt" -o /dev/null -X POST "$G/api/orders" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: trace-$RANDOM-$RANDOM" -d '{"items":[{"skuId":1,"quantity":1}]}'
ID=$(trace_of "$RUN_DIR/trace-h.txt"); echo "  响应头 X-Trace-Id: ${ID}"; sleep 1; show "$ID"
step "2. 商品详情：网关 → 商品服务 → 库存服务（经过熔断器包装的 HTTP Interface 客户端）"
curl -s -D "$RUN_DIR/trace-h.txt" -o /dev/null "$G/api/products/2" -H "Authorization: Bearer $T"
ID=$(trace_of "$RUN_DIR/trace-h.txt"); echo "  响应头 X-Trace-Id: ${ID}"; sleep 1; show "$ID"
