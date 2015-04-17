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
        tokenSentHome=$(grep "Sending a token back home\. Token: Token{$id, $owner" $fileToCheck)
        if [[ $tokenSentHome ]]; then
            echo "Sending a token home. Token{$id, $owner} to $destination}";
            fileToCheck=$(echo $owner | cut -c21-22).log;
            echo $fileToCheck
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
        else
            echo "Token was not sent home!"
        fi
        echo -e "\n"
    done
done
