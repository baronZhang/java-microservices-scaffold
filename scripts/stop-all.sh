#!/usr/bin/env bash
# 停止本脚本启动的全部服务实例（含第二实例）：先发 SIGTERM 优雅停机，20 秒还没退出再强杀。
set -uo pipefail
source "$(dirname "$0")/env.sh"

shopt -s nullglob
pids=("$PID_DIR"/*.pid)
if [[ ${#pids[@]} -eq 0 ]]; then
  echo "没有在运行的实例"
  exit 0
fi
for file in "${pids[@]}"; do
  name=$(basename "$file" .pid)
  if is_running "$file"; then
    kill "$(cat "$file")" && echo "停止 ${name}（pid $(cat "$file")）"
  fi
done
for file in "${pids[@]}"; do
  for _ in $(seq 1 20); do
    is_running "$file" || break
    sleep 1
  done
  if is_running "$file"; then
    kill -9 "$(cat "$file")" && echo "强制停止 $(basename "$file" .pid)"
  fi
  rm -f "$file"
done
echo "已全部停止"
