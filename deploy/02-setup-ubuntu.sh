#!/usr/bin/env bash
# ============================================================================
#  02-setup-ubuntu.sh —— 在虚拟机里执行：装 MySQL + Nginx，建库账号
#
#  用法：  bash 02-setup-ubuntu.sh
#  前提：  有 sudo 权限、虚拟机已通网（apt 要下载约 100MB）
#  不改：  不动你的项目代码，只装系统软件 + 在 MySQL 里建一个账号
# ============================================================================
set -euo pipefail

DB_NAME=tlias
DB_USER=tlias

echo "===== 1/4 更新 apt 索引 ====="
sudo apt-get update

echo
echo "===== 2/4 安装 MySQL、Nginx（顺带 unzip / curl） ====="
# Ubuntu 24.04 官方源的 mysql-server 是 8.0.x，与项目开发库 8.0.36 同大版本
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-server nginx unzip curl

echo
echo "===== 3/4 启动并设置开机自启 ====="
sudo systemctl enable --now mysql
sudo systemctl enable --now nginx
# 时区：MySQL 的 NOW() / CURRENT_TIMESTAMP 跟着系统走，不设会差 8 小时
sudo timedatectl set-timezone Asia/Shanghai

echo
echo "===== 4/4 建数据库账号 ====="
echo "说明：Ubuntu 上 MySQL 的 root 账号用 auth_socket 认证（只认 sudo mysql，不认密码），"
echo "      JDBC 拿密码连 root 必然失败，所以这里单独建一个 tlias 账号。"
echo
read -r -s -p "给 MySQL 账号 ${DB_USER} 设置密码（输入时不显示）: " DB_PASS; echo
[ -n "$DB_PASS" ] || { echo "✗ 密码不能为空"; exit 1; }

# 走 sudo mysql（auth_socket），不需要 root 密码
sudo mysql <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME}
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

echo
echo "✓ 系统软件就绪"
echo
echo "接下来手工做两件事："
echo "  1) 解包发布包：  sudo mkdir -p /opt/tlias && sudo tar -xzf /tmp/tlias-deploy.tar.gz -C /opt/tlias"
echo "  2) 编辑 /opt/tlias/config/application-local.yml，填入："
echo "       spring.datasource.password = ${DB_PASS}"
echo "       tlias.jwt.secret-key       = \$(openssl rand -base64 48)"
echo "       storage.local.base-url     = http://<本机IP>/api    ← 用 ip a 查，别留 localhost"
echo
echo "然后执行：bash 03-install-app.sh"
