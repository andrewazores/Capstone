#include "mpi.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "controller.h"
#include "../logger.h"
#include <pthread.h>
#include <limits.h>

//Global Variables
int my_rank;
int processes_count;

GlobalState** global_states_array;
int global_states_count;

ProcessState** local_state_history;
int g_local_state_history_count;

SendableToken** g_waiting_tokens;
int g_waiting_tokens_count;

int token_ids_count = 0;
int global_states_ids_count = 0;
int* terminated_controllers;

Automaton g_automaton; //g for global

MPI_Request bulk_tokens_request;
int first_bulk_send = 1;

MPI_Request send_token_request;
int first_token_send = 1;
int messages_sent = 0;
SendableToken** pending_tokens_for_sending;
int pending_tokens_for_sending_count;
int signaled = 0;
int events_received = 0;

void init_controller(char* initial_state_file, char* automaton_file, char* conjunct_mapping_file, int p_count, int rank,
        int initial_global_states_count, int** automaton_parameters_per_initial_global_state, int param_count) {

    pending_tokens_for_sending = malloc(sizeof(SendableToken*) * 2000);
    pending_tokens_for_sending_count = 0;

    processes_count = p_count;
    terminated_controllers = malloc(sizeof(int) * p_count);
    my_rank = rank;

    int* gcut = malloc(processes_count * sizeof(int));
    int i;
    for (i = 0; i < processes_count; i++) {
        gcut[i] = 0;
        terminated_controllers[i] = 0;
    }

    // array for all possible Global states
    global_states_array = malloc(MAX_GLOBAL_STATES_COUNT * sizeof(GlobalState*));
    global_states_count = 0;

    //array for local event history for current process
    local_state_history = malloc(1000 * processes_count * sizeof(ProcessState*));
    g_local_state_history_count = 0;

    // Array for waiting tokens from other processes
    g_waiting_tokens = malloc(MAX_WAITING_TOKENS_COUNT * sizeof(SendableToken*));
    g_waiting_tokens_count = 0;

    // Array holding the mapping of all the possible states for each process
    //load_variable_process_mapping(state_process_mapping_file);
    //load initial state from file
    ProcessState** initial_processes_states_array = load_initial_state(initial_state_file, processes_count);
    local_state_history[0] = initial_processes_states_array[my_rank];
    g_local_state_history_count = 1;
    //	LOG_PRINT("C%i: Logging initial values!",-1);
    //log_variablevaluation(&(initial_processes_states_array[0]->variables),-1);
    //log_variablevaluation(&(initial_processes_states_array[1]->variables),-1);
    //load conjuncts mapping file
    load_conjunct_process_mapping(conjunct_mapping_file);
    load_automaton(automaton_file, &g_automaton);

    //Get automaton initial state q_0
    AutomatonState* q_0 = get_initial_automaton_state(&g_automaton);
    LOG_PRINT("C%i: Initialization complete, initial monitor state:%s", my_rank, q_0->state_name);

    for (i = 0; i < initial_global_states_count; i++) {
        // for each initial global state with automaton params,
        //check if initial global state can trigger a transition with the given params

        GlobalState* global_state = init_global_state();
        global_state->automaton_params = automaton_parameters_per_initial_global_state[i];
        global_state->params_count = param_count;

        global_state->gcut = copy_gcut_deep(gcut, processes_count);
        free_process_states(global_state->processes_states_array);
        global_state->processes_states_array = copy_processes_states_deep(initial_processes_states_array,
                processes_count);

        add_new_global_state(global_state);
        AutomatonState* new_automaton_state = advance_automaton(global_state, q_0->state_name, processes_count, my_rank,
                &g_automaton, automaton_parameters_per_initial_global_state[i], param_count);
        if (new_automaton_state != NULL) {
            LOG_PRINT("C%i: Initial global state satisfies a transition, new monitor state:%s", my_rank,
                    new_automaton_state->state_name);

            global_state->current_monitor_state = new_automaton_state;
            check_new_state(global_state, NULL, NULL, -1, -1);
        } else {
            global_state->current_monitor_state = q_0;
        }
    }

}

void free_process_states(ProcessState** process_states) {
    return;
    if (process_states) {
        int i;
        for (i = 0; i < processes_count; i++) {
            if (process_states[i]) {
                free(process_states[i]);
            }

        }
        free(process_states);
    }
}

GlobalState* init_global_state() {
    GlobalState* global_state = malloc(sizeof(GlobalState));
    global_state->tokens = malloc(
    MAX_TOKENS_PER_GLOBALSTATE_COUNT * (sizeof(Token*)));
    global_state->gcut = malloc(sizeof(int) * processes_count);
    //global_state->processes_states_array=malloc(sizeof(ProcessState)* processes_count);
    //better to be initialized while copying
    global_state->pending_events = malloc(sizeof(int) * MAX_PENDING_EVENTS_COUNT);
    global_state->tokens_count = global_state->pending_events_count = 0;
    global_state->is_done = 0;
    return global_state;
}

