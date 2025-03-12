# Use Nuitka to compile JET for faster execution

## Preparation

- Install build dependencies via system package manager (we use clang to compile C):

  ```bash
  sudo apt install ccache clang patchelf lld
  ```

  Make sure that all versions are compatible.
  On our build server (Ubuntu 20.04), we use the following versions:

  - ccache version 3.7.7
  - clang version 10.0.0-4ubuntu1 (Target: x86_64-pc-linux-gnu)
  - LLD version 10.0.0
  - patchelf version 0.10

- Use JET program with minimal dependencies: [GH:SebastianSchmidl/minimal-jet](https://github.com/SebastianSchmidl/minimal-jet)

- Within cloned JET folder, create a new (minimal) conda environment just for JET:

  ```bash
  conda create -n jet python=3.9.12 libpython-static
  conda activate jet
  pip install .
  ```

- Install nuitka

  ```bash
  pip install nuitka==2.6.7 psutil
  ```

## Building JET binary

- Change to `experiments/06-jet/`-folder

- Use nuitka to build the JET executable:

  ```bash
  LD="ld.lld" CC="clang" CCFLAGS="-O3" LDFLAGS="-O3 -fuse-ld=lld" python -m nuitka --mode=standalone --enable-plugin=no-qt --nofollow-import-to='*.tests' --nofollow-import-to='numba' --nofollow-import-to='llvmlite' --include-module='sklearn.tree' --noinclude-pytest-mode=nofollow --noinclude-setuptools-mode=nofollow --lto="yes" run-jet.py
  ```

  > This process can take 1-2 hours because we use link-time optimization.

## Deploying and executing JET

tbd

## Results

Unfortunately, compiling JET does not yield significant performance improvements.
Using Numba's JIT for the distance computations is more effective!

The table lists the runtime of JET for the _Coffee_ dataset and the three distance methods _SBD_, _DTW_, and _MSM_.
The runtime is measured in milliseconds for the execution of JET with 10 parallel jobs:

| dataset | distance | python | compiled | JITed |
| :------ | :------- | -----: | -------: | ----: |
| Coffee  | sbd      |   4382 |     3711 |  1159 |
| Coffee  | dtw      |   8432 |     6988 |  1131 |
| Coffee  | msm      |  20887 |    18142 |  1404 |
