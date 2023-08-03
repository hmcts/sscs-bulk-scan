ARG APP_INSIGHTS_AGENT_VERSION=3.4.13
FROM hmctspublic.azurecr.io/base/java:17-distroless

COPY lib/applicationinsights.json /opt/app/
COPY build/libs/sscs-bulk-scan.jar /opt/app/

EXPOSE 8090

CMD ["sscs-bulk-scan.jar"]
