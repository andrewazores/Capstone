
#include "automaton.h"
#include "vector_clocks.h"

void init_centralized_controller(char* initial_state_file, char* automaton_file,
		char* conjunct_mapping_file, int my_rank, int p_count);
void signal_centralized_controller_satisfaction(int);
void signal_centralized_controller_violation(int);
void check_centralized_controller_new_state(int);
void receive_centralized_controller_event(ProcessState* local_state);
ProcessState* convert_centralized_controller_local_event_to_local_state(
		LocalEvent* local_event);
void start_centralized_controller();
void start_centralized_controller_shared_memory();
void create_shared_buffer();
