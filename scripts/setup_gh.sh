#!/usr/bin/env bash
set -e
###############################################################################
# GitHub CLI (gh) – install only if missing, then configure
###############################################################################
# Check for root, use sudo if not root
if [ "$(id -u)" -ne 0 ]; then
  SUDO=sudo
else
  SUDO=""
fi
# Install gh when it isn’t already on PATH
if ! command -v gh >/dev/null 2>&1; then
  printf '[Codex setup] Installing GitHub CLI…\n'
  if [ -f /etc/debian_version ]; then                                # Debian/Ubuntu
    type -p curl >/dev/null || { $SUDO apt-get update -y && $SUDO apt-get install -y curl; }
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
      | $SUDO dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
    $SUDO chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] \
https://cli.github.com/packages stable main" \
      | $SUDO tee /etc/apt/sources.list.d/github-cli.list >/dev/null
    $SUDO apt-get update -y && $SUDO apt-get install -y gh
  elif command -v apk >/dev/null 2>&1; then                          # Alpine
    $SUDO apk add --no-cache github-cli
  elif command -v brew >/dev/null 2>&1; then                         # macOS shells
    brew update || true
    brew install gh || true
  else
    printf '[Codex setup] ERROR: no supported package manager found; install gh manually.\n' >&2
  fi
else
  printf '[Codex setup] gh already present – skipping installation.\n'
fi

# Non‑interactive authentication
if [ -n "$GH_TOKEN" ]; then
  printf '%s\n' "$GH_TOKEN" | gh auth login --with-token >/dev/null 2>&1 || true
elif [ -n "$GITHUB_TOKEN" ]; then
  printf '%s\n' "$GITHUB_TOKEN" | gh auth login --with-token >/dev/null 2>&1 || true
else
  printf '[Codex setup] No token provided; gh will prompt at first use.\n' >&2
fi

# Disable interactive prompts and use HTTPS for git
gh config set prompt disabled true 2>/dev/null || true
gh config set git_protocol https    2>/dev/null || true

# Confirm status (non‑fatal if auth incomplete)
gh auth status 2>/dev/null || true

printf '[Codex setup] GitHub CLI setup complete.\n'
###############################################################################
