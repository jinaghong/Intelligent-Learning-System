#!/usr/bin/env bash
# ============================================================================
#  03-install-app.sh —— 在虚拟机里执行：装应用、导数据、配 systemd + nginx、启动
#
#  用法：  bash 03-install-app.sh
#  前提：  02 已跑过；发布包已解到 /opt/tlias；config/application-local.yml 已填好
#
#  放置后的目录结构：
#    /opt/tlias/tlias-backend.jar
#    /opt/tlias/web/                       前端静态文件
#    /opt/tlias/config/application-local.yml
#    /opt/tlias/uploads/                   头像等上传文件（app 用户可写）
# ============================================================================
set -euo pipefail

APP_DIR=/opt/tlias
CFG="$APP_DIR/config/application-local.yml"
JAR="$APP_DIR/tlias-backend.jar"
SVC_USER=tlias

echo "===== 0/6 前置检查 ====="
[ -f "$JAR" ] || { echo "✗ 找不到 $JAR —— 先 sudo tar -xzf /tmp/tlias-deploy.tar.gz -C $APP_DIR"; exit 1; }
[ -d "$APP_DIR/web" ] || { echo "✗ 找不到 $APP_DIR/web —— 发布包里缺前端产物"; exit 1; }
[ -f "$CFG" ] || { echo "✗ 找不到 $CFG"; exit 1; }

# 占位符没删干净就别往下走：带着模板启动只会得到一句很难懂的 "Access denied"
if grep -qE '把 |改成 ' "$CFG"; then
  echo "✗ $CFG 里还有未替换的中文占位符，先填好再来："
  grep -nE '把 |改成 ' "$CFG" | sed 's/^/    /'
  exit 1
fi
if grep -qE 'base-url:\s*http://(localhost|127\.0\.0\.1)' "$CFG"; then
  echo "✗ storage.local.base-url 还是 localhost —— 别的机器打开页面头像会全裂"
  echo "  改成 http://<本机IP>/api（用 ip a 查），或 http://<你的域名>/api"
  exit 1
fi
echo "  配置检查通过"

echo
echo "===== 1/6 导入建表脚本与种子数据 ====="
# SQL 脚本打在 jar 里，先抽出来
TMPDB=$(mktemp -d)
unzip -o -j -q "$JAR" 'BOOT-INF/classes/db/*.sql' -d "$TMPDB"
# schema.sql 自带 CREATE DATABASE / USE，直接喂给 mysql 即可
sudo mysql < "$TMPDB/schema.sql"
sudo mysql tlias < "$TMPDB/data.sql"
EMP_CNT=$(sudo mysql -N -e "select count(*) from tlias.emp")
sudo mysql tlias < "$TMPDB/verify.sql" >/dev/null || true
rm -rf "$TMPDB"
echo "  已导入，emp 表 $EMP_CNT 行"

echo
echo "===== 2/6 创建运行账号、准备目录 ====="
id -u "$SVC_USER" >/dev/null 2>&1 || sudo useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$SVC_USER"
sudo mkdir -p "$APP_DIR/uploads"
# uploads 必须给运行账号写权限，否则上传头像直接 500，且日志里只有一句 FileNotFoundException
sudo chown -R "$SVC_USER:$SVC_USER" "$APP_DIR/uploads" "$CFG"
sudo chmod 600 "$CFG"
echo "  账号 $SVC_USER 与 uploads/ 就绪"

echo
echo "===== 3/6 注册 systemd 服务 ====="
sudo cp "$APP_DIR/tlias.service" /etc/systemd/system/tlias.service
[ -f "$APP_DIR/tlias.env" ] || sudo tee "$APP_DIR/tlias.env" >/dev/null <<'ENV'
# 可选环境变量（优先级高于 application-local.yml）
TZ=Asia/Shanghai
# 要接真实 AI 就把下一行的注释去掉，并填上 key；同时把 profile 改成 dev,deepseek
# SPRING_PROFILES_ACTIVE=dev,deepseek
# DEEPSEEK_API_KEY=sk-xxxxxxxx
ENV
sudo systemctl daemon-reload
sudo systemctl enable tlias
echo "  /etc/systemd/system/tlias.service 已注册"

echo
echo "===== 4/6 配置 nginx ====="
sudo cp "$APP_DIR/nginx-tlias.conf" /etc/nginx/sites-available/tlias
sudo ln -sf /etc/nginx/sites-available/tlias /etc/nginx/sites-enabled/tlias
# 不删掉默认站点的话，两个 default_server 会让 nginx -t 直接报错
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
echo "  nginx 站点已启用"

echo
echo "===== 5/6 启动后端 ====="
sudo systemctl restart tlias
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
    echo "  后端已就绪（${i}s）"
    break
  fi
  [ "$i" -eq 30 ] && { echo "✗ 30 秒内没起来，看日志：sudo journalctl -u tlias -n 80 --no-pager"; exit 1; }
  sleep 1
done

echo
echo "===== 6/6 端到端自检 ====="
curl -s http://127.0.0.1:8080/api/health; echo
echo "  --- 经 nginx ---"
curl -s -o /dev/null -w "  /            → HTTP %{http_code}\n" http://127.0.0.1/
curl -s -o /dev/null -w "  /api/health  → HTTP %{http_code}\n" http://127.0.0.1/api/health
echo
echo "✓ 部署完成。浏览器访问： http://$(hostname -I | awk '{print $1}')/"
echo "  日志： sudo journalctl -u tlias -f"
