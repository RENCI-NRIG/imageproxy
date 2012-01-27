#!/bin/bash

# Parameter 1: Image ID

export PATH=${EC2_HOME}/bin:${PATH}

if [ -z ${IMAGEPROXY_LOG} ]; then
	IMAGEPROXY_LOG=/tmp/imageproxy.register.log
fi

if [ -z ${DATE} ]; then
	DATE=`date`
fi

MIN_PARAMS=1
if [ $# -ne $MIN_PARAMS ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "Too few parameters specified to registration script; $MIN_PARAMS required." | tee -a $IMAGEPROXY_LOG
    exit 1
fi

echo "[$DATE] Preparing to deregister image..." >> $IMAGEPROXY_LOG

if [ -z ${PROXY_EUCA_KEY_DIR} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "PROXY_EUCA_KEY_DIR environment variable undefined; please set. Exiting." | tee -a $IMAGEPROXY_LOG
    exit 1
fi
echo "[$DATE] Using PROXY_EUCA_KEY_DIR as ${PROXY_EUCA_KEY_DIR}" >> $IMAGEPROXY_LOG
source ${PROXY_EUCA_KEY_DIR}/eucarc

function check_exit_code {
  if [ $? -ne 0 ]; then
    echo "Exception while executing script. Check $IMAGEPROXY_LOG for more information."
    echo "[$DATE] $RESULT Exit code of $EXIT for last command.  Exiting." >> $IMAGEPROXY_LOG
    exit 1
  fi
}   

echo "[$DATE] Parameter #1(Image ID) is $1" >> $IMAGEPROXY_LOG
IMG_ID=$1

## Discovering image information
FIND_CMD="euca-describe-images | grep ^IMAGE | grep $IMG_ID"
echo "[$DATE] Locating image to deregister..." >> $IMAGEPROXY_LOG
echo "[$DATE] $FIND_CMD" >> $IMAGEPROXY_LOG
RESULT=`$FIND_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Extract the image's bucket name and part prefix
BUKKIT_NAME=`echo $RESULT | awk '{print substr($3, 1, index($3,"/")-1)}'`
IMG_PREFIX=`echo $RESULT | awk '{str = substr($3, index($3, "/") + 1); sub(/\.manifest\.xml/, "", str); print str}'`
BUNDLE_MANIFEST_NAME=`echo $RESULT 2> /dev/null| grep "Generating manifest" | awk '{print $NF}'`
if [ -z ${BUKKIT_NAME} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Unable to determine image bucket. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    exit 1
fi
if [ -z ${IMG_PREFIX} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Unable to determine image prefix. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    exit 1
fi

## Deregistering the image
DEREGISTER_CMD="euca-deregister $IMG_ID"
echo "[$DATE] Deregistering image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $DEREGISTER_CMD" >> $IMAGEPROXY_LOG
RESULT=`$DEREGISTER_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Deleting the image
DELETE_CMD="euca-delete-bundle -b $BUKKIT_NAME -p $IMG_PREFIX"
echo "[$DATE] Deleting image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $DELETE_CMD" >> $IMAGEPROXY_LOG
RESULT=`$DELETE_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code
