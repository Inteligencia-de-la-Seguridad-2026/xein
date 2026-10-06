#!/bin/sh
set -eu

log_file=/var/lib/mysql/general.log
max_bytes=10485760

if [ ! -f "$log_file" ]; then
    exit 0
fi

current_bytes=$(wc -c < "$log_file")
if [ "$current_bytes" -lt "$max_bytes" ]; then
    exit 0
fi

set_general_log() {
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --protocol=socket \
        --execute="SET GLOBAL general_log = '$1';"
}

set_general_log OFF
trap 'set_general_log ON' EXIT

rm -f "$log_file.5"
for index in 4 3 2 1; do
    if [ -f "$log_file.$index" ]; then
        next_index=$((index + 1))
        mv "$log_file.$index" "$log_file.$next_index"
    fi
done
mv "$log_file" "$log_file.1"
