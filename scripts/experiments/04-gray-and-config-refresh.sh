#!/usr/bin/env bash
# 实验 4：灰度路由 + 配置动态刷新（默认 profile：改 config/gateway.yml 后 POST /actuator/refresh）
set -uo pipefail
source "$(dirname "$0")/lib.sh"
GUEST=$(token guest); EDITOR=$(token editor)
served() { curl -s -o /dev/null -D - "$G/api/products/1" -H "Authorization: Bearer $1" ${2:+-H "$2"} | grep -i '^X-Served-By' | tr -d '\r' | sed 's/X-Served-By: //'; }
CONFIG="$MS_ROOT/config/gateway.yml"

step "启动商品服务的灰度版本：第二个实例 9093，版本 v2（静态服务列表里它的 hint=gray）"
MS_INSTANCE_VERSION=v2 "$MS_ROOT/scripts/start-instance.sh" product-service 9093 >/dev/null && wait_healthy 9093 && sleep 6
step "1. 普通请求（不带灰度标签）：只会落到稳定版"
for i in 1 2 3 4; do echo "  guest → $(served "$GUEST")"; done
step "2. 带 X-Gray: gray 请求头（测试同学指定灰度版）"
for i in 1 2; do echo "  guest + X-Gray → $(served "$GUEST" 'X-Gray: gray')"; done
step "3. 动态配置：把 guest（用户 id 3）加入灰度白名单，刷新网关配置"
cp "$CONFIG" "$RUN_DIR/gateway.yml.bak"
sed -i '' 's/      user-ids: \[\]/      user-ids: [3]/' "$CONFIG"
echo "  POST /actuator/refresh → $(curl -s -X POST localhost:9080/actuator/refresh)"
for i in 1 2 3; do echo "  guest → $(served "$GUEST")  |  editor → $(served "$EDITOR")"; done
step "4. 恢复配置并刷新"
cp "$RUN_DIR/gateway.yml.bak" "$CONFIG"
echo "  POST /actuator/refresh → $(curl -s -X POST localhost:9080/actuator/refresh)"
echo "  guest → $(served "$GUEST")"
step "5. 灰度实例宕机：灰度用户退回稳定版，普通用户不受影响"
kill -9 "$(pid_of product-service-9093)"; rm -f "$PID_DIR/product-service-9093.pid"; sleep 6
echo "  guest + X-Gray → $(served "$GUEST" 'X-Gray: gray')"
