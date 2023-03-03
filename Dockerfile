#
# The MIT License
# Copyright © 2022 FAIR Data Team
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

FROM maven:3-eclipse-temurin-17 as builder

WORKDIR /builder

ADD . /builder

RUN mvn --quiet -B -U --fail-fast -DskipTests package

################################################################################
# RUN STAGE
FROM eclipse-temurin:17

WORKDIR /app
EXPOSE 8080

ENV SPRING_PROFILE=production

# Mount point for rolling log files
RUN mkdir /app/logs

COPY --from=builder /builder/target/app.jar /app/app.jar
COPY --from=builder /builder/target/classes/application.yml /app/application.yml

ENTRYPOINT java -jar app.jar --spring.profiles.active=$SPRING_PROFILE --spring.config.location=classpath:/application.yml,file:/app/application.yml
