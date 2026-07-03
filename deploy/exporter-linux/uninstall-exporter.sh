#!/usr/bin/env bash
# ============================================================
# Node Exporter Uninstaller (Linux)
# 停止并移除 systemd 服务，保留二进制和配置文件。
# 用法: sudo ./uninstall-exporter.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Node Exporter Uninstaller ==="
echo ""

if [[ $EUID -ne 0 ]]; then
    echo "[ERROR] This script must be run as root (sudo)."
    exit 1
fi

echo "[1/3] Stopping node_exporter service ..."
systemctl stop node_exporter || true

echo "[2/3] Disabling node_exporter service ..."
systemctl disable node_exporter || true

echo "[3/3] Removing systemd unit file ..."
rm -f /etc/systemd/system/node_exporter.service
systemctl daemon-reload

echo ""
echo "=== Uninstallation complete ==="
echo "Note: node_exporter binary / collect-ports.sh / textfile/ preserved in ${SCRIPT_DIR}"
echo "      Delete the directory manually if you want a full cleanup."
