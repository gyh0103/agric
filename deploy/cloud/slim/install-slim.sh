#!/usr/bin/env bash
# 绿洲丰语 · 轻量云主机一键部署（适配 2 核 2G / 4G Ubuntu）
# 用法： sudo bash install-slim.sh
set -euo pipefail

APP_NAME=oasis-harvest
APP_DIR=/opt/oasis
ENV_DIR=/etc/oasis
ENV_FILE=$ENV_DIR/oasis.env
JAR=$APP_DIR/$APP_NAME.jar
SERVER_PORT=8081

log() { printf '\n\033[32m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { printf '\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "请用 root 运行：sudo bash $0"
. /etc/os-release
[[ "${ID:-}" == ubuntu || "${ID:-}" == debian ]] || die "仅支持 Ubuntu / Debian，当前为 ${ID:-unknown}"

# ---------- 0. 检查安装包 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ -f "$SCRIPT_DIR/$APP_NAME.jar" ]] || die "缺少 $APP_NAME.jar，请确认它与本脚本在同一目录"

mem_mb=$(awk '/MemTotal/{print int($2/1024)}' /proc/meminfo)
disk_mb=$(df -Pm /opt | awk 'NR==2{print $4}')
log "检测到内存 ${mem_mb}MB，/opt 可用磁盘 ${disk_mb}MB"
(( disk_mb > 3000 )) || die "磁盘剩余不足 3GB，请先清理"
if (( mem_mb < 1500 )); then
  heap=448m; swap_gb=4; ibp=96M; maxconn=30
elif (( mem_mb < 3500 )); then
  heap=896m; swap_gb=2; ibp=192M; maxconn=50
elif (( mem_mb < 7500 )); then
  heap=1536m; swap_gb=0; ibp=256M; maxconn=60
else
  heap=2560m; swap_gb=0; ibp=512M; maxconn=100
fi

# ---------- 1. swap（小内存机器必需）----------
if (( swap_gb > 0 )) && [[ ! -f /swapfile ]]; then
  log "内存 ${mem_mb}MB 偏小，创建 ${swap_gb}GB swap"
  fallocate -l "${swap_gb}G" /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=$((swap_gb*1024)) status=none
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo 'vm.swappiness = 60' >/etc/sysctl.d/99-oasis-swap.conf
  sysctl -q -p /etc/sysctl.d/99-oasis-swap.conf
fi

# ---------- 2. 安装依赖 ----------
log "更新 apt 索引并安装 JDK17 / MySQL / Nginx"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends \
  openjdk-17-jre-headless mysql-server nginx curl ca-certificates tzdata

ln -sfn /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
systemctl enable --now mysql >/dev/null 2>&1 || systemctl enable --now mariadb

# ---------- 3. 数据库 ----------
db_pass=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 20)
log "创建数据库 oasis 与专用账号"
mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS oasis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'oasis'@'localhost' IDENTIFIED BY '${db_pass}';
ALTER USER 'oasis'@'localhost' IDENTIFIED BY '${db_pass}';
GRANT ALL PRIVILEGES ON oasis.* TO 'oasis'@'localhost';
FLUSH PRIVILEGES;
SQL

# 小内存调优（幂等）
cat >/etc/mysql/mysql.conf.d/zz-oasis.cnf <<CNF
[mysqld]
innodb_buffer_pool_size = $ibp
performance_schema = OFF
max_connections = $maxconn
table_open_cache = 200
CNF
systemctl restart mysql

# ---------- 4. 目录与环境变量 ----------
log "准备应用目录 $APP_DIR"
id -u oasis >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin oasis
install -d -o oasis -g oasis "$APP_DIR/logs"
install -m 0644 "$SCRIPT_DIR/$APP_NAME.jar" "$JAR"

if [[ -f "$ENV_FILE" ]]; then
  log "已存在 $ENV_FILE，保留原配置（数据库密码不覆盖）"
  db_pass=$(sed -n 's/^DB_PASSWORD=//p' "$ENV_FILE" | tail -n 1)
  mysql -uroot <<SQL
ALTER USER 'oasis'@'localhost' IDENTIFIED BY '${db_pass}';
FLUSH PRIVILEGES;
SQL
else
  install -d -m 0750 "$ENV_DIR"
  jwt_secret=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 48)
  cat >"$ENV_FILE" <<ENV
# 绿洲丰语运行配置 —— 修改后执行 systemctl restart oasis-harvest
DB_URL=jdbc:mysql://127.0.0.1:3306/oasis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=oasis
DB_PASSWORD=$db_pass
JWT_SECRET=$jwt_secret
SERVER_PORT=$SERVER_PORT
SERVER_TOMCAT_THREADS_MAX=40
SERVER_TOMCAT_THREADS_MIN_SPARE=4
JAVA_OPTS=-Xms128m -Xmx$heap -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m -Xss512k -Duser.timezone=Asia/Shanghai
# 可选：接入大模型问答（不填则问答功能自动降级）
# AGENT_BASE_URL=https://api.deepseek.com/v1
# AGENT_MODEL=deepseek-chat
# AGENT_API_KEY=
ENV
  chmod 0640 "$ENV_FILE"
  chown root:oasis "$ENV_FILE"
fi

# ---------- 5. systemd ----------
log "写入 systemd 服务"
cat >/etc/systemd/system/oasis-harvest.service <<UNIT
[Unit]
Description=Oasis Harvest (绿洲丰语) 智慧农业平台
After=network-online.target mysql.service
Wants=network-online.target
Requires=mysql.service

[Service]
Type=simple
User=oasis
Group=oasis
WorkingDirectory=$APP_DIR
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/java \$JAVA_OPTS -jar $JAR
SuccessExitStatus=143
Restart=always
RestartSec=5
StandardOutput=append:$APP_DIR/logs/app.log
StandardError=append:$APP_DIR/logs/app.log
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
UNIT

# ---------- 6. Nginx 反向代理（80 → 8081）----------
log "配置 Nginx 反向代理"
cat >/etc/nginx/sites-available/oasis <<'NGX'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    client_max_body_size 12m;
    proxy_read_timeout 300s;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
NGX
ln -sfn /etc/nginx/sites-available/oasis /etc/nginx/sites-enabled/oasis
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx

systemctl daemon-reload
systemctl enable oasis-harvest
systemctl restart oasis-harvest

# ---------- 7. 健康检查 ----------
log "等待应用启动（最多 4 分钟，首次建表+播种较慢）"
ok=0
for _ in $(seq 1 120); do
  if curl -fsS "http://127.0.0.1:$SERVER_PORT/api/health" >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [[ $ok -eq 1 ]]; then
  ip=$(curl -fsS --max-time 3 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
  log "部署完成 ✅  http://${ip}/"
  echo "  工作台: http://${ip}/platform/"
  echo "  多作物: http://${ip}/multicrop/"
  echo "  健康检查: $(curl -fsS http://127.0.0.1:$SERVER_PORT/api/health)"
else
  echo
  echo "启动超时，最近日志："
  tail -n 40 "$APP_DIR/logs/app.log" 2>/dev/null || journalctl -u oasis-harvest -n 40 --no-pager
  die "请把上面的报错发给开发者"
fi
