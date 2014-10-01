#!/bin/bash
export folder="/home/m6mostaf/Dropbox/WaterlooWork/datasets/FlightAware/exp15/-1"
for (( i=3; i<=8; i++ ))
do
	echo $i
	python /home/m6mostaf/Dropbox/WaterlooWork/datasets/FlightAware/prepareExperiment.py $i
	export two=2
	export one=1
	export all=$(($i+$one))
	echo $all
	mkdir $folder/n=$i"_timeline/"
	mpiexec -np $all ADM 120 200 80 1
	cp timeline_*  $folder/n=$i"_timeline/"
	cp log_results.txt $folder/n=$i
	cp log.txt $folder/n=$i"_log.txt"
done
for (( i=3; i<=8 ;i++ ))
do
	echo $i
	#python /home/m6mostaf/Dropbox/WaterlooWork/datasets/FlightAware/plotflights.py $i
done
