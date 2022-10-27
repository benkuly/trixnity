FROM ubuntu:20.04

# make sure to update Versions.kt, when you change something here
ARG ANDROID_CLI_TOOLS=8512546
ARG ANDROID_PLATFORM=33
ARG ANDROID_BUILD_TOOLS=33.0.0

ENV ANDROID_HOME=/android

ARG ANDROID_SDK_MANAGER=${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y apt-utils && \
    apt-get upgrade -y && \
    apt-get install -y locales docker.io wget unzip libncurses5 libcurl4-openssl-dev openjdk-17-jdk-headless firefox

RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    unzip commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip -d ${ANDROID_HOME}/cmdline-tools/ && \
    rm commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest
RUN yes | ${ANDROID_SDK_MANAGER} --licenses
RUN echo y | ${ANDROID_SDK_MANAGER} "platforms;android-${ANDROID_PLATFORM}" >/dev/null
RUN echo y | ${ANDROID_SDK_MANAGER} "build-tools;${ANDROID_BUILD_TOOLS}" >/dev/null