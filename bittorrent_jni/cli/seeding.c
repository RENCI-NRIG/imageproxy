#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>

#include <libtransmission/resume.c>
#include <libtransmission/transmission.h>
#include <libtransmission/bencode.h>
#include <libtransmission/torrent.h>
#include <libtransmission/tr-getopt.h>
#include <libtransmission/utils.h> /* tr_wait_msec */
#include <libtransmission/platform.h>
#include <libtransmission/web.h> /* tr_webRun */
#include <sqlite3.h>

/***
****
***/

#define MEM_K 1024
#define MEM_K_STR "KiB"
#define MEM_M_STR "MiB"
#define MEM_G_STR "GiB"
#define MEM_T_STR "TiB"

#define DISK_K 1024
#define DISK_B_STR   "B"
#define DISK_K_STR "KiB"
#define DISK_M_STR "MiB"
#define DISK_G_STR "GiB"
#define DISK_T_STR "TiB"

#define SPEED_K 1024
#define SPEED_B_STR   "B/s"
#define SPEED_K_STR "KiB/s"
#define SPEED_M_STR "MiB/s"
#define SPEED_G_STR "GiB/s"
#define SPEED_T_STR "TiB/s"

char  *	rootDir=NULL;
char  * database=NULL;
char  * configDir="/settings";
char  * downloadDir="/download";
tr_session  * h=NULL;
tr_benc       settings;
sqlite3 *db = NULL;

static
char* readProperties(char* );

static 
void sessionInit(const char* root)
{
	if(h==NULL)
	{
		char cDir[strlen(root)+strlen(configDir)];
		char dDir[strlen(root)+strlen(downloadDir)];
		sprintf(cDir,"%s%s", root, configDir);
		sprintf(dDir, "%s%s", root, downloadDir);
		struct stat cDirStat, dDirStat;
		int cDirExist=stat(cDir, &cDirStat);
		int dDirExist=stat(dDir, &dDirStat);
		if(!S_ISDIR(cDirStat.st_mode))
		{
			fprintf(stderr, "couldn't find config directory %s\n",cDir);
			exit(1);
		}
		else if(!S_ISDIR(dDirStat.st_mode))
		{
			fprintf(stderr, "couldn't find download directory %s\n",dDir);
			exit(1);
		}
		tr_formatter_mem_init( MEM_K, MEM_K_STR, MEM_M_STR, MEM_G_STR, MEM_T_STR );
		tr_formatter_size_init( DISK_K,DISK_K_STR, DISK_M_STR, DISK_G_STR, DISK_T_STR );
		tr_formatter_speed_init( SPEED_K, SPEED_K_STR, SPEED_M_STR, SPEED_G_STR, SPEED_T_STR );
		tr_bencInitDict( &settings, 0 );
		tr_sessionLoadSettings( &settings, cDir, NULL);
		tr_bencDictAddStr( &settings, TR_PREFS_KEY_DOWNLOAD_DIR, dDir );
	  	h = tr_sessionInit( "client", cDir, FALSE, &settings );
	}
	//reset the SEEDING status to 0 if it's not null
	char* updateString = "UPDATE IMAGES SET SEEDING=0 WHERE SEEDING=1";
	char* errMsg=NULL;
	int rc= sqlite3_exec(db, updateString, NULL, NULL, &errMsg);
	if(rc!=SQLITE_OK)
	{
		perror(errMsg);
		exit;
	}
}

static
void seeding(void * args)
{
	tr_torrent *tor=args;

	const tr_stat * st;
	const char * messageName[] = { NULL, "Tracker gave a warning:",
                                             "Tracker gave an error:",
                                             "Error:" };
	char downloadedImage[strlen(rootDir)+strlen(downloadDir)+1+strlen(tor->info.files[0].name)];
	sprintf(downloadedImage, "%s%s/%s", rootDir, downloadDir, tor->info.files[0].name);
printf(tor->info.files[0].name);
	tr_torrentStart( tor );
	while(1)
	{
        	tr_wait_msec( 200 );
		st = tr_torrentStat( tor );
        	if( st->activity & TR_STATUS_STOPPED )
            	    	break;
		else if( st->activity & TR_STATUS_SEED )
		{
			if(access(downloadedImage, F_OK)!=0)//the downloaded file has been deleted
			{
				unlink(downloadedImage);
				unlink(tor->info.torrent);
				//tr_torrentFree(tor);
				char* resumeName=getResumeFilename(tor);
				unlink(resumeName);
				tr_free(resumeName);
				tr_fdTorrentClose(tor->session, tor->uniqueId);
				printf("%s stops seeding\n", downloadedImage);
				return;
			}
			printf("%s is seeding\n", tor->info.torrent);
		}
		else if( (st->activity & TR_STATUS_CHECK_WAIT) || (st->activity & TR_STATUS_CHECK) );
		else
		{
			fprintf( stderr, "%s should be seeding\n", tor->info.torrent);
		}
        	if( messageName[st->error] )
		{
            		fprintf( stderr, "\n%s: %s\n", messageName[st->error], st->errorString );
		}
	}
	tr_torrentFree(tor);
}

