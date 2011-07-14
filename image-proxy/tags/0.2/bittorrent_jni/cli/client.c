#include "orca_imageproxy_BTDownload.h"

#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <libtransmission/transmission.h>
#include <libtransmission/bencode.h>
#include <libtransmission/torrent.h>
#include <libtransmission/tr-getopt.h>
#include <libtransmission/platform.h>
#include <libtransmission/utils.h> /* tr_wait_msec */
#include <libtransmission/version.h>
#include <libtransmission/web.h> /* tr_webRun */

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

const char  * downloadDir="/download";
const char  * configDir="/settings";
char 	    * rootfolder=NULL;
static tr_bool waitingOnWeb;
static tr_bool verify                = 0;
tr_session  * h=NULL;
tr_benc       settings;

static void
onTorrentFileDownloaded( tr_session   * session UNUSED,
                         long           response_code UNUSED,
                         const void   * response,
                         size_t         response_byte_count,
                         void         * ctor )
{
    tr_ctorSetMetainfo( ctor, response, response_byte_count );
    waitingOnWeb = FALSE;
}

static char*
getResumeFilename( const tr_info *info )
{
    char * base = tr_metainfoGetBasename( info );
    char * filename = tr_strdup_printf( "%s" TR_PATH_DELIMITER_STR "%s.resume",
                                        tr_getResumeDir( h ), base );
    tr_free( base );
    return filename;
}

JNIEXPORT void JNICALL Java_orca_imageproxy_BTDownload_initSession
  (JNIEnv * env, jobject obj, jstring path)
{
	if(h==NULL)
	{
		rootfolder=(*env)->GetStringUTFChars(env, path, 0);
		char* download_path[strlen(rootfolder)+strlen(downloadDir)];
		char* config_path[strlen(rootfolder)+strlen(configDir)];
		sprintf(download_path, "%s%s", rootfolder, downloadDir);
		sprintf(config_path, "%s%s", rootfolder, configDir);

		tr_formatter_mem_init( MEM_K, MEM_K_STR, MEM_M_STR, MEM_G_STR, MEM_T_STR );
		tr_formatter_size_init( DISK_K,DISK_K_STR, DISK_M_STR, DISK_G_STR, DISK_T_STR );
		tr_formatter_speed_init( SPEED_K, SPEED_K_STR, SPEED_M_STR, SPEED_G_STR, SPEED_T_STR );
		tr_bencInitDict( &settings, 0 );
		tr_sessionLoadSettings( &settings, config_path, "BT" );
		tr_bencDictAddStr( &settings, TR_PREFS_KEY_DOWNLOAD_DIR, download_path );
	  	h = tr_sessionInit( "client", config_path, FALSE, &settings );
	}
}

JNIEXPORT jstring JNICALL Java_orca_imageproxy_BTDownload_getFileLength
  (JNIEnv * env, jobject obj, jstring url, jstring path)
{
	int           parse_result;
	tr_ctor     * ctor;
	tr_info	    setme_info;
	const char *torrentPath=(*env)->GetStringUTFChars(env, url, 0);
	if(h==NULL)
	{
		char *errbuffer="the session is not initialized";
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);

		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		return NULL;
	}
	ctor = tr_ctorNew( h );
    	tr_ctorSetPaused( ctor, TR_FORCE, FALSE );
	if( !memcmp( torrentPath, "http", 4 ) ) {
		tr_webRun( h, torrentPath, NULL, onTorrentFileDownloaded, ctor );
		waitingOnWeb = TRUE;
		while( waitingOnWeb ) tr_wait_msec( 1000 );
	    } else {
		char errbuffer[strlen(torrentPath)+50];
		sprintf( errbuffer, "ERROR: Unrecognized torrent \"%s\".\n", torrentPath );
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);

		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		return NULL;
	    }
    	
	parse_result = tr_torrentParse( ctor, &setme_info );
    	tr_ctorFree( ctor );
	if( parse_result==TR_PARSE_ERR)
    	{
		char errbuffer[strlen(torrentPath)+100];
		sprintf( errbuffer, "Failed opening torrent file '%s' maybe because of failing on connecting to server\n", torrentPath );
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);

		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		return NULL;
    	}
	if(setme_info.fileCount!=1)
	{
		char *errbuffer="one torrent should only contain one image file!";
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);
		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		return NULL;
	}
	int64_t length = setme_info.files[0].length;
	char result[strlen(setme_info.torrent)+50];
	sprintf(result, "%s%"PRIu64, setme_info.torrent, length);
	(*env)->ReleaseStringUTFChars(env, url, torrentPath);
	return (*env)->NewStringUTF(env, result);
}

