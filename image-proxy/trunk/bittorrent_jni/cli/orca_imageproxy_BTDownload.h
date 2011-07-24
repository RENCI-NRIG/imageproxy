/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class orca_imageproxy_BTDownload */

#ifndef _Included_orca_imageproxy_BTDownload
#define _Included_orca_imageproxy_BTDownload
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     orca_imageproxy_BTDownload
 * Method:    deleteImageBT
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_orca_imageproxy_BTDownload_deleteImageBT
  (JNIEnv *, jobject, jstring, jstring);

/*
 * Class:     orca_imageproxy_BTDownload
 * Method:    getFileLength
 * Signature: (Ljava/lang/String;)Ljava/lang/Long;
 */
JNIEXPORT jlong JNICALL Java_orca_imageproxy_BTDownload_getFileLength
  (JNIEnv *, jobject, jstring);

/*
 * Class:     orca_imageproxy_BTDownload
 * Method:    initSession
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_orca_imageproxy_BTDownload_initSession
  (JNIEnv *, jobject, jstring);

/*
 * Class:     orca_imageproxy_BTDownload
 * Method:    btdownloadfromURL
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT jstring JNICALL Java_orca_imageproxy_BTDownload_btdownloadfromURL
  (JNIEnv *, jobject, jstring, jstring);

#ifdef __cplusplus
}
#endif
#endif
