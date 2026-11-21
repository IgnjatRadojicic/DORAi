#!/bin/sh
# Nightly pg_dump of the DORAi database into /backups, keeping BACKUP_KEEP_DAYS days.
set -eu

: "${BACKUP_TIME:=02:00}"
: "${BACKUP_KEEP_DAYS:=14}"

seconds_until_backup() {
    now=$(date +%s)
    next=$(date -d "today $BACKUP_TIME" +%s)
    if [ "$next" -le "$now" ]; then
        next=$(date -d "tomorrow $BACKUP_TIME" +%s)
    fi
    echo $((next - now))
}

while true; do
    wait_s=$(seconds_until_backup)
    echo "next backup in ${wait_s}s at $BACKUP_TIME"
    sleep "$wait_s"

    file="/backups/dorai-$(date +%Y-%m-%d_%H%M).dump"
    if pg_dump --format=custom --file="$file.partial"; then
        mv "$file.partial" "$file"
        echo "backup written: $file"
    else
        rm -f "$file.partial"
        echo "backup FAILED" >&2
    fi
    find /backups -name 'dorai-*.dump' -mtime +"$BACKUP_KEEP_DAYS" -delete
done
