#!/bin/bash

# Parameter 1: Image ID

if [ -z ${IMAGEPROXY_LOG} ]; then
	IMAGEPROXY_LOG=/tmp/imageproxy.register.log
fi

if [ -z ${DATE} ]; then
	DATE=`date`
fi

NUM_PARAMS=1
if [ $# -ne $NUM_PARAMS ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "Wrong number of parameters specified to de-registration script; $NUM_PARAMS required." | tee -a $IMAGEPROXY_LOG
    exit 1
fi

echo "[$DATE] Preparing to delete image..." >> $IMAGEPROXY_LOG

if [ -z ${PROXY_EUCA_KEY_DIR} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "PROXY_EUCA_KEY_DIR environment variable undefined; please set. Exiting." | tee -a $IMAGEPROXY_LOG
    exit 1
fi

echo "[$DATE] Using PROXY_EUCA_KEY_DIR as ${PROXY_EUCA_KEY_DIR}" >> $IMAGEPROXY_LOG

if [ -f ${PROXY_EUCA_KEY_DIR}/eucarc ]; then
    source ${PROXY_EUCA_KEY_DIR}/eucarc
elif [ -f ${PROXY_EUCA_KEY_DIR}/novarc ]; then
    source ${PROXY_EUCA_KEY_DIR}/novarc
else
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "Directory $PROXY_EUCA_KEY_DIR does not contain a eucarc or novarc. Exiting." | tee -a $IMAGEPROXY_LOG
    exit 1
fi

function check_exit_code {
    if [ $? -ne 0 ]; then
        echo "Exception while executing script. Check $IMAGEPROXY_LOG for more information."
        echo "[$DATE] $RESULT Exit code of $EXIT for last command.  Exiting." >> $IMAGEPROXY_LOG
        exit 1
    fi
}   

echo "[$DATE] Parameter #1(Image ID) is $1" >> $IMAGEPROXY_LOG
IMG_ID=$1

## Delete the image
DELETE_CMD="openstack image delete $IMG_ID"
echo "[$DATE] Deleting image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $DELETE_CMD" >> $IMAGEPROXY_LOG
RESULT=`$DELETE_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code