void start_controller() {

    int informed_others_about_termination = 0;
    LOG_PRINT("C%i: start_controller", my_rank);
    while (1) {

        merge_similar_global_states();
        process_pending_events_all();
        send_pending_tokens_bulk();
        int message_pending;
        MPI_Status pending_status;
        //usleep(10);
        //LOG_PRINT("C%i: waiting for next event",my_rank);
        MPI_Iprobe( MPI_ANY_SOURCE, MPI_ANY_TAG, MPI_COMM_WORLD, &message_pending, &pending_status);
        //LOG_PRINT("C%i: prob done",my_rank);
        if (message_pending == 1) {
            //LOG_PRINT("C%i: received something",my_rank);
            if (pending_status.MPI_TAG == 1) // message from my process (event update)
                    {

                MPI_Status request;
                char recvMsg[sizeof(LocalEvent)];
                MPI_Recv(&recvMsg, sizeof(LocalEvent), MPI_CHAR, pending_status.MPI_SOURCE, pending_status.MPI_TAG,
                MPI_COMM_WORLD, &request);
                LocalEvent local_event; //Re-make the struct
                memcpy(&local_event, recvMsg, sizeof(local_event));
                ProcessState* local_state = convert_local_event_to_local_state(&local_event);
                LOG_PRINT("C%i: Received an event: local event # %i from %i, p%i_goingwest:%i", my_rank,
                        local_event.event_id, pending_status.MPI_SOURCE, my_rank,
                        local_event.variables.state_value_array[0]);
                events_received++;
                local_state->requires_processessing = 1;
                receive_event(local_state);

            } else if (pending_status.MPI_TAG == 4) // message from my process (vector clock update)
                    {

                MPI_Status request;
                char recvMsg[sizeof(LocalEvent)];
                MPI_Recv(&recvMsg, sizeof(LocalEvent), MPI_CHAR, pending_status.MPI_SOURCE, pending_status.MPI_TAG,
                MPI_COMM_WORLD, &request);
                LocalEvent local_event; //Re-make the struct
                memcpy(&local_event, recvMsg, sizeof(local_event));
                ProcessState* local_state = convert_local_event_to_local_state(&local_event);
                LOG_PRINT("C%i: Received an event: communication event # %i from %i, p%i_goingwest:%i", my_rank,
                        local_event.event_id, pending_status.MPI_SOURCE, my_rank,
                        local_event.variables.state_value_array[0]);

                local_state->requires_processessing = 0;
                receive_event(local_state);

            } else if (pending_status.MPI_TAG == 2) // single token from a fellow controller
                    {
                MPI_Status request;
                LOG_PRINT("C%i: Received a token from:C%i, size of struct:%i", my_rank,
                        pending_status.MPI_SOURCE - (processes_count), sizeof(SendableToken));
                char recvMsg[sizeof(SendableToken)];
                MPI_Recv(&recvMsg, sizeof(SendableToken), MPI_CHAR, pending_status.MPI_SOURCE, pending_status.MPI_TAG,
                MPI_COMM_WORLD, &request);
                SendableToken* received_token = malloc(sizeof(SendableToken)); //Re-make the struct
                memcpy(received_token, recvMsg, sizeof(SendableToken));
                if (received_token->destination != my_rank && received_token->parent_process_rank != my_rank) {
                    LOG_PRINT(
                            "C%i: INVALID: Received an INVALID token from C%i: destination %i: parent:C%i, token ID:%i",
                            my_rank, (pending_status.MPI_SOURCE - processes_count), received_token->destination,
                            received_token->parent_process_rank, received_token->unique_id);

                }
                //				LOG_PRINT_RESULT("%i,receive_token,%i,%i,%i", my_rank, (pending_status.MPI_SOURCE - processes_count),
                //						received_token->parent_process_rank, received_token->unique_id);
                LOG_PRINT("C%i: Received a token from C%i: parent:C%i, token ID:%i", my_rank,
                        (pending_status.MPI_SOURCE - processes_count), received_token->parent_process_rank,
                        received_token->unique_id);
                receive_token(received_token);
            } else if (pending_status.MPI_TAG == 7) // bulk tokens from a fellow controller
                    {
                MPI_Status request;
                LOG_PRINT("C%i: Received bulk tokens from:C%i", my_rank, pending_status.MPI_SOURCE);
                char recvMsg[sizeof(SendableToken) * 50];
                MPI_Recv(&recvMsg, sizeof(SendableToken) * 50, MPI_CHAR, pending_status.MPI_SOURCE,
                        pending_status.MPI_TAG,
                        MPI_COMM_WORLD, &request);
                int i;
                int count;

                MPI_Get_count(&request, MPI_CHAR, &count);
                count = count / (sizeof(SendableToken));
                SendableToken* received_tokens = malloc(sizeof(SendableToken) * count); //Re-make the struct

                memcpy(received_tokens, recvMsg, sizeof(SendableToken) * count);
                LOG_PRINT("C%i: Received %i bulk tokens from C%i", my_rank, count,
                        (pending_status.MPI_SOURCE - processes_count));
                int k;
                int count_bulk_received = 0;

                for (i = 0; i < count; i++) {
                    LOG_PRINT("C%i: receive_bulk from C%i:parent:C%i destination:%i,unique ID:%i", my_rank,
                            (pending_status.MPI_SOURCE - processes_count), received_tokens[i].parent_process_rank,
                            received_tokens[i].destination, received_tokens[i].unique_id);
                }

                for (k = 0; k < count; k++) {
                    if (received_tokens[k].destination != -1) {
                        if (received_tokens[k].target_process_rank == my_rank) {
                            LOG_PRINT("C%i: Received a token from C%i: parent:C%i, token ID:%i", my_rank,
                                    (pending_status.MPI_SOURCE - processes_count),
                                    received_tokens[k].parent_process_rank, received_tokens[k].unique_id);

                            receive_token(&(received_tokens[k]));

                        } else {
                            LOG_PRINT(
                                    "C%i: INVALID: Received an INVALID token from C%i: destination %i: parent:C%i, token ID:%i",
                                    my_rank, (pending_status.MPI_SOURCE - processes_count),
                                    received_tokens[k].destination, received_tokens[k].parent_process_rank,
                                    received_tokens[k].unique_id);

                        }
                        count_bulk_received++;
                    }
                }
                //	LOG_PRINT("C%i: Received %i bulk tokens from C%i",my_rank,count_bulk_received,(pending_status.MPI_SOURCE-processes_count));
            } else if (pending_status.MPI_TAG == 3) // end of program signal not final states signals
                    {
                MPI_Status request;
                LOG_PRINT("C%i: Received an end program signal", my_rank);

                LocalEvent recvMsg;
                MPI_Recv(&recvMsg, sizeof(LocalEvent), MPI_CHAR, pending_status.MPI_SOURCE, pending_status.MPI_TAG,
                MPI_COMM_WORLD, &request);
                int j;
                for (j = 0; j < global_states_count; j++) {
                    global_states_array[j]->is_done = 1;
                }
                LOG_PRINT_RESULT("%i,[prog-over]Messages sent,%i", my_rank, messages_sent);
                LOG_PRINT_RESULT("%i,all Events received,%i", my_rank, events_received);
                terminated_controllers[my_rank] = 1;
            }

            else if (pending_status.MPI_TAG == 5) //another controller is done.
                    {
                int controller_id = pending_status.MPI_SOURCE - (processes_count);
                terminated_controllers[controller_id] = 1;
                MPI_Status request;
                LOG_PRINT("C%i: Received an end of program signal from controller:C%i", my_rank,
                        (pending_status.MPI_SOURCE - processes_count));
                char recvMsg[5];
                MPI_Recv(&recvMsg, 5, MPI_CHAR, pending_status.MPI_SOURCE, pending_status.MPI_TAG, MPI_COMM_WORLD,
                        &request);
                //LOG_PRINT_RESULT("%i,Messages sent[not final],%i",my_rank,messages_sent);
                if (informed_others_about_termination == 1) {
                    int all_done = 1;
                    int i;
                    for (i = 0; i < processes_count; i++) {
                        if (terminated_controllers[i] == 0) {
                            all_done = 0;
                            LOG_PRINT("C%i: controller C%i is not terminated yet", my_rank, i);
                            break;
                        }
                    }
                    if (all_done == 1) {
                        LOG_PRINT("C%i: all done, Terminating", my_rank);
                        break;
                    }
                }
            } else {
                LOG_PRINT("C%i: Received unknown signal ", my_rank);
            }

        } else {
            //usleep(500);
            //LOG_PRINT("C%i: Nothing to receive",my_rank);
            int all_done = 1;
            int j;
            for (j = 0; j < global_states_count; j++) {
                if (global_states_array[j]->is_done == 0) {
                    all_done = 0;

                    break;
                }
            }
            if (all_done == 1) {
                terminated_controllers[my_rank] = 1;
                if (my_rank == 0) {
                    char over[] = "done";
                    MPI_Status status;

                    if (first_bulk_send == 0 && bulk_tokens_request != MPI_REQUEST_NULL) {
                        MPI_Wait(&bulk_tokens_request, &status);

                    }
                    MPI_Isend(&over, sizeof(over), MPI_CHAR, my_rank, 4,
                    MPI_COMM_WORLD, &bulk_tokens_request);
                }
            }

            if (terminated_controllers[my_rank] == 1) {
                // If I terminated, then send back any waiting tokens for target events more than my last event
                int i;
                //LOG_PRINT("C%i: waiting tokens count:%i",my_rank,g_waiting_tokens_count);
                for (i = 0; i < g_waiting_tokens_count; i++) {
                    if (g_waiting_tokens[i]->target_event_id >= g_local_state_history_count - 1) {
                        LOG_PRINT("C%i: waiting token for further events... returning waiting token to C%i", my_rank,
                                g_waiting_tokens[i]->parent_process_rank);
                        int j;
                        for (j = 0; j < g_waiting_tokens[i]->predicates_count; j++) {
                            g_waiting_tokens[i]->predicates_eval[j] = 0;
                        }
                        g_waiting_tokens[i]->gcut[my_rank] =
                                local_state_history[g_local_state_history_count - 1]->vector_clock[my_rank];
                        g_waiting_tokens[i]->target_process_variables = local_state_history[g_local_state_history_count
                                - 1]->variables;
                        g_waiting_tokens[i]->target_process_rank = g_waiting_tokens[i]->parent_process_rank;
                        send_token(g_waiting_tokens[i], g_waiting_tokens[i]->parent_process_rank);
                        remove_waiting_token(i);
                        i--;
                    }

                }

                send_pending_tokens_bulk();
                int pending_events_exists = 0;
                int m;
                for (m = 0; m < global_states_count; m++) {
                    if (global_states_array[m]->pending_events_count > 0) {
                        pending_events_exists = 1;
                        //LOG_PRINT("C%i: pending events exist",my_rank);
                        break;
                    }
                }

                if (pending_events_exists == 0) //if I have no pending events and the app terminated, return all waiting tokens
                        {

                    LOG_PRINT("C%i: waiting tokens count:%i", my_rank, g_waiting_tokens_count);
                    for (i = 0; i < g_waiting_tokens_count; i++) {
                        LOG_PRINT("C%i: Processed all events... returning waiting token to C%i", my_rank,
                                g_waiting_tokens[i]->parent_process_rank);

                        int j;
                        for (j = 0; j < g_waiting_tokens[i]->predicates_count; j++) {
                            g_waiting_tokens[i]->predicates_eval[j] = 0;
                        }
                        g_waiting_tokens[i]->gcut[my_rank] =
                                local_state_history[g_local_state_history_count - 1]->vector_clock[my_rank];
                        g_waiting_tokens[i]->target_process_variables = local_state_history[g_local_state_history_count
                                - 1]->variables;
                        g_waiting_tokens[i]->target_process_rank = g_waiting_tokens[i]->parent_process_rank;
                        send_token(g_waiting_tokens[i], g_waiting_tokens[i]->parent_process_rank);
                        remove_waiting_token(i);
                        i--;
                    }
                }
                if (pending_events_exists == 0 && pending_tokens_for_sending_count == 0
                        && informed_others_about_termination == 0) {

                    LOG_PRINT("C%i: Will send termination signal to other controllers", my_rank);
                    int i;
                    for (i = 0; i < processes_count; i++) {
                        if (i != my_rank) {
                            char over[] = "done";
                            int controller_id = i + (processes_count);

                            MPI_Status status;

                            if (first_bulk_send == 0 && bulk_tokens_request != MPI_REQUEST_NULL) {
                                MPI_Wait(&bulk_tokens_request, &status);

                            }
                            MPI_Isend(&over, sizeof(over), MPI_CHAR, controller_id, 5,
                            MPI_COMM_WORLD, &bulk_tokens_request);
                            LOG_PRINT("C%i: sent termination signal to c%i", my_rank, i);

                        }
                    }
                    informed_others_about_termination = 1;

                }
                if (informed_others_about_termination == 1) {
                    int all_done = 1;
                    int i;
                    for (i = 0; i < processes_count; i++) {
                        if (terminated_controllers[i] == 0) {
                            all_done = 0;
                            LOG_PRINT("C%i: controller C%i is not terminated yet", my_rank, i);
                            break;
                        }
                    }
                    if (all_done == 1) {
                        LOG_PRINT("C%i: all done, Terminating", my_rank);
                        break;
                    }
                }
            }
        }

    }
    LOG_PRINT("C%i: out of while loop", my_rank);
    if (signaled == 0) {
        LOG_PRINT_RESULT("%i,Messages sent- finished,%i", my_rank, messages_sent);
        LOG_PRINT_RESULT("%i,Events received- finished,%i", my_rank, events_received);
    } else {
        LOG_PRINT_RESULT("%i,[All done]Messages sent- finished,%i", my_rank, messages_sent);
        LOG_PRINT_RESULT("%i,[All done]Events received- finished,%i", my_rank, events_received);
    }

    LOG_PRINT_RESULT("%i,max global states count,%i", my_rank, global_states_ids_count);

    int i;
    for (i = 0; i < global_states_count; i++) {
        LOG_PRINT_RESULT("%i,Global State,%i,%s", my_rank, global_states_array[i]->unique_id,
                global_states_array[i]->current_monitor_state->state_name);
    }
    for (i = 0; i < g_local_state_history_count; i++) {
        if (local_state_history[i])
            free(local_state_history[i]);
    }
    if (local_state_history)
        free(local_state_history);
    for (i = 0; i < global_states_count; i++) {
        if (global_states_array[i]) {
            //remove_global_state (global_states_array[i]);
            free(global_states_array[i]);
        }
    }
    if (global_states_array)
        free(global_states_array);

}

