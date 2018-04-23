#!/bin/bash

cd ${OCB_USER_HOME}/opencat-business
generate_settings.sh

ocb-test.sh run --summary --config settings
cd -

echo $USER
cat /etc/passwd

cp ${OCB_USER_HOME}/opencat-business/*.log ${OCB_USER_HOME}/results/
cp ${OCB_USER_HOME}/opencat-business/target/surefire-reports/TEST-ocb-tests.xml ${OCB_USER_HOME}/results/

cd -
