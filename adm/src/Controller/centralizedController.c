#include "mpi.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "centralizedController.h"
#include "../logger.h"
// shared memory
#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <stdio.h>

int buffer_id;
LocalEvent *buffer_pointer;
key_t buffer_key = 5678;
int buffer_size = sizeof(LocalEvent) * 20;

int buffer_lock_id;
int *buffer_lock_pointer;
key_t buffer_lock_key = 5679;
int buffer_lock_size = sizeof(int);

int buffer_size_id;
int *buffer_size_pointer;
key_t buffer_size_key = 5680;
int buffer_size_size = sizeof(int);
//end shared memory
int cent_signaled = 0;
MPI_Request monitor_send_request;
int first_monitor_send = 1;
GlobalState* global_state;
int my_rank;
int* terminated_processes;
int processes_count;
Automaton g_automaton; //g for global
int messages_count = 0;
void init_centralized_controller(char* initial_state_file, char* automaton_file,
		char* conjunct_mapping_file, int rank, int p_count) {
	create_shared_buffer();
	my_rank = rank;
	LOG_PRINT("starting centralized monitor");
	processes_count = p_count;
	terminated_processes = malloc(sizeof(int) * p_count);
	global_state = malloc(sizeof(GlobalState));
	global_state->gcut = malloc(sizeof(int) * processes_count);
	int i;
	for (i = 0; i < processes_count; i++) {
		global_state->gcut[i] = 0;
		terminated_processes[i] = 0;
	}
	//load initial state from file
	LOG_PRINT("initial file: %s", initial_state_file);
	ProcessState** initial_processes_states_array = load_initial_state(
			initial_state_file, processes_count);
	global_state->processes_states_array = initial_processes_states_array;
	load_conjunct_process_mapping(conjunct_mapping_file);
	load_automaton(automaton_file, &g_automaton, 0);
	global_state->current_monitor_state = get_initial_automaton_state(
			&g_automaton);
}
void create_shared_buffer() {

	// first create buffer, then size of buffer then lock
	/*
	 * We'll name our shared memory segment
	 * "5678".
	 */

	/*
	 * Create the segment.
	 */
	if ((buffer_id = shmget(buffer_key, buffer_size, IPC_CREAT | 0666)) < 0) {
		perror("shmget");
		exit(1);
	}

	/*
	 * Now we attach the segment to our data space.
	 */
	buffer_pointer = (LocalEvent*) shmat(buffer_id, NULL, 0);

	///////////////////////////////////////
	/*
	 * Create the segment.
	 */
	if ((buffer_lock_id = shmget(buffer_lock_key, buffer_lock_size,
			IPC_CREAT | 0666)) < 0) {
		perror("shmget");
		exit(1);
	}

	/*
	 * Now we attach the segment to our data space.
	 */
	buffer_lock_pointer = (int*) shmat(buffer_lock_id, NULL, 0);

	///////////////////////////////////////
	/*
	 * Create the segment.
	 */
	if ((buffer_size_id = shmget(buffer_size_key, buffer_size_size,
			IPC_CREAT | 0666)) < 0) {
		perror("shmget");
		exit(1);
	}

	/*
	 * Now we attach the segment to our data space.
	 */
	buffer_size_pointer = (int*) shmat(buffer_size_id, NULL, 0);

}
void start_centralized_controller_shared_memory() {
	int finalize = 0;
	//LOG_PRINT("C%i: start_controller",my_rank);
	while (1) {
		if (*buffer_lock_pointer == 0 && *buffer_size_pointer > 0) {
			LOG_PRINT("C%i: Reading from shared memory", my_rank);
			(*buffer_lock_pointer) = 1; //lock the buffer
			LocalEvent* local_event = &buffer_pointer[0]; //read the top of the queue
			int i;
			for (i = 0; i < (*buffer_size_pointer) - 1; i++) {
				buffer_pointer[i] = buffer_pointer[i + 1];
			}
			(*buffer_size_pointer)--; //decrement the size
			(*buffer_lock_pointer) = 0; //unlock the buffer
			if (local_event->flag == 1) {
				ProcessState* local_state =
						convert_centralized_controller_local_event_to_local_state(
								local_event);
				LOG_PRINT("C%i: Received a local event # %i from %i", my_rank,
						local_event->event_id, local_event->process_rank);
				LOG_PRINT("C%i: received event:  %s:%i from %i", my_rank,
						local_event->variables.state_name_array[1],
						local_event->variables.state_value_array[1],
						local_event->process_rank);
				receive_centralized_controller_event(local_state);
				messages_count++;
			} else if (local_event->flag == 3) {
				LOG_PRINT("C%i: Received an end program signal from p%i",
						my_rank, local_event->process_rank);
				terminated_processes[local_event->process_rank] = 1;
				int i;

				int all_done = 1;
				for (i = 0; i < processes_count; i++) {
					if (terminated_processes[i] == 0) {
						all_done = 0;
						break;
					}
				}
				if (all_done == 1) {
					finalize = 1;
				}

				if (finalize == 1) {

					LOG_PRINT("C%i: all done, Terminating", my_rank);
					LOG_PRINT_RESULT("messages_sent,%i", messages_count);
					shmctl(buffer_id, IPC_RMID, NULL);
					shmctl(buffer_size_id, IPC_RMID, NULL);
					shmctl(buffer_lock_id, IPC_RMID, NULL);
					break;

				}

			}
		}
	}
}
void start_centralized_controller() {
//	int finalize=0;
//	//LOG_PRINT("C%i: start_controller",my_rank);
//	while(1)
//	{
//		if(cent_signaled==1)
//		{
//			break;
//		}
//		int flag;
//		MPI_Status status;
//		MPI_Iprobe( MPI_ANY_SOURCE, MPI_ANY_TAG, MPI_COMM_WORLD, &flag, &status );
//		if(flag)
//		{
//			if(status.MPI_TAG==1)
//			{
//
//				MPI_Status request;
//				char recvMsg[sizeof(LocalEvent)];
//				MPI_Recv(&recvMsg,sizeof(LocalEvent),MPI_CHAR,status.MPI_SOURCE, status.MPI_TAG, MPI_COMM_WORLD, &request);
//				LocalEvent local_event; //Re-make the struct
//				memcpy(&local_event, recvMsg, sizeof(local_event));
//				ProcessState* local_state=convert_centralized_controller_local_event_to_local_state(&local_event);
//				LOG_PRINT("C%i: Received a local event # %i from P%i",my_rank,local_event.event_id,status.MPI_SOURCE);
//
//				receive_centralized_controller_event(local_state);
//				messages_count++;
//			}
//
//			else if(status.MPI_TAG==3)// end of program signal
//			{
//				MPI_Status request;
//				LOG_PRINT("C%i: Received an end program signal from p%i",my_rank,status.MPI_SOURCE);
//				char recvMsg[5];
//				MPI_Recv(&recvMsg,5,MPI_CHAR,status.MPI_SOURCE, status.MPI_TAG, MPI_COMM_WORLD, &request);
//
//				terminated_processes[status.MPI_SOURCE]=1;
//				int i;
//
//				int all_done=1;
//				for(i=0;i<processes_count;i++)
//				{
//					if(terminated_processes[i]==0)
//					{
//						all_done=0;
//						break;
//					}
//				}
//				if(all_done==1)
//				{
//					finalize=1;
//				}
//
//				if(finalize==1)
//				{
//
//
//					LOG_PRINT("C%i: all done, Terminating",my_rank);
//					LOG_PRINT_RESULT("messages_sent,%i",messages_count);
//					break;
//
//
//				}
//
//			}
//		}
//	}
}
ProcessState* convert_centralized_controller_local_event_to_local_state(
		LocalEvent* local_event) {

	LOG_PRINT("C%i: convert_local_event_to_local_state", my_rank);
	ProcessState* state = malloc(sizeof(ProcessState));
	state->state_id = local_event->event_id;
	state->process_rank = local_event->process_rank;
	int i;
	for (i = 0; i < local_event->variables.count; i++) {
		strcpy(state->variables.state_name_array[i],
				local_event->variables.state_name_array[i]);
		state->variables.state_value_array[i] =
				local_event->variables.state_value_array[i];

	}
	state->variables.count = local_event->variables.count;
	state->vector_clock = (int*) malloc(sizeof(int) * processes_count);
	for (i = 0; i < processes_count; i++) {
		state->vector_clock[i] = local_event->vector_clock[i];
	}
	return state;
}