ProcessState* convert_local_event_to_local_state(LocalEvent* local_event) {

    //LOG_PRINT(  "C%i: convert_local_event_to_local_state",my_rank);
    ProcessState* state = malloc(sizeof(ProcessState));
    state->state_id = local_event->event_id;
    state->process_rank = local_event->process_rank;
    int i;
    for (i = 0; i < local_event->variables.count; i++) {
        strcpy(state->variables.state_name_array[i], local_event->variables.state_name_array[i]);
        state->variables.state_value_array[i] = local_event->variables.state_value_array[i];
        //		LOG_PRINT("C%i: original variable:%s = %i",my_rank,local_event->variables.state_name_array[i],local_event->variables.state_value_array[i]);
        //		LOG_PRINT("C%i: copied variable:%s = %i",my_rank,state->variables.state_name_array[i],state->variables.state_value_array[i]);
    }
    state->variables.count = local_event->variables.count;
    state->vector_clock = (int*) malloc(sizeof(int) * processes_count);
    for (i = 0; i < processes_count; i++) {
        state->vector_clock[i] = local_event->vector_clock[i];
    }
    state->timestamp = local_event->timestamp;
    return state;
}
void process_pending_events_all() {
    int i;

    for (i = 0; i < global_states_count; i++) {
        GlobalState* s = global_states_array[i];

        if (s->tokens_count == 0) {
            if (s->pending_events_count != 0) {
                LOG_PRINT("C%i: process_pending_events_all pending events count:%i, calling process_pending_events",
                        my_rank, s->pending_events_count);

                process_pending_events(s);
            }
        } else {
//			LOG_PRINT("C%i: can't process pending events, I have waiting tokens in my global state",my_rank);
//			int j;
//			for (j = 0; j < s->tokens_count; j++)
//			{
//
//				Token* t = (s->tokens[j]);
//
//				LOG_PRINT("C%i: token at global_state[%i].token[%i],token_id:%i token eval:%i, transition:%i,target_event_id:%i, target process:%i, s->pending_event_count:%i",
//						my_rank, i, j, t->unique_id, t->eval, t->transition_id, t->target_event_id, t->destination,
//						s->pending_events_count);
//
//
//			}

        }
    }
}
void process_waiting_tokens(ProcessState* local_state) {

    print_waiting_tokens();
    int i;
    for (i = 0; i < g_waiting_tokens_count; i++) {

        if (g_waiting_tokens[i]->target_event_id == local_state->vector_clock[my_rank]) {
            SendableToken* waiting_token = g_waiting_tokens[i];
            LOG_PRINT("C%i: receive_event: processing waiting_token:%i with target_event_id:%i", my_rank,
                    waiting_token->unique_id, waiting_token->target_event_id);
            //remove the token from the waiting tokens array

            // process token returns 1 if the waiting token was returned to parent
            if (process_token(waiting_token, local_state, 1) == 1) {
                remove_waiting_token(i);
                i--;
            }
        } else {
            LOG_PRINT("C%i: receive_event: received event:%i doesn't match: waiting_token: %i target_event_id=%i",
                    my_rank, local_state->vector_clock[my_rank], g_waiting_tokens[i]->unique_id,
                    g_waiting_tokens[i]->target_event_id);

        }
    }
}
void receive_event(ProcessState* local_state) {
    LOG_PRINT("C%i: receive_event: adding event to local event history", my_rank);

    merge_similar_global_states();
    LOG_PRINT("C%i: receive_event", my_rank);

    local_state_history[g_local_state_history_count] = local_state;
    g_local_state_history_count++;
    //iterate over waiting tokens
    LOG_PRINT("C%i: receive_event: checking waiting token, waiting tokens count:%i", my_rank, g_waiting_tokens_count);

    LOG_PRINT("C%i: receive_event: new waiting tokens count:%i", my_rank, g_waiting_tokens_count);

    LOG_PRINT("C%i: receive_event: checking global states, global states count:%i", my_rank, global_states_count);

    int i;
    process_waiting_tokens(local_state);
    for (i = 0; i < global_states_count; i++) {

        int j;
        GlobalState* s = global_states_array[i];
        if (s->is_done == 1) {
            continue;
        }

        LOG_PRINT("C%i: receive_event: checking global state[%i], state:%s tokens count:%i", my_rank, i,
                global_states_array[i]->current_monitor_state->state_name, s->tokens_count);

        for (j = 0; j < s->tokens_count; j++) {

            Token* t = (s->tokens[j]);
            if (t->eval == -1) {
                LOG_PRINT(
                        "C%i: receive_event: token at global_state[%i].token[%i],token_id:%i token eval:%i, transition:%i,target_event_id:%i, target process:%i, s->pending_event_count:%i",
                        my_rank, i, j, t->unique_id, t->eval, t->transition_id, t->target_event_id, t->destination,
                        s->pending_events_count);

            }
        }

        if (s->tokens_count == 0) {
            if (s->pending_events_count != 0) {
                LOG_PRINT("C%i: receive_event: pending events count:%i, calling process_pending_events", my_rank,
                        s->pending_events_count);
                s->pending_events[s->pending_events_count] = g_local_state_history_count - 1;	//last event
                s->pending_events_count++;
                process_pending_events(s);

            } else {
                LOG_PRINT("C%i: receive_event: no waiting tokens at other processes, calling process_event", my_rank);

                process_event(s, local_state);
            }
        } else {
            s->pending_events[s->pending_events_count] = local_state->state_id;
            s->pending_events_count++;
            LOG_PRINT(
                    "C%i: receive_event: tokens waiting at other process: %i, adding event to pending events for global_state[%i], t->pending_events_count:%i",
                    my_rank, s->tokens_count, i, s->pending_events_count);

        }
    }
    send_pending_tokens_bulk();
}
void advance_automaton_localy(GlobalState * global_state) {
    AutomatonState* new_state = advance_automaton(global_state, global_state->current_monitor_state->state_name,
            processes_count, my_rank, &g_automaton, global_state->automaton_params, global_state->params_count);
    if (new_state != NULL) {
        LOG_PRINT("C%i: current global state satisfies a transition, advancing monitor state to %s!", my_rank,
                new_state->state_name);

        global_state->current_monitor_state = new_state;

        check_new_state(global_state, NULL, NULL, -1, -1);
    }
}

void process_event(GlobalState* global_state, ProcessState* local_state) {

    LOG_PRINT("C%i: processing event#:%i for global_state:%i, p%i_goingwest:%i", my_rank, local_state->state_id,
            global_state->unique_id, my_rank, local_state->variables.state_value_array[0]);

    process_waiting_tokens(local_state);
    if (local_state->requires_processessing == 0) {
        return;
    }
    LOG_PRINT("C%i: process_event #%i", my_rank, local_state->state_id);

    global_state->gcut[my_rank] = local_state->vector_clock[my_rank];
    global_state->processes_states_array[my_rank] = local_state;

    advance_automaton_localy(global_state);

    check_outgoing_transitions(global_state, local_state->vector_clock);

}
ProcessState** copy_processes_states_deep(ProcessState** source, int processes_count) {

    ProcessState** destination = malloc(processes_count * sizeof(ProcessState*));
    int i;
    for (i = 0; i < processes_count; i++) {
        destination[i] = malloc(sizeof(ProcessState));
        destination[i]->process_rank = source[i]->process_rank;
        destination[i]->state_id = source[i]->state_id;
        destination[i]->vector_clock = malloc(sizeof(int) * processes_count);
        destination[i]->variables = source[i]->variables;
        //LOG_PRINT("C%i: new global state.variables.count:%i",my_rank,destination [i]->variables.count);
        int j;
        for (j = 0; j < processes_count; j++) {
            destination[i]->vector_clock[j] = source[i]->vector_clock[j];

        }
    }
    return destination;
}
void log_process_variables(ProcessState** global_state, int processes_count) {
    int i;
    for (i = 0; i < processes_count; i++) {
        int j;
        for (j = 0; j < global_state[i]->variables.count; j++) {
            LOG_PRINT("C%i:P%i: %s=%d ", my_rank, i, global_state[i]->variables.state_name_array[j],
                    global_state[i]->variables.state_value_array[j]);
        }
    }

}
Predicate* parse_predicate_by_process(Predicate* pred, int process_rank) {
    int i;
    Predicate * new_pred = malloc(sizeof(Predicate));
    new_pred->conjunct_count = 0;
    for (i = 0; i < pred->conjunct_count; i++) {
        if (pred->conjuncts[i].owner_processes[0] == process_rank) {
            strcpy(new_pred->conjuncts[new_pred->conjunct_count].conjunct_name, pred->conjuncts[i].conjunct_name);
            new_pred->conjuncts[new_pred->conjunct_count].owner_processes[0] = pred->conjuncts[i].owner_processes[0];
            new_pred->conjuncts[new_pred->conjunct_count].owner_processes_count =
                    pred->conjuncts[i].owner_processes_count;
            new_pred->conjunct_count++;
        }
    }
    return new_pred;
}

