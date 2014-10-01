//logger.c
#include <stdio.h>
#include <stdarg.h>
#include <time.h>
#include <stdlib.h>
#include <string.h>

FILE *fp ;
FILE *fp2 ;
FILE *fp3 ;
FILE *fp4 ;
static int SESSION_TRACKER=0; //Keeps track of session
static int SESSION_TRACKER2=0; //Keeps track of session
static int SESSION_TRACKER3=0; //Keeps track of session
static int SESSION_TRACKER4=0; //Keeps track of session

void log_print_probs(char* filename, int line, char *fmt,...)
{
	va_list         list;
	char            *p, *r;
	double             e;
	int 				i;
	if(SESSION_TRACKER4 > 0)
		fp4 = fopen ("log_leader_probs.txt","a+");
	else
		fp4 = fopen ("log_leader_probs.txt","w");


	//fprintf(fp4,"%u,",(int)(time(NULL)));
	va_start( list, fmt );

	for ( p = fmt ; *p ; ++p )
	{
		if ( *p != '%' )//If simple string
		{
			fputc( *p,fp4 );
		}
		else
		{
			switch ( *++p )
			{
			/* string */
			case 's':
			{
				r = va_arg( list, char * );

				fprintf(fp4,"%s", r);
				continue;
			}

			/* integer */
			case 'i':
			{
				i = va_arg( list, int );

				fprintf(fp4,"%d", i);
				continue;
			}
			/* double */
			case 'd':
			{
				e = va_arg( list, double );

				fprintf(fp4,"%f", e);
				continue;
			}

			default:
				fputc( *p, fp4 );
			}
		}
	}
	va_end( list );
	//fprintf(fp2," [%s][line: %d] ",filename,line);
	fputc( '\n', fp4 );
	SESSION_TRACKER4++;
	fclose(fp4);
}

char* print_time()
{
	time_t t;
	char *buf;

	time(&t);
	buf = (char*)malloc(strlen(ctime(&t))+ 1);

	snprintf(buf,strlen(ctime(&t)),"%s ", ctime(&t));

	return buf;
}
void log_print(char* filename, int line, char *fmt,...)
{
	va_list         list;
	char            *p, *r;
	double             e;
	int 				i;
	if(SESSION_TRACKER > 0)
		fp = fopen ("log.txt","a+");
	else
		fp = fopen ("log.txt","w");

	fprintf(fp ,"%u ",(int)(time(NULL)));
	va_start( list, fmt );

	for ( p = fmt ; *p ; ++p )
	{
		if ( *p != '%' )//If simple string
		{
			fputc( *p,fp );
		}
		else
		{
			switch ( *++p )
			{
			/* string */
			case 's':
			{
				r = va_arg( list, char * );

				fprintf(fp,"%s", r);
				continue;
			}

			/* integer */
			case 'i':
			{
				i = va_arg( list, int );

				fprintf(fp,"%d", i);
				continue;
			}
			/* double */
			case 'd':
			{
				e = va_arg( list, double );

				fprintf(fp,"%f", e);
				continue;
			}

			default:
				fputc( *p, fp );
			}
		}
	}
	va_end( list );
	fprintf(fp," [%s][line: %d] ",filename,line);
	fputc( '\n', fp );
	SESSION_TRACKER++;
	fclose(fp);
}
void log_print_result(char* filename, int line, char *fmt,...)
{
	va_list         list;
	char            *p, *r;
	double             e;
	int 				i;
	if(SESSION_TRACKER2 > 0)
		fp2 = fopen ("log_results.txt","a+");
	else
		fp2 = fopen ("log_results.txt","w");


	fprintf(fp2,"%u,",(int)(time(NULL)));
	va_start( list, fmt );

	for ( p = fmt ; *p ; ++p )
	{
		if ( *p != '%' )//If simple string
		{
			fputc( *p,fp2 );
		}
		else
		{
			switch ( *++p )
			{
			/* string */
			case 's':
			{
				r = va_arg( list, char * );

				fprintf(fp2,"%s", r);
				continue;
			}

			/* integer */
			case 'i':
			{
				i = va_arg( list, int );

				fprintf(fp2,"%d", i);
				continue;
			}
			/* double */
			case 'd':
			{
				e = va_arg( list, double );

				fprintf(fp2,"%f", e);
				continue;
			}

			default:
				fputc( *p, fp2 );
			}
		}
	}
	va_end( list );
	//fprintf(fp2," [%s][line: %d] ",filename,line);
	fputc( '\n', fp2 );
	SESSION_TRACKER2++;
	fclose(fp2);
}
void log_print_timeline(char* filename, int line,int rank, char *fmt,...)
{
	va_list         list;
	char            *p, *r;
	double             e;
	int 				i;
	char file[1000];
	char file_base[1000]="timeline_%d.txt";
	sprintf(file,file_base,rank);

	if(SESSION_TRACKER3 > 0)
		fp3 = fopen (file,"a+");
	else
		fp3 = fopen (file,"w");

	fprintf(fp3,"%u,",(int)(time(NULL)));
	//fprintf(fp,"%s ",print_time());
	va_start( list, fmt );

	for ( p = fmt ; *p ; ++p )
	{
		if ( *p != '%' )//If simple string
		{
			fputc( *p,fp3 );
		}
		else
		{
			switch ( *++p )
			{
			/* string */
			case 's':
			{
				r = va_arg( list, char * );

				fprintf(fp3,"%s", r);
				continue;
			}

			/* integer */
			case 'i':
			{
				i = va_arg( list, int );

				fprintf(fp3,"%d", i);
				continue;
			}
			/* double */
			case 'd':
			{
				e = va_arg( list, double );

				fprintf(fp3,"%f", e);
				continue;
			}

			default:
				fputc( *p, fp3 );
			}
		}
	}
	va_end( list );
	//fprintf(fp3," [%s][line: %d] ",filename,line);
	fputc( '\n', fp3 );
	SESSION_TRACKER3++;
	fclose(fp3);

}
