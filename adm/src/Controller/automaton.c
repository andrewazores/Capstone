#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>

#include "automaton.h"
#include "../logger.h"

void load_automaton(char* file_name, Automaton* g_automaton) {
    FILE* stream = fopen(file_name, "r");
    char line[1024];
    fgets(line, 1024, stream);
    g_automaton->states_count = atoi(line);
    AutomatonState* states = (AutomatonState*) malloc(g_automaton->states_count * sizeof(AutomatonState));

    // Adding states to automaton
    int i;
    for (i = 0; i < g_automaton->states_count; i++) {
        AutomatonState state;
        fgets(line, 1024, stream);
        char* tmp = strdup(line);
        state.state_name = (char*) malloc(MAX_AUTOMATON_STATE_NAME_LEN);
        strcpy(state.state_name, getfield(tmp, 1));
        tmp = strdup(line);
        state.type = (char*) malloc(2);
        strcpy(state.type, getfield(tmp, 2));
        states[i] = state;
    }

    fgets(line, 1024, stream);
    g_automaton->transitions_count = atoi(line);
    AutomatonTransition* transitions = (AutomatonTransition*) malloc(
            g_automaton->transitions_count * sizeof(AutomatonTransition));
    int tran_id = 0;

    // Adding transitions to automaton
    for (i = 0; i < g_automaton->transitions_count; i++) {
        AutomatonTransition transition;
        transition.id = tran_id;
        tran_id++;
        fgets(line, 1024, stream);
        char* tmp = strdup(line);
        transition.from = (char*) malloc(MAX_AUTOMATON_STATE_NAME_LEN);
        strcpy(transition.from, getfield(tmp, 1));
        tmp = strdup(line);
        transition.to = (char*) malloc(MAX_AUTOMATON_STATE_NAME_LEN);
        strcpy(transition.to, getfield(tmp, 2));
        tmp = strdup(line);

        // Building a predicate for the transition.
        char** pred_states_array = split_states(getfield(tmp, 3), '&',
        MAX_CONJUNCTS_PER_PREDICATE);
        transition.pred.conjunct_count = 0;
        int j;
        for (j = 0; j < MAX_CONJUNCTS_PER_PREDICATE; j++) {
            char* pred_name = pred_states_array[j];
            if (!pred_name) // pointer points to something
            {
                break;
            }
            // since monitor is parametrized, the conjunct will be replaced later with true params, '
            // so just get the name of the conjunct(which includes the params, such that each param is numbered 0-99)
            // other details not important
            //Conjunct* conjunct_ptr = get_conjunct_by_name(pred_name);
            Conjunct* conjunct_ptr = malloc(sizeof(Conjunct));
            strcpy(conjunct_ptr->conjunct_name, pred_name);
            transition.pred.conjuncts[transition.pred.conjunct_count] = *conjunct_ptr;
            transition.pred.conjunct_count++;
        }
        //		tmp = strdup(line);
        //		transition.leader=atoi(getfield(tmp, 4));
        //		tmp = strdup(line);
        //		tmp=(getfield(tmp, 5));
        //		if(tmp!=NULL)
        //		{
        //			transition.notification_set=(char*) malloc(3);
        //			strcpy(transition.notification_set,tmp);
        //		}
        transitions[i] = transition;
    }
    fclose(stream);

    g_automaton->states = states;
    g_automaton->transitions = transitions;
}

AutomatonState* get_initial_automaton_state(Automaton* g_automaton) {
    int i;
    for (i = 0; i < g_automaton->states_count; i++) {
        char* initial = "I";
        if (strcmp(g_automaton->states[i].type, initial) == 0) {
            return &(g_automaton->states[i]);
        }
    }
    return NULL;
}