JNIEXPORT void JNICALL Java_orca_imageproxy_BTDownload_deleteImageBT
  (JNIEnv * env, jobject obj, jstring hash, jstring url)
{
	int parse_result;
	tr_ctor     * ctor;
	tr_info     setme_info;
	uint8_t     * fileContents;
	size_t        fileLength;

	char *torrentPath=(*env)->GetStringUTFChars(env, url, 0);
	char *filename=(char *)(*env)->GetStringUTFChars(env, hash, 0);

	if(h==NULL)
	{
		char *errbuffer="the session is not initialized";
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);

		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		(*env)->ReleaseStringUTFChars(env, hash, filename);
		return;
	}
	ctor = tr_ctorNew( h );
	fileContents = tr_loadFile( torrentPath, &fileLength );
	tr_ctorSetPaused( ctor, TR_FORCE, FALSE );
	if( fileContents != NULL ) {
	        tr_ctorSetMetainfo( ctor, fileContents, fileLength );
	}else{
		//it haven't been downloaded yet
		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		(*env)->ReleaseStringUTFChars(env, hash, filename);
		return;
	}
	tr_free( fileContents );
	parse_result = tr_torrentParse( ctor, &setme_info );
	tr_ctorFree( ctor );
    	if( parse_result==TR_PARSE_ERR)
    	{
		char errbuffer[strlen(torrentPath)+50];
		sprintf( errbuffer, "Failed opening torrent file `%s'\n", torrentPath );
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);
		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		(*env)->ReleaseStringUTFChars(env, hash, filename);
		return;
    	}
	else
	{
		char downloadedImage[strlen(rootfolder)+strlen(downloadDir)+1+strlen(filename)];
		sprintf(downloadedImage, "%s%s/%s", rootfolder, downloadDir, filename);
		//delete the image, torrent and resume
		unlink(downloadedImage);
		char *resumeName=getResumeFilename(&setme_info);
		unlink(resumeName);
		tr_free(resumeName);
		unlink(setme_info.torrent);
	}
	(*env)->ReleaseStringUTFChars(env, url, torrentPath);
	(*env)->ReleaseStringUTFChars(env, hash, filename);
}

