#!/bin/bash

set -e

# default value
macOS=""
compatibility_version="0.1"
EXIT_CODE=1

LOG_WARN() {
    local content=${1}
    echo -e "\033[31m[WARN] ${content}\033[0m"
}

LOG_INFO() {
    local content=${1}
    echo -e "\033[32m[INFO] ${content}\033[0m"
}

SHELL_FOLDER=$(
    cd $(dirname $0)
    pwd
)

dir_must_exist() {
    if [ ! -d "$1" ]; then
        echo "$1 DIR does not exist, please check!"
        exit $EXIT_CODE
    fi
}

dir_must_not_exist() {
    if [ -d "$1" ]; then
        echo "$1 DIR existed, please check!"
        exit $EXIT_CODE
    fi
}

check_env() {
    if [ "$(uname)" == "Darwin" ]; then
        macOS="macOS"
    fi
}

download_WeDPR_dynamic_lib() {
    dir_must_exist ${SHELL_FOLDER}/../src/main/resources/
    if [ ! -d "${SHELL_FOLDER}/../src/main/resources/WeDPR_dynamic_lib" ]; then
        cd ${SHELL_FOLDER}/
        package_name="linux_WeDPR_dynamic_lib.tar.gz"
        [ ! -z "${macOS}" ] && package_name="mac_WeDPR_dynamic_lib.tar.gz"
        Download_Link="https://github.com/WeDPR/TestBinary/releases/download/v${compatibility_version}/${package_name}"
        LOG_INFO "Downloading WeDPR dynamic lib from ${Download_Link} ..."
        curl -LO ${Download_Link}
        tar -zxf ${package_name} -C ${SHELL_FOLDER}/../src/main/resources/
        rm ${package_name}
        LOG_INFO "Downloading WeDPR dynamic lib to ${SHELL_FOLDER}/../src/main/resources/ successful!"
    fi
    LOG_INFO "${SHELL_FOLDER}/../src/main/resources/WeDPR_dynamic_lib DIR existed, continue!"
}

main() {
    check_env
    download_WeDPR_dynamic_lib
}

main
