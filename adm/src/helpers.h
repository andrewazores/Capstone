#define MAX_VEHICLES_COUNT 30

typedef struct {
    double x;
    double y;
} Vector;

typedef struct {
    Vector location;
    Vector velocity;
    long timestamp;
    double distance_front;
    double distance_behind;
    double distance_left;
    double distance_right;

} VehicleState;

typedef struct {
    int direction;
    int front_switched;
    int left_switched;
    int right_switched;
    int behind_switched;
    int me_switched;
    int unique_identifier;
} DirectionSwitch;

typedef struct {
    int direction;
    int unique_identifier;
} LeaderMessage;

typedef struct {
    int vector_clock[MAX_VEHICLES_COUNT];
    Vector locations[MAX_VEHICLES_COUNT];
    LeaderMessage message;
    int count;
} KnowledgeVector;
typedef struct {
    int buffer_id;
    Vector *buffer_pointer;
    key_t buffer_key;
    int buffer_size;

} SharedMemory;

long get_time_now();
const char* getfield(char* line, int num);
void update_location(VehicleState* vehicle_state);
void update_acceleration(VehicleState* vehicle_state, Vector* acceleration);

int* load_leader_switch_probs(char* file, int* count, int*, int);
Vector* load_mission_file(char* file, int* count);
