FROM ubuntu:20.04

# make sure to update Versions.kt, when you change something here
ARG ANDROID_CLI_TOOLS=8512546
ARG ANDROID_PLATFORM=32
ARG ANDROID_BUILD_TOOLS=32.0.0
ARG ANDROID_NDK=23.1.7779620
ARG CMAKE=3.24.0

ENV ANDROID_HOME=/android

ARG ANDROID_SDK_MANAGER=${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y apt-utils && \
    apt-get upgrade -y && \
    apt-get install -y docker.io wget unzip build-essential ninja-build mingw-w64 libncurses5 libcurl4-openssl-dev openjdk-17-jdk firefox
RUN wget "https://github.com/Kitware/CMake/releases/download/v${CMAKE}/cmake-${CMAKE}-linux-x86_64.sh" && \
    chmod +x cmake-${CMAKE}-linux-x86_64.sh && \
    ./cmake-${CMAKE}-linux-x86_64.sh --skip-license --exclude-subdir --prefix=/usr && \
    rm cmake-${CMAKE}-linux-x86_64.sh
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    unzip commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip -d ${ANDROID_HOME}/cmdline-tools/ && \
    rm commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest
RUN yes | ${ANDROID_SDK_MANAGER} --licenses
RUN echo y | ${ANDROID_SDK_MANAGER} "platforms;android-${ANDROID_PLATFORM}" >/dev/null
RUN echo y | ${ANDROID_SDK_MANAGER} "build-tools;${ANDROID_BUILD_TOOLS}" >/dev/null
RUN echo y | ${ANDROID_SDK_MANAGER} "ndk;${ANDROID_NDK}" >/dev/null