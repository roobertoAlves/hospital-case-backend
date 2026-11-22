FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY lib lib
COPY src/main src/main
RUN find src/main/java -name '*.java' > sources.txt \
    && javac --release 21 -encoding UTF-8 -nowarn -d classes -cp "lib/compile/*:src/main/webapp/WEB-INF/lib/*" @sources.txt

FROM tomcat:9.0-jre21-temurin
ENV APP_DIR=/usr/local/tomcat/webapps/hospital-case-backend
RUN rm -rf /usr/local/tomcat/webapps/* \
    && sed -i 's#</Host>#  <Valve className="org.apache.catalina.valves.RemoteIpValve" remoteIpHeader="x-forwarded-for" protocolHeader="x-forwarded-proto" internalProxies=".*" />\n      </Host>#' /usr/local/tomcat/conf/server.xml
COPY --from=build /src/src/main/webapp ${APP_DIR}
COPY --from=build /src/classes ${APP_DIR}/WEB-INF/classes
COPY docker/entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh
EXPOSE 8080
CMD ["/entrypoint.sh"]
