#!/bin/bash

cd ../app/src/main/res/

# nothing to do
#mam
#pbu
#prs
#zh-rCN

# strip off the region code
mkdir -p values-bn && mv values-bn-rBD/strings.xml values-bn/strings.xml #bn
mkdir -p values-es && mv values-es-rES/strings.xml values-es/strings.xml #es
mkdir -p values-fr && mv values-fr-rFR/strings.xml values-fr/strings.xml #fr
mkdir -p values-hi && mv values-hi-rIN/strings.xml values-hi/strings.xml #hi
mkdir -p values-my && mv values-my-rMM/strings.xml values-my/strings.xml #my
mkdir -p values-pt && mv values-pt-rPT/strings.xml values-pt/strings.xml #pt
mkdir -p values-quc && mv values-quc-rGT/strings.xml values-quc/strings.xml #quc
mkdir -p values-sw && mv values-sw-rKE/strings.xml values-sw/strings.xml #sw

# add the 2-letter code with region (which is the one Android usually/always? uses)
mkdir -p values-ps-rAF && cp values-pbu/strings.xml values-ps-rAF/strings.xml #pbu -> ps-AF
mkdir -p values-fa-rAF && cp values-prs/strings.xml values-fa-rAF/strings.xml #pbu -> fa-AF

# the presence of this directory causes the app not to build
rm -r values-qaa-rx-rtest