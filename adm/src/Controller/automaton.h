#include "utilities.h"
#include "vector_clocks.h"

void load_automaton(char* file_name,Automaton* g_automaton ,int process_rank );

AutomatonState* advance_automaton(GlobalState* global_state,char* current_state_name,
		int processes_count,int process_rank, Automaton* g_automaton, int* automaton_params,
		int params_count);

AutomatonState* get_initial_automaton_state(Automaton* g_automaton);

int could_be_satisfied_by(GlobalState* global_state,Predicate*pred,
		int* unsatisfying_processes_boolean_array,int process_rank,int processes_count
		,int*automaton_params,int params_count);
int global_state_satisfies_pred(GlobalState* global_state, Predicate* pred,
		int* unsatisfying_processes_boolean_array,int process_rank,int processes_count);
int token_satisfies_pred(SendableToken* t, int process_rank);

AutomatonState* get_automaton_state_by_name(char* name,Automaton* g_automaton);
Predicate*   deparametrize_pred(Predicate* pred,int* automaton_params,int params_count,int rank);
