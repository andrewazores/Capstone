#ifndef BASE_TYPES_H
#define BASE_TYPES_H

#define MAX_PENDING_EVENTS_COUNT 1000
#define MAX_WAITING_TOKENS_COUNT 100
#define MAX_GLOBAL_STATES_COUNT 200
#define MAX_TOKENS_PER_GLOBALSTATE_COUNT 100
#define MAX_AUTOMATON_STATE_NAME_LEN 4
#define MAX_VARIABLE_NAME_LEN 32
#define MAX_VARIABLES_PER_PROCESS 8
#define MAX_PROCESSES_COUNT 16
#define MAX_CONJUCATE_LEN 32
#define MAX_CONJUNCTS_PER_PREDICATE 20
#define MAX_PREDICATES_PER_PROCESS 10
//#define MAX_PROCESS_STATES_LEN ((MAX_STATES_PER_PROCESS*MAX_PROCESS_STATE_NAME_LEN)+MAX_STATES_PER_PROCESS); //including the &
//#define MAX_AUTOMATON_PRED_LEN ((MAX_STATES_PER_PROCESS*MAX_PROCESSES_COUNT*MAX_PROCESS_STATE_NAME_LEN)+(MAX_STATES_PER_PROCESS*MAX_PROCESSES_COUNT))

typedef struct {
	char conjunct_name[MAX_CONJUCATE_LEN];
	int owner_processes[MAX_PROCESSES_COUNT];
	int owner_processes_count;
} Conjunct;

typedef struct {
	Conjunct conjuncts[MAX_CONJUNCTS_PER_PREDICATE];
	int conjunct_count;
} Predicate;

typedef struct {
	char* state_name;
	int process_rank;
} VariableProcessMapping;

typedef struct {
	int count; //4
	char state_name_array[MAX_VARIABLES_PER_PROCESS][MAX_VARIABLE_NAME_LEN]; //8*32
	int state_value_array[MAX_VARIABLES_PER_PROCESS]; // assume that values are max 8 bytes //8*8

} VariableValuation;

typedef struct {
	int process_rank;
	int state_id;
	int timestamp;
	int requires_processessing;
	VariableValuation variables;
	int* vector_clock;

} ProcessState;

typedef struct {
	int process_rank; // 4
	int event_id; // 4
	int flag; // 4
	int timestamp; // 4
	VariableValuation variables; //(4+ (8*32*1) + (8*8)
	int vector_clock[MAX_PROCESSES_COUNT]; //4*16
} LocalEvent;

typedef struct {
	char* from;
	char* to;
	Predicate pred;
	int leader;
	char* notification_set;
	int id;
} AutomatonTransition;

typedef struct {
	char* state_name;
	char* type;
} AutomatonState;

typedef struct {
	AutomatonState* states;
	int states_count;
	AutomatonTransition* transitions;
	int transitions_count;

} Automaton;

//This struct size must be static so it can be sent and received easily.
typedef struct {
	int unique_id;
	int transition_id;
	int destination;
	int target_event_id;
	int eval;  //-1: Nil 0:false 1:true
	int parent_process_rank;
	int start_timestamp;
	int end_timestamp;
	int events_till_evaluation;
	int pred_index_in_sendabletoken;
	int* gcut;
	char from_state[MAX_AUTOMATON_STATE_NAME_LEN];
	char to_state[MAX_AUTOMATON_STATE_NAME_LEN];
	Predicate pred;
	VariableValuation* target_process_variables;

} Token;
typedef struct {
	int destination; //the process that the token should check
	int target_process_rank; // the rank of the process to send token to, should be equal to the owner when returning back
	int parent_process_rank;
	int unique_id;
	int target_event_id;
	int owner_globalstate_id;
	int start_timestamp;
	int end_timestamp;
	int events_till_evaluation;
	int predicates_count;
	int gcut[MAX_PROCESSES_COUNT];
	int predicates_eval[10];
	VariableValuation target_process_variables;
	Predicate predicates[10];

} SendableToken;

//
//typedef struct
//{
//	char* state; //separated by &
//	int* vector_clock;
//	int process_rank;
//}LocalEvent;

//typedef struct
//{
//	Token token;
//	LocalEvent* pending_events;
//	int peding_events_count;
//	AutomatonTransition* transition;
//	int state; //-1: pending, 0: false, 1:true
//}Handler;

typedef struct {
	int tokens_count;
	int pending_events_count;
	int params_count;
	int index;
	int unique_id;
	int is_done;
	int* pending_events;
	ProcessState** processes_states_array; // a global state is an array of localstates for each process
	AutomatonState* current_monitor_state;
	int* gcut;
	Token** tokens;

	int* automaton_params;

} GlobalState;
#endif /* BASE_TYPES_H */
