# auth-service 的镜像：多阶段构建，第一阶段把 Spring Boot 的可执行 jar 按层拆开，第二阶段只拷贝运行需要的东西。
# 构建（在项目根目录执行，先 ./mvnw -DskipTests package）：
#   docker build -f auth-service/Dockerfile -t ms/auth-service:0.1.0 .
# 基础镜像用 eclipse-temurin 25 JRE。连不上 Docker Hub 时换成镜像源，例如：
#   --build-arg BASE_IMAGE=mirror.gcr.io/library/eclipse-temurin:25-jre
ARG BASE_IMAGE=eclipse-temurin:25-jre

FROM ${BASE_IMAGE} AS extract
WORKDIR /build
COPY auth-service/target/auth-service-*.jar app.jar
# Boot 的分层 jar：依赖（几乎不变）、快照依赖、资源、业务代码（经常变）分成不同的层
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM ${BASE_IMAGE}
# 不用 root 运行：容器逃逸时攻击者拿到的只是普通用户
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER app
EXPOSE 9081
# MaxRAMPercentage：按容器内存限制而不是宿主机内存来算堆大小
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED" \
    MS_CONFIG_DIR=/config
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
