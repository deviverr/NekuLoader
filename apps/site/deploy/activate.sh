#!/usr/bin/env bash
set -euo pipefail
cd /home/nekudev/nekuloader-release-20261110
[ "$(id -u)" = 0 ] || { echo 'Run with sudo bash /home/nekudev/nekuloader-release-20261110/activate.sh'; exit 1; }
# Fail safely rather than overwrite a site configured after preparation.
[ ! -e /etc/nginx/sites-available/nekudev.com ]
[ ! -e /etc/nginx/sites-enabled/nekudev.com ]
[ ! -e /var/www/nekudev/nekuloader ]
install -d -m 755 /var/www/nekudev/nekuloader
cp -R public/. /var/www/nekudev/nekuloader/
find /var/www/nekudev/nekuloader -type d -exec chmod 755 {} +
find /var/www/nekudev/nekuloader -type f -exec chmod 644 {} +
install -m 644 nekudev.com.conf /etc/nginx/sites-available/nekudev.com
ln -s /etc/nginx/sites-available/nekudev.com /etc/nginx/sites-enabled/nekudev.com
if ! nginx -t; then
    rm /etc/nginx/sites-enabled/nekudev.com
    echo 'Configuration disabled; existing nginx remains active.'
    exit 1
fi
systemctl reload nginx
curl --fail --silent --output /dev/null http://127.0.0.1/nekuloader/ -H 'Host: nekudev.com'
echo 'HTTP origin enabled. Configure DNS to this VPS and HTTPS before public launch.'
