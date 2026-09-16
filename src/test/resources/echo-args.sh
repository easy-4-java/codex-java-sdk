#!/bin/sh
#
# Test double for the `codex` CLI: prints every argument verbatim, joined by
# single spaces on one line, and exits with 0 — the same output contract as
# `/bin/echo` but portable. /bin/echo cannot be used directly because GNU
# coreutils echo (Linux) interprets --version/--help as flags while BSD echo
# (macOS) prints them literally.
#
# Drain piped stdin first: real `codex login --with-api-key` consumes its
# stdin payload; reading to EOF also keeps the executor's input pump race-free
# (an immediately-closed pipe yields instant EOF here).
cat > /dev/null 2>/dev/null
printf '%s\n' "$*"
