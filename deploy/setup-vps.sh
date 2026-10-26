#!/usr/bin/env bash
# Prepara un VPS Ubuntu 24.04 limpio y levanta todo el sistema.
# Uso (como root, dentro de la carpeta deploy del repositorio):  bash setup-vps.sh
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "==> Instalando Docker"
  curl -fsSL https://get.docker.com | sh
fi

echo "==> Firewall: SSH, HTTP, HTTPS y el puerto del GPS (5023)"
apt-get install -y ufw >/dev/null
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 5023/tcp
ufw --force enable

if [ ! -f .env ]; then
  echo "==> Creando .env con secretos aleatorios"
  ip=$(curl -fsS https://api.ipify.org)
  dashed=${ip//./-}
  read -rp "Usuario para entrar a la app [alejandro]: " user
  user=${user:-alejandro}
  read -rsp "Contraseña para la app (mínimo 10 caracteres): " pass; echo
  if [ ${#pass} -lt 10 ]; then echo "La contraseña es muy corta"; exit 1; fi
  secret() { openssl rand -hex 24; }
  cat > .env <<ENV
APP_DOMAIN=moto.${dashed}.sslip.io
TRACCAR_DOMAIN=traccar.${dashed}.sslip.io
POSTGRES_PASSWORD=$(secret)
TRACCAR_DB_PASSWORD=$(secret)
TRACKER_DB_PASSWORD=$(secret)
TRACKER_USERNAME=${user}
TRACKER_PASSWORD=${pass}
TRACKER_REMEMBER_ME_KEY=$(secret)
TRACKER_WEBHOOK_TOKEN=$(secret)
TRACKER_TELEGRAM_BOT_TOKEN=
TRACKER_TELEGRAM_CHAT_ID=
ENV
  chmod 600 .env
fi

echo "==> Construyendo y levantando los servicios (la primera vez tarda unos minutos)"
docker compose up -d --build

set -a; . ./.env; set +a
ip=$(curl -fsS https://api.ipify.org)
cat <<MSG

Listo.
  App:      https://${APP_DOMAIN}
  Traccar:  https://${TRACCAR_DOMAIN}   (crea ahí tu usuario administrador y agrega el GPS con su IMEI)
  GPS:      envía por SMS  SERVER,0,${ip},5023,0#

MSG
