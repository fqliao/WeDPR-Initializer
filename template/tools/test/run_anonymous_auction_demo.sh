#!/usr/bin/env bash

set -e

dirpath="$(cd "$(dirname "$0")" && pwd)"
cd $dirpath

LANG=zh_CN.UTF-8

function check_java(){
   version=$(java -version 2>&1 |grep version |awk '{print $3}')
   len=${#version}-2
   version=${version:1:len}

   IFS='.' arr=($version)
   IFS=' '
   if [ -z ${arr[0]} ];then
      LOG_ERROR "At least Java8 is required."
      exit 1
   fi
   if [ ${arr[0]} -eq 1 ];then
      if [ ${arr[1]} -lt 8 ];then
           LOG_ERROR "At least Java8 is required."
           exit 1
      fi
   elif [ ${arr[0]} -gt 8 ];then
          :
   else
       LOG_ERROR "At least Java8 is required."
       exit 1
   fi
}

make_dir() {
    if [ ! -e "$1" ]; then
        mkdir -p "${output_dir}"
    fi
}

# default value 
type=

function help(){
	echo -e "\033[31m"$1"\033[0m"
    cat << EOF
Usage:
    -t <auction type>                [Required] Set highest or lowest.
    -h Help
EOF

exit 0 
}

function run(){
    java -Djdk.tls.namedGroups="secp256k1" -cp "../../apps/*:../../conf/:../../lib/*" com.webank.wedpr.example.anonymousauction.DemoMain ${type}
}

function parse_params() {
  while getopts "t:h" option;do
      case $option in
      t) type=$OPTARG;;
      h) help;;
      esac
  done
}

function main() {
  [ -z $type ] && help 'ERROR: Please provide -t option to set auction type.'
  run
}

check_java
parse_params $@
main



