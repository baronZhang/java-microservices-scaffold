#!/usr/bin/env bash
# 由 config/ 目录重新生成 deploy/k8s/01-configmap.yaml。
# ConfigMap 是生成物，改配置只改 config/ 下的源文件，然后执行本脚本；CI 里可以执行后用 git diff --exit-code 检查有没有漂移。
# 不依赖 kubectl：每个文件原样缩进成一个 YAML 块标量
set -euo pipefail
cd "$(dirname "$0")/.."

out=deploy/k8s/01-configmap.yaml
{
  echo '# 外部配置：由 scripts/gen-k8s-configmap.sh 从 config/ 目录生成（不要手工改这个文件），'
  echo '# 挂载到每个容器的 /config（环境变量 MS_CONFIG_DIR=/config）。'
  echo '# 改了 ConfigMap 以后，挂载的文件会在一两分钟内自动更新，再对服务执行 POST /actuator/refresh 即可生效（或用 Nacos 推送）'
  echo 'apiVersion: v1'
  echo 'kind: ConfigMap'
  echo 'metadata:'
  echo '  name: ms-config'
  echo '  namespace: ms'
  echo 'data:'
  for f in config/gateway.yml config/ms-common.yml; do
    echo "  $(basename "${f}"): |"
    # 空行不加缩进，避免行尾空格
    sed -e 's/^\(.\)/    \1/' "${f}"
  done
} > "${out}"
echo "已生成 ${out}"
