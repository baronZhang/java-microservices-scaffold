#!/usr/bin/env bash
# 查看各实例的健康状态
source "$(dirname "$0")/env.sh"
for port in 9080 9081 9082 9083 9084 9085 9093 9094 9095; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health" 2>/dev/null)
  [[ "$code" == "200" ]] && echo "  :$port UP" || echo "  :$port -"
done
