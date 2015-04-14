#!/bin/bash

IFS=$'\n';
lines=($(ls -1 *.log));

for file in "${lines[@]}"; do
    echo $file;
    uIDs=($(grep "Sending a token\." $file | egrep -o "uniqueLocalIdentifier=[0-9]+"));
    owners=($(grep "Sending a token\." $file | egrep -o "owner={serviceName='x[0-9]'"));
    destinations=($(grep "Sending a token\." $file | egrep -o "destination={serviceName='x[0-9]'"));
    for ((i = 0; i < ${#uIDs[@]}; i++)); do
        id=${uIDs[i]};
        owner=${owners[i]};
        destination=$(echo ${destinations[i]} | cut -c27-28);
        fileToCheck=$destination.log;
        pattern="receiveTokenInternal: Token{$id, $owner"
        receiveToken="Entering receiveToken. Token: Token{$id, $owner"
        if grep -q $pattern $fileToCheck; then
            echo "Token received internally.";
        else
            echo "Token not received internally.";
        fi
        if grep -q $receiveToken $fileToCheck; then
            echo "Token received.";
        else
            echo "Token{$id, $owner} not received on $fileToCheck.";
        fi

    done
done
