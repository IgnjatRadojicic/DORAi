# DORAi server image.
# The build stage runs on the build machine's own platform: the output is plain JVM jars,
# so Gradle runs once for every target platform (amd64, arm64). Only the short runtime stage
# runs per platform, under QEMU for arm64 in the release workflow.

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :server:installDist --no-daemon --console=plain -q

FROM eclipse-temurin:21-jre
RUN groupadd --system dorai && useradd --system --gid dorai --home-dir /var/lib/dorai dorai \
    && mkdir -p /var/lib/dorai && chown dorai:dorai /var/lib/dorai
COPY --from=build /src/server/build/install/server /opt/dorai
# The heap follows the container's memory limit; override JAVA_OPTS in compose to change it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
USER dorai
WORKDIR /var/lib/dorai
VOLUME /var/lib/dorai
EXPOSE 8080 8443
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsk https://localhost:8443/api/health || curl -fs http://localhost:8080/api/health || exit 1
ENTRYPOINT ["/opt/dorai/bin/server"]
