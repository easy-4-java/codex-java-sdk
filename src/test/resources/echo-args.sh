#!/bin/sh
#
# Test double for the `codex` CLI: prints every argument verbatim, joined by
# single spaces on one line, and exits with 0 — the same output contract as
# `/bin/echo` but portable. /bin/echo cannot be used directly because GNU
# coreutils echo (Linux) interprets --version/--help as flags while BSD echo
# (macOS) prints them literally.
#
printf '%s\n' "$*"
