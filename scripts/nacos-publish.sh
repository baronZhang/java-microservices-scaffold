#!/usr/bin/env bash
# 把 config/ 目录下的配置发布到 Nacos（nacos profile 用）：本地文件就是配置的"源码"，Nacos 是运行时的配置中心。
# 用法：scripts/nacos-publish.sh [nacos 地址]
set -euo pipefail
source "$(dirname "$0")/env.sh"
NACOS=${1:-http://127.0.0.1:8848}
publish() { # publish <dataId> <文件>
  # 用 v1 接口：Nacos 3.x 的 v3 管理接口在没开鉴权时也要求登录
  local result
  result=$(curl -s -X POST "$NACOS/nacos/v1/cs/configs" --data-urlencode "dataId=$1" --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" --data-urlencode "content@$2")
  echo "  发布 $1 ← ${2#$MS_ROOT/}：$result"
}
publish ms-common.yaml "$MS_ROOT/config/ms-common.yml"
publish gateway.yaml "$MS_ROOT/config/gateway.yml"
