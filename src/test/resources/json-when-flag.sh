#!/bin/sh
for arg in "$@"; do
  if [ "$arg" = "--json" ]; then
    printf '%s\n' '{"type":"done","message":"json-enabled"}'
    exit 0
  fi
done
printf '%s\n' 'not-json'
