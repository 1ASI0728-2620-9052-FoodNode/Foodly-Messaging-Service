#!/usr/bin/env bash
# Genera un JWT de desarrollo (HS256, válido 60 minutos) para probar los endpoints protegidos de Messaging.
# Imita el token que emite IAM: el id de usuario (un UUID) y el rol van en claims y se firma con el secreto
# compartido.
#
# Uso:  scripts/dev-token.sh [rol] [usuario]
#   rol      GASTRONOMIC_EXPLORER (por defecto) o HUARIQUE_OWNER
#   usuario  UUID del usuario (por defecto 11111111-1111-1111-1111-111111111111)
#
# Variables: JWT_SECRET (el mismo que usa Messaging; por defecto el de desarrollo local),
#            JWT_USER_ID_CLAIM (sub) y JWT_ROLE_CLAIM (role).
# Solo para desarrollo: no use el secreto por defecto en ningún otro entorno.

set -euo pipefail

command -v openssl >/dev/null || { echo "ERROR: falta openssl" >&2; exit 1; }

SECRET="${JWT_SECRET:-local-dev-only-secret-change-me-0123456789}"
ROLE="${1:-GASTRONOMIC_EXPLORER}"
USER_ID="${2:-11111111-1111-1111-1111-111111111111}"
USER_CLAIM="${JWT_USER_ID_CLAIM:-sub}"
ROLE_CLAIM="${JWT_ROLE_CLAIM:-role}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

now=$(date +%s)
header=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"%s":"%s","%s":"%s","iat":%s,"exp":%s}' \
  "$USER_CLAIM" "$USER_ID" "$ROLE_CLAIM" "$ROLE" "$now" "$((now + 3600))" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)

printf '%s.%s.%s\n' "$header" "$payload" "$signature"
