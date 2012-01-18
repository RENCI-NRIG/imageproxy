#!/bin/bash

# Parameter 1: Image location
# Parameter 2: Bukkit name
# Parameter 3: Image type (filesystem/kernel/ramdisk)
# Parameter 4: Base directory into which to bundle
# Parameter 5: Signature of image

export PATH=${EC2_HOME}/bin:${PATH}

if [ -z ${IMAGEPROXY_LOG} ]; then
	IMAGEPROXY_LOG=/tmp/imageproxy.register.log
fi

if [ -z ${DATE} ]; then
	DATE=`date`
fi

MIN_PARAMS=5
if [ $# -ne $MIN_PARAMS ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "Too few parameters specified to registration script; $MIN_PARAMS required." | tee -a $IMAGEPROXY_LOG
    exit 1
fi

echo "[$DATE] Preparing to register new image..." >> $IMAGEPROXY_LOG

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

echo "[$DATE] Parameter #1(Image location) is $1" >> $IMAGEPROXY_LOG
IMG_LOCATION=$1

echo "[$DATE] Parameter #2(Bukkit name) is $2" >> $IMAGEPROXY_LOG
BUKKIT_NAME=$2

COMPRESSED_FILESYSTEM=false
echo "[$DATE] Parameter #3(Image type) is $3" >> $IMAGEPROXY_LOG
if [ "$3" = "KERNEL" ]; then
    BUNDLE_PARAM="--kernel true"
elif [ "$3" = "RAMDISK" ]; then
    BUNDLE_PARAM="--ramdisk true"
elif [ "$3" = "ZFILESYSTEM" ]; then
    COMPRESSED_FILESYSTEM=true
fi

echo "[$DATE] Parameter #4(Bundle path) is $4" >> $IMAGEPROXY_LOG
echo "[$DATE] Parameter #5(Signature) is $5" >> $IMAGEPROXY_LOG
TMP_DIR="$4/$5"
echo "[$DATE] Creating working directory $TMP_DIR" >> $IMAGEPROXY_LOG
RESULT=`mkdir $TMP_DIR 2>> $IMAGEPROXY_LOG`

check_exit_code

# Uncompress compressed images before attempting to bundle
if $COMPRESSED_FILESYSTEM; then
    echo "[$DATE] Compressed filesystem specified; preparing to decompress." >> $IMAGEPROXY_LOG
    RESULT=`file -i $IMG_LOCATION 2>> $IMAGEPROXY_LOG`
    check_exit_code

    UNCOMPRESSED_IMG="$TMP_DIR/$(basename $IMG_LOCATION).uncompressed"
    if [ -n "$(echo $RESULT | grep -i 'gzip\|compress')" ]; then
        UNCOMPRESS_CMD="gzip -dc"
    elif [ -n "$(echo $RESULT | grep -i bzip2)" ]; then
        UNCOMPRESS_CMD="bzip2 -dc"
    elif [ -n "$(echo $RESULT | grep -i lzma)" ]; then
        UNCOMPRESS_CMD="lzma -dc"
    fi

    if [ -n "$UNCOMPRESS_CMD" ]; then
        echo "[$DATE] Uncompressing image..." >> $IMAGEPROXY_LOG
        echo "[$DATE] $UNCOMPRESS_CMD $IMG_LOCATION > $UNCOMPRESSED_IMG" >> $IMAGEPROXY_LOG
        RESULT=`$UNCOMPRESS_CMD $IMG_LOCATION > $UNCOMPRESSED_IMG 2>> $IMAGEPROXY_LOG`
        check_exit_code
    else
        echo -n "[$DATE] " >> $IMAGEPROXY_LOG
        echo -n "Unsupported compressed image type. Exiting. " | tee -a $IMAGEPROXY_LOG
        echo "Check $IMAGEPROXY_LOG for more information."
        echo "" >> $IMAGEPROXY_LOG
        exit 1
    fi

    IMG_LOCATION=$UNCOMPRESSED_IMG
    echo "[$DATE] Image successfully uncompressed." >> $IMAGEPROXY_LOG
fi

## Bundling image
BUNDLE_CMD="euca-bundle-image -d $TMP_DIR -i $IMG_LOCATION $BUNDLE_PARAM"
echo "[$DATE] Bundling image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $BUNDLE_CMD" >> $IMAGEPROXY_LOG
RESULT=`$BUNDLE_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Extract the name of the bundled image's manifest file, in preparaion for uploading the image
BUNDLE_MANIFEST_NAME=`echo $RESULT 2> /dev/null| grep "Generating manifest" | awk '{print $NF}'`
if [ -z ${BUNDLE_MANIFEST_NAME} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Unable to bundle image. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    exit 1
fi

## Uploading the bundled image
UPLOAD_CMD="euca-upload-bundle -b $BUKKIT_NAME -m $BUNDLE_MANIFEST_NAME"
echo "[$DATE] Uploading bundled image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $UPLOAD_CMD" >> $IMAGEPROXY_LOG
RESULT=`$UPLOAD_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Extracting uploaded image's name, in preparation for registration
UPLOADED_IMAGE_NAME=`echo $RESULT 2> /dev/null| grep "Uploaded image as" | awk '{print $NF}'`
if [ -z ${UPLOADED_IMAGE_NAME} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Unable to upload bundle. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    exit 1
fi

## Registering the uploaded image
REGISTER_CMD="euca-register $UPLOADED_IMAGE_NAME"
echo "[$DATE] Registering image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $REGISTER_CMD" >> $IMAGEPROXY_LOG
RESULT=`$REGISTER_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Extracting the assigned image id
IMAGE_ID=`echo $RESULT 2> /dev/null| grep "IMAGE" | awk '{print $NF}'`
if [ -z ${IMAGE_ID} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Unable to register image. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    exit 1
fi

# Remove TMP_DIR (thereby cleaning up)
rm -rf $TMP_DIR

## Return the registered image id
echo "$IMAGE_ID"
