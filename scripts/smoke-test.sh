#!/usr/bin/env bash
# 经网关用 curl 走一遍核心接口，逐项断言，最后打印通过 / 失败数。
# 前提：scripts/start-all.sh 已经启动全部服务（demo profile）。
# 用法：scripts/smoke-test.sh [网关地址]
set -uo pipefail
source "$(dirname "$0")/env.sh"

G=${1:-http://localhost:9080}
PW=$(demo_password)
PASS=0
FAIL=0

# 从 JSON 里取值：json '<python 表达式，d 是解析后的对象>'
json() { python3 -I -c 'import json,sys; d=json.load(sys.stdin); v=eval(sys.argv[1]); print(json.dumps(v, ensure_ascii=False) if isinstance(v,(dict,list)) else v)' "$1"; }

# 发请求：call <方法> <路径> [token] [JSON 请求体] [额外请求头]；结果放在 BODY、STATUS、HEADERS
call() {
  local method=$1 path=$2 token=${3:-} body=${4:-} extra=${5:-}
  local args=(-s -X "$method" "$G$path" -D /dev/stdout -o "$RUN_DIR/smoke-body.json" -w '%{http_code}')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  [[ -n "$extra" ]] && args+=(-H "$extra")
  local out
  out=$(curl "${args[@]}")
  STATUS=${out: -3}
  HEADERS=${out%???}
  BODY=$(cat "$RUN_DIR/smoke-body.json")
}

check() { # check <描述> <实际值> <期望值>
  if [[ "$2" == "$3" ]]; then
    PASS=$((PASS + 1)); echo "  ✓ $1"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $1：期望 [$3]，实际 [$2]"
  fi
}

login() { call POST /api/auth/login "" "{\"username\":\"$1\",\"password\":\"$PW\"}"; }

echo "== 认证"
call POST /api/auth/login "" '{"username":"admin","password":"wrong"}'
check "密码错误：HTTP 200 + 业务码 10001" "$STATUS $(echo "$BODY" | json 'd["code"]')" "200 10001"
login admin;  ADMIN=$(echo "$BODY" | json 'd["data"]["accessToken"]'); ADMIN_RT=$(echo "$BODY" | json 'd["data"]["refreshToken"]')
check "admin 登录，expiresIn=300" "$(echo "$BODY" | json 'd["data"]["expiresIn"]')" "300"
login editor; EDITOR=$(echo "$BODY" | json 'd["data"]["accessToken"]')
login guest;  GUEST=$(echo "$BODY" | json 'd["data"]["accessToken"]')
call GET /api/auth/profile "$ADMIN"
check "profile：管理员的权限码是 *" "$(echo "$BODY" | json 'd["data"]["permissions"]')" '["*"]'
check "响应头带 X-Trace-Id" "$(echo "$HEADERS" | grep -ci '^x-trace-id:')" "1"
call GET /api/users
check "不带 token：HTTP 401 + 业务码 401" "$STATUS $(echo "$BODY" | json 'd["code"]')" "401 401"
call GET /api/users "not-a-jwt"
check "伪造 token：HTTP 401" "$STATUS" "401"

echo "== 用户管理与权限"
call GET "/api/users?keyword=user05&page=1&pageSize=3" "$EDITOR"
check "editor 搜 user05：共 10 条（和 Mock 一致）" "$(echo "$BODY" | json 'd["data"]["total"]')" "10"
call GET /api/users/7 "$ADMIN"
check "种子数据和 Mock 一致：用户 7 = 周娜洋 / editor / disabled" "$(echo "$BODY" | json '[d["data"]["nickname"], d["data"]["role"], d["data"]["status"]]')" '["周娜洋", "editor", "disabled"]'
call GET /api/users "$GUEST"
check "guest 查用户列表：HTTP 403 + 业务码 403" "$STATUS $(echo "$BODY" | json 'd["code"]')" "403 403"
call DELETE /api/users/10 "$EDITOR"
check "editor 删除用户（没有 user:delete）：403" "$STATUS" "403"
call PUT /api/users/2/status "$EDITOR" '{"status":"disabled"}'
check "editor 禁用自己：HTTP 200 + 业务码 10002" "$STATUS $(echo "$BODY" | json 'd["code"]')" "200 10002"
call GET /api/users/999 "$ADMIN"
check "不存在的用户：HTTP 404" "$STATUS" "404"
call GET /internal/inventory/stocks?skuIds=1 "$ADMIN"
check "内部接口不经网关暴露：404" "$STATUS" "404"

echo "== refresh token 轮换"
call POST /api/auth/refresh "" "{\"refreshToken\":\"$ADMIN_RT\"}"
NEW_RT=$(echo "$BODY" | json 'd["data"]["refreshToken"]')
check "第一次刷新成功" "$(echo "$BODY" | json 'd["code"]')" "0"
call POST /api/auth/refresh "" "{\"refreshToken\":\"$ADMIN_RT\"}"
check "旧 refresh token 再用：401" "$STATUS" "401"
call POST /api/auth/refresh "" "{\"refreshToken\":\"$NEW_RT\"}"
check "复用检测：整个会话作废，新 token 也失效" "$STATUS" "401"

echo "== token 立即失效（demo 接口）与退出登录"
login admin; ADMIN=$(echo "$BODY" | json 'd["data"]["accessToken"]'); ADMIN_RT=$(echo "$BODY" | json 'd["data"]["refreshToken"]')
call GET /api/debug/stats; BEFORE=$(echo "$BODY" | json 'd["data"]["refreshCount"]')
call POST /api/debug/expire-token "$ADMIN"
check "expire-token 成功" "$(echo "$BODY" | json 'd["code"]')" "0"
call GET /api/auth/profile "$ADMIN"
check "被吊销的 token：401" "$STATUS" "401"
call POST /api/auth/refresh "" "{\"refreshToken\":\"$ADMIN_RT\"}"
ADMIN=$(echo "$BODY" | json 'd["data"]["accessToken"]')
call GET /api/debug/stats
check "刷新计数 +1" "$(echo "$BODY" | json 'd["data"]["refreshCount"]')" "$((BEFORE + 1))"
login guest; G2=$(echo "$BODY" | json 'd["data"]["accessToken"]')
call POST /api/auth/logout "$G2"
call GET /api/auth/profile "$G2"
check "退出登录后旧 access token 立即失效：401" "$STATUS" "401"

echo "== 商品与库存"
call GET "/api/products?pageSize=10" "$GUEST"
check "商品列表 6 个，带库存档位" "$(echo "$BODY" | json '[d["data"]["total"], d["data"]["list"][0]["stockStatus"]]')" '[6, "IN_STOCK"]'
call GET /api/inventory/1 "$GUEST"; STOCK1=$(echo "$BODY" | json 'd["data"]["available"]')
call PUT /api/inventory/1 "$EDITOR" '{"available":100}'
check "editor 调整库存（没有 inventory:edit）：403" "$STATUS" "403"

echo "== 下单链路"
KEY="smoke-$(date +%s)-$RANDOM"
call POST /api/orders "$GUEST" '{"items":[{"skuId":1,"quantity":2}]}' "Idempotency-Key: $KEY"
ORDER=$(echo "$BODY" | json 'd["data"]["orderNo"]')
check "下单成功，状态 PENDING_PAYMENT，金额 798.00" "$(echo "$BODY" | json '[d["data"]["status"], str(d["data"]["totalAmount"])]')" '["PENDING_PAYMENT", "798.0"]'
check "订单号是字符串（避免 JS 精度丢失）" "$(echo "$BODY" | json 'type(d["data"]["orderNo"]).__name__')" "str"
call POST /api/orders "$GUEST" '{"items":[{"skuId":1,"quantity":2}]}' "Idempotency-Key: $KEY"
check "同一个幂等键重复提交：返回同一个订单" "$(echo "$BODY" | json 'd["data"]["orderNo"]')" "$ORDER"
check "重放的响应带 Idempotent-Replayed: true" "$(echo "$HEADERS" | grep -ci '^idempotent-replayed: true')" "1"
call POST /api/orders "$GUEST" '{"items":[{"skuId":1,"quantity":5}]}' "Idempotency-Key: $KEY"
check "同一个幂等键、不同的下单内容：业务码 10002" "$(echo "$BODY" | json 'd["code"]')" "10002"
call GET /api/inventory/1 "$GUEST"
check "预占后可售 -2" "$(echo "$BODY" | json 'd["data"]["available"]')" "$((STOCK1 - 2))"
RESERVED1=$(echo "$BODY" | json 'd["data"]["reserved"]'); SOLD1=$(echo "$BODY" | json 'd["data"]["sold"]')
call POST /api/orders "$GUEST" '{"items":[{"skuId":1,"quantity":1}]}'
check "不带幂等键：400" "$STATUS" "400"
call POST "/api/orders/$ORDER/pay" "$GUEST"
check "支付成功" "$(echo "$BODY" | json 'd["data"]["status"]')" "PAID"
call POST "/api/orders/$ORDER/pay" "$GUEST"
check "重复支付：幂等返回 PAID" "$(echo "$BODY" | json 'd["data"]["status"]')" "PAID"
call GET "/api/orders/$ORDER" "$EDITOR"
check "别人的订单：404" "$STATUS" "404"
call GET "/api/orders/$ORDER" "$ADMIN"
check "管理员（order:all）可以查看任意订单" "$STATUS" "200"
sleep 3
call GET /api/inventory/1 "$GUEST"
# 断言变化量而不是绝对值：库里可能还有别的未支付订单占着预占量
check "支付事件经本地消息表投递后：预占 -2、已售 +2" "$(echo "$BODY" | json '[d["data"]["reserved"], d["data"]["sold"]]')" "[$((RESERVED1 - 2)), $((SOLD1 + 2))]"

call POST /api/orders "$GUEST" '{"items":[{"skuId":2,"quantity":3}]}' "Idempotency-Key: smoke-cancel-$RANDOM-$RANDOM"
ORDER2=$(echo "$BODY" | json 'd["data"]["orderNo"]')
call GET /api/inventory/2 "$GUEST"; AVAILABLE2=$(echo "$BODY" | json 'd["data"]["available"]')
call POST "/api/orders/$ORDER2/cancel" "$GUEST"
check "取消订单" "$(echo "$BODY" | json '[d["data"]["status"], d["data"]["cancelReason"]]')" '["CANCELLED", "USER"]'
sleep 3
call GET /api/inventory/2 "$GUEST"
check "取消事件投递后：库存退回 +3" "$(echo "$BODY" | json 'd["data"]["available"]')" "$((AVAILABLE2 + 3))"
call POST /api/orders "$GUEST" '{"items":[{"skuId":3,"quantity":99}]}' "Idempotency-Key: smoke-insufficient-$RANDOM-$RANDOM"
check "库存不足：HTTP 200 + 业务码 20001" "$STATUS $(echo "$BODY" | json 'd["code"]')" "200 20001"

echo
echo "通过 $PASS 项，失败 $FAIL 项"
[[ $FAIL -eq 0 ]]
