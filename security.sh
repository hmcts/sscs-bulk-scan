#!/usr/bin/env bash
echo ${TEST_URL}
zap-api-scan.py -t ${TEST_URL}/v2/api-docs -f openapi -P 1001 -r api-report.html -a 
ls
cat zap.out
pwd
echo "the current listing of files"
ls
echo "listings of zap folder"
ls /zap
echo "listings of root folder"
ls /
cp wrk/api-report.html functional-output/
zap-cli -p 1001 alerts -l Informational