void receive_centralized_controller_event(ProcessState* local_state) {
//	int i;
//	//	for(i=0;i<processes_count;i++)
//	//	{
//	//		LOG_PRINT("C%i: global_state.gcut[%i]=%i,local_state->vector_clock[%i]=%i",my_rank,i,global_state->gcut[i],i,local_state->vector_clock[i]);
//	//	}
//
//	global_state->gcut[local_state->process_rank]=local_state->vector_clock[my_rank];
//	global_state->processes_states_array[local_state->process_rank]=local_state;
//
//
//
//
//	int* automaton_parameters=malloc(sizeof(int)*processes_count);
//	int j;
//	for (j=0;j<processes_count;j++)
//	{
//		automaton_parameters[j]=j; //parameters stay the same (property hard coded not parametrized)
//	}
//	AutomatonState* new_state=advance_automaton(global_state->processes_states_array,
//			global_state->current_monitor_state->state_name,processes_count,-1,
//			&g_automaton,automaton_parameters,processes_count,NULL);
//	if(new_state!=NULL)
//	{
//		LOG_PRINT("C%i: current global state satisfies a transition, advancing monitor state to %s!",my_rank,new_state->state_name);
//
//
//
//		global_state->current_monitor_state=new_state;
//
//
//
//		check_centralized_controller_new_state(local_state->process_rank);
//		//return;
//	}

}

