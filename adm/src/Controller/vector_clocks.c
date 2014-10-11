#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "vector_clocks.h"

void merge_clocks(int* dest, int* source, int len) {
	int i;
	for (i = 0; i < len; i++) {
		if (source[i] > dest[i]) {
			dest[i] = source[i];
		}
	}

}

vector_clock_compare compare_clocks(int* VC_1, int* VC_2, int len) {
	int bigger = 1, smaller = 1, equal = 1;
	vector_clock_compare result;
	int i;
	for (i = 0; i < len; i++) {
		if (VC_1[i] < VC_2[i]) {
			bigger = 0;
			equal = 0;
		} else if (VC_1[i] > VC_2[i]) {
			smaller = 0;
			equal = 0;
		}
	}
	if (equal == 1) {
		result = VC_EQUAL;
	} else if (bigger == 0 && smaller == 0) {
		result = VC_CONCURRENT;
	} else if (bigger == 1) {
		result = VC_FIRST_BIGGER;
	} else if (smaller == 1) {
		result = VC_FIRST_SMALLER;
	}
	return result;
}

