#!/usr/bin/env bash
# ============================================================
# bootstrap.sh — Branch Platform 后端启动/停止/查看脚本
#
# 用法:
#   ./scripts/bootstrap.sh start            启动 bootstrap (后台 + 日志)
#   ./scripts/bootstrap.sh start -f         启动并前台 tail 日志
#   ./scripts/bootstrap.sh stop             停止 bootstrap
#   ./scripts/bootstrap.sh restart          重启 (stop + start)
#   ./scripts/bootstrap.sh status           查看 PID / 端口 / 启动状态
#   ./scripts/bootstrap.sh logs             查看当日日志末尾 200 行
#   ./scripts/bootstrap.sh logs -f          持续 tail 当日日志
#
# 日志位置: logs/bootstrap-YYYY-MM-DD.log
# PID 位置: logs/bootstrap.pid
# 端口:     18080（由脚本显式传给 Spring，可用 BOOTSTRAP_PORT 覆盖）
# SOAP端口: 30522
# Profile:  dev (可由 BOOTSTRAP_PROFILE 环境变量覆盖)
# ============================================================
set -uo pipefail

# ---------- 路径定位（脚本无视调用目录） ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
PID_FILE="${LOG_DIR}/bootstrap.pid"
LOG_FILE="${LOG_DIR}/bootstrap-$(date +%Y-%m-%d).log"

PORT="${BOOTSTRAP_PORT:-18080}"
SOAP_PORT="${BOOTSTRAP_SOAP_PORT:-30522}"
PROFILE="${BOOTSTRAP_PROFILE:-dev}"
CHECKOUT_ID="$(basename "$(dirname "${ROOT_DIR}")")-$(basename "${ROOT_DIR}")"
MAVEN_REPO_LOCAL="${BOOTSTRAP_MAVEN_REPO:-${HOME}/.m2-${CHECKOUT_ID}/repository}"
MVN_CMD=(mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -f "${ROOT_DIR}/bootstrap/pom.xml" -Dspring-boot.run.profiles="${PROFILE}" -DskipTests spring-boot:run)

# ---------- 颜色（仅在 TTY 输出） ----------
if [[ -t 1 ]]; then
  C_GREEN=$'\033[0;32m'; C_RED=$'\033[0;31m'; C_YELLOW=$'\033[0;33m'; C_RESET=$'\033[0m'
else
  C_GREEN=''; C_RED=''; C_YELLOW=''; C_RESET=''
fi

# ---------- 工具函数 ----------
log_info()  { echo "${C_GREEN}[bootstrap]${C_RESET} $*"; }
log_warn()  { echo "${C_YELLOW}[bootstrap]${C_RESET} $*"; }
log_error() { echo "${C_RED}[bootstrap]${C_RESET} $*" >&2; }

is_port_listening() {
  # 第 4 列形如 "*:8080" 或 "[::]:8080"，匹配冒号 + 端口 + 行尾/空白
  ss -ltn 2>/dev/null | awk -v port=":${PORT}" '$4 ~ port"$" {f=1} END{exit !f}'
}

