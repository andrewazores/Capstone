#!/bin/bash

export exp=18
PATH=$PATH:"/home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/"
export PATH
#export property="[](p_0_goingwest -> (<>(p_1_goingwest && p_2_goingwest && p_3_goingwest&& p_4_goingwest&& p_5_goingwest&& p_6_goingwest&& p_7_goingwest&& p_8_goingwest))) "
#export property="[](p_0_goingwest->(p_0_goingwest U (p_2_goingwest && p_1_goingwest && p_3_goingwest&& p_4_goingwest)))"
export property="[]((!p_2_goingwest U (p_0_goingwest && p_1_goingwest))&& X (!p_3_goingwest  U p_2_goingwest))"


export vars1="p_0_goingwest,p_1_goingwest" 
export vars2="p_2_goingwest,p_3_goingwest,p_4_goingwest,p_5_goingwest,p_6_goingwest,p_7_goingwest,p_8_goingwest" 
cmd="/home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/same_property.sh '$property' '$vars1' '$vars2'"
(cd /home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/ && eval $cmd)
export p=1
for((k=1;k<=0;k++)) # exp replications.
do
	export folder="/home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/exp"$exp"/results"
	export exp_folder="/home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/exp"$exp
	export flag=1
	for (( i=3; i<=$p; i++ ))
	do
		for((j=3; j<=$p;j++))
		do
			echo $i
			mkdir $exp_folder
			mkdir $folder
			python /home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/prepareExperiment_sameproperty.py $i $j $exp 5
			export two=2
			export all=$(($i*$j*$two))
			echo $all
			mkdir $folder"/n="$i"x"$j"_timeline/"
			echo "running for "$i"x"$j
			export one=1
			export read_probs=1
			if [ "$flag" -eq $one ] 
				then 
					export read_probs=0
					export flag=0
			fi
			echo $read_probs
			mpiexec -np $all ADM $i $j 0.1 $exp_folder 0 120 5 5 60 $read_probs
			cp timeline_*  $folder"/n="$i"x"$j"_timeline/"
			cp log_results.txt $folder"/n="$i"x"$j
			cp log.txt $folder"/n="$i"x"$j"_log.txt"
		done
	done
	
	
	python /home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/collect_results.py  $exp 
	for (( i=2; i<=4 ;i++ ))
	do
		for((j=2; j<=4;j++))
		do
			echo "plotting for "$i"x"$j
			python /home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/plot.py $i $j $exp
		done
	done
	export one=1
	export exp=$(($exp+$one)) 
done