int is_predicates_equal(Predicate* pred1, Predicate* pred2) {
    if (pred1->conjunct_count != pred2->conjunct_count) {
        return 0;
    }
    int i;
    for (i = 0; i < pred1->conjunct_count; i++) {
        int j;
        int found = 0;
        for (j = 0; j < pred2->conjunct_count; j++) {
            if (strcmp(pred1->conjuncts[i].conjunct_name, pred2->conjuncts[j].conjunct_name) == 0) {
                found = 1;
            }
        }
        if (found == 0) {
            return 0;
        }
    }
    return 1;
}
void check_outgoing_transitions(GlobalState* global_state, int*vector_clock) {

    int i;
    for (i = 0; i < g_automaton.transitions_count; i++) {
        AutomatonTransition tran = g_automaton.transitions[i];
        if (strcmp(tran.from, global_state->current_monitor_state->state_name) != 0 || strcmp(tran.from, tran.to) == 0) //ignore self loop as well
                {
            continue;
        }
        LOG_PRINT("C%i: check_outgoing_transitions: checking: transition:%i %s to %s", my_rank, tran.id, tran.from,
                tran.to);
        int* unsatisfying_processes_boolean_array = malloc(sizeof(int) * processes_count);
        int k;
        for (k = 0; k < processes_count; k++) {
            unsatisfying_processes_boolean_array[k] = 0;
        }
        Predicate* pred = deparametrize_pred(&(tran.pred), global_state->automaton_params, global_state->params_count,
                my_rank);

        if (could_be_satisfied_by(global_state, pred, unsatisfying_processes_boolean_array, my_rank, processes_count,
                global_state->automaton_params, global_state->params_count) == 1) {
            LOG_PRINT("C%i: check_outgoing_transitions: transition:%i could_be_satisfied_by returned true", my_rank,
                    tran.id);
            int j;
            for (j = 0; j < processes_count; j++) {
                if (j == my_rank || unsatisfying_processes_boolean_array[j] == 0) {
                    continue;
                }
                Predicate* parsed_pred = parse_predicate_by_process(pred, j);
                LOG_PRINT("C%i: process %i: should be checked for transition:%i predicate labelling %s to %s:", my_rank,
                        j, tran.id, tran.from, tran.to);
                //Create tokens with unique (target,gcut, target event id)
                //so first check in pending_tokens_for_sending if there exist a token with such params,
                // and then check if token.predicates contain the target pred, if not add it
                // if exists then link it to Global states.tokens
                // if not then create a new one.
                int target_event_id = global_state->gcut[j] + 1;
                //				if (vector_clock[j] == global_state->gcut[j])
                //					target_event_id++;
                int token_to_same_process_found = 0;
                for (k = 0; k < pending_tokens_for_sending_count; k++) {
                    if ((pending_tokens_for_sending[k]->destination == j
                            && pending_tokens_for_sending[k]->target_process_rank == j)
                            && (pending_tokens_for_sending[k]->parent_process_rank == my_rank)
                            && (global_state->unique_id == pending_tokens_for_sending[k]->owner_globalstate_id)
                            && (pending_tokens_for_sending[k]->target_event_id == target_event_id)) {
                        // check same gcut
                        token_to_same_process_found = 1;
                        int l;
                        for (l = 0; l < processes_count; l++) {
                            if (pending_tokens_for_sending[k]->gcut[l] != vector_clock[l])	//global_state->gcut[l])//
                                    {
                                token_to_same_process_found = 0;
                                break;
                            }
                        }
                        if (token_to_same_process_found == 1) // add my parsed pred to token.predicates array.
                                {
                            int pred_index_in_sendabletoken;
                            LOG_PRINT("C%i: Found a similar token to C%i, id:%i, use it.", my_rank,
                                    pending_tokens_for_sending[k]->target_process_rank,
                                    pending_tokens_for_sending[k]->unique_id);

                            int m;
                            int pred_found = 0;
                            for (m = 0; m < pending_tokens_for_sending[k]->predicates_count; m++) {
                                if (is_predicates_equal(parsed_pred, &(pending_tokens_for_sending[k]->predicates[m]))
                                        == 1) {
                                    LOG_PRINT("C%i: also Found my parsed pred in the token, id:%i, use it.", my_rank,
                                            pending_tokens_for_sending[k]->unique_id);
                                    pred_found = 1;
                                    pred_index_in_sendabletoken = m;
                                    break;
                                }
                            }
                            if (pred_found == 0) {
                                pending_tokens_for_sending[k]->predicates[pending_tokens_for_sending[k]->predicates_count] =
                                        *parsed_pred;
                                pending_tokens_for_sending[k]->predicates_eval[pending_tokens_for_sending[k]->predicates_count] =
                                        -1;
                                pred_index_in_sendabletoken = pending_tokens_for_sending[k]->predicates_count;
                                pending_tokens_for_sending[k]->predicates_count++;
                                LOG_PRINT("C%i: token has now %i predicates!", my_rank,
                                        pending_tokens_for_sending[k]->predicates_count);
                            }
                            //void add_token_to_global_state(GlobalState* global_state,AutomatonTransition* tran,Predicate* pred, int j, int token_unique_id,int pred_index_in_sendabletoken)
                            add_token_to_global_state(global_state, &tran, pred, j,
                                    pending_tokens_for_sending[k]->unique_id, pred_index_in_sendabletoken,
                                    target_event_id);
                            break;
                        }
                    }
                }
                if (token_to_same_process_found == 0 || pending_tokens_for_sending_count == 0) {
                    SendableToken* sendableToken = malloc(sizeof(SendableToken));
                    sendableToken->predicates_eval[0] = -1;
                    sendableToken->predicates[0] = *parsed_pred;
                    sendableToken->predicates_count = 1;
                    int k;
                    for (k = 0; k < processes_count; k++) {
                        sendableToken->gcut[k] = vector_clock[k]; //global_state->gcut[k];				//global_state->gcut[k];//
                    }
                    sendableToken->owner_globalstate_id = global_state->unique_id;
                    sendableToken->target_event_id = target_event_id;
                    //sendableToken->start_timestamp=global_state->processes_states_array[my_rank]->timestamp;
                    sendableToken->parent_process_rank = my_rank;
                    sendableToken->unique_id = token_ids_count;
                    sendableToken->destination = j;
                    sendableToken->target_process_rank = j;

                    pending_tokens_for_sending[pending_tokens_for_sending_count] = sendableToken;
                    pending_tokens_for_sending_count++;
                    add_token_to_global_state(global_state, &tran, pred, j, token_ids_count, 0, target_event_id);
                    LOG_PRINT("C%i: Creating a token to C%i, id:%i targeting event: %i", my_rank, j,
                            sendableToken->unique_id, sendableToken->target_event_id);

                    token_ids_count++;
                }
            }
        } else {
            LOG_PRINT("C%i: tran:%i Could be satisfied returned false", my_rank, tran.id);
        }
    }

    return;

}
void add_token_to_global_state(GlobalState* global_state, AutomatonTransition* tran, Predicate* pred, int j,
        int token_unique_id, int pred_index_in_sendabletoken, int target_event_id) {
    global_state->tokens[global_state->tokens_count] = malloc(sizeof(Token));
    global_state->tokens[global_state->tokens_count]->eval = -1;
    strcpy(global_state->tokens[global_state->tokens_count]->from_state, tran->from);
    strcpy(global_state->tokens[global_state->tokens_count]->to_state, tran->to);
    global_state->tokens[global_state->tokens_count]->transition_id = tran->id;
    global_state->tokens[global_state->tokens_count]->pred = *(pred);
    //global_state->tokens[global_state->tokens_count]->gcut=copy_gcut_deep(global_state->gcut,processes_count);

    global_state->tokens[global_state->tokens_count]->target_event_id = target_event_id;
    global_state->tokens[global_state->tokens_count]->start_timestamp =
            global_state->processes_states_array[my_rank]->timestamp;
    global_state->tokens[global_state->tokens_count]->pred_index_in_sendabletoken = pred_index_in_sendabletoken;
    global_state->tokens[global_state->tokens_count]->parent_process_rank = my_rank;
    global_state->tokens[global_state->tokens_count]->unique_id = token_unique_id;
    global_state->tokens[global_state->tokens_count]->destination = j;
    global_state->tokens_count++;
}
void remove_pending_token_for_sending(int j) {
    LOG_PRINT("C%i: remove_pending_token_for_sending: removing   token id:%i, target process:%i of index:%i", my_rank,
            pending_tokens_for_sending[j]->unique_id, pending_tokens_for_sending[j]->target_process_rank, j);

    int i;
    for (i = j; i < pending_tokens_for_sending_count - 1; i++) {
        pending_tokens_for_sending[i] = pending_tokens_for_sending[i + 1];
    }
    pending_tokens_for_sending_count--;
}
void send_pending_tokens_bulk() {

    if (pending_tokens_for_sending_count == 0) {
        //	LOG_PRINT("C%i: send_tokens_bulk, I have %i tokens to send for this step, RETURNING",my_rank,c );

        return;
    }

    int j, i;

    int flag = 1;
    while (flag == 1) {
        MPI_Status status;

        if (first_bulk_send == 0 && bulk_tokens_request != MPI_REQUEST_NULL) {
            MPI_Test(&bulk_tokens_request, &flag, &status);
            if (flag == 0) {
                //LOG_PRINT("C%i: buffer not safe yet",my_rank);
                return;
            }
        }
        j = pending_tokens_for_sending[0]->target_process_rank; //to avoid starvation
        if (my_rank == j) {
            return;
        }
        LOG_PRINT("C%i: send_tokens_bulk, I have %i tokens to send for this step", my_rank,
                pending_tokens_for_sending_count);

        LOG_PRINT("C%i: send_token_bulk, preparing for C%i", my_rank, j);
        SendableToken** tokens_per_process = malloc(sizeof(SendableToken*) * 10);
        int tokens_per_process_count = 0;

        for (i = 0; i < pending_tokens_for_sending_count; i++) {
            if (pending_tokens_for_sending[i]->target_process_rank == j) {
                LOG_PRINT("C%i: adding t.id:%i, t.targetprocessrank:%i", my_rank,
                        pending_tokens_for_sending[i]->unique_id, pending_tokens_for_sending[i]->target_process_rank);
                SendableToken* t = (pending_tokens_for_sending[i]);

                if (tokens_per_process_count >= 10) {
                    LOG_PRINT("C%i: send_tokens_bulk, exceeding 50 tokens per process.", my_rank);

                    break;
                }
                remove_pending_token_for_sending(i);
                i--;
                tokens_per_process[tokens_per_process_count] = t;
                tokens_per_process_count++;
                LOG_PRINT("C%i: New count:%i. i:%i", my_rank, pending_tokens_for_sending_count, i);

            }
        }

        if (tokens_per_process_count != 0) {
            LOG_PRINT("C%i: token per process ptr:%i, pred count:%i,pred[0]:%s", my_rank, tokens_per_process,
                    tokens_per_process[0]->predicates_count,
                    tokens_per_process[0]->predicates[0].conjuncts[0].conjunct_name);
            send_token_bulk(tokens_per_process, j, tokens_per_process_count);

        }
        first_bulk_send = 0;

    }

}
void send_token_bulk(SendableToken** tokens_ptr, int receiver_rank, int count) {
    LOG_PRINT("C%i: send_token_bulk to C%i, count:%i", my_rank, receiver_rank, count);
    messages_sent++;
    SendableToken * tokens = malloc(sizeof(SendableToken) * count);
    int i;
    for (i = 0; i < count; i++) {

        tokens[i] = *(tokens_ptr[i]);
        if (receiver_rank != tokens[i].target_process_rank) {
            LOG_PRINT("C%i: send_token_bulk: INVALID SEND", my_rank);
        }
        LOG_PRINT("C%i: send_token_bulk:sending to:%i , destination:%i,unique ID:%i, owner:%i", my_rank,
                (receiver_rank), tokens[i].target_process_rank, tokens[i].unique_id, tokens[i].parent_process_rank);
        //		LOG_PRINT_RESULT("%i,send_token,%i,%i,%i", my_rank, receiver_rank, tokens[i].parent_process_rank,
        //				tokens[i].unique_id);

    }
    char b[sizeof(SendableToken) * 100];
    memcpy(b, tokens, sizeof(SendableToken) * count);

    MPI_Isend(&b, sizeof(SendableToken) * count, MPI_CHAR, (receiver_rank + processes_count), 7, MPI_COMM_WORLD,
            &bulk_tokens_request);

    //	for(i=0;i<count;i++)
    //	{
    //		free(tokens_ptr[i]);
    //	}
    //	free(tokens_ptr);
    free(tokens);
}
void send_token(SendableToken* token, int receiver_rank) {
    pending_tokens_for_sending[pending_tokens_for_sending_count] = token;
    pending_tokens_for_sending_count++;
    return;

}

