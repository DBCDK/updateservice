#!/usr/bin/env bash

cd ../opencat-business
java -jar ../ocb-tools/target/dist/ocb-tools-1.0.0/bin/ocb-test-1.0-SNAPSHOT-jar-with-dependencies.jar run -c testrun --summary "$@"
cd -