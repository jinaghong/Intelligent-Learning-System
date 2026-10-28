#!/usr/bin/env bash
# ============================================================================
#  01-check-env.sh —— 在虚拟机里执行，只读自检，不装任何东西、不改任何配置
#
#  用法：  bash 01-check-env.sh
# ============================================================================

ok()   { printf '  \033[32m[已装]\033[0m %-14s %s\n' "$1" "$2"; }
miss() { printf '  \033[31m[缺]\033[0m   %-14s %s\n' "$1" "$2"; }
note() { printf '  \033[33m[可选]\033[0m %-14s %s\n' "$1" "$2"; }

echo "===================== 系统 ====================="
. /etc/os-release 2>/dev/null || true
echo "  发行版 : ${PRETTY_NAME:-未知}"
echo "  内核   : $(uname -r)"
echo "  时区   : $(timedatectl show -p Timezone --value 2>/dev/null || date +%Z)"
echo "  内存   : $(free -h | awk '/^Mem:/{print $2" 总量 / "$7" 可用"}')"
echo "  根分区 : $(df -h / | awk 'NR==2{print $2" 总量 / "$4" 可用"}')"

echo
echo "===================== 运行时 ====================="
if command -v java >/dev/null 2>&1; then
  JV=$(java -version 2>&1 | head -1)
  case "$JV" in
    *\"21*) ok "JDK 21" "$JV" ;;
    *)      miss "JDK 21" "当前是 $JV —— 版本不对，jar 需要 21" ;;
  esac
else
  miss "JDK 21" "未安装"
fi

echo
echo "===================== 中间件 ====================="
if command -v mysqld >/dev/null 2>&1 || command -v mysql >/dev/null 2>&1; then
  ok "MySQL" "$(mysql --version 2>/dev/null)"
  if systemctl is-active --quiet mysql 2>/dev/null; then
    echo "         服务状态 : 运行中（3306 $(ss -lnt 2>/dev/null | grep -q ':3306' && echo 在监听 || echo 未监听)）"
    if sudo -n mysql -N -e "show databases" 2>/dev/null | grep -qx tlias; then
      echo "         数据库   : tlias 已存在，emp 表 $(sudo -n mysql -N -e 'select count(*) from tlias.emp' 2>/dev/null || echo '?') 行"
    else
      echo "         数据库   : tlias 尚未创建（需要跑 02 + 03）"
    fi
  else
    echo "         服务状态 : 未启动（sudo systemctl start mysql）"
  fi
else
  miss "MySQL" "未安装 —— 02 脚本会装"
fi

if command -v nginx >/dev/null 2>&1; then
  ok "Nginx" "$(nginx -v 2>&1)"
  systemctl is-active --quiet nginx && echo "         服务状态 : 运行中" || echo "         服务状态 : 未启动"
else
  miss "Nginx" "未安装 —— 02 脚本会装"
fi

if command -v redis-server >/dev/null 2>&1; then
  note "Redis" "$(redis-server --version | cut -d' ' -f1-3)（仅当你要激活 redis profile 才需要）"
else
  note "Redis" "未安装 —— 项目默认走 Caffeine 缓存，可以不装"
fi

if command -v node >/dev/null 2>&1; then
  note "Node.js" "$(node -v)（用预构建 dist 的话完全不需要）"
else
  note "Node.js" "未安装 —— 用预构建 dist 就不需要"
fi
if command -v mvn >/dev/null 2>&1; then
  note "Maven" "$(mvn -v 2>/dev/null | head -1)（有现成 jar，不需要）"
else
  note "Maven" "未安装 —— 有现成 jar，不需要"
fi

echo
echo "===================== 端口占用 ====================="
for P in 80 8080 3306 6379; do
  if ss -lnt 2>/dev/null | grep -q ":$P "; then
    echo "  $P  已被占用：$(ss -lntp 2>/dev/null | awk -v p=":$P " '$4 ~ p {print $6}' | head -1)"
  else
    echo "  $P  空闲"
  fi
done

echo
echo "===================== 防火墙 ====================="
if command -v ufw >/dev/null 2>&1; then
  echo "  ufw    : $(sudo -n ufw status 2>/dev/null | head -1 || echo '需要 sudo 才能查看')"
else
  echo "  ufw    : 未安装"
fi

echo
echo "===================== 结论 ====================="
echo "  JDK 21 有了之后，真正还需要装的就两样：MySQL 8 + Nginx。"
echo "  缺的话执行：bash 02-setup-ubuntu.sh"