AutomatonState* get_automaton_state_by_name(char* name, Automaton* g_automaton) {
    int i;
    for (i = 0; i < g_automaton->states_count; i++) {
        if (strcmp(g_automaton->states[i].state_name, name) == 0) {
            return &(g_automaton->states[i]);
        }
    }
    return NULL;
}
Predicate* deparametrize_pred(Predicate* predicate, int* automaton_params_valuation, int params_count, int process_rank) {
    Predicate* pred = malloc(sizeof(Predicate));
    pred->conjunct_count = predicate->conjunct_count;

    int i;
    for (i = 0; i < pred->conjunct_count; i++) {
        char deparametrized_conjunct_name[MAX_CONJUCATE_LEN];
        strcpy(deparametrized_conjunct_name, predicate->conjuncts[i].conjunct_name);

        //LOG_PRINT("C%i: before deparametrizing deparametrize_pred %s, param_count:%i",process_rank,deparametrized_conjunct_name,params_count);
        int j;
        for (j = 0; j < params_count; j++) {
            //LOG_PRINT("C%i: deparametrize_pred automaton_param[%i]:%i",process_rank,j, automaton_params_valuation[j]);
            char pattern[3];
            sprintf(pattern, "_%d", j);		// parameter _0 in p_0_turns_left
            char replacer[3];
            sprintf(replacer, "%d", automaton_params_valuation[j]); //maps to p2_turns_left
            if (strcmp(replacer, pattern) == 0) {
                continue;
            }
            //	LOG_PRINT("C%i: during deparametrizing deparametrize_pred %s, replacing %s with %s",process_rank,deparametrized_conjunct_name, pattern,replacer);

            strcpy(deparametrized_conjunct_name, str_replace(deparametrized_conjunct_name, pattern, replacer));

        }
        //LOG_PRINT("C%i: after deparametrizing deparametrize_pred %s",process_rank,deparametrized_conjunct_name);

        //Now get the conjunct based on the new deparametrized_conjunct_name.
        Conjunct* conjunct = get_conjunct_by_name(deparametrized_conjunct_name, process_rank);
        if (conjunct == NULL) {
            LOG_PRINT("C%i: deparametrizing conjunct %s NOT found", process_rank, deparametrized_conjunct_name);
            //exit(0);
        }
        pred->conjuncts[i] = *(conjunct);

    }
    //LOG_PRINT("C%i: deparametrizing, returning",process_rank);
    return pred;
}

int is_global_state_consistent_for_pred(int process_rank, int processes_count, Predicate* pred,
        GlobalState * global_state, int* inconsistent, int* pred_participants) {
    int j;

    pred_participants[process_rank] = 1;
    for (j = 0; j < pred->conjunct_count; j++) {
        pred_participants[pred->conjuncts[j].owner_processes[0]] = 1;
    }
    int is_con = 1;
    for (j = 0; j < processes_count; j++) {
        int k;
        for (k = j + 1; k < processes_count; k++) {
            if (pred_participants[j] == 1 && pred_participants[k] == 1) {
                vector_clock_compare res = compare_clocks(global_state->processes_states_array[j]->vector_clock,
                        global_state->processes_states_array[k]->vector_clock, processes_count);
                if (res != VC_CONCURRENT && res != VC_EQUAL) {
                    if (inconsistent != NULL)
                        inconsistent[j] = inconsistent[k] = 1;
                    is_con = 0;
                }
            }

        }
    }
    if (inconsistent != NULL)
        inconsistent[process_rank] = 0;
    return is_con;
}

AutomatonState* advance_automaton(GlobalState* global_state, char* current_state_name, int processes_count,
        int process_rank, Automaton* g_automaton, int* automaton_params, int params_count) {
    LOG_PRINT("C%i: advance_automaton", process_rank);
    int i;

    for (i = 0; i < g_automaton->transitions_count; i++) {
        AutomatonTransition tran = g_automaton->transitions[i];
        if (strcmp(tran.from, current_state_name) != 0 || strcmp(tran.from, tran.to) == 0) {
            continue;
        }

        LOG_PRINT("C%i: advance_automaton, checking transition:%i from %s to %s", process_rank, tran.id, tran.from,
                tran.to);
        Predicate* pred = deparametrize_pred(&(tran.pred), automaton_params, params_count, process_rank);
        int* pred_participants = malloc(sizeof(int) * processes_count);
        memset(pred_participants, 0, sizeof(int) * processes_count);

        int is_con = is_global_state_consistent_for_pred(process_rank, processes_count, pred, global_state, NULL,
                pred_participants);
        if (is_con == 0) {
            LOG_PRINT("C%i: advance_automaton, not consistent", process_rank);
            continue;
        }
        if (global_state_satisfies_pred(global_state, pred, NULL, process_rank, processes_count) == 1) {
            LOG_PRINT("C%i: advance_automaton, new monitor state:%s", process_rank, tran.to);
            return get_automaton_state_by_name(tran.to, g_automaton);

        } else {
            LOG_PRINT("C%i: advance_automaton, transition from %s to %s not satisfied", process_rank, tran.from,
                    tran.to);

        }
    }
    LOG_PRINT("C%i: advance_automaton, NO satisfying predicate FOUND, stay where we are :(", process_rank);

    return NULL;
}

