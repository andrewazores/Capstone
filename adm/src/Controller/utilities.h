
#include "../interpreter.h"






const char* get_field(char* line, int num);
char** split_states(char* input,char separator,int number_elements_max);
int* get_pending_events_deep_copy(int* source, int local_events_count,int processes_count);
void copy_processes_states_to_token(ProcessState** source,SendableToken* token,int processes_count,int my_rank  );
void copy_token_to_processes_states(SendableToken *t,GlobalState* s,int processes_count );
int* copy_gcut_deep(int* source ,int processes_count);
char * str_replace ( const char *string, const char *substr, const char *replacement );

int get_min(int* array, int array_count);

int get_max(int* array, int array_count);
int arrays_equal(int* array1, int count1, int *array2, int count2);
