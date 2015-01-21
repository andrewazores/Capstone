#!/bin/sh

TMP="$(mktemp)"
echo "$TMP"
dot -Tpdf <&0 >"$TMP"
xdg-open "$TMP"
