# Literal Mutator

[![arXiv](https://img.shields.io/badge/arXiv-2009.12156-b31b1b)](https://arxiv.org/abs/2009.12156)

This repository contains code for the paper "An Empirical Study on the Impact of Deep Parameters on Mobile App Energy Usage" (SANER 2022), presenting the deep paramter mutation and testing framework.

## Dependency

- JDK (tested against OpenJDK 16)
- [android-appium-profiler](https://github.com/qiangxu1996/android-appium-profiler)
- (Optional) Visual Studio Code with [Bookmarks](https://marketplace.visualstudio.com/items?itemName=alefragnani.Bookmarks) extension

## Setup

```bash
mkdir <work dir> && cd <work dir>
git clone https://github.com/qiangxu1996/literal-mutator.git
cd literal-mutator
./gradlew installDist
cd ..
git clone https://github.com/qiangxu1996/android-appium-profiler.git
# Setup android-appium-profiler following its README
```

## Extracting deep parameters from an app

Assume the app source code tree is placed at `<app dir>`. Its Java source code is usually placed at `<app dir>/<module name>/src/main/java` in a per-module basis.

```bash
mkdir <test dir> && cd <test dir>
<work dir>/literal-mutator/build/install/literal-mutator/bin/literal-mutator init <app dir> <module 1>/src/main/java <module 2>/src/main/java ... -t [NUM|BOOL|ENUM] [-m|-c <csv file>]
```

One can specify the type of deep parameters to extract – numeric, boolean, or enumerations. After executing the command, a number of files are populated in `<app dir>`:

- `literal-paths.txt`: the extracted deep parameters, one per line, in Spoon's path representation
- `test-conf.yml`: configuration file used to direct the mutation and testing phase
- `test-counter.txt`: track the number of parameters tested (for the mutation and testing phase) to enable pause-and-continue testing
- `bookmarks.json` if `-m` is specified: the extracted deep parameters in Visual Studio Code's bookmark format. To see the parameters in Visual Studio Code, install the Bookmarks extension, turn on `bookmarks.saveBookmarksInProject`, and copy/move the file to `<app dir>/.vscode/`
- `<csv file>` if `-c` is specified: the extracted deep parameters in CSV format, with the first column being the Spoon path, and the second column being the line containing the parameter

### Coverage-based filtering

The program performs heuristic-based filtering of deep parameters by default. To additionally enable coverage-based filtering, follow the README of android-appium-profiler, supply the generated coverage file after `-e`, and corresponding bytecode directories after `-b`, repeated for each bytecode directory.

### Manual filtering

We suggest performing manual filtering using Visual Studio Code's bookmark feature. Remove the corresponding bookmarks of the constants that are not deep paramters. A companion script `bookmark-extract-path.py` can be used to convert the resulting `bookmarks.json` to `literal-paths.txt`.

## Deep paramter mutation and testing

Configure `test-conf.yml` before running the tests:

```yaml
---
general:
  project: <app dir>, pre-filled by the init command
  sources: list of directories with Java source code, pre-filled by the init command
  literalType: NUM, BOOL, or ENUM
  enumDefinitions: additional enum definitions that are not in the app source code, see framework-enums.csv for an example, optional
android:
  driverScript: path to run_test.py in android-appium-profiler
  uiScript: the app test script to use from android_appium_profiler/apps, without .py
  stableThreshold: redo the tests for a specific parameter if the normalized standard deviation of the results is higher than the threshold
```

In android-appium-profiler, we provide UI scripts for all apps that we use in the paper, and they should work well with the app versions specified in Table III of the paper.

Then create a script – `build.sh` – in `<app dir>` to build the app. Make sure the script is executable, and the resulting APK file is at `<app dir>/app-debug.apk`.

After configuring `test-conf.yml` and `build.sh`, run the tests from within `<test dir>`:

```bash
<work dir>/literal-mutator/build/install/literal-mutator/bin/literal-mutator test
```

`ref-results.json` and `mut-results.json` give the measurement results for the original and mutated paramters, respectively. `process_ref.py` and `process_mut.py` can further process and visualize the results. Refer to the source code for details.

## Citation

Please cite this paper if it helps your research:

```
@INPROCEEDINGS{xu2022param,
  author={Xu, Qiang and Davis, James C. and Hu, Y. Charlie and Jindal, Abhilash},
  booktitle={2022 IEEE International Conference on Software Analysis, Evolution and Reengineering (SANER)},
  title={An Empirical Study on the Impact of Deep Parameters on Mobile App Energy Usage},
  year={2022}
}
```
