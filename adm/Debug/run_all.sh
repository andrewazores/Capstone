#!/bin/bash
make all
python run_exp.py 100 0 0 noplot 1 normal decent
python run_exp.py 101 0 0 noplot 1 normal decent
python run_exp.py 102 0 0 noplot 1 normal decent

python run_exp.py 200 0 0 noplot 2 normal decent
python run_exp.py 201 0 0 noplot 2 normal decent
python run_exp.py 202 0 0 noplot 2 normal decent

python run_exp.py 300 0 0 noplot 3 normal decent
python run_exp.py 301 0 0 noplot 3 normal decent
python run_exp.py 302 0 0 noplot 3 normal decent
################################################# 
python run_exp.py 103 0 0 noplot 1 poisson decent
python run_exp.py 104 0 0 noplot 1 poisson decent
python run_exp.py 105 0 0 noplot 1 poisson decent

python run_exp.py 203 0 0 noplot 2 poisson decent
python run_exp.py 204 0 0 noplot 2 poisson decent
python run_exp.py 205 0 0 noplot 2 poisson decent

python run_exp.py 303 0 0 noplot 3 poisson decent
python run_exp.py 304 0 0 noplot 3 poisson decent
python run_exp.py 305 0 0 noplot 3 poisson decent
