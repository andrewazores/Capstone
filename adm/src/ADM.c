#include "mpi.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "helpers.h"
#include "LocationHelpers.h"
#include "Controller/controller.h"
#include "logger.h"

#include <limits.h>
#include <unistd.h>
//#define NULL ((void*)0)

// shared memory
#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <stdio.h>
#include <math.h>
///normal guassian distribution

typedef enum {
    vector_clock_update, event_update
} update_type;

#ifndef Pi
#define Pi 3.141592653589793238462643
#endif

void update_knowledgeVector(KnowledgeVector* v1, KnowledgeVector* v2);
void log_knowledge_vector(KnowledgeVector* vec);
void log_variablevaluation_process(VariableValuation* variables_array, int process_rank);
void broadcast_location_neighbors(long current_timestamp);
LocalEvent* prepare_local_event();
long poissonRandom(double u, double lambda, double std);

////////////////////////////////////////////
//Global vars:
int iteration_index = 0;
KnowledgeVector knowledge_vector;
MPI_Request send_request;
int first_send = 1;
char *distribution_type;
double variance = 2;
long x = 5;
int rank;
int pcount;
int n;
int size;
int flag = 0;
int my_col, my_row, rows, cols;
VehicleState* vehicle_state;
DirectionSwitch *directionSwitchQueue;
int directionSwitchQueueCount = 0;
int process_messages_sent = 0;
Vector* checkpoints;
Vector* failure_injected_checkpoints;
int current_checkpoints_index;
int checkpoints_count;
double radius = 50;
////////////////////////////////////////////

void log_knowledge_vector(KnowledgeVector* vec) {
    int j;
    for (j = 0; j < vec->count; j++) {
        //	LOG_PRINT("P%i: knowledge_vector.vector_clock[%i]=%i, data_index=%i",rank,j,vec->vector_clock[j],vec->locations[j].index);

    }
}

//poisson distribution, std not used.
long poissonRandom(double u, double lambda, double std) {
    long k = 0;
    double p = 1.0;
    double L = exp(-lambda);
    do {
        k++;
        p *= ((double) rand() / (double) RAND_MAX);
    } while (p >= L);

    LOG_PRINT("P%i: poisson var:%i", rank, k - 1);
    return k - 1;

}

// Normal distrubution
long phi(double x, double mean, double std) {
    x = (2 * x) - 1;
    double L, K, w;
    /* constants */
    double const a1 = 0.31938153, a2 = -0.356563782, a3 = 1.781477937;
    double const a4 = -1.821255978, a5 = 1.330274429;

    L = fabs(x);
    K = 1.0 / (1.0 + 0.2316419 * L);
    w = 1.0
            - 1.0 / sqrt(2 * Pi) * exp(-L * L / 2)
                    * (a1 * K + a2 * K * K + a3 * pow(K, 3) + a4 * pow(K, 4) + a5 * pow(K, 5));

    if (x < 0) {
        w = 1.0 - w;
    }

    w = (w * std) + mean;
    LOG_PRINT("P%i: generated random variable with input:%d mean:%d is %i", rank, x, mean, (long )(w));

    return (long) (w);
}

long get_random_number(double x, double mean, double std) {
    char*tmp = "poisson";
    if (strcmp(distribution_type, tmp) == 0) {
        return poissonRandom(x, mean, std);
    }

    else
        return phi(x, mean, std);
}
void update_knowledgeVector(KnowledgeVector* dest, KnowledgeVector* src) {
    //LOG_PRINT("p%i: logging my vector clock before update:",rank);
    //log_knowledge_vector(dest);
    //LOG_PRINT("p%i: logging received vector clock:",rank);
    //log_knowledge_vector(src);
    int i;
    for (i = 0; i < dest->count; i++) {
        if (dest->vector_clock[i] < src->vector_clock[i]) {
            dest->vector_clock[i] = src->vector_clock[i];
            dest->locations[i] = src->locations[i];
        }
    }
    //LOG_PRINT("p%i: logging my vector clock after update:",rank);
    //log_knowledge_vector(dest);
}
void log_variablevaluation_process(VariableValuation* variables_array, int process_rank) {
    int i;
    for (i = 0; i < variables_array->count; i++) {
        LOG_PRINT("P%i: %s=%d", process_rank, variables_array->state_name_array[i],
                variables_array->state_value_array[i]);
    }
}

