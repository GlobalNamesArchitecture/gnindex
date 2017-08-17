FROM ubuntu:16.04
MAINTAINER Dmitry Mozzherin
ENV LAST_FULL_REBUILD 2017-08-25

ENV SBT_VERSION 0.13.15
ENV SBT_HOME /usr/local/sbt
ENV PATH ${PATH}:${SBT_HOME}/bin

RUN apt-get update \
    && apt-get install -y apt-transport-https apt-utils software-properties-common \
    && apt-add-repository ppa:brightbox/ruby-ng \
    && apt-get update \
    && apt-get install -y ruby2.3 ruby2.3-dev ruby-switch \
       libxslt-dev supervisor build-essential nodejs supervisor \
       zlib1g-dev libssl-dev libreadline-dev libyaml-dev \
       libxml2-dev libxslt-dev nodejs libpq-dev liblzma-dev \
       openjdk-8-jdk curl postgresql-client git \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN curl -sL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | \
    gunzip | tar -x -C /usr/local && \
    echo -ne "- with sbt $SBT_VERSION\n" >> /root/.built

RUN ruby-switch --set ruby2.3
RUN echo 'gem: --no-rdoc --no-ri' | tee -a $HOME/.gemrc
RUN gem install bundle

RUN mkdir /app
WORKDIR /app

COPY ./project/*.sbt ./project/*.scala ./project/build.properties /app/project/
COPY build.sbt /app/
RUN sbt test:compile

COPY ./db-migration/Gemfile /app/db-migration/
WORKDIR /app/db-migration
RUN bundle
COPY . /app
WORKDIR /app

CMD ["/app/bin/docker-startup.sh"]
