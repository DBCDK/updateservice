#!/bin/bash

mkdir -p updateservice-1.0.0
cd updateservice-1.0.0
rm -rf *

cp ../../README.md .
cp ../../updateservice-system-tests/updateservice-system-tests-servers/glassfish/home/glassfish4/glassfish/domains/domain1/config/domain.xml .
cp ../../updateservice-app/target/*.ear .
cd ..

tar -c -z updateservice-1.0.0 > updateservice-1.0.0.tgz
