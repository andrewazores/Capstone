#!/bin/bash

IFS=$'\n';
lines=($(adb shell ls /mnt/sdcard/Logs/))

for line in "${lines[@]}"; do
    echo $line;
    numbers=($(echo $line | cut -c8-20));
    largest=0;
    for i in "${numbers[@]}"; do
        if [ "$largest" -lt "$i" ]; then
            largest=$i;
        fi
    done
done
echo $largest;
if [ -n "${1:+x}" ]; then
    adb pull /mnt/sdcard/Logs/logcat_$largest.log $1;
else
    adb pull /mnt/sdcard/Logs/logcat_$largest.log .;
fi
