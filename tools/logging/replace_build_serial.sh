#!/bin/bash

IFS=$'\n';
files=($(ls | grep "logcat"));

for file in "${files[@]}"; do
    echo "Processing file: $file";
    lines=($(grep "x[0-9] - name:" $file))

    for line in "${lines[@]}"; do
        echo $line;
        var=$(echo $(echo $line | cut -d':' -f4) | cut -c1-3);
        name=$(echo $(echo $line | cut -d':' -f5) | cut -d',' -f1);
        echo "var: $(echo $var | tr -d [:space:]), name: $name";
        sed -i "s/$name/$var/" "$file"
    done
done
