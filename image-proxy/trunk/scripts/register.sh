#!/bin/bash

#Parameter 1: Image location
#Parameter 2: Bukkit name
#Parameter 3: Image type (filesystem/kernel/ramdisk)

export PATH=${EC2_HOME}/bin:${PATH}

if [ -z ${IMAGEPROXY_LOG} ]; then
	IMAGEPROXY_LOG=/tmp/imageproxy.register.log
fi

if [ -z ${DATE} ]; then
	DATE=`date`
fi

if [ -n "$1" ]; then
    echo "[$DATE] Parameter #1(Image location) is $1" >> $IMAGEPROXY_LOG
fi 

if [ -n "$2" ]; then
    echo "[$DATE] Parameter #2(Bukkit name) is $2" >> $IMAGEPROXY_LOG
fi

if [ -n "$3" ]; then
    echo "[$DATE] Parameter #3(Image type) is $3" >> $IMAGEPROXY_LOG
    
    if [ "$3" = "KERNEL" ]; then
	BUNDLE_PARAM="--kernel true"
    fi

    if [ "$3" = "RAMDISK" ]; then
	BUNDLE_PARAM="--ramdisk true"
    fi
fi

if [ -z ${PROXY_EUCA_KEY_DIR} ]; then
    echo "Need to set PROXY_EUCA_KEY_DIR"
    echo "[$DATE] Need to set PROXY_EUCA_KEY_DIR" >> $IMAGEPROXY_LOG
    exit 1
fi  

echo "[$DATE] Using PROXY_EUCA_KEY_DIR as ${PROXY_EUCA_KEY_DIR}" >> $IMAGEPROXY_LOG

source ${PROXY_EUCA_KEY_DIR}/eucarc

function exit_cleanly {
  EXIT=`echo $?`
  if [ $EXIT != "0" ]
  then
    echo "Exception while executing script. Check logs at $IMAGEPROXY_LOG"
    echo "[$DATE] $RESULT Exit code of $EXIT for last command.  Exiting" >> $IMAGEPROXY_LOG
    exit 1
  fi
}   

echo "[$DATE] Bundling image" >> $IMAGEPROXY_LOG
echo "[$DATE] euca-bundle-image -i $1 $BUNDLE_PARAM" >> $IMAGEPROXY_LOG

##bundling image, path of which is passed as a parameter to the script
RESULT=`euca-bundle-image -i $1 $BUNDLE_PARAM 2>> $IMAGEPROXY_LOG`

exit_cleanly

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

##extracting the name of the bundled image's manifest file, which is required for uploading the image to euca storage server
BUNDLE_MANIFEST_NAME=`echo $RESULT 2> /dev/null| grep "Generating manifest" | awk '{print $NF}'`

if [ -z ${BUNDLE_MANIFEST_NAME} ]; then
	echo "Unable to bundle image, exiting. Check logs at $IMAGEPROXY_LOG"
	echo "[$DATE] Unable to bundle image, exiting." >> $IMAGEPROXY_LOG
	exit 1
fi


echo "[$DATE] Uploading bundled image" >> $IMAGEPROXY_LOG
echo "[$DATE] euca-upload-bundle -b $2 -m $BUNDLE_MANIFEST_NAME" >> $IMAGEPROXY_LOG

##uploading the bundled image
RESULT=`euca-upload-bundle -b $2 -m $BUNDLE_MANIFEST_NAME 2>> $IMAGEPROXY_LOG`

exit_cleanly

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

##extracting uploaded image's name, which is required for registration
UPLOADED_IMAGE_NAME=`echo $RESULT 2> /dev/null| grep "Uploaded image as" | awk '{print $NF}'`

if [ -z ${UPLOADED_IMAGE_NAME} ]; then
	echo "Unable to upload bundle, exiting. Check logs at $IMAGEPROXY_LOG"
	echo "[$DATE] Unable to upload bundle, exiting." >> $IMAGEPROXY_LOG
	exit 1
fi


echo "[$DATE] Registering image" >> $IMAGEPROXY_LOG
echo "[$DATE] euca-register $UPLOADED_IMAGE_NAME" >> $IMAGEPROXY_LOG

##registering the uploaded file
RESULT=`euca-register $UPLOADED_IMAGE_NAME 2>> $IMAGEPROXY_LOG`

exit_cleanly

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

##extracting the image id assigned by the euca server
IMAGE_ID=`echo $RESULT 2> /dev/null| grep "IMAGE" | awk '{print $NF}'`

if [ -z ${IMAGE_ID} ]; then
	echo "Unable to register image, exiting. Check logs at $IMAGEPROXY_LOG"
	echo "[$DATE] Unable to register image, exiting." >> $IMAGEPROXY_LOG
	exit 1
fi

# remove parts files from /tmp
PARTNAME=`basename $1`.part
MANNAME=`basename $1`.manifest.xml
rm -f /tmp/$PARTNAME.*
rm -f /tmp/$MANNAME

##returning the registered image id
echo "$IMAGE_ID"