int get_global_state_by_token_id(int token_id, GlobalState** global_states, Token** tokens) {
    //LOG_PRINT(  "C%i: get_global_state_by_token_id, global states count : %i",my_rank,global_states_count);
    int i, count = 0;
    for (i = 0; i < global_states_count; i++) {
        GlobalState* s = global_states_array[i];
        int j;
        if (s == NULL || s->tokens == NULL) {
            LOG_PRINT("C%i: get_global_state_by_token_id ERROR global state NULL, gs count: %i", my_rank,
                    global_states_count);
            return 0;
        }
        //LOG_PRINT("C%i: get_global_state_by_token_id looking for token id:%i, tokens count:%i at globalstate:%i",my_rank,token_id,s->tokens_count,s->index);

        for (j = 0; j < s->tokens_count; j++) {
            Token* t = s->tokens[j];
            if (t->unique_id == token_id) {
                global_states[count] = s;
                tokens[count] = t;
                //LOG_PRINT("C%i: get_global_state_by_token_id FOUNDDDD",my_rank);
                count++;
            }
        }
    }
    if (count == 0) {
        LOG_PRINT("C%i: get_global_state_by_token_id NOT FOUNDDDD!!!!", my_rank);
    }
    return count;
}
void check_new_state(GlobalState* new_global_state, Token* t, GlobalState* owner_global_state, int delay_time,
        int delay_events) {
    if (new_global_state == NULL) {
        LOG_PRINT("C%i: global state is null!", my_rank);
        return;
    }
    int transition = -1;
    if (t != NULL) {
        transition = t->transition_id;
    }
    char*tmp;
    char base[100] = ",p%d,%lf,%lf";

    char *all = malloc(500);

    int i;
    int found = 0;
    LOG_PRINT_RESULT("%i,New Global State,%s,%i", my_rank, new_global_state->current_monitor_state->state_name,
            transition);
    LOG_PRINT("C%i: New Global State,%s, Transition: %i", my_rank, new_global_state->current_monitor_state->state_name,
            transition);
    if (owner_global_state != NULL && t != NULL) {
        for (i = 0; i < owner_global_state->tokens_count; i++) {
            if (owner_global_state->tokens[i]->transition_id == transition) {
                if (owner_global_state->tokens[i]->eval == 1 && (owner_global_state->tokens[i] != NULL)
                        && (owner_global_state->tokens[i]->target_process_variables != NULL)) {
                    int p_index = owner_global_state->tokens[i]->destination;
                    LOG_PRINT("C%i: check new state: checking token id:%i", my_rank,
                            owner_global_state->tokens[i]->unique_id);
                    tmp = "x";
                    double x_other = get_variable_value(new_global_state->processes_states_array[p_index]->variables,
                            tmp);
                    tmp = "y";
                    double y_other = get_variable_value(new_global_state->processes_states_array[p_index]->variables,
                            tmp);
                    char p[2000];
                    LOG_PRINT("C%i: constructing satisfaction gstate: x:%d, y:%d", my_rank, x_other, y_other);
                    sprintf(p, base, p_index, x_other, y_other);
                    LOG_PRINT("C%i:%s", my_rank, p);
                    strcat(all, p);
                    found = 1;
                }
            }
        }
    }
    if (found == 0) {
        all = malloc(1);
        all[0] = '\0';
    }
    LOG_PRINT("C%i: %s", my_rank, all);
    tmp = "x";
    double x = get_variable_value(new_global_state->processes_states_array[my_rank]->variables, tmp);
    tmp = "y";
    double y = get_variable_value(new_global_state->processes_states_array[my_rank]->variables, tmp);
    tmp = "i";
    int index = (int) get_variable_value(new_global_state->processes_states_array[my_rank]->variables, tmp);
    char* final_state_type;

    tmp = "V";
    final_state_type = "Violation";
    signalFinalState(new_global_state, new_global_state->current_monitor_state->type, tmp, final_state_type, x, y,
            transition, delay_time, delay_events, all, 1, index, new_global_state->gcut[my_rank]);

    tmp = "S";
    final_state_type = "Satisfaction";
    signalFinalState(new_global_state, new_global_state->current_monitor_state->type, tmp, final_state_type, x, y,
            transition, delay_time, delay_events, all, 1, index, new_global_state->gcut[my_rank]);

    tmp = "PV";
    final_state_type = "Possible Violation";
    signalFinalState(new_global_state, new_global_state->current_monitor_state->type, tmp, final_state_type, x, y,
            transition, delay_time, delay_events, all, 0, index, new_global_state->gcut[my_rank]);

    tmp = "PS";
    final_state_type = "Possible Satisfaction";
    signalFinalState(new_global_state, new_global_state->current_monitor_state->type, tmp, final_state_type, x, y,
            transition, delay_time, delay_events, all, 0, index, new_global_state->gcut[my_rank]);

    merge_similar_global_states();
    free(all);
}

