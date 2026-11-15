#!/usr/bin/env bash
# Runs the unit tests `circleci testsuite` selected and collects their JUnit reports.
#   .circleci/run-unit-tests.sh <junit directory> <test class>...
# One variant per module: the libraries' debug unit tests, the app's GitHub debug unit tests and
# the Kotlin-only module's tests. Android Gradle writes each module's reports under its own
# build/test-results, so the fresh ones are copied into the directory the tool reads.
set -uo pipefail
junit_dir=$1
shift
mkdir -p "$junit_dir"
marker=$(mktemp)
classes=$(IFS=,; printf '%s' "$*")
./gradlew testDebugUnitTest testGithubDebugUnitTest :core:model:test --continue -PtestsuiteTests="$classes"
status=$?
find . -path '*/build/test-results/*' -name 'TEST-*.xml' -newer "$marker" -exec cp {} "$junit_dir"/ \;
rm -f "$marker"
exit "$status"