void send_local_state_to_controller(update_type type) {

    LocalEvent* event = prepare_local_event();

    //log_variablevaluation_process(&(event->variables),  rank);
    int my_controller_id = rank + (size / 2);
    char b[sizeof(LocalEvent)];
    MPI_Request send_request;
    memcpy(b, event, sizeof(LocalEvent));
    int tag = 1;
    if (type == vector_clock_update) {
        tag = 4;
    }
    MPI_Isend(&b, sizeof(LocalEvent), MPI_CHAR, my_controller_id, tag,
    MPI_COMM_WORLD, &send_request); //TAG =1, message from process to controller

}

LocalEvent* prepare_local_event() {
    LocalEvent* event = malloc(sizeof(LocalEvent));
    event->timestamp = (int) get_time_now();
    event->event_id = knowledge_vector.vector_clock[rank];
    event->process_rank = rank;
    int i = 0;
    for (i = 0; i < checkpoints_count; i++) {
        char var[10];
        char var_base[25] = "p%d_in_cp%d_radius";
        sprintf(var, var_base, rank, i);
        strcpy(event->variables.state_name_array[i], var);
        double distance = sqrt(
                pow((checkpoints[i].x - vehicle_state->location.x), 2)
                        + pow((checkpoints[i].y - vehicle_state->location.y), 2));

        if (distance > radius) {
            event->variables.state_value_array[i] = 0;
        } else {
            event->variables.state_value_array[i] = 1;
            LOG_PRINT("P%i: %s is true", rank, var);
            LOG_PRINT_RESULT("%i,%s is true", rank, var);
        }

    }
    char* var_name = "i";
    strcpy(event->variables.state_name_array[i], var_name);
    event->variables.state_value_array[i] = iteration_index;

    i++;
    var_name = "x";
    strcpy(event->variables.state_name_array[i], var_name);
    event->variables.state_value_array[i] = knowledge_vector.locations[rank].x;

    i++;
    var_name = "y";
    strcpy(event->variables.state_name_array[i], var_name);
    event->variables.state_value_array[i] = knowledge_vector.locations[rank].y;
    i++;

    //-------------------------------------------
    event->variables.count = i;

    for (i = 0; i < knowledge_vector.count; i++) {
        event->vector_clock[i] = knowledge_vector.vector_clock[i];
    }
    event->flag = 1;
    return event;
}

int get_front_rank() {
    if (my_row == 0) {
        return -1;
    }
    return rank - cols;
}
int get_behind_rank() {
    if (my_row == rows - 1) {
        return -1;
    }
    return rank + cols;
}
int get_left_rank() {
    if (my_col == 0) {
        return -1;
    }
    return rank - 1;
}
int get_right_rank() {
    if (my_col == cols - 1) {
        return -1;
    }
    return rank + 1;
}
int get_row_by_rank(int r) {
    return rank / cols;
}
int get_col_by_rank(int r, int my_row) {
    return rank - (my_row * cols);
}

