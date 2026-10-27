#!/usr/bin/env bash
# First installation only. Keeps existing websites and their ports unchanged.
set -euo pipefail
umask 077
fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }
[[ $EUID -eq 0 ]] || fail 'Run with sudo bash install-server.sh'
command -v systemctl >/dev/null || fail 'systemd is required. Send the OS information for a different installation method.'
command -v ss >/dev/null || fail 'ss is required to check port 8080.'
command -v curl >/dev/null || fail 'curl is required for the local readiness check.'

base_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
package="$base_dir/campus-club-0.1.0.tar"
[[ -f "$package" ]] || fail 'The application TAR must be beside this installer.'
for path in /opt/campus-club /etc/systemd/system/campus-club.service /etc/campus-club.env /var/lib/campus-club; do
    [[ ! -e "$path" ]] || fail "Existing installation path found: $path. Stop and review before updating."
done
id campus-club >/dev/null 2>&1 && fail 'The campus-club account already exists. Review the previous installation first.'
if ss -ltnH | awk '{print $4}' | grep -Eq '(^|:)8080$'; then
    fail 'Port 8080 is occupied. No existing service has been stopped.'
fi

find_java() {
    local candidate major
    for candidate in "$(command -v java || true)" /usr/lib/jvm/*/bin/java; do
        [[ -x "$candidate" ]] || continue
        major=$("$candidate" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)
        if [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 17 )); then
            readlink -f "$candidate"
            return 0
        fi
    done
    return 1
}

if ! java_bin=$(find_java); then
    printf 'Installing Java 17 from the operating system package repository...\n'
    if command -v apt-get >/dev/null; then
        apt-get update
        DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless
    elif command -v dnf >/dev/null; then
        dnf install -y java-17-openjdk-headless
    elif command -v yum >/dev/null; then
        yum install -y java-17-openjdk-headless
    else
        fail 'No supported package manager was found. Send cat /etc/os-release output.'
    fi
    java_bin=$(find_java) || fail 'Java 17+ was not found after installation.'
fi
java_home=$(dirname "$(dirname "$java_bin")")

useradd --system --user-group --home-dir /var/lib/campus-club --shell /sbin/nologin campus-club
install -d -m 755 /opt/campus-club
tar -xf "$package" -C /opt/campus-club --strip-components=1
chmod -R a+rX /opt/campus-club
chmod 755 /opt/campus-club/bin/campus-club
install -d -m 750 -o campus-club -g campus-club /var/lib/campus-club

{
    printf 'HOST=0.0.0.0\nPORT=8080\nDATA_FILE=/var/lib/campus-club/state.json\n'
    printf 'JAVA_HOME=%s\nJAVA_OPTS=-Xmx384m\n' "$java_home"
    for role in STUDENT CLUB_STAFF TEACHER ADMIN; do
        password=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
        printf 'BOOTSTRAP_%s_PASSWORD=%s\n' "$role" "$password"
    done
} > /etc/campus-club.env
chmod 600 /etc/campus-club.env

cat > /etc/systemd/system/campus-club.service <<'UNIT'
[Unit]
Description=Campus Clubs Kotlin website
After=network.target

[Service]
Type=simple
User=campus-club
Group=campus-club
WorkingDirectory=/opt/campus-club
EnvironmentFile=/etc/campus-club.env
ExecStart=/opt/campus-club/bin/campus-club
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now campus-club

ready=false
for attempt in {1..30}; do
    if curl --max-time 2 -fsS http://127.0.0.1:8080/api/health >/dev/null; then
        ready=true
        break
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    journalctl -u campus-club --no-pager -n 30
    fail 'The service did not become ready. Keep the output for diagnosis; rerunning is not required.'
fi

if command -v firewall-cmd >/dev/null && firewall-cmd --state >/dev/null 2>&1; then
    firewall-cmd --permanent --add-port=8080/tcp
    firewall-cmd --add-port=8080/tcp
elif command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
    ufw allow 8080/tcp
fi

printf '\nREADY: Campus Clubs is running on port 8080.\n'
printf 'Allow TCP 8080 in the Alibaba Cloud instance firewall if it is not already allowed.\n'
printf 'Then visit http://47.109.153.19:8080/\n'
printf 'Accounts: student, club, teacher, admin. Each has a different generated password.\n'
printf 'View passwords privately with: sudo cat /etc/campus-club.env\n'
printf 'Do not send that password output in a screenshot or chat.\n'
printf 'Logs: sudo journalctl -u campus-club --no-pager -n 30\n'
