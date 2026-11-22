#!/bin/sh
set -e

if [ -z "${DB_HOST:-}" ]; then
  echo "No DB_HOST: starting the embedded MariaDB (demo data is rebuilt on every start)"
  mkdir -p /run/mysqld && chown mysql:mysql /run/mysqld
  if [ ! -d /var/lib/mysql/mysql ]; then
    mariadb-install-db --user=mysql --datadir=/var/lib/mysql --auth-root-authentication-method=socket >/dev/null
  fi
  mariadbd-safe --user=mysql --datadir=/var/lib/mysql >/dev/null 2>&1 &
  for i in $(seq 1 60); do mariadb-admin ping --silent 2>/dev/null && break; sleep 1; done
  DB_PASSWORD="${DB_PASSWORD:-$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')}"
  mariadb -e "CREATE USER IF NOT EXISTS 'asklep'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
    ALTER USER 'asklep'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON \`hospital%\`.* TO 'asklep'@'127.0.0.1'; FLUSH PRIVILEGES;"
  DB_HOST=127.0.0.1
  DB_PORT=3306
  DB_USER=asklep
fi

: "${DB_USER:?Set DB_USER}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"

cat > "$APP_DIR/WEB-INF/etc/config.local.json" <<EOF
{
  "app": { "env": "production", "debug": false, "url": "${APP_URL:-}", "frontend_url": "${FRONTEND_URL:-}" },
  "database": { "connections": { "mysql": {
    "host": "${DB_HOST}", "port": ${DB_PORT:-3306}, "user": "${DB_USER}", "password": "${DB_PASSWORD}", "dbname": "${DB_NAME:-hospital}"
  } } },
  "mail": { "enabled": false },
  "sms": { "enabled": false }
}
EOF

sed -i "s/port=\"8080\"/port=\"${PORT:-8080}\"/" /usr/local/tomcat/conf/server.xml
export CATALINA_OPTS="${CATALINA_OPTS:--Xmx220m -Xss512k -XX:MaxMetaspaceSize=110m -XX:+UseSerialGC -XX:TieredStopAtLevel=1}"

if [ "$DB_HOST" = "127.0.0.1" ]; then
  (until curl -s -o /dev/null "http://127.0.0.1:${PORT:-8080}/hospital-case-backend/api/public/catalog"; do sleep 2; done) &
fi
exec catalina.sh run
