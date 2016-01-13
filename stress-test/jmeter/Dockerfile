# update-stress-test

FROM java:latest
MAINTAINER Sune Thomas Poulsen, stp@dbc.dk

ENV JAVA_HOME     /usr/lib/jvm/java-8-openjdk-amd64
ENV APP_USER      jmeter
ENV APP_USER_HOME /home/$APP_USER

ENV PATH /usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$JAVA_HOME/bin:$GLASSFISH_HOME/bin

RUN apt-get update && \
    apt-get install -y expect procps curl unzip zip inotify-tools less jmeter && \
    rm -rf /var/lib/apt/lists/*

# Create user to run jmeter
RUN useradd $APP_USER && \
    mkdir $APP_USER_HOME && \
    chown $APP_USER_USER:$APP_USER_USER $APP_USER_HOME

ADD plan $APP_USER_HOME/plan/
COPY entry_point.sh $APP_USER_HOME/.

RUN chown -R $APP_USER:$APP_USER $APP_USER_HOME

USER $APP_USER
WORKDIR $APP_USER_HOME

CMD [ "./entry_point.sh" ]
