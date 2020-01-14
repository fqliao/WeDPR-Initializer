#!/usr/bin/env bash

set -e

LANG=zh_CN.UTF-8

function help(){
	echo -e "\033[31m"$1"\033[0m"
    cat << EOF
Usage:
    -i <Input Directory>                           [Required] Set secret parts input directory.
    -o <Output Directory>                          [Required] Set secret output directory.
    -h Help
EOF

exit 0 
}

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

abspath() {                                               
    echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")";
}

# default value 
input_dir= 
output_dir= 

function run(){
  input_dir=$(abspath ${input_dir})
  output_dir=$(abspath ${output_dir})
  dirpath="$(cd "$(dirname "$0")" && pwd)"
  cd $dirpath
	java -cp "../apps/*:../conf/:../lib/*" com.webank.wedpr.tool.KeyManager ${input_dir} ${output_dir} "recover_secret"
}

function parse_params() {
  while getopts "i:o:h" option;do
      case $option in
      i) input_dir=$OPTARG;;
      o) output_dir=$OPTARG;;
      h) help;;
      esac
  done
}

function main() {
  [ -z $input_dir ] || [ -z $output_dir ] && help 'ERROR: Please set -i and -o option.'
  make_dir ${output_dir}
  run
}

check_java
parse_params $@
main



