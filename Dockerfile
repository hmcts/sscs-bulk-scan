FROM hmcts/cnp-java-base:openjdk-jre-8-alpine-1.4

ENV APP sscs-bulk-scan.jar
ENV APPLICATION_TOTAL_MEMORY 1024M
ENV APPLICATION_SIZE_ON_DISK_IN_MB 56

COPY build/libs/$APP /opt/app/

WORKDIR /opt/app

HEALTHCHECK --interval=10s --timeout=10s --retries=10 CMD if [ `wget -qO- http://localhost:8090/health | grep "UP"` ]; then exit 0; else exit 1; fi

EXPOSE 8090

