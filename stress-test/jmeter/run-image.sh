#!/usr/bin/env bash

echo "docker run --rm --link service_update-stresstests-gf_1:update $@ update-stress-test"
docker run --rm --link service_update-stresstests-gf_1:update $@ update-stress-test
