#!/bin/ksh
#set -x

cmd="ss -nlp"
# Because macOS is "special"
if [ "$(uname)" == "Darwin" ]
then
    cmd="netstat -ap tcp"
fi

i=0
SOLR_PORT_NR=12345
while test $i -lt 20; do
    i=$((( $i + 1 )))
    p=$((( RANDOM % 60000) + 1025 ))
    eval ${cmd} | grep -q $p || { SOLR_PORT_NR=$p; break; }
done

export SOLR_PORT_NR