void merge_similar_global_states() {
    int i;

    //LOG_PRINT("C%i: Merging global_state: count before:%i",my_rank,global_states_count);
    for (i = 0; i < global_states_count; i++) {
        int j;
        for (j = i + 1; j < global_states_count; j++) {
            if (global_states_array[i]->current_monitor_state == global_states_array[j]->current_monitor_state) {
                int k, m;
                if (global_states_array[j]->pending_events_count != 0
                        && arrays_equal(global_states_array[i]->pending_events,
                                global_states_array[i]->pending_events_count, global_states_array[j]->pending_events,
                                global_states_array[j]->pending_events_count) == 0) {
                    //LOG_PRINT(  "C%i: Can't merge due to difference in pending event", my_rank);
                    //continue;
                    int * pending_events_intersection = malloc(
                    MAX_PENDING_EVENTS_COUNT * sizeof(int));
                    int count = 0;
                    for (k = 0; k < global_states_array[i]->pending_events_count; k++) {
                        for (m = 0; m < global_states_array[j]->pending_events_count; m++) {
                            if (global_states_array[i]->pending_events[k]
                                    == global_states_array[j]->pending_events[m]) {
                                pending_events_intersection[count] = global_states_array[i]->pending_events[k];
                                count++;
                            }
                        }
                    }
                    free(global_states_array[i]->pending_events);
                    global_states_array[i]->pending_events = pending_events_intersection;
                    global_states_array[i]->pending_events_count = count;

                }

                if (global_states_array[j]->tokens_count != 0) {
                    //LOG_PRINT("C%i: Can't merge due to pending tokens", my_rank);
                    continue;
                    //merge waiting tokens

                    //
                    //					for(k=0;k<global_states_array[j]->tokens_count;k++)
                    //					{
                    //						global_states_array[i]->tokens[global_states_array[i]->tokens_count]=global_states_array[j]->tokens[k];
                    //						global_states_array[i]->tokens_count++;
                    //					}
                }
                int merge = 1;
                //for(k=0;k<processes_count;k++)
                {
                    if (global_states_array[j]->gcut[my_rank] != global_states_array[i]->gcut[my_rank]) {
                        merge = 0;
                        //LOG_PRINT("C%i: Can't merge due to difference in clocks", my_rank);

                        break;
                    }
                }
                if (merge == 0) {
                    continue;
                }
                LOG_PRINT("C%i: Merging global_state[%i] and [%i]", my_rank, i, j);
                // merge into i and delete j

                for (k = 0; k < processes_count; k++) {
                    if (global_states_array[i]->gcut[k] < global_states_array[j]->gcut[k]) {
                        global_states_array[i]->gcut[k] = global_states_array[j]->gcut[k];
                        global_states_array[i]->processes_states_array[k]->variables =
                                global_states_array[j]->processes_states_array[k]->variables;
                    }
                }

                remove_global_state(global_states_array[j]);
                j--;
            }

        }
    }

    //LOG_PRINT("C%i: Merging global_state: count after:%i",my_rank,global_states_count);
}
int signalFinalState(GlobalState * new_global_state, char* type, char* final_state_type, char* name, double x, double y,
        int transition, int delay_time, int delay_events, char* all, int terminate, int i, int events_so_far) {
    //send to all other processes informing them of final state
    if (strcmp(type, final_state_type) == 0) {
        LOG_PRINT_RESULT("%i,signal %s,%i,%d,%d,%i,%i,%i%s", my_rank, name, i, x, y, transition, delay_time,
                delay_events, all);
        LOG_PRINT("C%i: signal %s,index:%i,x:%d,y:%d,%i,%i,%i", my_rank, name, i, x, y, transition, delay_time,
                delay_events);

        if (terminate == 1) {
            char over[] = "done";

            LOG_PRINT("C%i: Final signal for Global State:%i", my_rank, new_global_state->unique_id);
            //			MPI_Isend(&over, sizeof(over), MPI_CHAR, my_rank, 4,
            //					MPI_COMM_WORLD, &bulk_tokens_request);
            new_global_state->is_done = 1;

        }
        return 1;
    }
    return 0;
}

void receive_token(SendableToken* received_token) {
    LOG_PRINT("C%i: receive_token id:%i, owner:C%i, target_event_id:%i", my_rank, received_token->unique_id,
            received_token->parent_process_rank, received_token->target_event_id);
    if (received_token->parent_process_rank == my_rank) {
        //get owner global state
        GlobalState ** found_global_states = malloc(sizeof(GlobalState*) * MAX_GLOBAL_STATES_COUNT);
        Token** found_tokens = malloc(sizeof(Token*) * MAX_TOKENS_PER_GLOBALSTATE_COUNT);
        int count_found = get_global_state_by_token_id(received_token->unique_id, found_global_states, found_tokens);
        if (count_found == 0) {
            LOG_PRINT("C%i: receive_token, global state deleted, ignore message", my_rank);
            return;
        }
        int k, i;
        for (k = 0; k < count_found; k++) {
            LOG_PRINT("C%i: receive_token, processing token#%i", my_rank, k);
            Token* token = found_tokens[k];
            GlobalState* global_state = found_global_states[k];
            // update local token with received token.
            int destination = received_token->destination;
            token->eval = received_token->predicates_eval[token->pred_index_in_sendabletoken];
            token->end_timestamp = received_token->end_timestamp;
            token->start_timestamp = received_token->start_timestamp;
            token->events_till_evaluation = received_token->events_till_evaluation;
            token->target_process_variables = malloc(sizeof(VariableValuation));
            token->target_process_variables->count = received_token->target_process_variables.count;
            token->gcut = copy_gcut_deep(received_token->gcut, processes_count);
            for (i = 0; i < token->target_process_variables->count; i++) {
                token->target_process_variables->state_value_array[i] =
                        received_token->target_process_variables.state_value_array[i];

                strcpy(token->target_process_variables->state_name_array[i],
                        received_token->target_process_variables.state_name_array[i]);
            }
            LOG_PRINT("C%i: sanity check: %d=%d", my_rank, token->target_process_variables->state_value_array[0],
                    received_token->target_process_variables.state_value_array[0]);

            LOG_PRINT("C%i: receive_token, My token eval:%i", my_rank, token->eval);
            //   check tokens for same transitions,
            // If all returned true, then enable transition
            int transition_enabled_bool = check_transition_tokens_state(global_state, token);
            int is_consistent = 0;
            if (transition_enabled_bool == 1)
                is_consistent = check_gcut_consistent(global_state, token);
            int tran_status = check_global_state(global_state);
            LOG_PRINT("C%i: transition %i: %s to %s returns: %i, transition_status:%i", my_rank, token->transition_id,
                    token->from_state, token->to_state, transition_enabled_bool, tran_status);
            //only update global state with the received token info
            //if the token returned true or if the returned local state is the next to what I know -- no gaps in the history(in case token no longer concurrent)
            //			if(token->eval==1 || (received_token->gcut[destination] - global_state->gcut[destination]) == 1)
            //			{
            //				global_state->gcut[ destination] =received_token->gcut[destination];
            //				//get destination's event details
            //				global_state->processes_states_array[ destination]->variables =(received_token->target_process_variables);
            //				global_state->processes_states_array[ destination]->vector_clock = copy_gcut_deep(received_token->gcut,
            //						processes_count);
            //			}

            if (transition_enabled_bool == 1 && is_consistent == 1)	//  || (received_token->gcut[destination] - global_state->gcut[destination]) == 1)
                    {
                for (i = 0; i < global_state->tokens_count; i++) {
                    if (global_state->tokens[i]->transition_id == token->transition_id) {
                        if (global_state->tokens[i]->eval == 1) {
                            global_state->gcut[global_state->tokens[i]->destination] =
                                    global_state->tokens[i]->gcut[global_state->tokens[i]->destination];
                            //get destination's event details
                            global_state->processes_states_array[global_state->tokens[i]->destination]->variables =
                                    *(global_state->tokens[i]->target_process_variables);
                            global_state->processes_states_array[global_state->tokens[i]->destination]->vector_clock =
                                    copy_gcut_deep(global_state->tokens[i]->gcut, processes_count);
                        }
                    }
                }
            }
            if (transition_enabled_bool == 1 && is_consistent == 1) {
                int delay_time = get_dalay(global_state, token);
                int delay_events = get_dalay_events(global_state, token);
                GlobalState* new_global_state = init_global_state();
                new_global_state->gcut = copy_gcut_deep(global_state->gcut, processes_count);
                new_global_state->automaton_params = global_state->automaton_params;
                new_global_state->params_count = global_state->params_count;
                new_global_state->processes_states_array = copy_processes_states_deep(
                        global_state->processes_states_array, processes_count);	//copy_processes_states_deep(global_state->processes_states_array,processes_count);
                copy_token_to_processes_states(received_token, new_global_state, processes_count);

                for (i = 0; i < processes_count; i++) {
                    LOG_PRINT("C%i: receive_token, new_global_state->gcut[%i]=%i", my_rank, i,
                            new_global_state->gcut[i]);
                }
                new_global_state->current_monitor_state = get_automaton_state_by_name(token->to_state, &g_automaton);
                LOG_PRINT(
                        "C%i: receive_token, Transition %i enabled, new global state at %s , processing pending events",
                        my_rank, token->transition_id, new_global_state->current_monitor_state->state_name);
                add_new_global_state(new_global_state);
                check_new_state(new_global_state, token, global_state, delay_time, delay_events);

                new_global_state->pending_events = get_pending_events_deep_copy(global_state->pending_events,
                        global_state->pending_events_count, processes_count);
                new_global_state->pending_events_count = global_state->pending_events_count;
                LOG_PRINT("C%i: receive_token, adding new_global_state", my_rank);
                //If all tokens returned true, then delete this global_state
                tran_status = check_global_state(global_state);
                if (tran_status == 1 || tran_status == 2) {
                    remove_global_state(global_state);
                }

                //advance_automaton_localy(new_global_state);
                //check_outgoing_transitions(new_global_state,inconsistent_processes);
                //process_pending_events(new_global_state);
            } else if (transition_enabled_bool == 0 || (transition_enabled_bool == 1 && is_consistent == 0)) {
                //to disable next
                token->eval = 0;
                //if all tokens returned false, then stay at same automaton state
                for (i = 0; i < global_state->tokens_count; i++) {
                    if (global_state->tokens[i]->transition_id == token->transition_id
                            && global_state->tokens[i]->eval == 1) {
                        global_state->tokens[i]->eval = 0;
                    }
                }
                tran_status = check_global_state(global_state);
                if (tran_status == 0) {
                    LOG_PRINT(
                            "C%i: receive_token, Transition disabled, no more tokens for this global state, processing pending events",
                            my_rank);
                    //update gcut for tokens that only bigger by one (doesn't matter eval==1 or eval==0)

                    for (i = 0; i < global_state->tokens_count; i++) {
                        destination = global_state->tokens[i]->destination;
                        if ((global_state->tokens[i]->gcut[destination] - global_state->gcut[destination]) == 1) {
                            if (global_state->gcut[destination] < global_state->tokens[i]->gcut[destination]) {
                                global_state->gcut[destination] = global_state->tokens[i]->gcut[destination];
                                //get destination's event details
                                global_state->processes_states_array[destination]->variables =
                                        *(global_state->tokens[i]->target_process_variables);
                                global_state->processes_states_array[destination]->vector_clock = copy_gcut_deep(
                                        global_state->tokens[i]->gcut, processes_count);
                            }
                        }

                    }

                    //delete all tokens, process pending events;
                    delete_tokens(global_state);

                    //process_pending_events(global_state);
                }

                else if (tran_status == -1) {
                    LOG_PRINT("C%i: receive_token, Transition disabled, global state has more tokens...waiting",
                            my_rank);

                } else if (tran_status == 2) {
                    // the case where a transition was first enabled, and then later ones were disabled.
                    LOG_PRINT("C%i: all tokens returned, some true, some false...deleting global state");

                    delete_tokens(global_state);
                    remove_global_state(global_state);

                } else {

                    LOG_PRINT("C%i: SHOULDN'T HAPPEN!!!");
                }

            }

        }
        free(found_global_states);
        free(found_tokens);

    } else // I am not the parent of this token
    {
        LOG_PRINT("C%i: receive_token: id:%i token owner: %i, target_event_id:%i", my_rank, received_token->unique_id,
                received_token->parent_process_rank, received_token->target_event_id);
        int event_id = received_token->target_event_id;
        ProcessState* event = get_local_history_event(event_id);
        if (event == NULL) {

            LOG_PRINT("C%i: receive_token id:%i event requested not yet occurred!, adding to waiting tokens", my_rank,
                    received_token->unique_id);
            g_waiting_tokens[g_waiting_tokens_count] = received_token;
            g_waiting_tokens_count++;

        } else {
            LOG_PRINT("C%i: receive_token id:%i event requested occurred!, calling process token", my_rank,
                    received_token->unique_id);

            int token_returned = process_token(received_token, event, 1);
            if (token_returned == 0) // should be added to waiting tokens, but first check if it targeting old event
                    {
                int i;
                // in case targeting old event
                for (i = received_token->target_event_id;
                        i <= local_state_history[g_local_state_history_count - 1]->vector_clock[my_rank]; i++) {
                    LOG_PRINT(
                            "C%i: receive_token id:%i event requested but didn't satisfy, checking event:%i, current max:%i",
                            my_rank, received_token->unique_id, i,
                            local_state_history[g_local_state_history_count - 1]->vector_clock[my_rank]);

                    token_returned = process_token(received_token, local_state_history[i], 1);
                    if (token_returned == 1) {
                        break;
                    }
                }
                if (token_returned == 0) {
                    LOG_PRINT(
                            "C%i: receive_token id:%i owner:C%i new target event not yet occurred!, adding to waiting tokens",
                            my_rank, received_token->unique_id, received_token->destination);

                    g_waiting_tokens[g_waiting_tokens_count] = received_token;
                    g_waiting_tokens_count++;
                }
            }
        }
    }

}

