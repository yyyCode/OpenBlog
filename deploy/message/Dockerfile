# OpenBlog-message 容器镜像
# 构建策略与 business 一致：本地/CI Maven 打 fat jar → 上传 → 镜像内只运行（不编译）。

FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

ARG JAR_FILE=OpenBlog-message-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENV JAVA_OPTS="-Xmx512M -Xms128M"

EXPOSE 8083
EXPOSE 20883

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
