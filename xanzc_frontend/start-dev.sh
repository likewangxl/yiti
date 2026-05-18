#!/usr/bin/env bash
# xanzc_frontend 开发服务器启动脚本
# ----------------------------------------------------------------
# 用法：
#   ./start-dev.sh          # 启动 vite dev server（后台运行）
#   ./start-dev.sh stop     # 停止上次启动的 vite
#   ./start-dev.sh restart  # 先 stop 再 start
#   ./start-dev.sh status   # 查看运行状态
#   ./start-dev.sh tail     # 实时跟随当前日志
#
# 日志：logs/dev-YYYYMMDD-HHMMSS.log（每次启动一个新文件）
# PID：logs/vite.pid
# 端口：8090（vite.config.js 配置；冲突时此脚本会报错退出）
# ----------------------------------------------------------------
set -euo pipefail

# 切到脚本所在目录（无论从哪里调用都对得上）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LOG_DIR="$SCRIPT_DIR/logs"
PID_FILE="$LOG_DIR/vite.pid"
PORT_FILE="$LOG_DIR/vite.port"   # 实际监听端口（vite 滚动后可能 != 8090）
DEFAULT_PORT=8090                # vite.config.js 默认配置端口（被占用时 vite 会自动滚动）
mkdir -p "$LOG_DIR"

# ---------- 内部工具 ----------
is_running() {
  [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

port_holder() {
  # 返回占用指定端口的 PID（没占用时空字符串）；POSIX awk 兼容（不用 GNU 3-arg match）
  # 末尾的 `|| true` 是兜底：端口空闲时 grep 返回 1，配合 set -euo pipefail 会
  # 让上层的 `HOLDER=$(port_holder ...)` 直接终止整个脚本，连日志都没机会写。
  local p="${1:-$DEFAULT_PORT}"
  ss -lntp 2>/dev/null | grep ":$p " | grep -oE 'pid=[0-9]+' | grep -oE '[0-9]+' | head -1 || true
}

actual_port() {
  # 从最近日志里解析 vite 真实监听端口（"Local: http://localhost:NNNN/" 那行）
  [ -f "$PORT_FILE" ] && cat "$PORT_FILE"
}

# ---------- 子命令 ----------
cmd_start() {
  if is_running; then
    echo "[start-dev] 已在运行：PID=$(cat "$PID_FILE")（用 ./start-dev.sh restart 重启）" >&2
    exit 1
  fi
  HOLDER=$(port_holder "$DEFAULT_PORT")
  if [ -n "${HOLDER:-}" ]; then
    HOLDER_CMD=$(tr '\0' ' ' < /proc/$HOLDER/cmdline 2>/dev/null | head -c 80)
    echo "[start-dev] 提示：默认端口 $DEFAULT_PORT 被 PID=$HOLDER 占用（${HOLDER_CMD}…）"
    echo "[start-dev]       vite 会自动滚动到下一个空闲端口，启动后看 ready 行确认实际端口"
  fi

  if [ ! -d "node_modules" ]; then
    echo "[start-dev] 检测到 node_modules 缺失，先 npm install..."
    npm install
  fi

  TS=$(date +%Y%m%d-%H%M%S)
  LOG_FILE="$LOG_DIR/dev-$TS.log"
  ln -sfn "dev-$TS.log" "$LOG_DIR/dev-latest.log"

  echo "[start-dev] 启动 vite，日志：$LOG_FILE"
  # nohup + setsid 保证父 shell 退出后 vite 继续跑
  setsid nohup npm run dev > "$LOG_FILE" 2>&1 < /dev/null &
  PID=$!
  echo "$PID" > "$PID_FILE"

  # 等待 vite ready（Local: http... 行）最多 60 秒
  for i in $(seq 1 60); do
    if grep -qE "Local:[[:space:]]+http" "$LOG_FILE" 2>/dev/null; then
      LOCAL_URL=$(grep -oE "http://localhost:[0-9]+/?" "$LOG_FILE" | head -1)
      ACTUAL=$(echo "$LOCAL_URL" | grep -oE '[0-9]+' | tail -1)
      echo "${ACTUAL:-$DEFAULT_PORT}" > "$PORT_FILE"
      if [ "${ACTUAL:-}" != "$DEFAULT_PORT" ]; then
        echo "[start-dev] ⚠️ 实际端口 $ACTUAL（$DEFAULT_PORT 被占用，vite 自动让路）"
      fi
      echo "[start-dev] ready in ${i}s — $LOCAL_URL  (PID=$PID)"
      exit 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "[start-dev] vite 进程已退出，检查日志：$LOG_FILE" >&2
      tail -30 "$LOG_FILE" >&2
      rm -f "$PID_FILE"
      exit 1
    fi
    sleep 1
  done

  echo "[start-dev] 60 秒内没看到 ready，请查看日志：$LOG_FILE" >&2
  exit 1
}

cmd_stop() {
  if ! is_running; then
    echo "[start-dev] 未运行（PID 文件：$PID_FILE）"
    rm -f "$PID_FILE"
    exit 0
  fi
  PID=$(cat "$PID_FILE")
  echo "[start-dev] 停止 PID=$PID..."
  # vite 由 npm 包了一层，杀整个进程组才干净
  kill -TERM -- "-$(ps -o pgid= "$PID" | tr -d ' ')" 2>/dev/null || kill -TERM "$PID"
  for i in $(seq 1 10); do
    if ! kill -0 "$PID" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$PID" 2>/dev/null; then
    echo "[start-dev] SIGTERM 失败，发 SIGKILL"
    kill -KILL "$PID" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
  echo "[start-dev] 已停止"
}

cmd_status() {
  if is_running; then
    PID=$(cat "$PID_FILE")
    P=$(actual_port)
    P="${P:-$DEFAULT_PORT}"
    echo "RUNNING  PID=$PID  PORT=$P  log=$LOG_DIR/dev-latest.log  url=http://localhost:$P/"
    ss -lntp 2>/dev/null | grep ":$P " || true
  else
    echo "STOPPED"
  fi
}

cmd_tail() {
  if [ -L "$LOG_DIR/dev-latest.log" ]; then
    tail -f "$LOG_DIR/dev-latest.log"
  else
    echo "[start-dev] 还没有日志文件" >&2
    exit 1
  fi
}

# ---------- 入口 ----------
case "${1:-start}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status)  cmd_status ;;
  tail)    cmd_tail ;;
  *) echo "Usage: $0 {start|stop|restart|status|tail}" >&2; exit 2 ;;
esac
