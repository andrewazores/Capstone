#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "LocationHelpers.h"
#include "interpreter.h"
#include "logger.h"


//Global variables

VariableProcessMapping** g_all_variables_process_mapping;
int g_all_variables_process_mapping_count;

Conjunct* g_process_conjuncts_mapping;
int g_process_conjuncts_mapping_count;



void load_variable_process_mapping(char* state_process_mapping_file)
{
	FILE* stream = fopen(state_process_mapping_file, "r");
	int c,newline_count=0;
	while ( (c=fgetc(stream)) != EOF ) {
		if ( c == '\n' )
			newline_count++;
	}
	fclose(stream);
	g_all_variables_process_mapping=malloc(newline_count*sizeof(VariableProcessMapping*));
	stream = fopen(state_process_mapping_file, "r");
	char line[1024];
	char *ptr; //points to the unparsed parts of the string, ignore
	int i=0;
	while (fgets(line, 1024, stream))
	{
		VariableProcessMapping* local_state=malloc(sizeof(VariableProcessMapping));
		char* tmp = strdup(line);
		local_state->state_name=(char*) malloc(4);
		strcpy(local_state->state_name, get_field(tmp, 1));
		tmp = strdup(line);
		local_state->process_rank=atoi(get_field(tmp, 2));
		g_all_variables_process_mapping[i]=local_state;
		i++;
	}
	g_all_variables_process_mapping_count=newline_count;
}

int get_process_rank_by_variable(char*variable)
{
	int i;
	for(i=0;i< g_all_variables_process_mapping_count;i++)
	{
		if(strcmp(g_all_variables_process_mapping[i]->state_name,variable)==0)
		{
			return  g_all_variables_process_mapping[i]->process_rank;
		}

	}
	//fprintf("Error at:get_process_rank_by_state:%d",my_rank);
	exit(1);
	return -1;
}
ProcessState** load_initial_state(char* file, int process_count)
{

	ProcessState ** process_state= malloc(process_count*sizeof(ProcessState*));
	int i;
	for (i=0;i<process_count;i++)
	{
		process_state[i]=malloc(sizeof(ProcessState));
		process_state[i]->process_rank=i;
		process_state[i]->state_id=0;
		process_state[i]->variables.count=0;
		process_state[i]->vector_clock=malloc(sizeof(int)*process_count);
		int j;
		for (j=0;j<process_count;j++)
		{
			process_state[i]->vector_clock[j]=0;
		}
	}
	FILE*  stream = fopen( file, "r");
	char line[1024];
	char *ptr; //points to the unparsed parts of the string, ignore
	i=0;
	/*
	 * sample file:
	 * 1,a,5.1
	 * 1,b,6.5
	 * 2,c,0
	 */
	while (fgets(line, 1024, stream))
	{
		char* tmp = strdup(line);
		if(tmp==NULL)
		{
			break;
		}
		int process_rank=atoi(get_field(tmp, 1));
		tmp = strdup(line);
		int count=process_state[process_rank]->variables.count;
		strcpy(process_state[process_rank]->variables.state_name_array[count], get_field(tmp, 2));
		char *ptr;
		tmp = strdup(line);
		double value=strtod(get_field(tmp, 3),&ptr);
		//LOG_PRINT("loading initial values: %s = %d", process_state[process_rank]->variables.state_name_array[count],value);
		process_state[process_rank]->variables.state_value_array[count]=value;
		process_state[process_rank]->variables.count++;

	}
	LOG_PRINT("initial values loaded");
	return process_state;
}


void load_conjunct_process_mapping(char* file)
{
	LOG_PRINT("Opened file:%s",file);
	FILE* stream = fopen(file, "r");
	int c,newline_count=0;
	while ( (c=fgetc(stream)) != EOF ) {
		if ( c == '\n' )
			newline_count++;
	}
	fclose(stream);
	g_process_conjuncts_mapping=(Conjunct*) malloc(newline_count*sizeof(Conjunct));
	stream = fopen(file, "r");
	char line[1024];
	char *ptr; //points to the unparsed parts of the string, ignore
	int i=0;
	while (fgets(line, 1024, stream))
	{

		Conjunct conjunct;
		char* tmp = strdup(line);
		if(tmp==NULL)
		{
			break;
		}
		strcpy(conjunct.conjunct_name, get_field(tmp, 1));
		tmp = strdup(line);
		int j=2;
		while(get_field(tmp, j)!= NULL)
		{
			tmp = strdup(line);
			conjunct.owner_processes[j-2]=atoi(get_field(tmp, j));
			tmp = strdup(line);
			j++;
		}
		conjunct.owner_processes_count=j-2;
		g_process_conjuncts_mapping[i]=conjunct;
		i++;

	}
	g_process_conjuncts_mapping_count=newline_count;
}

int startsWith(const char *pre, const char *str)
{
	size_t lenpre = strlen(pre),
			lenstr = strlen(str);
	return lenstr < lenpre ? 0 : strncmp(pre, str, lenpre) == 0;
}

