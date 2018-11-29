FROM hmcts/cnp-java-base:openjdk-jre-8-alpine-1.4

COPY build/libs/sscs-bulk-scan.jar /opt/app/lib/

WORKDIR /opt/app

HEALTHCHECK --interval=10s --timeout=10s --retries=10 CMD if [ `wget -qO- http://localhost:8090/health | grep "UP"` ]; then exit 0; else exit 1; fi

EXPOSE 8090

ENTRYPOINT ["/opt/app/bin/sscs-bulk-scan"]
