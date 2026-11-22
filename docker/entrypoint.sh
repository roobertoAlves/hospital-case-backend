#!/bin/sh
set -e

: "${DB_HOST:?Set DB_HOST}"
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
exec catalina.sh run
