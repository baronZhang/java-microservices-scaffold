#!/usr/bin/env bash
# 实验 9（nacos profile）：注册中心换成 Nacos；在 Nacos 里改配置，网关自动收到推送并刷新（不调用 /actuator/refresh）
# 前提：docker compose -f deploy/docker-compose.infra.yml up -d nacos；scripts/nacos-publish.sh；MS_PROFILES=demo,nacos scripts/start-all.sh
set -uo pipefail
source "$(dirname "$0")/lib.sh"
NACOS=http://127.0.0.1:8848
GUEST=$(token guest)
served() { curl -s -o /dev/null -D - "$G/api/products/1" -H "Authorization: Bearer $GUEST" | grep -i '^X-Served-By' | tr -d '\r' | sed 's/X-Served-By: //'; }
publish_gateway() { curl -s -X POST "$NACOS/nacos/v1/cs/configs" --data-urlencode dataId=gateway.yaml --data-urlencode group=DEFAULT_GROUP \
  --data-urlencode type=yaml --data-urlencode "content@$1"; }

step "1. 服务都注册到了 Nacos"
curl -s "$NACOS/nacos/v1/ns/service/list?pageNo=1&pageSize=20" | json 'd["doms"]' | sed 's/^/  /'
step "2. 启动商品服务 v2，注册时带元数据 hint=gray"
MS_PROFILES=demo,nacos MS_INSTANCE_VERSION=v2 "$MS_ROOT/scripts/start-instance.sh" product-service 9093 \
  --spring.cloud.nacos.discovery.metadata.hint=gray >/dev/null && wait_healthy 9093 && sleep 3
curl -s "$NACOS/nacos/v1/ns/instance/list?serviceName=product-service" | json '[(h["port"], h["metadata"].get("hint")) for h in d["hosts"]]' | sed 's/^/  Nacos 里的实例：/'
for i in 1 2 3; do echo "  guest → $(served)"; done
step "3. 只在 Nacos 里把 guest（id 3）加进灰度名单，不调用任何刷新接口"
sed 's/      user-ids: \[\]/      user-ids: [3]/' "$MS_ROOT/config/gateway.yml" > "$RUN_DIR/gateway-gray.yml"
echo "  发布：$(publish_gateway "$RUN_DIR/gateway-gray.yml")"
sleep 2
grep -E "Receive Nacos config change|Refresh keys changed" "$LOG_DIR/gateway-9080.log" | tail -2 | sed -E 's/.*\] [^:]+: /  网关日志：/' | cut -c1-140
for i in 1 2 3; do echo "  guest → $(served)"; done
step "4. 恢复 Nacos 里的配置"
echo "  发布：$(publish_gateway "$MS_ROOT/config/gateway.yml")"; sleep 2
echo "  guest → $(served)"
kill "$(pid_of product-service-9093)"; rm -f "$PID_DIR/product-service-9093.pid"
