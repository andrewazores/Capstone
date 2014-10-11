#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "utilities.h"
#include "../logger.h"

char** split_states(char* input, char separator, int number_elements_max) {
	char** parts = (char**) malloc(number_elements_max * sizeof(char*));
	int partcount = 0;

	parts[partcount++] = input;

	char* ptr = input;
	while (*ptr) { //check if the string is over
		if (*ptr == separator) {
			*ptr = 0;
			parts[partcount++] = ptr + 1;
		}
		ptr++;
	}
	return parts;
}

const char* get_field(char* line, int num) {
	const char* tok;
	for (tok = strtok(line, ","); tok && *tok; tok = strtok(NULL, ",\n")) {
		if (!--num)
			return tok;
	}
	return NULL;
}

int get_max(int* array, int array_count) {
	int i;
	int max = -1;
	for (i = 0; i < array_count; i++) {
		if (max > array[i]) {
			max = array[i];
		}
	}
	return max;
}

int get_min(int* array, int array_count) {
	int i;
	int min;
	if (array_count > 0) {
		min = array[0];
	} else {
		return -1;
	}
	for (i = 0; i < array_count; i++) {
		if (min < array[i]) {
			min = array[i];
		}
	}
	return min;
}

int arrays_equal(int* array1, int count1, int* array2, int count2) {
	if (count1 != count2) {
		return 0;
	}
	//might not be sorted
	int i;
	for (i = 0; i < count1; i++) {
		int j;
		int found = 0;
		for (j = 0; j < count2; j++) {
			if (array1[i] == array2[j]) {
				found = 1;
			}
		}
		if (found == 0) {
			return 0;
		}
	}
	return 1;
}

int* get_pending_events_deep_copy(int* source, int local_events_count,
		int processes_count) {
	//LOG_PRINT("C%i: get_pending_events_deep_copy  !",-1);
	int* copy_events = malloc(sizeof(int) * MAX_PENDING_EVENTS_COUNT);
	int i;
	for (i = 0; i < local_events_count; i++) {
		copy_events[i] = source[i];
	}
	//LOG_PRINT("C%i: get_pending_events_deep_copy pointer:%i !",-1,copy_events);
	return copy_events;
}

int* copy_gcut_deep(int* source, int processes_count) {

	int* destination = malloc(processes_count * sizeof(int));
	int i;
	for (i = 0; i < processes_count; i++) {
		destination[i] = source[i];
	}
	return destination;
}

void copy_processes_states_to_token(ProcessState** source, SendableToken* token,
		int processes_count, int my_rank) {
	//
	//	int i;
	//	for(i=0;i<processes_count;i++)
	//	{
	//		int j;
	//		LOG_PRINT("C%i: copying global state variables for p%i to token, count:%i",my_rank,i,source[i]->variables.count);
	//
	//		for(j=0;j<source[i]->variables.count;j++)
	//		{
	//
	//			strcpy(token->variables_array[i].state_name_array[j], source[i]->variables.state_name_array[j]);
	//			token->variables_array[i].state_value_array[j]=source[i]->variables.state_value_array[j];
	//
	//		}
	//		token->variables_array[i].count=source[i]->variables.count;
	//	}
}

void copy_token_to_processes_states(SendableToken *t, GlobalState * s,
		int processes_count) {
	//The process that my token was sent to
	int i = t->destination;
	s->processes_states_array[i] = malloc(sizeof(ProcessState));
	s->processes_states_array[i]->vector_clock = malloc(sizeof(ProcessState));
	int j;
	for (j = 0; j < processes_count; j++) {
		s->processes_states_array[i]->vector_clock[j] = t->gcut[j];
	}
	for (j = 0; j < t->target_process_variables.count; j++) {
		strcpy(s->processes_states_array[i]->variables.state_name_array[j],
				t->target_process_variables.state_name_array[j]);
		s->processes_states_array[i]->variables.state_value_array[j] =
				t->target_process_variables.state_value_array[j];

	}
	s->processes_states_array[i]->variables.count =
			t->target_process_variables.count;

}
char *str_replace(const char *string, const char *substr,
		const char *replacement) {
	char *tok = NULL;
	char *newstr = NULL;
	char *oldstr = NULL;
	/* if either substr or replacement is NULL, duplicate string a let caller handle it */
	if (substr == NULL || replacement == NULL)
		return strdup(string);
	newstr = strdup(string);
	while ((tok = strstr(newstr, substr))) {
		oldstr = newstr;
		newstr = malloc(
				strlen(oldstr) - strlen(substr) + strlen(replacement) + 1);
		/*failed to alloc mem, free old string and return NULL */
		if (newstr == NULL) {
			free(oldstr);
			return NULL;
		}
		memcpy(newstr, oldstr, tok - oldstr);
		memcpy(newstr + (tok - oldstr), replacement, strlen(replacement));
		memcpy(newstr + (tok - oldstr) + strlen(replacement),
				tok + strlen(substr),
				strlen(oldstr) - strlen(substr) - (tok - oldstr));
		memset(newstr + strlen(oldstr) - strlen(substr) + strlen(replacement),
				0, 1);
		free(oldstr);
	}
	return newstr;
}

