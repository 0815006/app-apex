#!/usr/bin/env bash
# ============================================================
# Apex Node Exporter Installer (Linux)
# 从同目录下的 tgz 包解压 node_exporter 二进制，
# 注册为 systemd 服务。
# 用法: sudo ./install-exporter.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TGZ_FILE="${SCRIPT_DIR}/node_exporter-1.11.1.linux-amd64.tar.gz"
TEXTFILE_DIR="${SCRIPT_DIR}/textfile"

echo "=== Apex Node Exporter Installer ==="
echo ""

# ---- 权限检查 ----
if [[ $EUID -ne 0 ]]; then
    echo "[ERROR] This script must be run as root (sudo)."
    exit 1
fi

# ---- 1. 检查 tgz 包 ----
if [[ ! -f "${TGZ_FILE}" ]]; then
    echo "[ERROR] ${TGZ_FILE} not found."
    exit 1
fi

# ---- 2. 解压得到 node_exporter 二进制 ----
if [[ ! -f "${SCRIPT_DIR}/node_exporter" ]]; then
    echo "[1/5] Extracting node_exporter from tarball ..."
    tar -xzf "${TGZ_FILE}" -C /tmp/
    # tgz 内路径: node_exporter-1.11.1.linux-amd64/node_exporter
    cp /tmp/node_exporter-1.11.1.linux-amd64/node_exporter "${SCRIPT_DIR}/node_exporter"
    rm -rf /tmp/node_exporter-1.11.1.linux-amd64
    chmod +x "${SCRIPT_DIR}/node_exporter"
    echo "      Done."
else
    echo "[1/5] node_exporter binary already exists, skip."
fi

# ---- 3. 创建 textfile 目录 ----
if [[ ! -d "${TEXTFILE_DIR}" ]]; then
    echo "[2/5] Creating textfile directory ..."
    mkdir -p "${TEXTFILE_DIR}"
    echo "      Done."
else
    echo "[2/5] textfile directory already exists, skip."
fi

# ---- 4. 替换 __BASE__ 占位符 ----
echo "[3/5] Installing systemd unit file ..."
sed "s|__BASE__|${SCRIPT_DIR}|g" "${SCRIPT_DIR}/node_exporter.service" > /etc/systemd/system/node_exporter.service

# ---- 5. 启用并启动服务 ----
echo "[4/5] Reloading systemd daemon ..."
systemctl daemon-reload

echo "[5/5] Enabling and starting node_exporter service ..."
systemctl enable node_exporter
systemctl start node_exporter

echo ""
echo "=== Installation complete ==="
echo "Exporter metrics : http://localhost:9100/metrics"
echo "Textfile dir     : ${TEXTFILE_DIR}"
echo "Service status   : systemctl status node_exporter"
echo ""
echo "To verify: curl -s http://localhost:9100/metrics | grep node_listening_port"
echo ""
echo "Next step: run install-port-collector.sh to enable port listening collection."
