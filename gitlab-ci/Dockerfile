FROM ubuntu:24.04

# make sure to update libs.versions.toml, when you change something here
# https://developer.android.com/studio#command-tools
ARG ANDROID_CLI_TOOLS=11076708
ARG ANDROID_PLATFORM=35
ARG ANDROID_BUILD_TOOLS=35.0.0

ENV ANDROID_HOME=/android

ARG ANDROID_SDK_MANAGER=${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y apt-utils && \
    apt-get upgrade -y && \
    apt-get install -y locales docker.io wget unzip libncurses6 libcurl4-openssl-dev openjdk-11-jdk-headless openjdk-21-jdk-headless

RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

RUN install -d -m 0755 /etc/apt/keyrings && \
    wget -q https://packages.mozilla.org/apt/repo-signing-key.gpg -O- | tee /etc/apt/keyrings/packages.mozilla.org.asc > /dev/null && \
    gpg -n -q --import --import-options import-show /etc/apt/keyrings/packages.mozilla.org.asc | awk '/pub/{getline; gsub(/^ +| +$/,""); if($0 == "35BAA0B33E9EB396F59CA838C0BA5CE6DC6315A3") print "\nThe key fingerprint matches ("$0").\n"; else print "\nVerification failed: the fingerprint ("$0") does not match the expected one.\n"}' && \
    echo "deb [signed-by=/etc/apt/keyrings/packages.mozilla.org.asc] https://packages.mozilla.org/apt mozilla main" | tee -a /etc/apt/sources.list.d/mozilla.list > /dev/null && \
    echo "Package: *\nPin: origin packages.mozilla.org\nPin-Priority: 1000\n\n" | tee /etc/apt/preferences.d/mozilla > /dev/null && \
    apt-get update && \
    apt-get -y install firefox

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    unzip commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip -d ${ANDROID_HOME}/cmdline-tools/ && \
    rm commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest
RUN yes | ${ANDROID_SDK_MANAGER} --licenses
RUN echo y | ${ANDROID_SDK_MANAGER} "platforms;android-${ANDROID_PLATFORM}" >/dev/null
RUN echo y | ${ANDROID_SDK_MANAGER} "build-tools;${ANDROID_BUILD_TOOLS}" >/dev/null