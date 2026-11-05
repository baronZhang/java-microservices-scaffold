#!/usr/bin/env bash
# 实验脚本的公共函数：被各个实验 source。
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
G=${GATEWAY:-http://localhost:9080}
PW=$(demo_password)

json() { python3 -I -c 'import json,sys; d=json.load(sys.stdin); v=eval(sys.argv[1]); print(json.dumps(v, ensure_ascii=False) if isinstance(v,(dict,list)) else v)' "$1"; }
token() { curl -s -X POST "$G/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$PW\"}" | json 'd["data"]["accessToken"]'; }
step() { echo; echo "▶ $*"; }
now_ms() { python3 -I -c 'import time; print(int(time.time()*1000))'; }
pid_of() { cat "$PID_DIR/$1.pid" 2>/dev/null; }