void broadcast_location_neighbors(long current_timestamp) {
    //LOG_PRINT("P%i: Broadcasting at iteration:%i",rank,iteration_index);

    //convert struct to char array to send
    char b[sizeof(knowledge_vector)];
    memcpy(b, &knowledge_vector, sizeof(knowledge_vector));
    int j;
    MPI_Status status;

    int count;
    int to_notify[4];

    count = 4;

    to_notify[0] = get_front_rank();
    to_notify[1] = get_behind_rank();
    to_notify[2] = get_left_rank();
    to_notify[3] = get_right_rank();

    for (j = 0; j < count; j++) {
        //int other_rank=neighbors[j];
        if (to_notify[j] != -1) {
            if (first_send == 0)
                MPI_Wait(&send_request, &status);
            //sleep(1);
            LOG_PRINT("P%i: sending location to:p%i", rank, to_notify[j]);
            MPI_Isend(&b, sizeof(knowledge_vector), MPI_CHAR, to_notify[j], 0,
            MPI_COMM_WORLD, &send_request);
            //LOG_PRINT_TIMELINE(rank,"%i,%i,S,%i,%i",current_timestamp,iteration_index,j, knowledge_vector.vector_clock[rank]);
            process_messages_sent++;
            first_send = 0;
        }
    }
}
void broadcast_location_all(long current_timestamp) {
    //LOG_PRINT("P%i: Broadcasting at iteration:%i",rank,iteration_index);

    //convert struct to char array to send
    char b[sizeof(knowledge_vector)];
    memcpy(b, &knowledge_vector, sizeof(knowledge_vector));
    int j;
    MPI_Status status;

    for (j = 0; j < pcount; j++) {
        //int other_rank=neighbors[j];
        if (j != rank) {
            if (first_send == 0)
                MPI_Wait(&send_request, &status);
            //sleep(1);
            LOG_PRINT("P%i: sending location to:p%i", rank, j);
            MPI_Isend(&b, sizeof(knowledge_vector), MPI_CHAR, j, 0,
            MPI_COMM_WORLD, &send_request);
            //LOG_PRINT_TIMELINE(rank,"%i,%i,S,%i,%i",current_timestamp,iteration_index,j, knowledge_vector.vector_clock[rank]);
            process_messages_sent++;
            first_send = 0;
        }
    }
}
void set_row_col() {
    my_row = rank / cols;
    my_col = rank - (my_row * cols);
}

void switch_direction(int direction) {
//	//with random probability inject failure
//	int r=rand()%100;
//	if(r<60)
//	{
//		LOG_PRINT_RESULT("i,injecting failure");
//		return;
//	}
    if (vehicle_state->velocity.y == 0 && vehicle_state->velocity.x < 0) //going west
            {
        if (direction == 0) //North
                {
            vehicle_state->velocity.y = -(vehicle_state->velocity.x);
            vehicle_state->velocity.x = 0;
        }

        if (direction == 2) {
            // go south
            vehicle_state->velocity.y = vehicle_state->velocity.x;
            vehicle_state->velocity.x = 0;
        }
    } else if (vehicle_state->velocity.y > 0 && vehicle_state->velocity.x == 0) //going north
            {
        if (direction == 1) //West
                {
            vehicle_state->velocity.x = -(vehicle_state->velocity.y);
            vehicle_state->velocity.y = 0; //going left means driving in -ve x axis only
        }
        if (direction == 3) //East
                {
            vehicle_state->velocity.x = (vehicle_state->velocity.y);
            vehicle_state->velocity.y = 0;
        }
    } else if (vehicle_state->velocity.y < 0 && vehicle_state->velocity.x == 0) //going south
            {
        if (direction == 1) //West
                {
            vehicle_state->velocity.x = vehicle_state->velocity.y;
            vehicle_state->velocity.y = 0;
        }
        if (direction == 3) //East
                {

            vehicle_state->velocity.x = -(vehicle_state->velocity.y);
            vehicle_state->velocity.y = 0;
        }
    } else if (vehicle_state->velocity.y == 0 && vehicle_state->velocity.x > 0) //going east
            {
        if (direction == 0) //North
                {
            vehicle_state->velocity.y = (vehicle_state->velocity.x);

            vehicle_state->velocity.x = 0;
        }
        if (direction == 2) //South
                {
            vehicle_state->velocity.y = -(vehicle_state->velocity.x);

            vehicle_state->velocity.x = 0;
        }
    }
    LOG_PRINT("P%i: new velocity:x:%d, y:%d", rank, vehicle_state->velocity.x, vehicle_state->velocity.y);

}

