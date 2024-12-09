 # renovate: datasource=github-releases depName=microsoft/ApplicationInsights-Java
ARG APP_INSIGHTS_AGENT_VERSION=3.6.2
FROM hmctspublic.azurecr.io/base/java:21-distroless

COPY lib/applicationinsights.json /opt/app/
COPY build/libs/sscs-bulk-scan.jar /opt/app/

EXPOSE 8090

CMD ["sscs-bulk-scan.jar"]
