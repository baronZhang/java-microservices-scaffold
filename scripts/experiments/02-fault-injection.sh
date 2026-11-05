#!/usr/bin/env bash
# 实验 2：故障注入——让服务变慢（超时 + 降级 + 熔断）、把服务停掉（下单链路的补偿）
set -uo pipefail
source "$(dirname "$0")/lib.sh"
T=$(token guest)

product() {
  local start end body
  start=$(now_ms)
  body=$(curl -s "$G/api/products/1" -H "Authorization: Bearer $T")
  end=$(now_ms)
  echo "  $(echo "$body" | json '[d["data"]["stock"], d["data"]["stockStatus"]]')  耗时 $((end - start))ms"
}
breaker() { curl -s "localhost:$1/actuator/circuitbreakers" | json "d['circuitBreakers'].get('$2', {}).get('state')"; }

step "A1. 正常情况：商品详情带库存"
product
step "A2. 故障注入：库存服务的内部接口每次延迟 3 秒（商品服务调用它的 read-timeout 是 1 秒）"
curl -s -X POST localhost:9084/actuator/chaos -H 'Content-Type: application/json' -d '{"delayMs":3000,"pathPattern":"/internal/**"}'; echo
for i in $(seq 1 7); do product; done
echo "  商品服务的熔断器 inventory-service 状态：$(breaker 9083 inventory-service)"
step "A3. 撤掉故障，等熔断器 10 秒后进入半开、放请求试探"
curl -s -X DELETE localhost:9084/actuator/chaos > /dev/null
sleep 11
for i in $(seq 1 3); do product; done
echo "  熔断器状态：$(breaker 9083 inventory-service)"

step "B1. kill -9 停掉库存服务，然后连续下单 7 次（订单服务：重试 2 次 → 熔断 → 结果未知 → 取消订单 + 释放事件）"
kill -9 "$(pid_of inventory-service-9084)"; rm -f "$PID_DIR/inventory-service-9084.pid"
for i in $(seq 1 7); do
  start=$(now_ms)
  r=$(curl -s -X POST "$G/api/orders" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
      -H "Idempotency-Key: fault-$RANDOM-$RANDOM" -d '{"items":[{"skuId":2,"quantity":1}]}' -w ' HTTP %{http_code}')
  echo "  ${r}  耗时 $(( $(now_ms) - start ))ms"
done
echo "  订单服务的熔断器 inventory-reserve 状态：$(breaker 9085 inventory-reserve)"
step "B2. 本地消息表：补偿用的释放事件投递不出去，按指数退避重试"
curl -s localhost:9085/actuator/outbox | json '{k: d[k] for k in ("pending","sent","dead")}'
curl -s localhost:9085/actuator/outbox | json 'd["unsent"][:3]'
step "B3. 重新启动库存服务，等消息中继把积压的事件投递完"
"$MS_ROOT/scripts/start-instance.sh" inventory-service && wait_healthy 9084 && echo "  库存服务已恢复"
for i in $(seq 1 12); do
  pending=$(curl -s localhost:9085/actuator/outbox | json 'd["pending"]')
  [[ "$pending" == "0" ]] && break
  sleep 5
done
curl -s localhost:9085/actuator/outbox | json '{k: d[k] for k in ("pending","sent","dead")}'
step "B4. 库存服务收到的释放事件：之前预占都没成功，所以全部是空回滚"
grep -E "空回滚" "$LOG_DIR/inventory-service-9084.log" | tail -3 | sed -E 's/.*\] [^:]+: /  /'