void update_position(long current_timestamp) {
    //Update new position
    update_location(vehicle_state);
    // neighbors will know about my new position via a sensor(eg: red light), however in simulation we use shared memory.
    //update_my_location_at_neighbors();
    knowledge_vector.locations[rank] = vehicle_state->location;
    knowledge_vector.vector_clock[rank]++;
    //my position changed...inform controller and broadcast to processes

    //LOG_PRINT_TIMELINE(rank,"U,%i,%i,%d,%d",current_timestamp,iteration_index,vehicle_state->location.x,vehicle_state->location.y);
    LOG_PRINT_RESULT("%i,U,%i,%i,%d,%d", rank, current_timestamp, iteration_index, vehicle_state->location.x,
            vehicle_state->location.y);

    broadcast_location_neighbors(current_timestamp);
    update_type type = event_update;
    send_local_state_to_controller(type);
}
int update_direction(VehicleState* vehicle_state, Vector checkpoint) {
    //check if vehicle is close to the checkpoint x coordinate by atleast radius.
    if (fabs(vehicle_state->location.x - checkpoint.x) > radius) {
        //switch direction towards either east or west to get to x.
        if (vehicle_state->location.x - checkpoint.x > 0) {
            //vehicle should switch direction to east
            switch_direction(1);
        } else {
            switch_direction(3);
        }
        return 1;
    } else if (fabs(vehicle_state->location.y - checkpoint.y) > radius) {
        //switch direction towards either east or west to get to x.
        if (vehicle_state->location.y - checkpoint.y > 0) {
            //vehicle should switch direction to south
            switch_direction(2);
        } else {
            switch_direction(0);
        }
        return 1;
    } else {
        // I am in radius of the checkpoint!
        //stay here for a while
        return 0;
    }
}
void send_termination_signal() {

    char b[1];
    int j;
    for (j = 1; j < pcount; j++) {
        MPI_Request send_request;
        LOG_PRINT("P%i: sending termination signal to:P%i", rank, j);
        MPI_Isend(&b, 1, MPI_CHAR, j, 1, MPI_COMM_WORLD, &send_request);
        process_messages_sent++;
    }

}
int main(int argc, char * argv[]) {

    MPI_Init(0, 0);
    MPI_Comm_size(MPI_COMM_WORLD, &size);
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    //usleep(100)
    LOG_PRINT_RESULT("%i,Init process", rank);
    LOG_PRINT("P%i: Init process", rank);
    char *ptr;
    rows = atoi(argv[1]);
    cols = atoi(argv[2]);
    char* exp_folder = argv[3]; ///home/m6mostaf/Dropbox/WaterlooWork/datasets/AutonmousDriving/

    double initial_distance = strtod(argv[4], &ptr);
    double initial_velocity = strtod(argv[5], &ptr);
    LOG_PRINT("P%i: Parameters read successfully", rank);
    distribution_type = argv[6];
    int pos_update_mean = atoi(argv[7]);
    radius = strtod(argv[8], &ptr);

    LOG_PRINT("P%i: Parameters read successfully", rank);
    // Init MPI

    if ((rows * cols) * 2 != size) {
        perror("invalid number of rows,cols and np for decentralized monitor");
        return 0;
    }

    sleep(5);
    if (rank >= size / 2) {
        LOG_PRINT("P%i: Init process", rank);
        LOG_PRINT("P%i: Distribution Type:%s", rank, distribution_type);
        pcount = size / 2;
        int my_process_rank = rank - (size / 2);
        //1- get  monitor file path
        char automaton_file[1000];
        char automaton_file_base[1000] = "/%s/%d/automaton.my";
        sprintf(automaton_file, automaton_file_base, exp_folder, my_process_rank);
        //3-- get  state-process mapping file path
        char initial_state_file[1000];
        char initial_state_file_base[1000] = "%s/%d/initial_state.my";
        sprintf(initial_state_file, initial_state_file_base, exp_folder, my_process_rank);
        //1- get  monitor file path
        char conjunct_mapping_file[1000];
        char conjunct_mapping_file_base[1000] = "%s/%d/conjunct_mapping.my";
        sprintf(conjunct_mapping_file, conjunct_mapping_file_base, exp_folder, my_process_rank);
        //5- init controller
        //Create 2d array, automaton as rows, and automaton params as cols
        int automaton_param_count = pcount; //5
        int automaton_count_per_process = 1;
        int** automaton_parameters = malloc(sizeof(int*) * automaton_count_per_process);
        int i;
        rank = my_process_rank;
        set_row_col();
        //LOG_PRINT("C%i:my row:%i, my_col:%i",my_process_rank, my_row,my_col);
        for (i = 0; i < automaton_count_per_process; i++) {

            automaton_parameters[i] = malloc(sizeof(int) * automaton_param_count);
            int j;
            for (j = 0; j < pcount; j++) {
                automaton_parameters[i][j] = j; //parameters stay the same (property hard coded not parametrized)
            }
        }
        init_controller(initial_state_file, automaton_file, conjunct_mapping_file, size / 2, my_process_rank,
                automaton_count_per_process, automaton_parameters, automaton_param_count);
        //6- start controller
        start_controller();

        LOG_PRINT("C%i: FINALIZED.", my_process_rank);

    } else {

        LOG_PRINT("P%i: Init process", rank);
        LOG_PRINT("P%i: Distribution Type:%s", rank, distribution_type);
        pcount = size / 2;
        knowledge_vector.count = pcount;
        //init knowledge vector

        int k;
        for (k = 0; k < knowledge_vector.count; k++) {
            knowledge_vector.vector_clock[k] = 0;
        }
        vehicle_state = malloc(sizeof(VehicleState));
        set_row_col();

        int switched_dir = 0; // 0:North 1:West, 2:south, 3 East
        vehicle_state->location.x = my_col * initial_distance;
        vehicle_state->location.y = my_row * initial_distance;
        vehicle_state->velocity.x = 0; //driving in y axis only initially
        vehicle_state->velocity.y = initial_velocity;
        vehicle_state->distance_behind = vehicle_state->distance_front = vehicle_state->distance_left =
                vehicle_state->distance_right = initial_distance;

        long start_time = get_time_now();
        vehicle_state->timestamp = (int) start_time;
        iteration_index = 0;

        srand(rank);
        long next_pos_update = start_time
                + get_random_number(((double) rand() / (double) RAND_MAX), pos_update_mean, variance); //pos_update_period;
        int received_signal = 0;
        char mission_checkpoints_file[1000];
        char mission_checkpoints_file_base[1000] = "%s/%d/mission.txt";
        sprintf(mission_checkpoints_file, mission_checkpoints_file_base, exp_folder, rank);
        LOG_PRINT("P%i: loading checkpoints file:%s", rank, mission_checkpoints_file);
        checkpoints = load_mission_file(mission_checkpoints_file, &checkpoints_count);
        failure_injected_checkpoints = load_mission_file(mission_checkpoints_file, &checkpoints_count);
        if (rank == 2) {
            failure_injected_checkpoints[1].x = -failure_injected_checkpoints[1].x;
        }
        while (1) {
            if (current_checkpoints_index == checkpoints_count) // || knowledge_vector.vector_clock[rank]> 200*size))
                    {
                LOG_PRINT("P%i: Will terminate", rank);
                //send_termination_signal();
                break;
            }

            long current_timestamp = get_time_now();

            if (current_timestamp >= next_pos_update) {
                LOG_PRINT("P%i: New location update", rank);
                int updated = update_direction(vehicle_state, failure_injected_checkpoints[current_checkpoints_index]);

                update_position(get_time_now());
                iteration_index++;
                if (updated == 0) //vehicle in radius of the checkpoint
                        {
                    // stay here for a while!
                    next_pos_update = get_time_now()
                            + get_random_number(((double) rand() / (double) RAND_MAX), pos_update_mean + 3, variance); //pos_update_period;//phi(rand()%100)//;
                    current_checkpoints_index++;
                } else {

                    next_pos_update = get_time_now()
                            + get_random_number(((double) rand() / (double) RAND_MAX), pos_update_mean, variance); //pos_update_period;//phi(rand()%100)//;
                }

            }
            MPI_Status status;
            MPI_Status recv_request;
            MPI_Iprobe( MPI_ANY_SOURCE, MPI_ANY_TAG, MPI_COMM_WORLD, &flag, &status);
            if (flag) {

                LOG_PRINT("P%i: received something", rank);
                if (status.MPI_TAG == 0) {
                    //message from a fellow process

                    char recvMsg[sizeof(knowledge_vector)];
                    MPI_Recv(&recvMsg, sizeof(knowledge_vector), MPI_CHAR, status.MPI_SOURCE, 0,
                    MPI_COMM_WORLD, &recv_request);
                    KnowledgeVector other; //Re-make the struct
                    memcpy(&other, recvMsg, sizeof(other));
                    LOG_PRINT("P%i: Received message from P%i at iteration:%i: other.x:%d,other.y:%d,", rank,
                            status.MPI_SOURCE, iteration_index, other.locations[status.MPI_SOURCE].x,
                            other.locations[status.MPI_SOURCE].y);

                    //LOG_PRINT_TIMELINE(rank,"%i,%i,R,%i",current_timestamp,iteration_index,status.MPI_SOURCE);
                    //update my knowledge vector with other's knowledge vector
                    knowledge_vector.vector_clock[rank]++;
                    update_knowledgeVector(&knowledge_vector, &other);

                    update_type type = vector_clock_update;
                    send_local_state_to_controller(type);
                    LOG_PRINT("P%i: Event sent to controller", rank);

                } else if (status.MPI_TAG == 1) //termination from leader
                        {

                    LOG_PRINT("P%i: Received Termination signal.", rank);
                    char recvMsg[1];
                    MPI_Recv(&recvMsg, 1, MPI_CHAR, status.MPI_SOURCE, 1,
                    MPI_COMM_WORLD, &recv_request);

                    break;

                } else if (status.MPI_TAG == 4) {
                    //Signal violation from my controller.
                    LOG_PRINT("P%i: Received SIGNAL VIOLATION.", rank);
                    char recvMsg[5];
                    MPI_Recv(&recvMsg, 5, MPI_CHAR, status.MPI_SOURCE, 4,
                    MPI_COMM_WORLD, &recv_request);
                    //LOG_PRINT_TIMELINE(rank,"%i,%i,V",current_timestamp,iteration_index);
                    received_signal = 1;
                    if (rank == 0) {
                        //send_termination_signal();
                    }
                    //break;

                } else {
                    LOG_PRINT("P%i: Received unknown signal:%i.", rank, status.MPI_TAG);
                }
            }

        }
        int execution_in_seconds = get_time_now() - start_time;
        LOG_PRINT_RESULT("%i,time,%i", rank, execution_in_seconds);
        // notify other processes
        int j;
        LocalEvent over;
        over.flag = 3;
        over.process_rank = rank;
        over.event_id = -1;
        LOG_PRINT("P%i: FINISHED, notifying controller.", rank);
        //send end of log message to controller

        if (received_signal == 0) {
            int my_controller_id = rank + (size / 2);
            LOG_PRINT("P%i: send termination signal to C%i", rank, rank);
            MPI_Request send_request;
            MPI_Isend(&over, sizeof(over), MPI_CHAR, my_controller_id, 3,
            MPI_COMM_WORLD, &send_request);
        }
        //LOG_PRINT_RESULT("%i,Event,%i",rank,knowledge_vector.vector_clock[rank]);
        LOG_PRINT_RESULT("%i,processes_messages,%i", rank, process_messages_sent);

    }
    MPI_Barrier(MPI_COMM_WORLD);
    MPI_Finalize();
    return 0;
}
