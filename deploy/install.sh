#!/usr/bin/env bash
# Installs and starts AtelierFlow on a fresh Ubuntu server (tested target: Oracle Cloud Always Free, Ubuntu 24.04).
#
#   ./deploy/install.sh atelierflow.duckdns.org
#
# Idempotent: safe to run again (it keeps the existing .env and its secrets).
set -euo pipefail

DOMAIN="${1:?usage: ./deploy/install.sh <domain, e.g. atelierflow.duckdns.org>}"
cd "$(dirname "$0")/.."

# 1. Docker Engine + Compose plugin (official Docker install script)
if ! command -v docker >/dev/null 2>&1; then
  echo "==> Installing Docker"
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# 2. Firewall: Oracle's Ubuntu images only allow SSH in iptables; open HTTP and HTTPS
if command -v netfilter-persistent >/dev/null 2>&1 && ! sudo iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null; then
  echo "==> Opening ports 80 and 443"
  sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
  sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
  sudo netfilter-persistent save
fi

# 3. Secrets, generated once and kept in .env (never committed)
if [ ! -f .env ]; then
  echo "==> Generating secrets in .env"
  umask 077
  {
    echo "DOMAIN=${DOMAIN}"
    echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)"
    echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
  } > .env
else
  sed -i "s/^DOMAIN=.*/DOMAIN=${DOMAIN}/" .env
fi

# 4. Build and start (Caddy obtains the HTTPS certificate on first start)
echo "==> Building and starting the stack (the first build takes a few minutes)"
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --wait

echo
echo "AtelierFlow is running: https://${DOMAIN}"
