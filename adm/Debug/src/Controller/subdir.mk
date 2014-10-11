################################################################################
# Automatically-generated file. Do not edit!
################################################################################

# Add inputs and outputs from these tool invocations to the build variables 
C_SRCS += \
../src/Controller/automaton.c \
../src/Controller/centralizedController.c \
../src/Controller/controller.c \
../src/Controller/utilities.c \
../src/Controller/vector_clocks.c 

OBJS += \
./src/Controller/automaton.o \
./src/Controller/centralizedController.o \
./src/Controller/controller.o \
./src/Controller/utilities.o \
./src/Controller/vector_clocks.o 

C_DEPS += \
./src/Controller/automaton.d \
./src/Controller/centralizedController.d \
./src/Controller/controller.d \
./src/Controller/utilities.d \
./src/Controller/vector_clocks.d 


# Each subdirectory must supply rules for building sources it contributes
src/Controller/%.o: ../src/Controller/%.c
	@echo 'Building file: $<'
	@echo 'Invoking: Cross GCC Compiler'
	mpicc -O0 -g3 -Wall -c -fmessage-length=0 -MMD -MP -MF"$(@:%.o=%.d)" -MT"$(@:%.o=%.d)" -o "$@" "$<"
	@echo 'Finished building: $<'
	@echo ' '


