#!/usr/bin/env bash
echo ${TEST_URL}
zap-api-scan.py -t ${TEST_URL}/v2/api-docs -f openapi -P 1001 -r api-report.html -a 
ls
cat zap.out
cp wrk/api-report.html functional-output/
zap-cli -p 1001 alerts -l Informational
