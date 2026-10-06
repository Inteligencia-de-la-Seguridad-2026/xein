#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
failed=0

check() {
    local description="$1"
    shift
    if "$@"; then
        printf 'OK: %s\n' "$description"
    else
        printf 'FAIL: %s\n' "$description" >&2
        failed=1
    fi
}

check_file() {
    test -f "$1"
}

has_private_value() {
    grep -Eq "^$1=.+$" .env
}

has_lab_ip() {
    ip -4 -o addr show | grep -Fq "inet $1/"
}

check "Private environment file" check_file .env
check "Private database seed" check_file database/xein-seed.sql
check "Docker CLI" command -v docker

if command -v docker >/dev/null 2>&1; then
    check "Docker daemon" docker info --format '{{.ServerVersion}}'
    check "Compose configuration" docker compose config --quiet
fi

cpu_count="$(getconf _NPROCESSORS_ONLN)"
memory_kib="$(awk '/MemTotal:/ {print $2}' /proc/meminfo)"
disk_kib="$(df -Pk . | awk 'NR == 2 {print $4}')"
check "At least two vCPUs" test "$cpu_count" -ge 2
check "At least 4 GiB RAM" test "$memory_kib" -ge 4194304
check "At least 15 GiB free disk" test "$disk_kib" -ge 15728640

if command -v timedatectl >/dev/null 2>&1; then
    ntp_status="$(timedatectl show -p NTPSynchronized --value 2>/dev/null || true)"
    check "NTP synchronized" test "$ntp_status" = yes
else
    printf 'FAIL: timedatectl is unavailable; verify NTP manually\n' >&2
    failed=1
fi

if test -f .env; then
    check "Private card number configured" has_private_value XEIN_LAB_CARD_NUMBER
    check "Private card expiry configured" has_private_value XEIN_LAB_CARD_EXPIRY
    check "Private card CVV configured" has_private_value XEIN_LAB_CARD_CVV
    bind_address="$(sed -n 's/^XEIN_BIND_ADDRESS=//p' .env | tail -n 1)"
    if test -n "$bind_address" && test "$bind_address" != 127.0.0.1 && test "$bind_address" != 0.0.0.0; then
        check "Web bound to a local lab interface" has_lab_ip "$bind_address"
    else
        printf 'FAIL: XEIN_BIND_ADDRESS must be the isolated lab interface IP\n' >&2
        failed=1
    fi
fi

exit "$failed"
