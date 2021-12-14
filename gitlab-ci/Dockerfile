FROM ubuntu:latest

# make sure to update Versions.kt, when you change something here
ARG ANDROID_CLI_TOOLS=7583922
ARG ANDROID_PLATFORM=30
ARG ANDROID_BUILD_TOOLS=30.0.3
ARG ANDROID_NDK=23.1.7779620
ARG CMAKE=3.22.1

ENV ANDROID_SDK_ROOT=/android

ARG ANDROID_SDK_MANAGER=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y docker.io wget unzip chromium-browser build-essential ninja-build libncurses5 libcurl4-openssl-dev openjdk-11-jre-headless
RUN wget "https://github.com/Kitware/CMake/releases/download/v${CMAKE}/cmake-${CMAKE}-linux-x86_64.sh" && \
    chmod +x cmake-${CMAKE}-linux-x86_64.sh && \
    ./cmake-${CMAKE}-linux-x86_64.sh --skip-license --exclude-subdir --prefix=/usr && \
    rm cmake-${CMAKE}-linux-x86_64.sh
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    unzip commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools/ && \
    rm commandlinetools-linux-${ANDROID_CLI_TOOLS}_latest.zip && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest
RUN yes | ${ANDROID_SDK_MANAGER} --licenses
RUN echo y | ${ANDROID_SDK_MANAGER} "platforms;android-${ANDROID_PLATFORM}" >/dev/null
RUN echo y | ${ANDROID_SDK_MANAGER} "build-tools;${ANDROID_BUILD_TOOLS}" >/dev/null
RUN echo y | ${ANDROID_SDK_MANAGER} "ndk;${ANDROID_NDK}" >/dev/null