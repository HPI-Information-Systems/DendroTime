# Use Nuitka to compile JET for faster execution

## Preparation

- Install build dependencies via system package manager (we use clang to compile C):

  ```bash
  sudo apt install clang ccache patchelf
  ```

- Use JET program with minimal dependencies: GitHub/SebastianSchmidl/minimal-jet

- Within cloned JET folder, create a new (minimal) conda environment just for JET:

  ```bash
  conda create -n jet python=3.9.12 libpython-static
  conda activate jet
  pip install .
  ```

- Install nuitka

  ```bash
  pip install nuitka==2.6.8 psutil
  ```

## Building JET binary

- Change to `experiments/06-jet/`-folder

- Use nuitka to build the JET executable:

  ```bash
  #nice -n 10 python -m nuitka --mode=standalone --enable-plugin=no-qt --nofollow-import-to='*.tests' --nofollow-import-to='matplotlib' --nofollow-import-to='numba' --nofollow-import-to='tqdm' --nofollow-import-to='stumpy' --nofollow-import-to='llvmlite' --include-module='sklearn.tree' --noinclude-pytest-mode=nofollow --noinclude-setuptools-mode=nofollow run-jet.py
  #nice -n 10 python -m nuitka --mode=standalone --enable-plugin=no-qt --nofollow-import-to='*.tests' --nofollow-import-to='numba' --nofollow-import-to='llvmlite' --include-module='sklearn.tree' --noinclude-pytest-mode=nofollow --noinclude-setuptools-mode=nofollow run-jet.py

  CC="clang" CCFLAGS="-O3" LDFLAGS="-O3" nice -n 10 python -m nuitka --mode=standalone --enable-plugin=no-qt --nofollow-import-to='*.tests' --nofollow-import-to='numba' --nofollow-import-to='llvmlite' --include-module='sklearn.tree' --noinclude-pytest-mode=nofollow --noinclude-setuptools-mode=nofollow --lto="yes" run-jet.py

  # exclusion candidates:
  # scipy.sparse
  # scipy.optimize
  # scipy.ndimage
  # pandas.io
  # sklearn.model_selection, _loss, svm, decomposition?
  # statsmodels.genmod, graphics, ...?

  # try:
  # --cf-protection="none"
  # --onefile
  # --deployment
  ```

> !!
> Pandas is taking long (tsfresh depends on it for I/O) --> check if I can safely exclude submodules?
> statsmodels is huge, sklearn is huge

## Deploying and executing JET
