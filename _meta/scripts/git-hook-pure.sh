#!/usr/bin/env bash

# ====================== helpers ======================

hookNames=`cat <<EOF
applypatch-msg
commit-msg
post-applypatch
post-checkout
post-commit
post-merge
post-receive
post-rewrite
post-update
pre-applypatch
pre-auto-gc
pre-commit
pre-push
pre-rebase
pre-receive
prepare-commit-msg
push-to-checkout
sendemail-validate
update
EOF`

templateBannerStart="git-hook-pure start"

templateBannerEnd="git-hook-pure end"

gitFullPath=`git rev-parse --show-toplevel`

cleanCode() {
  local hookFullPath="$1"
  sed -i -Ee ":a;\$!{N;ba};s/(\s*\n|^)# =+ $templateBannerStart =+\n.*\n# =+ $templateBannerEnd =+\s*//g" "$hookFullPath"
}

hasHashbang() {
  echo "$1" | head -n1 | grep -m1 -qe "^#!"
}

# ====================== install =====================

installCode() {
  local hookFullPath="$1"
  local insertContent=`cat <<EOF

# ================== $templateBannerStart ==================
$templateContent
# ================== $templateBannerEnd ==================
EOF`

  if [ ! -e "$hookFullPath" ]; then
    mkdir -p `dirname "$hookFullPath"`
    echo -e "$templateContentHashbang\n$insertContent" > "$hookFullPath"
    chmod u+x "$hookFullPath"
    return
  fi

  if [ -z "`cat "$hookFullPath" | tr -d '[[:space:]]'`" ]; then
    echo -e "$templateContentHashbang\n$insertContent" > "$hookFullPath"
    return
  fi

  if ! cat "$hookFullPath" | head -n1 | grep -m1 -qe "$hashbangMatcher"; then
    echo "[git-hook-pure] WARNING: $hookFullPath install failed: hash bang not match $hashbangMatcher."
    return
  fi

  cleanCode "$hookFullPath"
  echo -e "$insertContent" >> "$hookFullPath"
}

install() {
  local defualtTemplateContent='
projectRoot=`git rev-parse --show-toplevel`
hookName=`basename "$0"`
gitParams="$*"

executeAllFiles() {
  local hookFolderPath="$1"
  local hookFilePath

  for f in `ls -1 "$hookFolderPath"`; do
    hookFilePath="$hookFolderPath"/"$f"
    if  [ ! -d "$hookFilePath" ]; then
      if [ -x "$hookFilePath" ]; then
        "$hookFilePath" "${@:2}" || exit 1
      else
        echo "==============================================="
        echo "WARNING: File $hookFilePath not executable"
        echo "==============================================="
      fi
    fi
  done
}

hookFolderPath="$projectRoot"/.githooks
if [ -d "$hookFolderPath" ]; then
  executeAllFiles "$hookFolderPath" "$hookName" "$@"
fi
if [ -d "$hookFolderPath"/"$hookName" ]; then
  executeAllFiles "$hookFolderPath"/"$hookName" "$@"
fi
'

  local templateContent
  local hashbangMatcher
  local templateContentHashbang

  if [ $# -lt 1 ]; then
    templateContent=`echo "$defualtTemplateContent" | tail -n+2`
    hashbangMatcher='\(bash\|zsh\)$'
    templateContentHashbang=`echo "$defualtTemplateContent" | head -n1`
  else
    templateContent=`cat "$1"`
    if hasHashbang "$templateContent"; then
      hashbangMatcher=`echo "$templateContent" | head -n1`
      templateContentHashbang="$hashbangMatcher"
      templateContent=`echo "$templateContent" | tail -n+2`
    fi
  fi

  for hn in $hookNames; do
    installCode "$gitFullPath"/.git/hooks/"$hn"
  done
  if [ ! -e "$gitFullPath"/.githooks ]; then
    mkdir "$gitFullPath"/.githooks
    touch "$gitFullPath"/.githooks/.gitkeep
  else
    if [ ! -d "$gitFullPath"/.githooks ]; then
      echo "[git-hook-pure] WARNING: $gitFullPath/.githooks existed but not a directory!"
      exit
    fi
  fi
  
  echo "[git-hook-pure] installed"
}

# ====================== uninstall =====================

uninstall() {
  for hn in $hookNames; do
    cleanCode "$gitFullPath"/.git/hooks/"$hn"
  done
  
  echo "[git-hook-pure] uninstalled"
}

# ====================== commands =====================

usage() {
  cat<<USAGE
Usage: `basename $0` [command] [args...]

Commands:
  `basename $0` install [<template-file-path>]  Install git-hook-pure with
     specified template file into .git/hooks/*

  `basename $0` uninstall  Uninstall git-hook-pure
USAGE
}

case "$1" in
  install)
    install "${@:2}"
  ;;
  uninstall)
    uninstall
  ;;
  -h|--help|*)
    if [ ! "$_GIT_HOOK_PURE_RUN_ENV" = "test" ]; then
      usage
      exit
    fi
  ;;
esac
