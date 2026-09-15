#!/usr/bin/env bash
# Scan for secrets and private data before committing or pushing.
#
#   scripts/check-secrets.sh            staged changes + untracked files (run before `git commit`)
#   scripts/check-secrets.sh --tree     every tracked file (run before `git push` / a release)
#   scripts/check-secrets.sh --history  every commit ever made (run before making a repo public)
#
# Exits 1 if anything matches. Private patterns (real IPs, hostnames, agent names, token
# prefixes) belong in the git-ignored docs/secret-patterns.local.txt — one extended regex
# per line, '#' comments allowed — never in this file.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

mode="${1:---staged}"
private_file="${SECRET_PATTERNS_FILE:-docs/secret-patterns.local.txt}"

generic='(password|passwd|secret|token|api[_-]?key)["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"'[:space:]]{8,}["'"'"']'
generic+='|-----BEGIN [A-Z ]*PRIVATE KEY-----'
generic+='|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}'
generic+='|sk-[A-Za-z0-9_-]{20,}|sk-ant-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}'
generic+='|xox[baprs]-[A-Za-z0-9-]{10,}|AKIA[0-9A-Z]{16}'
generic+='|(storePassword|keyPassword)=[^[:space:]]+'

# Known-safe placeholders and fixtures.
allow='pragma: allowlist secret|CHANGE_ME|\*\*\*\*\*\*\*\*|sk-key|sk-secret-key|sk-test|YOUR_|<[a-z_-]+>|\$\{|System\.getenv'

patterns="$generic"
if [[ -f "$private_file" ]]; then
  while IFS= read -r line; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    patterns+="|$line"
  done < "$private_file"
else
  echo "note: $private_file not found — only generic patterns are checked" >&2
fi

case "$mode" in
  --staged)
    content=$( { git diff --cached -U0 --no-color | grep -E '^\+[^+]' || true
                 git ls-files --others --exclude-standard -z | xargs -0 -r grep -nIH '' 2>/dev/null || true; } )
    ;;
  --tree)
    content=$(git grep -nI '' -- . ':!scripts/check-secrets.sh' || true)
    ;;
  --history)
    content=$(git log --all -p --no-color --format='commit %h' -- . ':!scripts/check-secrets.sh' | grep -E '^(commit |\+[^+])' || true)
    ;;
  *) echo "usage: $0 [--staged|--tree|--history]" >&2; exit 2 ;;
esac

hits=$(printf '%s\n' "$content" | grep -iE -- "$patterns" | grep -vE -- "$allow" | grep -v 'scripts/check-secrets.sh' || true)

# detect-secrets adds entropy-based detection when uv is available (optional).
if [[ "$mode" != "--history" ]] && command -v uvx >/dev/null 2>&1; then
  files=$(if [[ "$mode" == "--tree" ]]; then git ls-files; else git diff --cached --name-only --diff-filter=AM; git ls-files --others --exclude-standard; fi)
  if [[ -n "$files" ]]; then
    ds=$(printf '%s\n' "$files" | xargs -r uvx --quiet detect-secrets scan 2>/dev/null \
      | python3 -c 'import json,sys; d=json.load(sys.stdin)
for f,items in d.get("results",{}).items():
    for i in items: print(f"{f}:{i[\"line_number\"]}: detect-secrets: {i[\"type\"]}")' 2>/dev/null || true)
    [[ -n "$ds" ]] && hits+=$'\n'"$ds"
  fi
fi

hits=$(printf '%s\n' "$hits" | sed '/^[[:space:]]*$/d')
if [[ -n "$hits" ]]; then
  echo "Possible secrets or private data ($mode) — review every line before continuing:"
  printf '%s\n' "$hits" | cut -c1-200
  exit 1
fi
echo "No secrets or private data found ($mode)."