JNIEXPORT jstring JNICALL Java_orca_imageproxy_BTDownload_btdownloadfromURL
  (JNIEnv * env, jobject obj, jstring url, jstring path, jstring hash)
{
	int           error;
	tr_ctor     * ctor;
	tr_torrent  * tor = NULL;
	int completeFlag=0;

	const char *torrentPath=(*env)->GetStringUTFChars(env, url, 0);
	char *filename=(char *)(*env)->GetStringUTFChars(env, hash, 0);
	char *originalName=NULL;
	const tr_stat * st;
	const char * messageName[] = { NULL, "Tracker gave a warning:",
                                             "Tracker gave an error:",
                                             "Error:" };
	if(h==NULL)
	{
		char *errbuffer="the session is not initialized";
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);

		if(originalName!=NULL)
			tor->info.files[0].name=originalName;
		tr_torrentFree(tor);
		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		(*env)->ReleaseStringUTFChars(env, hash, filename);
		return NULL;
	}

	ctor = tr_ctorNew( h );
    	tr_ctorSetPaused( ctor, TR_FORCE, FALSE );
	if( !memcmp( torrentPath, "http", 4 ) ) {
		tr_webRun( h, torrentPath, NULL, onTorrentFileDownloaded, ctor );
		waitingOnWeb = TRUE;
		while( waitingOnWeb ) tr_wait_msec( 1000 );
	    } else {
		char errbuffer[strlen(torrentPath)+50];
		sprintf( errbuffer, "ERROR: Unrecognized torrent \"%s\".\n", torrentPath );
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);
		if(originalName!=NULL)
			tor->info.files[0].name=originalName;
		tr_torrentFree(tor);
		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		(*env)->ReleaseStringUTFChars(env, hash, filename);
		return NULL;
	    }
    	tor = tr_torrentNew( ctor, &error );
    	tr_ctorFree( ctor );

    	if( !tor )
    	{
		char errbuffer[strlen(torrentPath)+50];
                if(error==TR_PARSE_ERR)
                        sprintf( errbuffer, "Failed opening torrent file '%s' maybe because of failing on connecting to server\n", torrentPath );
                else if(error==TR_PARSE_DUPLICATE)
                        sprintf( errbuffer, "Failed opening torrent file '%s', because it may have been downloading already\n", torrentPath );
		jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
		(*env)->ThrowNew(env, newExcCls, errbuffer);
		if(originalName!=NULL)
			tor->info.files[0].name=originalName;
		tr_torrentFree(tor);
		(*env)->ReleaseStringUTFChars(env, url, torrentPath);
		(*env)->ReleaseStringUTFChars(env, hash, filename);
		return NULL;
    	}
	else//change the name of download file
	{
		tr_torrentSetDirty(tor);
		st = tr_torrentStat( tor );
        	if( messageName[st->error] )
		{
			char errbuffer[strlen(st->errorString)+strlen(messageName[st->error])+10];
            		sprintf( errbuffer, "\n%s: %s\n", messageName[st->error], st->errorString );
			jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
			(*env)->ThrowNew(env, newExcCls, errbuffer);
			if(originalName!=NULL)
				tor->info.files[0].name=originalName;
			tr_torrentFree(tor);
			(*env)->ReleaseStringUTFChars(env, url, torrentPath);
			(*env)->ReleaseStringUTFChars(env, hash, filename);
			return NULL;
		}
		if(tor->info.fileCount!=1)
		{
			char *errbuffer="one torrent should only contain one image file!";
			jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
			(*env)->ThrowNew(env, newExcCls, errbuffer);
			if(originalName!=NULL)
				tor->info.files[0].name=originalName;
			tr_torrentFree(tor);
			(*env)->ReleaseStringUTFChars(env, url, torrentPath);
			(*env)->ReleaseStringUTFChars(env, hash, filename);
			return NULL;
		}
		originalName=tor->info.files[0].name;
		tor->info.files[0].name=filename;
	}
    	tr_torrentStart( tor );
    	if( verify )
    	{
    	    	verify = 0;
            	tr_torrentVerify( tor );
    	}
	st = tr_torrentStat( tor );
	jstring correctHash=NULL;
	if(st->activity & TR_STATUS_SEED)
	{
		completeFlag=1;
		char entry[strlen(filename)];
		sprintf(entry, "%s", filename);
		jmethodID mid=(*env)->GetMethodID(env, (*env)->GetObjectClass(env,obj), "callbackComplete", "(Ljava/lang/String;)Ljava/lang/String;");
		correctHash=(jstring)(*env)->CallObjectMethod(env, obj, mid, (*env)->NewStringUTF(env, entry));
	}

	while(!completeFlag)
	{
        	tr_wait_msec( 200 );

		st = tr_torrentStat( tor );
        	if( st->activity & TR_STATUS_STOPPED )
            	    	break;
		else if( st->activity & TR_STATUS_SEED )
		{
			completeFlag=1;

			char filesize[20];
			sprintf(filesize, "%"PRIu64, tor->info.files[0].length);
			int entry_len=strlen(filename)+1+strlen(filesize)+1+1+1+strlen(tor->info.torrent);
			char entry[entry_len];
			sprintf(entry, "%s#%s#%d#%s", filename, filesize, 0, tor->info.torrent);
			if(originalName!=NULL)
				tor->info.files[0].name=originalName;
			tr_torrentFree(tor);
			jmethodID mid=(*env)->GetMethodID(env, (*env)->GetObjectClass(env,obj), "callbackComplete", "(Ljava/lang/String;)Ljava/lang/String;");
			correctHash=(jstring)(*env)->CallObjectMethod(env, obj, mid, (*env)->NewStringUTF(env, entry));
			(*env)->ReleaseStringUTFChars(env, url, torrentPath);
			(*env)->ReleaseStringUTFChars(env, hash, filename);
			return correctHash;
		}
        	if( messageName[st->error] )
		{
			char errbuffer[strlen(st->errorString)+strlen(messageName[st->error])+10];
            		sprintf( errbuffer, "\n%s: %s\n", messageName[st->error], st->errorString );
			jclass newExcCls=(*env)->FindClass(env,"java/lang/Exception");
			(*env)->ThrowNew(env, newExcCls, errbuffer);
			break;
		}
	}

	if(originalName!=NULL)
		tor->info.files[0].name=originalName;
	tr_torrentFree(tor);
	(*env)->ReleaseStringUTFChars(env, url, torrentPath);
	(*env)->ReleaseStringUTFChars(env, hash, filename);
	return correctHash;
}
