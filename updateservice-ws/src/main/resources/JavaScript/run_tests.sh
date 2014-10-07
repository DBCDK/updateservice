#!/bin/bash

SHELL_CMD=dbc-jsshell
SEARCH_PATH_ARGS="-a file:../iscrum/modules/unittests -a file:../iscrum -a file:../iscrum/templates -a file:../iscrum/rules -a file:. -l jstest.log -L info" 

$SHELL_CMD $SEARCH_PATH_ARGS ClassificationData_UnitTests.js
$SHELL_CMD $SEARCH_PATH_ARGS RawRepoCoreTests.js