void delete_tokens(GlobalState* global_state) {

    LOG_PRINT("C%i: delete_tokens", my_rank);
    int i;
    if (global_state->tokens) {
        //	LOG_PRINT("C%i: delete_tokens, ptr not null",my_rank);
        for (i = 0; i < global_state->tokens_count; i++) {

            if (global_state->tokens[i]) {

                //	LOG_PRINT("C%i: delete_tokens, tokens[%i] not null",my_rank,i);
                free(global_state->tokens[i]);
            }
        }
        free(global_state->tokens);
    }
    global_state->tokens = malloc(sizeof(Token*) * MAX_TOKENS_PER_GLOBALSTATE_COUNT);
    global_state->tokens_count = 0;
}

ProcessState* get_local_history_event(int event_id) {
    if (event_id >= g_local_state_history_count) {
        return NULL;
    }
    return (local_state_history[event_id]);
}

void remove_global_state(GlobalState* global_state) {
    LOG_PRINT("C%i: remove global state", my_rank);
    if (global_states_count == 1) {
        LOG_PRINT("C%i: can't remove the only global state I have", my_rank);
        return;
    }
    //	free(global_states_array[global_state->index] );
    LOG_PRINT_TIMELINE(my_rank, "%i,%i,remove_global_state,%i,%i", global_state->automaton_params[0],
            global_state->automaton_params[1], global_states_count, global_state->unique_id);

    int i;
    int found = 0;
    for (i = 0; i < global_states_count - 1; i++) {
        if (global_states_array[i]->unique_id == global_state->unique_id) {
            found = 1;
        }
        if (found == 1) {
            global_states_array[i] = global_states_array[i + 1];
            global_states_array[i]->index = i;
        }
    }

    global_states_count--;

    //free(global_state->tokens);
    //	free(global_state->gcut);
    //free(global_state->pending_events);
    //	free(global_state->processes_states_array);
    //free(global_state);
    LOG_PRINT("C%i: remove_global_state, current global_states_count:%i", my_rank, global_states_count);

}
void remove_waiting_token(int j) {
    LOG_PRINT("C%i: remove_token: removing waiting token id:%i of index:%i", my_rank, g_waiting_tokens[j]->unique_id,
            j);
    //free(g_waiting_tokens[index]);
    int i;
    for (i = j; i < g_waiting_tokens_count - 1; i++)
        g_waiting_tokens[i] = g_waiting_tokens[i + 1];
    //free(g_waiting_tokens[g_waiting_tokens_count-1]);
    g_waiting_tokens_count--;
}

void print_waiting_tokens() {
    int i;
    for (i = 0; i < g_waiting_tokens_count; i++) {
        LOG_PRINT("C%i: waiting_token[%i]:target_event_id:%i, unique_id:%i", my_rank, i,
                g_waiting_tokens[i]->target_event_id, g_waiting_tokens[i]->unique_id);

    }
}
int check_global_state(GlobalState* global_state) {

    int i = 0;
    int returned_false_count = 0;
    int returned_true_count = 0;
    for (i = 0; i < global_state->tokens_count; i++) {
        if (global_state->tokens[i]->eval == -1) {
            return -1;
        }
        if (global_state->tokens[i]->eval == 0) {
            returned_false_count++;
        } else if (global_state->tokens[i]->eval == 1) {
            int j;
            int others = 1;
            for (j = 0; j < global_state->tokens_count; j++) {
                if (i == j)
                    continue;
                if (global_state->tokens[i]->transition_id == global_state->tokens[j]->transition_id) {
                    if (global_state->tokens[j]->eval == -1) {
                        return -1;
                    }
                    if (global_state->tokens[j]->eval == 0) {
                        others = 0;
                        break;
                    } else if (global_state->tokens[j]->eval == 1) {
                        continue;
                    }
                }
            }
            if (others == 1) {
                returned_true_count++;
            } else {
                returned_false_count++;
            }
        }
    }
    if ((returned_false_count) == global_state->tokens_count) {
        return 0;
    }
    if (returned_true_count == global_state->tokens_count) {
        return 1;
    }
    //where some returned true some returned false.
    return 2;
}
int get_dalay(GlobalState* global_state, Token* token) {
    int i;
    int max_delay = 0;
    int max_start_timestamp = 0;
    for (i = 0; i < global_state->tokens_count; i++) {
        Token* t = global_state->tokens[i];

        if (t->transition_id == token->transition_id) {
            if (max_start_timestamp < t->start_timestamp) {
                max_start_timestamp = t->start_timestamp;	//last event that enabled transition
            }

        }
    }
    for (i = 0; i < global_state->tokens_count; i++) {
        Token* t = global_state->tokens[i];

        if (t->transition_id == token->transition_id) {

            int delay = t->end_timestamp - max_start_timestamp;
            if (delay > max_delay)
                max_delay = delay;
        }
    }

    return max_delay;

}
int get_dalay_events(GlobalState* global_state, Token* token) {
    int i;
    int max_delay = 0;
    int max_start_timestamp = 0;
    for (i = 0; i < global_state->tokens_count; i++) {
        Token* t = global_state->tokens[i];

        if (t->transition_id == token->transition_id) {
            if (max_start_timestamp < t->start_timestamp) {
                max_delay = t->events_till_evaluation;
                max_start_timestamp = t->start_timestamp;	//last event that enabled transition
            }

        }
    }

    return max_delay;

}
int check_transition_tokens_state(GlobalState* global_state, Token* token) {
    int i;
    LOG_PRINT("C%i: check_transition_tokens_state for token %s->%s destination:C%i global state.tokens count: %i",
            my_rank, token->from_state, token->to_state, token->destination, global_state->tokens_count);

    for (i = 0; i < global_state->tokens_count; i++) {
        LOG_PRINT("C%i: check_transition_tokens_state:  transition ID:%i token id:%i token eval:%i, destination:C%i",
                my_rank, global_state->tokens[i]->transition_id, global_state->tokens[i]->unique_id,
                global_state->tokens[i]->eval, global_state->tokens[i]->destination);

        // if all the tokens for the same transition didn't return -1 nor 0 then the transition should be enabled.

        if (global_state->tokens[i]->transition_id == token->transition_id) {
            if (global_state->tokens[i]->eval == -1) {
                return -1;
            } else if (global_state->tokens[i]->eval == 0) {
                return 0;
            }
            /*else if (global_state->tokens[i]->eval == 1 && token->eval == 1
             && global_state->tokens[i]->unique_id != token->unique_id)
             {
             LOG_PRINT("C%i: checking concurrent with other returned token [t1.dest=c%i,t2.dest=c%i] in this transition",
             my_rank, token->destination, global_state->tokens[i]->destination);
             //1 check if both tokens.gcut are concurrent
             int j;
             for (j = 0; j < processes_count; j++)
             {
             LOG_PRINT("C%i: received_token.gcut[%i]=%i", my_rank, j, token->gcut[j]);
             LOG_PRINT("C%i: other token.gcut[%i]=%i", my_rank, j, global_state->tokens[i]->gcut[j]);
             }

             //if(is_consistent4(global_state,processes_count,NULL)==0)
             vector_clock_compare res = compare_clocks(
             global_state->processes_states_array[token->destination]->vector_clock,
             global_state->processes_states_array[global_state->tokens[i]->destination]->vector_clock, processes_count);
             if (res != VC_CONCURRENT && res != VC_EQUAL)
             {
             //2 if not concurrent, return 0;
             //LOG_PRINT("C%i: not consistent",my_rank);
             LOG_PRINT("C%i:Tokens not concurrent [t1.dest=c%i,t2.dest=c%i] in this transition", my_rank,
             token->destination, global_state->tokens[i]->destination);
             return 0;

             }

             }*/
        }
    }
    return 1;
}

