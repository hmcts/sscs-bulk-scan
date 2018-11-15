#!/bin/bash
## Usage: ./create-test-data.sh
##
## Creates test data in CCD using the /exception-record endpoint

binFolder=$(dirname "$0")

userId=1
userToken="$(${binFolder}/idam-user-token.sh caseworker-sscs,caseworker-sscs-systemupdate,caseworker-sscs-anonymouscitizen,caseworker-sscs-callagent,caseworker $userId)"
serviceToken="$(${binFolder}/idam-service-token.sh ccd_data)"

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

for f in $scriptDir/../src/test/resources/import/*.json
do
  echo "Importing ${f}"
  curl -v \
    http://localhost:8090/exception-record \
    -H "Authorization: Bearer ${userToken}" \
    -H "ServiceAuthorization: Bearer ${serviceToken}" \
    -H "user-id: ${userId}" \
    -H "Content-Type: application/json" \
    --data @$f
done

echo



