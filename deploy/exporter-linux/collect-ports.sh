#!/usr/bin/env bash
# ============================================================
# 端口监听采集脚本 (Linux)
# 通过 ss 获取 TCP/UDP 监听端口，输出 Prometheus 格式，
# 供 node_exporter Textfile Collector 读取。
# 由 systemd timer 每 10 秒触发一次 (oneshot)。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/textfile"
OUTPUT_FILE="${OUTPUT_DIR}/listening_ports.prom"
TMP_FILE="${OUTPUT_FILE}.tmp"

mkdir -p "${OUTPUT_DIR}"

# 写入 HELP / TYPE 头部
cat > "${TMP_FILE}" <<'HEADER'
# HELP node_listening_port 监听端口状态 (1=在监听)
# TYPE node_listening_port gauge
HEADER

# --- TCP 监听端口 ---
# ss -tlnp 输出格式: LISTEN 0 128 0.0.0.0:22 0.0.0.0:* users:(("sshd",pid=1234,fd=3))
{ ss -tlnp 2>/dev/null || true; } | tail -n +2 | while read -r line; do
    # 提取端口号 (第4列, 如 0.0.0.0:22)
    loc_col=$(echo "${line}" | awk '{print $4}')
    port="${loc_col##*:}"
    # 提取进程名和 PID
    proc_info=$(echo "${line}" | grep -oP 'users:\(\("([^"]+)"' || true)
    if [[ -n "${proc_info}" ]]; then
        proc_name=$(echo "${proc_info}" | sed 's/users:(("//' | sed 's/".*//')
    else
        proc_name="unknown"
    fi
    pid_info=$(echo "${line}" | grep -oP 'pid=\d+' | head -1 || true)
    pid="${pid_info#pid=}"
    [[ -z "${pid}" ]] && pid="0"

    # 转义特殊字符
    esc_proc=$(echo "${proc_name}" | sed 's/\\/\\\\/g; s/"/\\"/g')
    esc_addr=$(echo "${loc_col%:*}" | sed 's/\\/\\\\/g; s/"/\\"/g')
    echo "node_listening_port{port=\"${port}\",local_address=\"${esc_addr}\",protocol=\"tcp\",process=\"${esc_proc}\",pid=\"${pid}\"} 1"
done >> "${TMP_FILE}" || true

# --- UDP 端口 ---
{ ss -ulnp 2>/dev/null || true; } | tail -n +2 | while read -r line; do
    loc_col=$(echo "${line}" | awk '{print $4}')
    port="${loc_col##*:}"
    # 跳过空端口
    [[ -z "${port}" ]] && continue
    proc_info=$(echo "${line}" | grep -oP 'users:\(\("([^"]+)"' || true)
    if [[ -n "${proc_info}" ]]; then
        proc_name=$(echo "${proc_info}" | sed 's/users:(("//' | sed 's/".*//')
    else
        proc_name="unknown"
    fi
    pid_info=$(echo "${line}" | grep -oP 'pid=\d+' | head -1 || true)
    pid="${pid_info#pid=}"
    [[ -z "${pid}" ]] && pid="0"

    esc_proc=$(echo "${proc_name}" | sed 's/\\/\\\\/g; s/"/\\"/g')
    esc_addr=$(echo "${loc_col%:*}" | sed 's/\\/\\\\/g; s/"/\\"/g')
    echo "node_listening_port{port=\"${port}\",local_address=\"${esc_addr}\",protocol=\"udp\",process=\"${esc_proc}\",pid=\"${pid}\"} 1"
done >> "${TMP_FILE}" || true

# 原子替换（如果 .tmp 不存在则仅保留 HEADER）
if [[ -f "${TMP_FILE}" ]]; then
    mv "${TMP_FILE}" "${OUTPUT_FILE}"
else
    echo "# HELP node_listening_port 监听端口状态 (1=在监听)" > "${OUTPUT_FILE}"
    echo "# TYPE node_listening_port gauge" >> "${OUTPUT_FILE}"
fi
