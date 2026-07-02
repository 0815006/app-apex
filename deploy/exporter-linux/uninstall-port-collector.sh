#!/usr/bin/env bash
# ============================================================
# Apex Port Collector Uninstaller (Linux)
# 停止并移除 systemd timer 和 service 单元。
# 用法: sudo ./uninstall-port-collector.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Apex Port Collector Uninstaller ==="
echo ""

if [[ $EUID -ne 0 ]]; then
    echo "[ERROR] This script must be run as root (sudo)."
    exit 1
fi

echo "[1/4] Stopping collect-ports.timer ..."
systemctl stop collect-ports.timer || true

echo "[2/4] Disabling collect-ports.timer ..."
systemctl disable collect-ports.timer || true

echo "[3/4] Removing systemd unit files ..."
rm -f /etc/systemd/system/collect-ports.timer
rm -f /etc/systemd/system/collect-ports.service
systemctl daemon-reload

echo "[4/4] Cleaning up .prom output ..."
rm -f "${SCRIPT_DIR}/textfile/listening_ports.prom"
rm -f "${SCRIPT_DIR}/textfile/listening_ports.prom.tmp"

echo ""
echo "=== Port collector uninstalled ==="
