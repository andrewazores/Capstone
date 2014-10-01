//logger.h
#define LOG_PRINT(...) log_print(__FILE__, __LINE__, __VA_ARGS__ )
#define LOG_PRINT_RESULT(...) log_print_result(__FILE__, __LINE__, __VA_ARGS__ )
#define LOG_PRINT_TIMELINE(...) log_print_timeline(__FILE__, __LINE__, __VA_ARGS__ )
#define LOG_PRINT_PROBS(...) log_print_probs(__FILE__, __LINE__, __VA_ARGS__ )