int main(int argc, char* argv[])
{
	if(argc==3)
	{
		rootDir = argv[1];
		database =argv[2];
	}
	else
	{
		fprintf(stderr, "the arguments' amount is incorrent, we expect to clarify the root directory.\n");
		fprintf(stderr, "seeding ROOTDIRECTORY DATABASE_FILE\n");
		return -1;
	}
        char lockpath[strlen(rootDir)+strlen(configDir)+strlen("/lock")];
	sprintf(lockpath, "%s%s%s", rootDir, configDir, "/lock");

	int fildes=open(lockpath, O_RDWR|O_CREAT, S_IRUSR|S_IWUSR|S_IRGRP|S_IROTH);
	if(fildes==-1)
	{
		perror("fail to open the database!");
		return -1;
	}
	int status=lockf(fildes, F_TLOCK, 0);
	if(status==-1)
	{
		perror("another seeding process is running!");
		return -1;
	}
	
	
	int rc = sqlite3_open(database, &db);
	if(rc!=SQLITE_OK)
	{
		fprintf(stderr, "%s\n", sqlite3_errmsg(db));
		return -1;
	}
	else
		printf("database %s is opened successfully.\n", database);

	sessionInit(rootDir);
	
	int rows=0;
	int columns=0;
	char **azResult;
	char *queryMsg;
	char* sql="SELECT * FROM IMAGES WHERE STATUS=1 AND SEEDING=0";
	while(1)
	{
		int queryResult=sqlite3_get_table(db, sql, &azResult,&rows, &columns, &queryMsg);
		if(queryResult!=SQLITE_OK)
		{
			fprintf(stderr, "%s\n", sqlite3_errmsg(db));
			return -1;
		}
		else
			sqlite3_free(queryMsg);
		for(int i=0;i<rows;i++)
		{
			//update seeding flag in database
			char update[strlen("UPDATE IMAGES SET SEEDING=1 WHERE GUID=")+2+strlen(azResult[i*columns+columns])];
			char* updateMsg;
			sprintf(update, "UPDATE IMAGES SET SEEDING=1 WHERE GUID='%s'", azResult[i*columns+columns]);
			int updateResult=sqlite3_exec(db, update, NULL, NULL, &updateMsg);
			if(updateResult==SQLITE_OK)
				sqlite3_free(updateMsg);
			else
				fprintf(stderr, "%s\n", updateMsg);
			//seeding image "hash"
			int error;
		        tr_ctor     * ctor;
		        tr_torrent  * tor = NULL;
		        uint8_t     * fileContents;
		        size_t        fileLength;
			char* torrentPath=azResult[i*columns+columns+4];
		
			ctor = tr_ctorNew( h );
		        fileContents = tr_loadFile( torrentPath, &fileLength );
		        tr_ctorSetPaused( ctor, TR_FORCE, FALSE );
		        if( fileContents != NULL ) {
		                tr_ctorSetMetainfo( ctor, fileContents, fileLength );
		        }else if(!memcmp( torrentPath, "http", 4 )){
		                fprintf( stderr, "regedit inconsistent detected, logged url is %s\n", torrentPath );
		                tr_sessionClose( h );
		                return -1;
		        }else{
		                fprintf( stderr, "ERROR: Unrecognized torrent \"%s\".\n", torrentPath );
		                tr_sessionClose( h );
		                return -1;
		        }
		        tr_free( fileContents );
		        tor = tr_torrentNew( ctor, &error );
		        tr_ctorFree( ctor );

		        if( !tor )
		        {
		                fprintf( stderr, "Failed opening torrent file `%s'\n", torrentPath );
		                tr_sessionClose( h );
		                return -1;
		        }
		        tr_free(tor->info.files[0].name);
		        tor->info.files[0].name=azResult[i*columns+columns];
		        tr_threadNew( seeding, tor );
		}
		sqlite3_free_table(azResult);
		tr_wait_msec( 30000 );//check if there's new downloaded images every 30s
	}
	status=lockf(fildes, F_ULOCK, 0);
	if(status==-1)
		perror("fail to release the lock!");
}
