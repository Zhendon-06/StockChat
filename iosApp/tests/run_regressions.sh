#!/bin/sh
set -eu

# Pass the DerivedData directory from an iOS Simulator Debug build.
stockchat_derived_data=${1:?Usage: sh iosApp/tests/run_regressions.sh /path/to/DerivedData}
stockchat_tests=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
stockchat_frameworks="$stockchat_derived_data/Build/Products/Debug-iphonesimulator/OpenKuiklyIOSRender"
stockchat_sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
stockchat_arch=$(uname -m)
stockchat_test_dir=$(mktemp -d /tmp/stockchat-ios-tests.XXXXXX)
trap 'rm -rf "$stockchat_test_dir"' EXIT

python3 -m unittest discover -s "$stockchat_tests" -p 'test_*.py'
for stockchat_suite in TextAreaRegression StreamAndGradientRegression ToastBridgeRegression; do
xcrun --sdk iphonesimulator clang \
    -target "$stockchat_arch-apple-ios14.1-simulator" -isysroot "$stockchat_sdk" \
    -fobjc-arc -fmodules -framework UIKit -framework Foundation \
    -framework AVFoundation -framework PhotosUI \
    -I "$stockchat_tests/../iosApp/KuiklyExpand" \
    -framework OpenKuiklyIOSRender -F "$stockchat_frameworks" \
    -Wl,-rpath,"$stockchat_frameworks" \
    "$stockchat_tests/$stockchat_suite.m" -o "$stockchat_test_dir/$stockchat_suite"
xcrun simctl spawn booted "$stockchat_test_dir/$stockchat_suite"
done
