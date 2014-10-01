

#include "automaton.h"
//#include "vector_clocks.h"


void init_controller(
		char* initial_state_file,char* automaton_file,char* conjunct_mapping_file, int p_count,int rank,
		int initial_global_states_count, int** automaton_parameters_per_initial_global_state,
		int param_count);

void start_controller( );
void log_process_variables(ProcessState** global_state, int processes_count);

void remove_waiting_token( int j);
void print_waiting_tokens();
void process_event(GlobalState* global_state,ProcessState* local_state);

int process_token(SendableToken* t,ProcessState* local_state,int already_in_waiting_tokens);
void process_pending_events_all();
void receive_event(ProcessState* local_state);

void check_outgoing_transitions(GlobalState* global_state,int*vector_clock);
int is_predicates_equal(Predicate* pred1,Predicate* pred2);
Predicate* parse_predicate_by_process(Predicate* pred,int process_rank);
ProcessState* convert_local_event_to_local_state(LocalEvent* local_event);

void add_new_global_state(GlobalState* new_global_state);

ProcessState* get_local_history_event(int event_id);


GlobalState* init_global_state();

int check_global_state(GlobalState* global_state);
int get_dalay_events(GlobalState* global_state,Token* token);
int check_transition_tokens_state(GlobalState* global_state,Token* token);
int check_gcut_consistent(GlobalState *global_state,Token* token);
int dequeue(int* pending_events_indices,int  count);

void remove_pending_event(int* array, int index, int array_length);

void process_pending_events(GlobalState*  global_state);

int evaluate_token(SendableToken* t,ProcessState* local_state,int already_in_waiting_tokens);

void receive_token(SendableToken* received_token);

void send_token(SendableToken* token, int process_rank);
void send_token_bulk(SendableToken**  tokens, int receiver_rank,int count );
void send_pending_tokens_bulk();
void add_token_to_global_state(GlobalState* global_state,AutomatonTransition* tran,Predicate* pred, int j, int token_unique_id,int pred_index_in_sendabletoken,int target_event_id);
void remove_pending_token_for_sending( int j);
void remove_global_state(GlobalState* global_state);

void delete_tokens(GlobalState* global_state);

int signalFinalState(GlobalState * new_global_state,char* type,char* final_state_type,char* name,double x, double y,int transition,int delay_time,int delay_events,char* all,int terminate, int i,int events_so_far);

void check_new_state(GlobalState* ,Token* t ,GlobalState* owner_global_state ,int delay_time,int delay_events );

int   get_dalay(GlobalState* global_state,Token* token);
ProcessState**   copy_processes_states_deep(ProcessState** source ,int processes_count);

//int  is_consistent(GlobalState *global_state,int len,int* arr);

void free_process_states(ProcessState** process_states);

int get_global_state_by_token_id(int token_id,GlobalState** global_state,Token** token);
void process_waiting_tokens(ProcessState* local_state);


void merge_similar_global_states();
