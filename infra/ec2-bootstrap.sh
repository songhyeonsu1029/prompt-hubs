#!/usr/bin/env bash
# EC2 user-data script. Installs Docker, fetches the repo, and prepares an
# .env stub for the demo backend stack.
#
# Paste this into the EC2 "User data" field on launch (Amazon Linux 2023 / Ubuntu 22.04+),
# or run it manually on a fresh box as root.
#
# After this runs, SSH in, fill out /opt/prompthubs/.env with real secrets,
# then:
#   cd /opt/prompthubs
#   docker compose -f docker-compose.deploy.yaml --env-file .env up -d --build

set -euxo pipefail

# ---- Detect distro and install Docker ----
if [ -f /etc/os-release ]; then
  . /etc/os-release
fi

case "${ID:-unknown}" in
  amzn)
    dnf -y update
    dnf -y install docker git
    systemctl enable --now docker
    # docker compose v2 plugin
    DOCKER_CONFIG=/usr/local/lib/docker
    mkdir -p "${DOCKER_CONFIG}/cli-plugins"
    curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
      -o "${DOCKER_CONFIG}/cli-plugins/docker-compose"
    chmod +x "${DOCKER_CONFIG}/cli-plugins/docker-compose"
    usermod -aG docker ec2-user || true
    ;;
  ubuntu|debian)
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/${ID}/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/${ID} $(. /etc/os-release && echo "${VERSION_CODENAME}") stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    usermod -aG docker ubuntu || true
    ;;
  *)
    echo "Unsupported distro: ${ID:-unknown}" >&2
    exit 1
    ;;
esac

# ---- Fetch the repo ----
APP_DIR=/opt/prompthubs
REPO_URL="${REPO_URL:-https://github.com/songhyeonsu1029/prompt-hubs}"
if [ ! -d "${APP_DIR}/.git" ]; then
  git clone "${REPO_URL}" "${APP_DIR}"
else
  git -C "${APP_DIR}" pull --ff-only
fi

# ---- Seed .env ----
if [ ! -f "${APP_DIR}/.env" ]; then
  if [ -f "${APP_DIR}/.env.example" ]; then
    cp "${APP_DIR}/.env.example" "${APP_DIR}/.env"
  else
    cat > "${APP_DIR}/.env" <<'EOF'
# --- Database ---
POSTGRES_DB=prompthubs
POSTGRES_USER=prompthubs
POSTGRES_PASSWORD=change-me-strong-password

# --- App secrets (rotate before opening to users) ---
JWT_SECRET=replace-with-32+chars-random-string-for-jwt-signing
TOKEN_ENCRYPTION_SECRET=replace-with-32bytes-random-secret-for-integration-tokens
TOKEN_ENCRYPTION_SALT=replace-with-16-hex-chars

# --- Google OAuth ---
GOOGLE_OAUTH_ENABLED=true
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_REDIRECT_URI=https://your-domain.example/api/v1/auth/oauth/google/callback
GOOGLE_OAUTH_FRONTEND_CALLBACK_URL=https://your-domain.example/oauth/callback
GOOGLE_OAUTH_FRONTEND_LOGIN_URL=https://your-domain.example/login

# --- CORS ---
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example

# --- JVM ---
JAVA_OPTS=-Xms256m -Xmx512m
EOF
  fi
  chmod 600 "${APP_DIR}/.env"
  echo "[bootstrap] .env created — FILL IN SECRETS BEFORE 'docker compose up'"
fi

echo "[bootstrap] Done. Next steps:"
echo "  1) edit ${APP_DIR}/.env"
echo "  2) cd ${APP_DIR} && docker compose -f docker-compose.deploy.yaml --env-file .env up -d --build"
