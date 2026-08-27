# OpenBlog-business 容器镜像
#
# 构建策略：本地 Maven 打包 fat jar → 上传服务器 → 镜像内只运行（不编译）。
# 避免在服务器上拉取 Maven 镜像 + 重复下载依赖，部署最快。
# 内存设置与宝塔原启动一致（-Xmx1024M -Xms256M），这里正确地放在 -jar 之前生效。

FROM eclipse-temurin:17-jre

# 时区：与业务时间处理保持一致
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# 上传的 fat jar（构建参数传入，避免硬编码版本号）
ARG JAR_FILE=OpenBlog-business-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# 堆内存：与宝塔原启动命令一致（正确写法为 -jar 之前的 JVM 参数）
ENV JAVA_OPTS="-Xmx1024M -Xms256M"

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
