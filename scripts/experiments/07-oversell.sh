#!/usr/bin/env bash
# 实验 7：端到端防超卖。秒杀商品（6 号）库存 50，经网关 200 个并发下单请求
set -uo pipefail
source "$(dirname "$0")/lib.sh"
ADMIN=$(token admin); T=$(token guest)
CONFIG="$MS_ROOT/config/gateway.yml"

step "1. 管理员把 6 号商品的可售库存重置为 50"
curl -s -X PUT "$G/api/inventory/6" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"available":50}' \
  | json '[d["data"]["available"], d["data"]["reserved"], d["data"]["sold"]]' | sed 's/^/  [可售, 预占, 已售] = /'
step "2. 压测前临时调高这个用户的限流阈值（动态配置，不重启）"
cp "$CONFIG" "$RUN_DIR/gateway.yml.bak"
sed -i '' 's/        replenish-rate: 20/        replenish-rate: 1000/; s/        burst-capacity: 40/        burst-capacity: 1000/' "$CONFIG"
curl -s -X POST localhost:9080/actuator/refresh | sed 's/^/  刷新的配置项：/'; echo
step "3. 200 个请求、50 路并发同时下单（每单 1 件，幂等键各不相同）"
start=$(now_ms)
OUT="$RUN_DIR/oversell"; rm -rf "$OUT"; mkdir -p "$OUT"
# 每个请求的响应单独写一个文件：50 个 curl 同时往标准输出写，行会互相穿插
seq 1 200 | xargs -P 50 -I{} curl -s -X POST "$G/api/orders" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: oversell-{}-$RANDOM" -d '{"items":[{"skuId":6,"quantity":1}]}' -o "$OUT/{}.json"
python3 -I -c 'import json,sys,glob,collections; c=collections.Counter(json.load(open(f))["code"] for f in glob.glob(sys.argv[1]+"/*.json")); print("  业务码分布（0 成功 / 20001 库存不足 / 429 限流）：", dict(c))' "$OUT"
echo "  总耗时 $(( $(now_ms) - start ))ms"
step "4. 库存：可售 0、预占 50，没有负数"
curl -s "$G/api/inventory/6" -H "Authorization: Bearer $T" | json '[d["data"]["available"], d["data"]["reserved"], d["data"]["sold"]]' | sed 's/^/  [可售, 预占, 已售] = /'
step "5. 恢复限流配置"
cp "$RUN_DIR/gateway.yml.bak" "$CONFIG"; curl -s -X POST localhost:9080/actuator/refresh >/dev/null; echo "  已恢复"