int endsWith(const char *str, const char *suffix)
{
	if (!str || !suffix)
		return 0;
	size_t lenstr = strlen(str);
	size_t lensuffix = strlen(suffix);
	if (lensuffix >  lenstr)
		return 0;
	return strncmp(str + lenstr - lensuffix, suffix, lensuffix) == 0;
}
void log_variablevaluation(VariableValuation* variables_array, int process_rank)
{
	int i;
	for(i=0;i<variables_array->count;i++)
	{
		LOG_PRINT("C%i: %s=%d",process_rank,variables_array->state_name_array[i],variables_array->state_value_array[i]);
	}
}
int satisfies(Conjunct* conjunct, VariableValuation*  p_first,int* unsatisfying_processes_boolean_array,int process_rank)
{
	//	log_variablevaluation(variables_array, process_rank);
	//LOG_PRINT("C%i: satisfies: conjunct:%s",process_rank,conjunct->conjunct_name  );

	//else if conjunct is simple 1
	char*tmp = "1";
	if(strcmp(conjunct->conjunct_name,tmp)==0)
	{
		//LOG_PRINT("C%i: satisfies: conjunct:%s, satisfied!!",process_rank,conjunct->conjunct_name  );

		return 1;
	}

	//else if conjunct simply maps to a variable.

	//Variables with single owner (direct mapping to conjuncts): cp1, idr1,cp',idr1',....
	//VariableValuation p_first=variables_array[conjunct->owner_processes[0]];
	int i;
	LOG_PRINT("C%i: satisfies: p_first->count:%i",process_rank,p_first->count  );
	for(i=0;i<p_first->count;i++)
	{
		LOG_PRINT("C%i: satisfies: checking p_first[%i]= %s, against %s",process_rank,i,p_first->state_name_array[i],conjunct->conjunct_name );
		if(startsWith(p_first->state_name_array[i],conjunct->conjunct_name)==1)
		{
			tmp="'";// now check for negation ex: idr1'
			if(endsWith(conjunct->conjunct_name,tmp))
			{
				if( p_first->state_value_array[i]==0)
				{
					LOG_PRINT("C%i: satisfies: conjunct:%s, satisfied!!",process_rank,conjunct->conjunct_name  );

					return 1;
				}
				else
				{
					if(unsatisfying_processes_boolean_array!=NULL)
					{
						LOG_PRINT("C%i: conjunct:%s, unsatisfied, setting sat of C%i to 0:(",process_rank,conjunct->conjunct_name, conjunct->owner_processes[0]  );

						unsatisfying_processes_boolean_array[ conjunct->owner_processes[0]]=1;
					}
					return 0;
				}
			}
			else
			{
				if( p_first->state_value_array[i]==1)
				{
					LOG_PRINT("C%i: satisfies: conjunct:%s, satisfied!!",process_rank,conjunct->conjunct_name  );

					return 1;
				}
				else
				{
					if(unsatisfying_processes_boolean_array!=NULL)
					{
						LOG_PRINT("C%i: conjunct:%s, unsatisfied, setting sat of C%i to 0:(",process_rank,conjunct->conjunct_name, conjunct->owner_processes[0]  );

						unsatisfying_processes_boolean_array[ conjunct->owner_processes[0]]=1;
					}
					return 0;
				}
			}
		}
	}
	LOG_PRINT("C%i: satisfies: conjunct:%s, variable not found!!!",process_rank,conjunct->conjunct_name  );
	if(unsatisfying_processes_boolean_array!=NULL)
	{
		unsatisfying_processes_boolean_array[ conjunct->owner_processes[0]]=1;
	}

	//variable not found!
	return 0;
}

void set_unsatisfying_array_for_shared_conjugates(Conjunct* conjunct,int* unsatisfying_processes_boolean_array,int process_rank)
{
	int i;
	for(i=0;i<conjunct->owner_processes_count;i++)
	{
		if(conjunct->owner_processes[i]!=process_rank)
		{
			unsatisfying_processes_boolean_array[conjunct->owner_processes[i]]=1;
			//LOG_PRINT("C%i: setting P%i as unsatisfying process for %s",process_rank,conjunct->owner_processes[i],conjunct->conjunct_name  );

		}
	}
}
Conjunct* get_conjunct_by_name(char* name, int process_rank)
{
	int i;
	//LOG_PRINT("C%i: looking for conjunct:%s, conjuncts count:%i",process_rank,name,g_process_conjuncts_mapping_count);
	for(i=0;i<g_process_conjuncts_mapping_count;i++)
	{
		//LOG_PRINT("C%i: comparing:g_process_conjuncts_mapping[%i].conjunct_name:%s and name:%s",process_rank,i,g_process_conjuncts_mapping[i].conjunct_name,name);

		if(strcmp(name,g_process_conjuncts_mapping[i].conjunct_name)==0)
		{
			return &(g_process_conjuncts_mapping[i]);
		}
	}
	LOG_PRINT("C%i: get_conjunct_by_name returned NULL ",process_rank);
	return NULL;
}

double get_variable_value(VariableValuation process_variables,  char* variable_name)
{
	int i;
	for(i=0;i<process_variables.count;i++)
	{
		if(strcmp(process_variables.state_name_array[i],variable_name)==0)
		{
			return process_variables.state_value_array[i];
		}
	}
	printf("error, variable not found: %s",variable_name);
	LOG_PRINT("C: ERRRORRR, variable not found: %s",variable_name);
	return -1;
}