int could_be_satisfied_by(GlobalState* global_state, Predicate* pred, int* unsatisfying_processes_boolean_array,
        int process_rank, int processes_count, int*automaton_params, int params_count) {

    int sat = global_state_satisfies_pred(global_state, pred, unsatisfying_processes_boolean_array, process_rank,
            processes_count);
    int* inconsistent = malloc(sizeof(int) * processes_count);
    memset(inconsistent, 0, sizeof(int) * processes_count);

    int* pred_participants = malloc(sizeof(int) * processes_count);
    memset(pred_participants, 0, sizeof(int) * processes_count);

    int is_con = is_global_state_consistent_for_pred(process_rank, processes_count, pred, global_state, inconsistent,
            pred_participants);

    //Global state is inconsistent, but my local state can satisfy the predicate
    //Then set all others that were inconsistent to unsatisfying
    //(even though what I know about them is satisfying)

    if (is_con == 0) {
        //set all others as unsatisfying
        int i;
        for (i = 0; i < processes_count; i++)
            if (inconsistent[i] == 1)	//if(i!=process_rank && pred_participants[i]==1)//
                unsatisfying_processes_boolean_array[i] = 1;
    }
    LOG_PRINT("C%i: could_be_satisfied_by, sat:%i, is_con:%i, unsat[%i]=%i", process_rank, sat, is_con, process_rank,
            unsatisfying_processes_boolean_array[process_rank]);
    if (sat == 1 && is_con == 1) {
        LOG_PRINT("C%i: could_be_satisfied_by...weird, global state satisfies the pred", process_rank);
        return 1;
    }

    else if (unsatisfying_processes_boolean_array[process_rank] == 0) {
        // if global state doesn't satisfy the pred, but my local state does

        LOG_PRINT("C%i: local state can satisfy predicate, returning true...great", process_rank);
        return 1;
    } else {
        LOG_PRINT("C%i: could_be_satisfied_by:false sat:%i, is_con:%i", process_rank, sat, is_con);
        return 0;
    }
}
// does global state satisfy the predicate
int global_state_satisfies_pred(GlobalState* global_state, Predicate* pred, int* unsatisfying_processes_boolean_array,
        int process_rank, int processes_count) {
    int satisfy = 1;
    VariableValuation* variables_array = malloc(sizeof(VariableValuation) * processes_count);
    int i;
    for (i = 0; i < processes_count; i++) {
        LOG_PRINT("C%i: global_state->processes_states_array[%i]->variables.state_name_array[0]=%s", process_rank, i,
                global_state->processes_states_array[i]->variables.state_name_array[0]);
        variables_array[i] = global_state->processes_states_array[i]->variables;
    }

    char* predicate = malloc(sizeof(char) * MAX_CONJUNCTS_PER_PREDICATE * MAX_CONJUCATE_LEN);

    predicate[0] = '\0';
    for (i = 0; i < pred->conjunct_count; i++) {
        strcat(predicate, pred->conjuncts[i].conjunct_name);
    }
    LOG_PRINT("C%i:global_state_satisfies_pred checking predicate:%s", process_rank, predicate);

    for (i = 0; i < pred->conjunct_count; i++) {

        LOG_PRINT("C%i:global_state_satisfies_pred checking:%s", process_rank, pred->conjuncts[i].conjunct_name);

        if (satisfies(&(pred->conjuncts[i]), &(variables_array[pred->conjuncts[i].owner_processes[0]]),
                unsatisfying_processes_boolean_array, process_rank) == 0) {
            satisfy = 0;
        }

    }
    return satisfy;
}
int token_satisfies_pred(SendableToken *t, int process_rank) {

    int at_least_one_satisfy = 0;
    // if at least one of the predicates in the token.predicates array is satisfied by token.target_process_variables
    //then return true

    int i, j;
    for (j = 0; j < t->predicates_count; j++) {
        int pred_sat = 1;
        for (i = 0; i < t->predicates[j].conjunct_count; i++) {

            if (0 == satisfies(&(t->predicates[j].conjuncts[i]), &(t->target_process_variables), NULL, process_rank)) {
                pred_sat = 0;
                LOG_PRINT("C%i: token_satisfies_pred: token doesn't satisfy conjunct:%s", process_rank,
                        t->predicates[j].conjuncts[i].conjunct_name);
                t->predicates_eval[j] = 0;
                //one conjuncts of the predicate is false, don't check the others.
                break;

            } else {
                LOG_PRINT("C%i: token_satisfies_pred: token satisfies conjunct:%s", process_rank,
                        t->predicates[j].conjuncts[i].conjunct_name);

            }
        }
        if (pred_sat == 1) {
            t->predicates_eval[j] = 1;
            at_least_one_satisfy = 1;
        }
    }

    return at_least_one_satisfy;
}