is_pid_alive() {
  local pid="$1"
  [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

read_pid() {
  [[ -f "${PID_FILE}" ]] && cat "${PID_FILE}" || echo ""
}

# ---------- start ----------
cmd_start() {
  mkdir -p "${LOG_DIR}"

  local existing_pid
  existing_pid="$(read_pid)"
  if is_pid_alive "${existing_pid}"; then
    log_warn "bootstrap 已在运行 (PID ${existing_pid})，跳过启动"
    log_info "如需强制重启: $0 restart"
    return 0
  fi

  if is_port_listening; then
    log_error "端口 ${PORT} 已被其他进程占用，请先停止占用进程"
    ss -ltnp 2>/dev/null | awk -v port=":${PORT}" '$4 ~ port"$"'
    return 1
  fi

  log_info "启动中  | profile=${PROFILE} | port=${PORT} | soapPort=${SOAP_PORT}"
  log_info "工作目录 ${ROOT_DIR}"
  log_info "Maven仓库 ${MAVEN_REPO_LOCAL}"
  log_info "日志文件 ${LOG_FILE}"

  # 写一行启动分隔，方便回看
  printf '\n========== %s start (profile=%s) ==========\n' \
         "$(date '+%Y-%m-%d %H:%M:%S')" "${PROFILE}" >> "${LOG_FILE}"

  # nohup 后台跑；用 setsid 防 SIGHUP 关掉
  nohup env SERVER_PORT="${PORT}" PLATFORM_SOAP_NETTY_PORT="${SOAP_PORT}" \
    setsid "${MVN_CMD[@]}" >>"${LOG_FILE}" 2>&1 &
  local mvn_pid=$!
  echo "${mvn_pid}" > "${PID_FILE}"
  log_info "已派发 mvn (PID ${mvn_pid})，等待 ${PORT} 监听 (上限 120s)..."

  # 等待端口监听
  local i
  for i in $(seq 1 60); do
    sleep 2
    if is_port_listening; then
      log_info "${C_GREEN}启动成功${C_RESET} (${i}*2s)，PID=${mvn_pid}"
      grep -E 'Started.*Application|Tomcat started on port' "${LOG_FILE}" 2>/dev/null | tail -2
      [[ "${1:-}" == "-f" ]] && exec tail -f "${LOG_FILE}"
      return 0
    fi
    if ! is_pid_alive "${mvn_pid}"; then
      log_error "mvn 进程已退出，启动失败。日志末尾："
      tail -30 "${LOG_FILE}"
      rm -f "${PID_FILE}"
      return 1
    fi
  done

  log_error "120s 未监听到 ${PORT}，启动可能失败。日志末尾："
  tail -20 "${LOG_FILE}"
  return 1
}

# ---------- stop ----------
cmd_stop() {
  local pid
  pid="$(read_pid)"

  if ! is_pid_alive "${pid}"; then
    log_warn "PID 文件中的进程不存在，尝试按命令行匹配清理"
    pid="$(pgrep -f "BranchPlatformApplication" | head -1 || true)"
  fi

  if [[ -z "${pid}" ]] || ! is_pid_alive "${pid}"; then
    log_info "bootstrap 未在运行"
    rm -f "${PID_FILE}"
    return 0
  fi

  log_info "停止中  | PID=${pid}"
  # 优雅停止 Spring Boot：找到 java 子进程也一起 SIGTERM
  local java_pids
  java_pids="$(pgrep -P "${pid}" 2>/dev/null || true)"
  kill -TERM "${pid}" ${java_pids} 2>/dev/null || true

  # 等待退出，最多 30s
  local i
  for i in $(seq 1 30); do
    sleep 1
    if ! is_pid_alive "${pid}" && ! is_port_listening; then
      log_info "${C_GREEN}已停止${C_RESET} (${i}s)"
      rm -f "${PID_FILE}"
      return 0
    fi
  done

  log_warn "30s 内未优雅退出，发送 SIGKILL"
  kill -KILL "${pid}" ${java_pids} 2>/dev/null || true
  # 兜底：再次扫所有 BranchPlatformApplication
  pkill -KILL -f "BranchPlatformApplication" 2>/dev/null || true
  rm -f "${PID_FILE}"
  log_info "已强制停止"
}

# ---------- restart ----------
cmd_restart() {
  cmd_stop
  cmd_start "${1:-}"
}

# ---------- status ----------
cmd_status() {
  local pid
  pid="$(read_pid)"
  echo "----- bootstrap status -----"
  echo "  ROOT_DIR : ${ROOT_DIR}"
  echo "  LOG_FILE : ${LOG_FILE}"
  echo "  PID_FILE : ${PID_FILE}"
  echo "  PROFILE  : ${PROFILE}"
  echo "  PORT     : ${PORT}"
  echo "  SOAP_PORT: ${SOAP_PORT}"
  echo "  MVN_REPO : ${MAVEN_REPO_LOCAL}"
  echo
  if is_pid_alive "${pid}"; then
    echo "  PID      : ${pid} ${C_GREEN}(alive)${C_RESET}"
  else
    echo "  PID      : ${pid:-<none>} ${C_RED}(dead)${C_RESET}"
  fi
  if is_port_listening; then
    echo "  PORT ${PORT} ${C_GREEN}listening${C_RESET}:"
    ss -ltn 2>/dev/null | awk -v port=":${PORT}" '$4 ~ port"$" {print "    "$0}'
  else
    echo "  PORT ${PORT} ${C_RED}not listening${C_RESET}"
  fi
  echo
  echo "  最近 5 行日志:"
  [[ -f "${LOG_FILE}" ]] && tail -5 "${LOG_FILE}" | sed 's/^/    /' || echo "    <无日志>"
}

# ---------- logs ----------
cmd_logs() {
  if [[ ! -f "${LOG_FILE}" ]]; then
    log_warn "今日日志文件不存在: ${LOG_FILE}"
    log_info "可用日志:"
    ls -lt "${LOG_DIR}"/bootstrap-*.log 2>/dev/null | head -5
    return 0
  fi
  if [[ "${1:-}" == "-f" ]]; then
    exec tail -f "${LOG_FILE}"
  else
    tail -200 "${LOG_FILE}"
  fi
}

# ---------- 主入口 ----------
usage() {
  sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# //;s/^#//'
  exit 1
}

ACTION="${1:-}"
shift || true
case "${ACTION}" in
  start)   cmd_start   "$@" ;;
  stop)    cmd_stop    "$@" ;;
  restart) cmd_restart "$@" ;;
  status)  cmd_status  "$@" ;;
  logs)    cmd_logs    "$@" ;;
  ""|-h|--help) usage ;;
  *)       log_error "未知命令: ${ACTION}"; usage ;;
esac
