#!/bin/sh

testCaseFolder=$1;
monitorInit='/mnt/sdcard/monitorInit/'
nfcInit='/mnt/sdcard/nfcInit/'

adb push $testCaseFolder/automaton.json $monitorInit
adb push $testCaseFolder/conjunct_mapping.my $monitorInit
adb push $testCaseFolder/initial_state.json $monitorInit
adb push $testCaseFolder/numPeers $monitorInit

adb push $testCaseFolder/destinationList.txt $nfcInit
adb push $testCaseFolder/destinations.txt $nfcInit
