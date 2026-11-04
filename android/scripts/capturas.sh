#!/usr/bin/env bash
# Abre o app no celular virtual e tira uma foto de cada tela (versão de teste).
set -u
APK="$1"; OUT="$2"; mkdir -p "$OUT"
adb install -r "$APK"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
tela() { # nome rota perfil [aba] [dalt]
  local extras="--es rota $2"
  [ -n "${3:-}" ] && extras="$extras --es perfil $3"
  [ -n "${4:-}" ] && extras="$extras --ei aba $4"
  [ -n "${5:-}" ] && extras="$extras --es dalt $5"
  adb shell am start -S -W -n com.quantumdrive.app/.MainActivity $extras >/dev/null
  sleep "${ESPERA:-7}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "capturada: $1"
}
tela 01_inicio inicio
tela 02_veiculos veiculos
tela 03_detalhes detalhes/seal
tela 04_economia economia
tela 05_login login
tela 06_cadastro cadastro
tela 07_admin_login admin-login
tela 08_historico historico cliente
tela 09_reserva reserva/dolphin cliente
tela 10_veiculos_deuteranopia veiculos cliente "" DEUTAN
tela 11_historico_deuteranopia historico cliente "" DEUTAN
adb shell pm clear com.quantumdrive.app >/dev/null
tela 12_painel admin dono 0
tela 13_financeiro admin dono 1
