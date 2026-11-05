#!/usr/bin/env bash
# 实验 6：两个订单服务实例 —— 雪花 workerId 自动分配、分布式锁保证定时任务不重复执行
set -uo pipefail
source "$(dirname "$0")/lib.sh"
T=$(token guest)

step "以 10 秒支付超时重启订单服务，并启动第二个实例 9095（两个实例共享同一个 H2 库）"
kill "$(pid_of order-service-9085)" 2>/dev/null; rm -f "$PID_DIR/order-service-9085.pid"; sleep 5
ORDER_PAYMENT_TIMEOUT=10s "$MS_ROOT/scripts/start-instance.sh" order-service 9085 >/dev/null
ORDER_PAYMENT_TIMEOUT=10s "$MS_ROOT/scripts/start-instance.sh" order-service 9095 >/dev/null
wait_healthy 9085 && wait_healthy 9095 && sleep 6
grep -h "雪花算法 workerId" "$LOG_DIR/order-service-9085.log" "$LOG_DIR/order-service-9095.log" | sed -E 's/.*WorkerIdLease *: /  /'
step "经网关下 6 单（负载均衡到两个实例），解析订单号"
for i in $(seq 1 6); do
  curl -s -D "$RUN_DIR/h.txt" -X POST "$G/api/orders" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
       -H "Idempotency-Key: lock-$RANDOM-$RANDOM" -d '{"items":[{"skuId":2,"quantity":1}]}' | json 'd["data"]["orderNo"]' > "$RUN_DIR/no.txt"
  NO=$(cat "$RUN_DIR/no.txt"); BY=$(grep -i '^X-Served-By' "$RUN_DIR/h.txt" | tr -d '\r' | cut -d' ' -f2)
  python3 -I -c "
no=int('$NO'); epoch=1767225600000
import datetime
ts=(no>>22)+epoch; worker=(no>>12)&1023; seq=no&4095
print(f'  {no}  由 $BY 生成 → 时间 {datetime.datetime.fromtimestamp(ts/1000, datetime.timezone.utc).strftime(\"%H:%M:%S.%f\")[:-3]}Z  workerId={worker}  序号={seq}')"
done
step "等支付超时（10 秒）+ 超时任务（每 5 秒）"
sleep 20
step "两个实例的超时任务日志：同一时刻只有一个实例拿到锁执行，6 张订单各被取消一次"
grep -h "任务 order-timeout-cancel 由" "$LOG_DIR/order-service-9085.log" "$LOG_DIR/order-service-9095.log" | sed -E 's/^.{11}([0-9:.]{12}).*OrderJobs *: /  \1 /'
echo "  超时取消日志共 $(cat "$LOG_DIR/order-service-9085.log" "$LOG_DIR/order-service-9095.log" | grep -c '超时未支付，已取消') 条"
step "停掉第二个实例，订单服务恢复默认配置"
kill "$(pid_of order-service-9095)"; rm -f "$PID_DIR/order-service-9095.pid"
kill "$(pid_of order-service-9085)"; rm -f "$PID_DIR/order-service-9085.pid"; sleep 5
"$MS_ROOT/scripts/start-instance.sh" order-service >/dev/null && wait_healthy 9085 && echo "  order-service 9085 已用默认配置重启"
