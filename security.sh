#!/usr/bin/env bash
while !(curl -s http://0.0.0.0:1001) > /dev/null
do
     i=$(( (i+1) %4 ))
     sleep .1
done
echo "ZAP has successfully started"
echo ${TEST_URL}
zap-api-scan.py -t ${TEST_URL}/v2/api-docs -f openapi -r api-report.html
cp *.html functional-output/
zap-cli -p 1001 alerts -l Informational