int signalCentralizedFinalState(char* type, char* final_state_type, char* name,
		double x, double y, int i, int terminate) {
	//send to all other processes informing them of final state
	if (strcmp(type, final_state_type) == 0) {
		LOG_PRINT_RESULT("%i,signal %s,%i,%d,%d", -1, name, i, x, y);
		LOG_PRINT("C%i: signal %s,index:%i,x:%d,y:%d", -1, name, i, x, y);

		if (terminate == 1) {
			int j;
			for (j = 0; j < processes_count; j++) {
				char over[] = "done";
				LOG_PRINT("C%i: Final signal", my_rank);
				MPI_Status status;
				if (first_monitor_send == 0)
					MPI_Wait(&monitor_send_request, &status);
				MPI_Isend(&over, sizeof(over), MPI_CHAR, j, 4,
				MPI_COMM_WORLD, &monitor_send_request);
				first_monitor_send = 0;
			}
			if (cent_signaled == 0) {

				LOG_PRINT_RESULT("%i,Events received- final state,%i", -1,
						messages_count);
				cent_signaled = 1;
			}
		}
		return 1;
	}
	return 0;
}

void check_centralized_controller_new_state(int process_rank) {

	char* tmp = "x";
	double x = get_variable_value(
			global_state->processes_states_array[process_rank]->variables, tmp);
	tmp = "y";
	double y = get_variable_value(
			global_state->processes_states_array[process_rank]->variables, tmp);
	tmp = "i";
	int index = (int) get_variable_value(
			global_state->processes_states_array[process_rank]->variables, tmp);
	tmp = "V";
	char* final_state_type = "Violation";
	signalCentralizedFinalState(global_state->current_monitor_state->type, tmp,
			final_state_type, x, y, index, 1);

	tmp = "S";
	final_state_type = "Satisfaction";
	signalCentralizedFinalState(global_state->current_monitor_state->type, tmp,
			final_state_type, x, y, index, 1);

	tmp = "PV";
	final_state_type = "Possible Violation";
	signalCentralizedFinalState(global_state->current_monitor_state->type, tmp,
			final_state_type, x, y, index, 0);

	tmp = "PS";
	final_state_type = "Possible Satisfaction";
	signalCentralizedFinalState(global_state->current_monitor_state->type, tmp,
			final_state_type, x, y, index, 0);

}