int check_gcut_consistent(GlobalState *global_state, Token* token) {

    int i, j, k;
    int* process_participate_in_pred = malloc(sizeof(int) * processes_count);
    memset(process_participate_in_pred, 0, sizeof(int) * processes_count);
    process_participate_in_pred[my_rank] = 1;
    for (j = 0; j < token->pred.conjunct_count; j++) {
        process_participate_in_pred[token->pred.conjuncts[j].owner_processes[0]] = 1;
    }
    for (i = 0; i < processes_count; i++) {
        if (process_participate_in_pred[i] == 1) {
            int* VC_i = global_state->processes_states_array[i]->vector_clock;
            LOG_PRINT("C%i: check_gcut_consistent, default for C%i ", my_rank, i);
            for (k = 0; k < global_state->tokens_count; k++) {
                if (global_state->tokens[k]->transition_id == token->transition_id
                        && global_state->tokens[k]->destination == i) {
                    VC_i = global_state->tokens[k]->gcut;
                    LOG_PRINT("C%i: check_gcut_consistent, found a token for C%i ", my_rank, i);
                    break;
                }
            }
            for (j = 0; j < processes_count; j++) {
                // if a token was sent for this process, get it
                if (i != j && process_participate_in_pred[j] == 1) {
                    int* VC_j = global_state->processes_states_array[j]->vector_clock;
                    LOG_PRINT("C%i: check_gcut_consistent, default for C%i ", my_rank, j);
                    for (k = 0; k < global_state->tokens_count; k++) {
                        if (global_state->tokens[k]->transition_id == token->transition_id
                                && global_state->tokens[k]->destination == j) {
                            VC_j = global_state->tokens[k]->gcut;
                            LOG_PRINT("C%i: check_gcut_consistent, found a token for C%i ", my_rank, j);
                            break;
                        }
                    }
                    vector_clock_compare res = compare_clocks(VC_i, VC_j, processes_count);
                    if (res != VC_CONCURRENT && res != VC_EQUAL) {
                        //2 if not concurrent, return 0;
                        //LOG_PRINT("C%i: not consistent",my_rank);
                        LOG_PRINT("C%i: created gcut is not consistent [c%i, c%i] for tran:%i", my_rank, i, j,
                                token->transition_id);
                        return 0;

                    } else {

                        LOG_PRINT("C%i: check_gcut_consistent,C%i and C%i are consistent ", my_rank, i, j);
                    }
                }
            }
        }
    }
    LOG_PRINT("C%i: check_transition_tokens_state: returning 1", my_rank);
    return 1;
}

int dequeue(int* pending_events_indices, int count) {
    LOG_PRINT("C%i:dequeue,ptr:%i", my_rank, pending_events_indices);
    if (pending_events_indices == NULL) {

        return 0;
    }
    int event_index = pending_events_indices[0];
    LOG_PRINT("C%i:dequeue,element at 0 retrieved", my_rank);
    remove_pending_event(pending_events_indices, 0, count);
    LOG_PRINT("C%i:dequeue,element at 0 removed", my_rank);

    return event_index;
}
void remove_pending_event(int* array, int index, int array_length) {

    int i;
    for (i = index; i < array_length - 1; i++)
        array[i] = array[i + 1];
}
void process_pending_events(GlobalState* global_state) {

    LOG_PRINT("C%i: process_pending_events, current process_pending_events_count:%i", my_rank,
            global_state->pending_events_count);
    if (global_state->pending_events_count != 0 && global_state->tokens_count == 0) {
        int event_id = dequeue(global_state->pending_events, global_state->pending_events_count);
        global_state->pending_events_count = (global_state->pending_events_count - 1);
        ProcessState* event = local_state_history[event_id];
        LOG_PRINT("C%i: processing Finally event#:%i for global_state:%i, p%i_goingwest:%i", my_rank, event->state_id,
                global_state->unique_id, my_rank, event->variables.state_value_array[0]);
        process_event(global_state, event);
        //sleep(2);
    }
    //	LOG_PRINT("C%i: finished process_pending_events, current process_pending_events_count:%i",my_rank,global_state->pending_events_count);

}
void add_new_global_state(GlobalState* new_global_state) {

    global_states_array[global_states_count] = new_global_state;
    new_global_state->index = global_states_count;
    global_states_count++;
    new_global_state->unique_id = global_states_ids_count;
    global_states_ids_count++;
    LOG_PRINT("C%i: add_new_global_state, params:%i,%i, current global_states_count:%i", my_rank,
            new_global_state->automaton_params[0], new_global_state->automaton_params[1], global_states_count);
    LOG_PRINT_TIMELINE(my_rank, "%i,%i,new_global_state,%i,%i", new_global_state->automaton_params[0],
            new_global_state->automaton_params[1], global_states_count, new_global_state->unique_id);

}

int process_token(SendableToken* t, ProcessState* local_state, int already_in_waiting_tokens) {
    LOG_PRINT("C%i: process_token id:%i ", my_rank, t->unique_id);

    LOG_PRINT("C%i: process_token,t->gcut[%i]=%i t->gcut[%i]=%i", my_rank, t->parent_process_rank,
            t->gcut[t->parent_process_rank], my_rank, t->gcut[my_rank]);
    LOG_PRINT("C%i: process_token, local_state->vector_clock[%i]=%i, local_state->vector_clock[%i]=%i", my_rank,
            t->parent_process_rank, local_state->vector_clock[t->parent_process_rank], my_rank,
            local_state->vector_clock[my_rank]);

    vector_clock_compare res = compare_clocks(t->gcut, local_state->vector_clock, processes_count);

    if (res == VC_CONCURRENT || res == VC_EQUAL) {
        //t->gcut[my_rank]=local_state->vector_clock[my_rank];
        t->target_process_variables = local_state->variables;

        LOG_PRINT("C%i: process_token, updating token with local state, variables.count:%i", my_rank,
                local_state->variables.count);
        return evaluate_token(t, local_state, already_in_waiting_tokens);
    } else if (res == VC_FIRST_BIGGER) {
        t->target_event_id = t->target_event_id + 1;
        if (already_in_waiting_tokens == 0) {
            g_waiting_tokens[g_waiting_tokens_count] = t;
            g_waiting_tokens_count++;
        }
        return 0;
    } else {
        LOG_PRINT("C%i: process_token id:%i, not concurrent anymore token requested pred failed, return to owner C%i",
                my_rank, t->unique_id, t->parent_process_rank);
        int i;
        for (i = 0; i < t->predicates_count; i++) {
            t->predicates_eval[i] = 0;
        }
        //now the token should reflect the next event  of what the token knows about me
        if (t->gcut[my_rank] + 1 < g_local_state_history_count) {
            local_state = local_state_history[t->gcut[my_rank] + 1];
        }
        for (i = 0; i < processes_count; i++) {
            t->gcut[i] = local_state->vector_clock[i];
        }
        t->target_process_variables = local_state->variables;
        t->target_process_rank = t->parent_process_rank;
        send_token(t, t->parent_process_rank);

        return 1;
    }

}
int evaluate_token(SendableToken* t, ProcessState* local_state, int already_in_waiting_tokens) {
    LOG_PRINT("C%i: evaluate_token", my_rank);
    //If the token's required conjunct is a shared conjunct, then it must be shared by the current
    //process and the sender process, so create a temp global state that contains only entries
    //t->process_state[my_rank]=*local_state;

    if (token_satisfies_pred(t, my_rank) == 1) //at least one predicate satisfied
            {
        LOG_PRINT("C%i: process_token, token id:%i requested pred success, return to owner C%i", my_rank, t->unique_id,
                t->parent_process_rank);

        t->start_timestamp = local_state->timestamp;
        time_t now;
        time(&now);
        t->end_timestamp = (int) now;
        t->target_process_rank = t->parent_process_rank;
        t->events_till_evaluation = local_state_history[g_local_state_history_count - 1]->state_id
                - local_state->state_id;
        //now the token should reflect the state of the event;
        int i;
        for (i = 0; i < processes_count; i++) {
            t->gcut[i] = local_state->vector_clock[i];
        }
        t->target_process_variables = local_state->variables;
        send_token(t, t->parent_process_rank);

        return 1;
    } else {
        t->target_event_id = t->target_event_id + 1;
        if (already_in_waiting_tokens == 0) {
            g_waiting_tokens[g_waiting_tokens_count] = t;
            g_waiting_tokens_count++;
        }
        LOG_PRINT(
                "C%i: process_token, token id:%i requested not yet fulfilled, add to waiting tokens, new target event:%i",
                my_rank, t->unique_id, t->target_event_id);
        return 0;
    }
}
