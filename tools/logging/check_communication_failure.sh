#!/bin/bash

IFS=$'\n';
lines=($(ls -1 *.log));

for file in "${lines[@]}"; do
    echo $file;
    tokensSent=$(grep "Sending a token\." $file)
    uIDs=($(egrep -o "uniqueLocalIdentifier=[0-9]+" <<< "$tokensSent"));
    owners=($(egrep -o "owner={serviceName='x[0-9]'" <<< "$tokensSent"));
    destinations=($(egrep -o "destination={serviceName='x[0-9]'" <<< "$tokensSent"));
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
