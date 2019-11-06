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
test_func=
count= 
qps= 

function help(){
	echo -e "\033[31m"$1"\033[0m"
    cat << EOF
Usage:
    -i <run issue credit perf test>                     Default no.
    -f <run fulfill credit perf test>                   Default no.
    -t <run transfer credit perf test>                  Default no.
    -c <transaction count>                              [Required] Set transaction count.
    -q <transaction qps>                                [Required] Set transaction qps.
    -h Help
EOF

exit 0 
}

function run(){
  if [ ${test_func} == "issueCredit" ];then
      java -Djdk.tls.namedGroups="secp256k1" -cp "../apps/*:../conf/:../lib/*" com.webank.wedpr.assethiding.PerfIssueCreditMain ${count} ${qps}
  elif [ ${test_func} == "fulfillCredit" ];then
      java -Djdk.tls.namedGroups="secp256k1" -cp "../apps/*:../conf/:../lib/*" com.webank.wedpr.assethiding.PerfFulfillCreditMain ${count} ${qps}
  elif [ ${test_func} == "transferCredit" ];then
      java -Djdk.tls.namedGroups="secp256k1" -cp "../apps/*:../conf/:../lib/*" com.webank.wedpr.assethiding.PerfTransferCreditMain ${count} ${qps}
  elif [ ${test_func} == "splitCredit" ];then
      java -Djdk.tls.namedGroups="secp256k1" -cp "../apps/*:../conf/:../lib/*" com.webank.wedpr.assethiding.PerfSplitCreditfMain ${count} ${qps}
  fi
}

function parse_params() {
  while getopts "c:q:iftsh" option;do
      case $option in
      i) test_func="issueCredit";;
      f) test_func="fulfillCredit";;
      t) test_func="transferCredit";;
      s) test_func="splitCredit";;
      c) count=$OPTARG;;
      q) qps=$OPTARG;;
      h) help;;
      esac
  done
}

function main() {
  [ -z $test_func ] && help 'ERROR: Please provide -i, -f, -t or -s option to select perf test.'
  [ -z $count ] && help 'ERROR: Please provide -c option to set transaction count.'
  [ -z $qps ] && help 'ERROR: Please provide -q option to set transaction qps.'
  run
}

check_java
parse_params $@
main



