#ifndef VECTOR_CLOCKS_H
#define VECTOR_CLOCKS_H


typedef enum {VC_EQUAL,VC_FIRST_BIGGER,VC_FIRST_SMALLER,VC_CONCURRENT} vector_clock_compare;
vector_clock_compare compare_clocks(int* VC_1, int* VC_2,int len);
void merge_clocks(int* dest, int* source,int len);

#endif /* VECTOR_CLOCKS_H */
