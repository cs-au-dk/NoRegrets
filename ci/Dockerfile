FROM java:8

RUN apt-get update -yqq && \
    apt-get install apt-transport-https -yqq

# Installing SBT
RUN echo "deb http://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823

RUN apt-get update -yqq && apt-get install sbt -yqq

RUN curl -sL https://deb.nodesource.com/setup_8.x | bash
RUN apt-get install nodejs



# Permission fix for writing volumes with right gid 
RUN apt-get update && apt-get -y --no-install-recommends install \
    ca-certificates \
    curl \
    bzip2 \
    vim \
    build-essential
RUN curl -o /usr/local/bin/gosu -SL "https://github.com/tianon/gosu/releases/download/1.4/gosu-$(dpkg --print-architecture)" \
    && chmod +x /usr/local/bin/gosu
COPY entrypoint.sh /usr/local/bin/fix-permissions-entry.sh

COPY mocha /mocha
RUN (cd /mocha && npm install -g)
RUN npm install -g yarn@1.3.2
RUN npm install -g typescript@2.5.3

RUN npm install -g phantomjs-prebuilt@2.1.16 || true

RUN npm install -g node-gyp

# Required for compilation
RUN apt-get -y install bc git

RUN apt-get --purge autoremove
RUN apt-get clean

# download everything we need in the ivy cache
COPY sample_sbt_project /sbt_sample
RUN bash -c "cd /sbt_sample; sbt compile"
