#!/bin/bash

IFS=$'\n';
lines=($(ls -1 *.log));

for file in "${lines[@]}"; do
    echo $file;
    tokensSent=($(grep "Sending a token\." $file))
    for ((i = 0; i < ${#tokensSent[@]}; i++)); do
        id=$(egrep -o "uniqueLocalIdentifier=[0-9]+" <<< "${tokensSent[i]}");
        owner=$(egrep -o "owner={serviceName='x[0-9]'" <<< "${tokensSent[i]}");
        destination=$(egrep -o "destination={serviceName='x[0-9]'" <<< "${tokensSent[i]}");
        echo "Sending token{$id, $owner} to $destination}";
        fileToCheck=$(echo $destination | cut -c27-28).log;
        receiveInternal="receiveTokenInternal: Token{$id, $owner"
        receiveToken="Entering receiveToken. Token: Token{$id, $owner"
        if grep -q $receiveInternal $fileToCheck; then
            echo "Token received internally.";
        else
            echo "Token not received internally.";
        fi
        if grep -q $receiveToken $fileToCheck; then
            echo "Token received.";
        else
            echo "Token not received.";
        fi

    done
done
