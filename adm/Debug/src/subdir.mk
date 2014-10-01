################################################################################
# Automatically-generated file. Do not edit!
################################################################################

# Add inputs and outputs from these tool invocations to the build variables 
C_SRCS += \
../src/ADM.c \
../src/LocationHelpers.c \
../src/helpers.c \
../src/interpreter.c \
../src/logger.c 

OBJS += \
./src/ADM.o \
./src/LocationHelpers.o \
./src/helpers.o \
./src/interpreter.o \
./src/logger.o 

C_DEPS += \
./src/ADM.d \
./src/LocationHelpers.d \
./src/helpers.d \
./src/interpreter.d \
./src/logger.d 


# Each subdirectory must supply rules for building sources it contributes
src/%.o: ../src/%.c
	@echo 'Building file: $<'
	@echo 'Invoking: Cross GCC Compiler'
	mpicc -O0 -g3 -Wall -c -fmessage-length=0 -MMD -MP -MF"$(@:%.o=%.d)" -MT"$(@:%.o=%.d)" -o "$@" "$<"
	@echo 'Finished building: $<'
	@echo ' '


