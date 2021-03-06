#!/usr/bin/env bash

set -e

LANG=zh_CN.UTF-8

function help(){
	echo -e "\033[31m"$1"\033[0m"
    cat << EOF
Usage:
    -i <Input file>                                [Required] Set secret file.
    -n <Secret parts number>                       [Required] Set the number of parts to produce (must be >1 and <= 2147483647).
    -t <Threshold parts number>                    [Required] Set the threshold of joinable parts (must be >1 and <=n).
    -o <Output Directory>                          [Required] Set secret parts output directory.
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
input_file= 
output_dir= 
n=
t=

function run(){
  input_file=$(abspath ${input_file})
  output_dir=$(abspath ${output_dir})
  dirpath="$(cd "$(dirname "$0")" && pwd)"
  cd $dirpath
  java -cp "../apps/*:../conf/:../lib/*" com.webank.wedpr.tool.KeyManager ${input_file} ${n} ${t} ${output_dir} "backup_secret"
}

function parse_params() {
  while getopts "i:n:t:o:h" option;do
      case $option in
      i) input_file=$OPTARG;;
      n) n=$OPTARG;;
      t) t=$OPTARG;;
      o) output_dir=$OPTARG;;
      h) help;;
      esac
  done
}

function main() {
  [ -z $input_file ] || [ -z $n ] || [ -z $t ] || [ -z $output_dir ] && help 'ERROR: Please set -i, -n, -t and -o option.'
  make_dir ${output_dir}
  run
}

check_java
parse_params $@
main




