#!/bin/bash

path=$1
origin=$2
current=$origin
IFS=$'\n'

mkdir -p $path/tmp

while :; do
	res=$(grep -noPm 1 '(?<=// ----- 8< ----- FILE ").*(?=" ----- 8< -----)' $path/$origin)
	if [ -z "$res" ]; then break; fi
	lastline=$(echo $res | grep -o '[0-9]*' | head -1)
	head -n $(($lastline - 3)) $path/$origin >$path/tmp/$current
	sed -i "1,$(($lastline + 1))d" $path/$origin
	current=$(echo $res | grep -oP "(?<=$lastline:).*")
done

mv $path/$origin $path/$current
mv $path/tmp/* $path/
rm -rf $path/tmp
