#!/bin/sh
# Sync only portable library source and build definitions; never host projects or build outputs.
set -eu
source_root="${1:-../KuiklyChartView}/kuikly-chart"
target_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)/kuikly-chart"
test -f "$source_root/src/commonMain/kotlin/com/guet/liang/kuiklychart/ChartView.kt"
mkdir -p "$target_root"
cp "$source_root/build.gradle.kts" "$source_root/build.ohos.gradle.kts" "$target_root/"
mkdir -p "$target_root/src"
cp -R "$source_root/src/commonMain" "$source_root/src/commonTest" "$target_root/src/"
