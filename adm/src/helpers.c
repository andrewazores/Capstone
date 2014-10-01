#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include  <float.h>
#include "helpers.h"
#include "logger.h"

#define LONG_MAX +2147483647
const char* getfield(char* line, int num)
{
	const char* tok;
	for (tok = strtok(line, ",");
			tok && *tok;
			tok = strtok(NULL, ",\n"))
	{
		if (!--num)
			return tok;
	}
	return NULL;
}

long get_time_now()
{
	time_t now;
	time(&now);
	return (long)now;
}
void update_location(VehicleState* vehicle_state)
{
	float delta_time=(int)(get_time_now()-vehicle_state->timestamp);
	if(delta_time==0)
	{
		delta_time=0.1;
	}
	// displacement_x= vel_x * delta_time
	vehicle_state->location.x+=(vehicle_state->velocity.x * delta_time);
	vehicle_state->location.y+=(vehicle_state->velocity.y * delta_time);
	vehicle_state->timestamp=get_time_now();


}
void update_acceleration(VehicleState* vehicle_state, Vector* acceleration)
{
	vehicle_state->velocity.x*=(acceleration->x==0?1:acceleration->x);
	vehicle_state->velocity.y*=(acceleration->y==0?1:acceleration->y);
	update_location(vehicle_state);
}

int* load_leader_switch_probs(char* file,int* count,int*  leader_switch_probs_positive_count,int switch_prob)
{

	FILE* stream = fopen(file, "r");
	int c,newline_count=0;
	while ( (c=fgetc(stream)) != EOF ) {
		if ( c == '\n' )
			newline_count++;
	}
	fclose(stream);
	int k=0;
	int* probs=  malloc(newline_count*sizeof(int));
	stream = fopen(file, "r");
	char line[1024];
 	int i=0;
	while (fgets(line, 1024, stream))
	{
		char* tmp = strdup(line);
		if(tmp==NULL)
		{
			break;
		}
		probs[i]=atoi(get_field(tmp, 1));
		if(probs[i] <=switch_prob)
		{
			k++;
		}
		i++;

	}
	*leader_switch_probs_positive_count=k;
	*count=newline_count;
	return probs;
}

Vector* load_mission_file(char* file,int* count)
{

	FILE* stream = fopen(file, "r");
	int c,newline_count=0;
	while ( (c=fgetc(stream)) != EOF ) {
		if ( c == '\n' )
			newline_count++;
	}
	fclose(stream);
	LOG_PRINT("%i lines in file",newline_count);
	Vector* checkpoints=  malloc(newline_count*sizeof(Vector));
	stream = fopen(file, "r");
	char line[1024];
	char *ptr; //points to the unparsed parts of the string, ignore
	int i=0;
	while (fgets(line, 1024, stream))
	{
		char* tmp = strdup(line);
		if(tmp==NULL)
		{
			break;
		}
		 checkpoints[i].x=strtod(get_field(tmp, 1),&ptr);
		 tmp = strdup(line);
		 checkpoints[i].y=strtod(get_field(tmp, 2),&ptr);

		LOG_PRINT("loaded first value in mission plan file");
		i++;

	}
	*count=newline_count;
	return checkpoints;
}
