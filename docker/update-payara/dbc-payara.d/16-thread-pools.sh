#!/bin/bash

env

asadmin --host localhost --port 4848 --passwordfile=./passfile.txt set server.thread-pools.thread-pool.http-thread-pool.max-thread-pool-size=${THREAD_POOL_SIZE}
