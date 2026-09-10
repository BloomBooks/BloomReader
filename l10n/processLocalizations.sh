#!/bin/bash
# Remap the folders Crowdin exports into the ones Android expects.
# Safe to run when some folders are absent (each step is conditional), so it exits 0 on a normal run.
set -e

cd "$(dirname "$0")/../app/src/main/res/"

# nothing to do
#mam

# strip off the region code
# Now that crowdin is giving us the 2-letter code by default, I think this section
#  is no longer needed. But I'll keep it here; it doesn't hurt anything,
#  and something could change which causes us to get the region code again.
move_region() { # move_region <from-dir> <to-dir>
  if [ -f "$1/strings.xml" ]; then
    mkdir -p "$2"
    mv "$1/strings.xml" "$2/strings.xml"
    rmdir "$1" 2>/dev/null || true   # only the (possibly non-empty) source dir removal is optional
  fi
}
move_region values-bn-rBD  values-bn  #bn
move_region values-es-rES  values-es  #es
move_region values-fr-rFR  values-fr  #fr
move_region values-hi-rIN  values-hi  #hi
move_region values-my-rMM  values-my  #my
move_region values-pt-rPT  values-pt  #pt
move_region values-quc-rGT values-quc #quc
move_region values-sw-rKE  values-sw  #sw

# Android uses the legacy ISO code "in" for Indonesian (Crowdin gives "id")
move_region values-id values-in #id -> in

# add the 2-letter code with region (which is the one Android usually/always? uses for these)
copy_to() { # copy_to <from-dir> <to-dir>
  if [ -f "$1/strings.xml" ]; then mkdir -p "$2" && cp "$1/strings.xml" "$2/strings.xml"; fi
}
copy_to values-pbu values-ps-rAF #pbu -> ps-AF
copy_to values-prs values-fa-rAF #prs -> fa-AF
copy_to values-zh  values-zh-rCN #zh -> zh-CN

# the test pseudo-locale (qaa-x-test) must not ship; the region-coded form also broke the build
rm -rf values-qaa-rx-rtest values-qaa