int is_consistent3(GlobalState* global_state, int processes_count, int * inconsistent_processes_boolean_array,
        int my_rank) {
    int is_con = 1;
    // 1 -1 with me first

    int i;
    for (i = 0; i < processes_count; i++) {
        if (i == my_rank) {
            continue;
        }
        vector_clock_compare res = compare_clocks(global_state->processes_states_array[i]->vector_clock,
                global_state->processes_states_array[my_rank]->vector_clock, processes_count);
        if (res != VC_CONCURRENT && res != VC_EQUAL) {
            is_con = 0;

            LOG_PRINT("C%i: C%i is inconsistent", my_rank, i);
            if (inconsistent_processes_boolean_array != NULL)
                inconsistent_processes_boolean_array[i] = 1;
        }
    }
    // other process 1-1 together
    for (i = 0; i < processes_count; i++) {
        int j;

        for (j = i + 1; j < processes_count; j++) {
            if (i == j || i == my_rank || j == my_rank) {
                continue;
            }
            vector_clock_compare res = compare_clocks(global_state->processes_states_array[i]->vector_clock,
                    global_state->processes_states_array[j]->vector_clock, processes_count);
            if (res != VC_CONCURRENT && res != VC_EQUAL) {
                is_con = 0;
                if (inconsistent_processes_boolean_array != NULL) {

                    if (inconsistent_processes_boolean_array[i] == 0 && inconsistent_processes_boolean_array[j] == 0)
                        LOG_PRINT("C%i: C%i is inconsistent", my_rank, i);
                    inconsistent_processes_boolean_array[i] = inconsistent_processes_boolean_array[j] = 1;
                }
            }
        }

    }
    return is_con;

}
int is_consistent2(GlobalState* global_state, int processes_count, int * inconsistent_processes_boolean_array,
        int my_rank) {
    int is_con = 1;
    int i;
    //does any other process have a higher value for process i than itself?
    int j;
    for (i = 0; i < processes_count; i++) {
        int i_value = global_state->processes_states_array[i]->vector_clock[i];
        for (j = 0; j < processes_count; j++) {
            if (i != j) {
                if (global_state->processes_states_array[j]->vector_clock[i] > i_value) {
                    if (inconsistent_processes_boolean_array != NULL)
                        inconsistent_processes_boolean_array[i] = 1;
                    is_con = 0;
                    LOG_PRINT("C%i: C%i is inconsistent!", my_rank, i);
                    break;
                }
            }
        }
        if (inconsistent_processes_boolean_array != NULL && inconsistent_processes_boolean_array[i] == 0) {
            LOG_PRINT("C%i: C%i is consistent", my_rank, i);
        }
    }
    return is_con;
}
int is_consistent4(GlobalState* global_state, int processes_count, int * inconsistent_processes_boolean_array,
        int my_rank) {
    int is_con = 1;
    int i;

    for (i = 0; i < processes_count; i++) {
        if (i == my_rank)
            continue;
        int event_clock[2];
        event_clock[0] = global_state->processes_states_array[my_rank]->vector_clock[my_rank];
        event_clock[1] = global_state->processes_states_array[my_rank]->vector_clock[i];

        int gcut_clock[2];
        gcut_clock[0] = global_state->gcut[my_rank];
        gcut_clock[1] = global_state->gcut[i];
        vector_clock_compare res = compare_clocks(gcut_clock, event_clock, 2);
        if (res != VC_CONCURRENT && res != VC_EQUAL)
        //if(local_state->vector_clock[i]  > gcut[i]) //the event knows something the global state doesn't know
                {
            is_con = 0;

            LOG_PRINT("C%i: C%i is inconsistent", my_rank, i);
            if (inconsistent_processes_boolean_array != NULL)
                inconsistent_processes_boolean_array[i] = 1;

        } else {

            LOG_PRINT("C%i: C%i is consistent", my_rank, i);
        }

    }
    return is_con;
}
int is_consistent(GlobalState* global_state, int processes_count, int * inconsistent_processes_boolean_array,
        int my_rank) {
    int is_con = 1;
    int i;

    for (i = 0; i < processes_count; i++) {
        if (i == my_rank)
            continue;

        vector_clock_compare res = compare_clocks(global_state->gcut,
                global_state->processes_states_array[my_rank]->vector_clock, processes_count);
        if (res != VC_CONCURRENT && res != VC_EQUAL)
        //if(local_state->vector_clock[i]  > gcut[i]) //the event knows something the global state doesn't know
                {
            is_con = 0;

            LOG_PRINT("C%i: C%i is inconsistent", my_rank, i);
            if (inconsistent_processes_boolean_array != NULL)
                inconsistent_processes_boolean_array[i] = 1;

        } else {

            LOG_PRINT("C%i: C%i is consistent", my_rank, i);
        }

    }
    return is_con;
}
