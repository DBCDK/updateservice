#!/usr/bin/env bash

./generate_settings.sh
jmeter -n -t plan.jmx -p settings.properties -l results/jmeter.log
