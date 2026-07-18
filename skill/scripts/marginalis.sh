#!/usr/bin/env bash
# Thin wrapper over the Marginalis HTTP API (JetBrains IDE margin-comment
# plugin). Exists mostly so comment bodies with quotes/newlines survive JSON
# encoding (jq does the escaping). Errors come back as {"error": "..."} on
# stdout — inspect them, they are written to be acted on.
set -euo pipefail

BASE="${MARGINALIS_URL:-http://127.0.0.1:63342/api/marginalis}"

usage() {
  echo "usage: marginalis.sh ping | discover [path] | unread | list | open-on <file> |" >&2
  echo "       add <file> <line> <anchor_text> <body> | reply <id> <body> |" >&2
  echo "       resolve <id> | reopen <id>" >&2
  exit 2
}

post() { # $1=endpoint, stdin=json
  curl -s --max-time 10 -X POST -H 'Content-Type: application/json' -d @- "$BASE/$1"
}

cmd="${1:-}"; [ -n "$cmd" ] || usage; shift || true
case "$cmd" in
  ping)
    curl -s --max-time 5 "$BASE/ping" ;;
  discover)
    # Multiple IDE processes stack their built-in servers on 63342, 63343, …
    # Find the server whose open projects contain the given path (default:
    # cwd) and print its base URL — export it as MARGINALIS_URL.
    target="${1:-$PWD}"
    target="$(cd "$target" 2>/dev/null && pwd -P || echo "$target")"
    found_any=""
    for port in 63342 63343 63344 63345; do
      info=$(curl -s --max-time 2 "http://127.0.0.1:$port/api/marginalis/ping") || true
      echo "$info" | jq -e '.status? == "ok"' >/dev/null 2>&1 || continue
      found_any="$found_any $port:$(echo "$info" | jq -r '[.projects[]?.name] | join(",")')"
      # Containment either way: working inside an open project, or working in
      # a directory that contains an open project (e.g. a repo root whose
      # subdirectory is what the IDE has open).
      match=$(echo "$info" | jq -r --arg t "$target" \
        '.projects[]?.path // empty
         | select(. as $p | $t == $p or ($t | startswith($p + "/")) or ($p | startswith($t + "/")))' | head -1)
      if [ -n "$match" ]; then
        echo "http://127.0.0.1:$port/api/marginalis"
        exit 0
      fi
    done
    if [ -n "$found_any" ]; then
      echo "no Marginalis server owns $target — servers found:$found_any" >&2
    else
      echo "no Marginalis server responding on ports 63342-63345" >&2
    fi
    exit 1 ;;
  unread)
    curl -s --max-time 5 "$BASE/comment_list?unread_only=true" ;;
  list)
    curl -s --max-time 5 "$BASE/comment_list" ;;
  open-on)
    file="${1:?file required}"
    curl -s --max-time 5 "$BASE/comment_list?file=$(jq -rn --arg f "$file" '$f|@uri')&status=open" ;;
  add)
    file="${1:?file}"; line="${2:?line}"; anchor="${3:?anchor_text}"; body="${4:?body}"
    jq -n --arg f "$file" --argjson l "$line" --arg a "$anchor" --arg b "$body" \
      '{file:$f, line:$l, body:$b, anchor_text:$a}' | post comment_add ;;
  reply)
    tid="${1:?thread_id}"; body="${2:?body}"
    jq -n --arg t "$tid" --arg b "$body" '{thread_id:$t, body:$b}' | post comment_reply ;;
  resolve|reopen)
    tid="${1:?thread_id}"
    jq -n --arg t "$tid" '{thread_id:$t}' | post "comment_$cmd" ;;
  *)
    usage ;;
esac
