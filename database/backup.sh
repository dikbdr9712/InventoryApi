#!/usr/bin/env bash
# =====================================================================================
# DP DrukBazaars: nightly backup on a Linux server.
#
# Saves the whole database plus the uploaded files (product photos, and the sellers' and
# drivers' identity documents), and keeps the last 14 days. Run it every night from cron:
#
#   sudo crontab -e
#   15 2 * * *  /opt/drukbazaars/database/backup.sh >> /var/log/drukbazaars-backup.log 2>&1
#
# The database password is NOT in this file. Put it in /etc/drukbazaars/backup.cnf (readable by root only):
#   [client]
#   user=drukbazaars_backup
#   password=THE_BACKUP_ACCOUNT_PASSWORD
# and give that account read-only rights (as MySQL root):
#   CREATE USER 'drukbazaars_backup'@'localhost' IDENTIFIED BY '...';
#   GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES, EVENT, PROCESS ON *.* TO 'drukbazaars_backup'@'localhost';
#   sudo chmod 600 /etc/drukbazaars/backup.cnf
#
# A backup on the same disk does not survive a broken disk: copy BACKUP_DIR to another place
# every day too (another server, cloud storage, or an external drive), and try a restore now and then.
#
# Restore (on an empty database made with 01-create-database-and-user.sql):
#   gunzip -c inventorydb-2026-10-02.sql.gz | mysql -u root -p inventorydb
#   tar -xzf files-2026-10-02.tar.gz -C /opt/drukbazaars
# =====================================================================================
set -euo pipefail

DB_NAME="${DB_NAME:-inventorydb}"
CREDENTIALS="${CREDENTIALS:-/etc/drukbazaars/backup.cnf}"
APP_DIR="${APP_DIR:-/opt/drukbazaars}"              # where the jar runs: uploads/ and private-uploads/ are here
BACKUP_DIR="${BACKUP_DIR:-/var/backups/drukbazaars}"
KEEP_DAYS="${KEEP_DAYS:-14}"

STAMP="$(date +%F)"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"   # identity documents are inside: only root may read the backups

# --single-transaction: a consistent copy without stopping the shop
mysqldump --defaults-extra-file="$CREDENTIALS" \
  --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
  "$DB_NAME" | gzip -9 > "$BACKUP_DIR/$DB_NAME-$STAMP.sql.gz.part"
mv "$BACKUP_DIR/$DB_NAME-$STAMP.sql.gz.part" "$BACKUP_DIR/$DB_NAME-$STAMP.sql.gz"

tar -czf "$BACKUP_DIR/files-$STAMP.tar.gz" -C "$APP_DIR" uploads private-uploads 2>/dev/null || \
  tar -czf "$BACKUP_DIR/files-$STAMP.tar.gz" -C "$APP_DIR" uploads

# keep the last KEEP_DAYS days
find "$BACKUP_DIR" -name "*.gz" -type f -mtime +"$KEEP_DAYS" -delete

echo "$(date '+%F %T') backup done: $(du -sh "$BACKUP_DIR/$DB_NAME-$STAMP.sql.gz" | cut -f1) database"
