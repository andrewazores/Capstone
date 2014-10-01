#!/usr/bin/python

import sys
from suds import null, WebFault
from suds.client import Client
import logging
import io
import time
from time import sleep
import datetime
import os
import shutil
from os import listdir
from os.path import isfile, join
from subprocess import call
import shutil
import glob
exp_number= int(sys.argv[1])
specific_i=int(sys.argv[2])
specific_j=int(sys.argv[3])
plot=sys.argv[4]
property_idx=sys.argv[5]
distribution_type=sys.argv[6]
monitoring_type='decent'
if len(sys.argv)>=8:
    monitoring_type=sys.argv[7]
flag=1
sys.path.append("/home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/")
base="/home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving"
exp_directory="%s/exp%s" %(base, exp_number)
if monitoring_type=='decent':
    results_directory="%s/results"%exp_directory
else:
    results_directory="%s/centralized-results"%exp_directory
if not os.path.exists(exp_directory):
    os.makedirs(exp_directory)
if not os.path.exists(results_directory):
    os.makedirs(results_directory)
vals=[(2,2),(3,2),(4,2),(3,3),(3,4)]
if specific_i!=0 and specific_j!=0:
    vals=[(specific_i,specific_j)]
for pair in vals:
    i=pair[0]
    j=pair[1]
    p_count=i*j
##    prop="p_1_goingwest"
##    vars1="p_0_goingwest"
##    vars2="p_1_goingwest"
##    for k in range(2,p_count):
##        prop+="&& p_%s_goingwest " %k
##        vars2+=",p_%s_goingwest" %k
##    ltl_property="[](p_0_goingwest->(p_0_goingwest U (%s)))"% prop
##    print "count:%s \r\n%s \r\n vars1:%s \r\n vars2:%s" %(p_count,ltl_property,vars1,vars2)
    ##/home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/same_property.sh '$property' '$vars1' '$vars2'
##    os.chdir("/home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/")
    #call(["bash", "same_property.sh",ltl_property,vars1,vars2])
    #shutil.copy2("/home/m6mostaf/Dropbox/WaterlooWork/LTLproperties/ltl3tools-0.0.7/exp24/property_first_row_neighbor/automaton_nodecomp_modified.my","/home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/automatons/property1/%s.my"%(i*j))
    
    os.chdir(base)
    call(["python","prepareExperiment_sameproperty.py",str(i),str(j),str(exp_number),"5",property_idx])
    
    
    read_probs=1
    if flag==1:
        read_probs=0
    #    flag=0 
    print "Running %s x %s \r\nread_probs:%s\r\n" %(i,j,read_probs)
    
    timeline_folder="%s/n=%sx%s_timeline"%(results_directory,i,j)
    print timeline_folder 
    try:
        os.makedirs(timeline_folder)
    except:
        pass
    os.chdir("/home/m6mostaf/Dropbox/WaterlooWork/workspace-parallel/ADM/Debug/")
    if monitoring_type=='decent':
        all_p_count=i*j*2
        call(["mpiexec","-np", str(all_p_count),"ADM",str(i),str(j), "0.1", exp_directory, "0", "120", "5", "5", "60", str(read_probs),distribution_type])
    else:
        all_p_count=(i*j)+1
        call(["mpiexec","-np", str(all_p_count),"ADM",str(i),str(j), "0.1", exp_directory, "1", "120", "5", "5", "60", str(read_probs),distribution_type])
    for f in glob.glob(r'timeline*'):                                                                                                                              
        shutil.copy(f , timeline_folder)
    shutil.copy("log_results.txt" ,"%s/n=%sx%s"%(results_directory,i,j))
    shutil.copy("log.txt" ,"%s/n=%sx%s_log.txt"%(results_directory,i,j))
    if plot=='plot':
        print "plotting for %sx%s" %(pair[0],pair[1])
        os.chdir(base)
        call(["python","plot.py",str(pair[0]),str(pair[1]),str(exp_number)])
os.chdir(base)
call(["python","collect_results.py",str(exp_number)])

