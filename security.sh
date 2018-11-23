#!/usr/bin/env bash
echo ${TEST_URL}
zap-api-scan.py -t ${TEST_URL}/v2/api-docs -f openapi -P 1001 -r /zap/wrk/api-report.html -a 
ls
cat zap.out
pwd
echo "the current listing of files"
ls -la
echo "listings of zap folder"
ls -la /zap
echo "listings of root folder"
ls /
echo "listings of wrk folder"
ls -la /zap/wrk
cp ./wrk/api-report.html functional-output/
zap-cli -p 1001 alerts -l Informational
