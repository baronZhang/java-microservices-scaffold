#!/usr/bin/env bash
# 实验 3：本地消息表的可靠投递（消费方宕机期间不丢）与重复投递（超时重投 → 消费幂等）
set -uo pipefail
source "$(dirname "$0")/lib.sh"
T=$(token guest)
order() { curl -s -X POST "$G/api/orders" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
          -H "Idempotency-Key: mq-$RANDOM-$RANDOM" -d "{\"items\":[{\"skuId\":$1,\"quantity\":$2}]}" | json 'd["data"]["orderNo"]'; }
stock() { curl -s "$G/api/inventory/$1" -H "Authorization: Bearer $T" | json '[d["data"]["available"], d["data"]["reserved"], d["data"]["sold"]]'; }
outbox() { curl -s localhost:9085/actuator/outbox | json '{k: d[k] for k in ("pending","sent","dead")}'; }

step "A1. 下单（预占 2 件 4 号商品），然后停掉库存服务"
NO=$(order 4 2); echo "  订单 ${NO}，库存 [可售, 预占, 已售] = $(stock 4)"
kill -9 "$(pid_of inventory-service-9084)"; rm -f "$PID_DIR/inventory-service-9084.pid"
step "A2. 库存服务宕机期间支付：订单状态立即变成 PAID，'确认库存'的事件留在本地消息表里"
curl -s -X POST "$G/api/orders/$NO/pay" -H "Authorization: Bearer $T" | json '{"status": d["data"]["status"]}'
sleep 8
echo "  本地消息表：$(outbox)"
curl -s localhost:9085/actuator/outbox | json '[{"type": e["type"], "attempts": e["attempts"], "nextAttemptAt": e["nextAttemptAt"]} for e in d["unsent"][:1]]' | sed 's/^/  /'
grep -E "第 [0-9]+ 次投递失败" "$LOG_DIR/order-service-9085.log" | tail -3 | sed -E 's/.*OutboxRelayService *: /  /' | cut -c1-120
step "A3. 库存服务恢复，事件补投，预占转为已售"
"$MS_ROOT/scripts/start-instance.sh" inventory-service >/dev/null && wait_healthy 9084
for i in $(seq 1 15); do [[ "$(curl -s localhost:9085/actuator/outbox | json 'd["pending"]')" == "0" ]] && break; sleep 2; done
echo "  本地消息表：$(outbox)，库存 = $(stock 4)"

step "B1. 让库存服务的事件接口变慢 3 秒（消息中继的读超时是 1 秒）：对方处理成功了，但中继以为失败"
curl -s -X POST localhost:9084/actuator/chaos -H 'Content-Type: application/json' -d '{"delayMs":3000,"pathPattern":"/internal/events/**"}' >/dev/null
NO=$(order 5 3); echo "  订单 $NO 已预占，库存 = $(stock 5)"
curl -s -X POST "$G/api/orders/$NO/cancel" -H "Authorization: Bearer $T" | json '{"status": d["data"]["status"]}' | sed 's/^/  取消：/'
sleep 12
curl -s -X DELETE localhost:9084/actuator/chaos >/dev/null
step "B2. 结果：中继超时后重投了同一个事件，库存服务按事件 id 判重，库存只退回一次"
grep -E "投递失败" "$LOG_DIR/order-service-9085.log" | grep "$NO" | tail -2 | sed -E 's/.*OutboxRelayService *: /  订单服务：/' | cut -c1-150
grep -E "$NO" "$LOG_DIR/inventory-service-9084.log" | grep -E "释放|重复" | sed -E 's/.*\] [^:]+: /  库存服务：/' | cut -c1-150
echo "  库存 = $(stock 5)，本地消息表：$(outbox)"
