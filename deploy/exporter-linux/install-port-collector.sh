#!/usr/bin/env bash
# ============================================================
# Port Collector Installer (Linux)
# 部署 collect-ports.sh 并注册 systemd timer 每 10 秒触发。
# 用法: sudo ./install-port-collector.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEXTFILE_DIR="${SCRIPT_DIR}/textfile"

echo "=== Port Collector Installer ==="
echo ""

if [[ $EUID -ne 0 ]]; then
    echo "[ERROR] This script must be run as root (sudo)."
    exit 1
fi

# ---- 1. 确保 collect-ports.sh 可执行 ----
echo "[1/5] Setting execute permission for collect-ports.sh ..."
chmod +x "${SCRIPT_DIR}/collect-ports.sh"

# ---- 2. 创建 textfile 目录 ----
if [[ ! -d "${TEXTFILE_DIR}" ]]; then
    echo "[2/5] Creating textfile directory ..."
    mkdir -p "${TEXTFILE_DIR}"
    echo "      Done."
else
    echo "[2/5] textfile directory already exists, skip."
fi

# ---- 3. 安装 systemd service (oneshot) ----
echo "[3/5] Installing collect-ports.service ..."
sed "s|__BASE__|${SCRIPT_DIR}|g" "${SCRIPT_DIR}/collect-ports.service" > /etc/systemd/system/collect-ports.service

# ---- 4. 安装 systemd timer ----
echo "[4/5] Installing collect-ports.timer ..."
cp "${SCRIPT_DIR}/collect-ports.timer" /etc/systemd/system/collect-ports.timer

# ---- 5. 启用并启动 timer ----
echo "[5/5] Reloading systemd and starting timer ..."
systemctl daemon-reload
systemctl enable collect-ports.timer
systemctl start collect-ports.timer

# 立即运行一次
echo "      Running first collection ..."
"${SCRIPT_DIR}/collect-ports.sh" || true

echo ""
echo "=== Port collector installed ==="
echo "Timer  : collect-ports.timer (every 10s)"
echo "Script : ${SCRIPT_DIR}/collect-ports.sh"
echo "Output : ${TEXTFILE_DIR}/listening_ports.prom"
echo ""
echo "To verify: systemctl status collect-ports.timer"
echo "           cat ${TEXTFILE_DIR}/listening_ports.prom"
