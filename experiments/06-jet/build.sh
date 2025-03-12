#!/usr/bin/env bash

# build JET using nuitka (be sure to use conda env jet!)

export CC="clang"
export LD="ld.lld"
export CCFLAGS="-O3"
export LDFLAGS="-O3 -fuse-ld=lld"

echo "$(date -Iseconds) Beginning compilation"
time python -m nuitka --mode=standalone --enable-plugin=no-qt \
  --nofollow-import-to='*.tests' --nofollow-import-to='numba' --nofollow-import-to='llvmlite' \
  --include-module='sklearn.tree' \
  --noinclude-pytest-mode=nofollow --noinclude-setuptools-mode=nofollow \
  --lto="yes" run-jet.py

echo "$(date -Iseconds) Finished"
