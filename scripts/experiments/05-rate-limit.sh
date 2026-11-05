#!/usr/bin/env bash
# 实验 5：网关限流（令牌桶）。普通接口按用户：每秒 20 个、桶容量 40；登录接口按 IP：每秒 1 个、桶容量 5
set -uo pipefail
source "$(dirname "$0")/lib.sh"
T=$(token editor)
sleep 3   # 上面的登录也消耗了登录接口的令牌，先等桶补满

step "1. 同一个用户并发打 60 个请求（桶里最多 40 个令牌）"
seq 1 60 | xargs -P 30 -I{} curl -s -o /dev/null -w '%{http_code}\n' "$G/api/products?pageSize=1" -H "Authorization: Bearer $T" \
  | sort | uniq -c | sed 's/^/  /'
step "2. 被限流时的响应（统一结构 + 限流头）"
for i in $(seq 1 50); do curl -s -o /dev/null "$G/api/products?pageSize=1" -H "Authorization: Bearer $T" & done; wait
curl -s -D - "$G/api/products?pageSize=1" -H "Authorization: Bearer $T" | grep -E "^HTTP|^X-RateLimit|code" | tr -d '\r' | sed 's/^/  /'
step "3. 暴力破解登录：同一 IP 连续 8 次错误密码"
sleep 6
for i in $(seq 1 8); do
  curl -s -X POST "$G/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}' -w ' HTTP %{http_code}\n'
done | sed 's/^/  /'
