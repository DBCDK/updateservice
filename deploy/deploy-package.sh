#!/bin/bash

mkdir -p updateservice-1.0.0
cd updateservice-1.0.0
rm -rf *

cp ../../README.md .
cp ../../demo/target/deployment/domain-*.xml .
cp ../../target/*.war .
cd ..

tar -c -z updateservice-1.0.0 > updateservice-1.0.0.tgz
