
#include "Controller/base_types.h"

extern double safety_radius;
extern double radar_radius;
extern double danger_radius;

int get_process_rank_by_state(char*state);
ProcessState** load_initial_state(char* file, int process_count);
void load_variable_process_mapping(char* state_process_mapping_file);
int local_state_satisfies_pred(ProcessState local_state,Predicate pred);


void load_conjunct_process_mapping(char* file);

int satisfies(Conjunct* conjunct, VariableValuation* variable,int* unsatisfying_processes_boolean_array,int process_rank);

void set_unsatisfying_array_for_shared_conjugates(Conjunct *conjunct,int* unsatisfying_processes_boolean_array,int process_rank);

Conjunct* get_conjunct_by_name(char* name,int p);

double get_variable_value(VariableValuation process_variables,  char* variable_name);

void log_variablevaluation(VariableValuation* variables_array, int process_rank);

