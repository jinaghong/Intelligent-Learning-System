#!/usr/bin/env bash
# ============================================================================
#  00-pack.sh —— 在 Windows（Git Bash）/ WSL / Linux 上执行，生成发布包
#
#  用法：  bash deploy/00-pack.sh
#  产物：  deploy/tlias-deploy.tar.gz
#           ├── tlias-backend.jar                    后端可执行 jar
#           ├── web/                                 前端静态文件
#           └── config/application-local.yml         虚拟机配置（待填）
#
#  这一步的核心不是「打包」，而是把 jar 里的 application-local.yml 剔掉：
#  那份文件带着 MySQL 密码、DeepSeek API Key、JWT 密钥、阿里云 AK/SK，
#  打进发布包就等于把它们交给每一个拿到 jar 的人。
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_SRC="$ROOT/tlias-backend/target/tlias-backend-1.0.0.jar"
WEB_SRC="$ROOT/tlias-frontend/dist"
STAGE="$ROOT/deploy/.stage"
OUT="$ROOT/deploy/tlias-deploy.tar.gz"
DROP_ENTRY="BOOT-INF/classes/application-local.yml"

if command -v python >/dev/null 2>&1; then PY=python
elif command -v python3 >/dev/null 2>&1; then PY=python3
else echo "✗ 需要 python 才能安全地改写 jar，未找到"; exit 1; fi

[ -f "$JAR_SRC" ] || { echo "✗ 找不到 $JAR_SRC —— 先在 IDEA 里 package 一次"; exit 1; }
[ -d "$WEB_SRC" ] || { echo "✗ 找不到 $WEB_SRC —— 先在 tlias-frontend 里 npm run build"; exit 1; }

rm -rf "$STAGE"; mkdir -p "$STAGE/web" "$STAGE/config"

echo "→ 复制 jar"
cp "$JAR_SRC" "$STAGE/tlias-backend.jar"

echo "→ 剔除 jar 内的敏感配置"
"$PY" - "$STAGE/tlias-backend.jar" "$DROP_ENTRY" <<'PYCODE'
import os, sys, zipfile

jar, drop = sys.argv[1], sys.argv[2]
tmp = jar + ".tmp"
hit = 0
with zipfile.ZipFile(jar) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == drop:
            hit += 1
            continue
        zout.writestr(item, zin.read(item.filename))
os.replace(tmp, jar)
print(f"   剔除 {hit} 个条目：{drop}")
with zipfile.ZipFile(jar) as z:
    left = [n for n in z.namelist() if n.endswith("application-local.yml")]
    if left:
        left.sort()
        print("   ✗ 仍残留：", left)
        sys.exit(1)
PYCODE

echo "→ 收集前端产物与配置模板"
cp -r "$WEB_SRC/." "$STAGE/web/"
cp "$ROOT/deploy/application-local.yml.example" "$STAGE/config/application-local.yml"
cp "$ROOT/deploy/tlias.service"        "$STAGE/"
cp "$ROOT/deploy/nginx-tlias.conf"     "$STAGE/"

echo "→ 打包"
# tar 里故意不带 .stage 这类隐藏目录，只打内容
( cd "$STAGE" && tar -czf "$OUT" . )
rm -rf "$STAGE"

echo
echo "✓ 已生成 $OUT  ($(du -h "$OUT" | cut -f1))"
echo "  下一步：scp deploy/tlias-deploy.tar.gz hjh@<虚拟机IP>:/tmp/"
echo "  提醒：config/application-local.yml 里的密码、JWT 密钥、base-url 必须先在虚拟机上填好"
