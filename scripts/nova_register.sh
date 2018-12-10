#!/bin/bash

# Parameter 1: Image location
# Parameter 2: Bukkit name
# Parameter 3: Image type (filesystem/kernel/ramdisk)
# Parameter 4: Base directory into which to bundle
# Parameter 5: Signature of image
# Parameter 6: Timeout, after which we declare the image to have failed
#              the registration process.

if [ -z ${IMAGEPROXY_LOG} ]; then
	IMAGEPROXY_LOG=/tmp/imageproxy.register.log
fi

if [ -z ${DATE} ]; then
	DATE=`date`
fi

NUM_PARAMS=6
if [ $# -ne $NUM_PARAMS ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo "Wrong number of parameters specified to registration script; $NUM_PARAMS required." | tee -a $IMAGEPROXY_LOG
    exit 1
fi

echo "[$DATE] Preparing to register new image..." >> $IMAGEPROXY_LOG

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
        kill -USR1 $$
        exit 1
    fi
}   

echo "[$DATE] Parameter #1(Image location) is $1" >> $IMAGEPROXY_LOG
IMG_LOCATION=$1

echo "[$DATE] Parameter #2(Bukkit name) is $2" >> $IMAGEPROXY_LOG
BUKKIT_NAME=$2

COMPRESSED_FILESYSTEM=false
ADD_PARAM="--disk-format ami --container-format ami"
echo "[$DATE] Parameter #3(Image type) is $3" >> $IMAGEPROXY_LOG
if [ "$3" = "KERNEL" ]; then
    ADD_PARAM="--disk-format aki --container-format aki"
elif [ "$3" = "RAMDISK" ]; then
    ADD_PARAM="--disk-format ari --container-format ari"
elif [ "$3" = "ZFILESYSTEM" ]; then
    COMPRESSED_FILESYSTEM=true
elif [ "$3" = "QCOW2" ]; then
    ADD_PARAM="--disk-format qcow2 --container-format bare"
fi

echo "[$DATE] Parameter #4(Bundle path) is $4" >> $IMAGEPROXY_LOG
echo "[$DATE] Parameter #5(Signature) is $5" >> $IMAGEPROXY_LOG
echo "[$DATE] Parameter #6(Registration timeout) is $6" >> $IMAGEPROXY_LOG
TMP_DIR="$4/$5"
TIMEOUT=$6

echo "[$DATE] Creating working directory $TMP_DIR" >> $IMAGEPROXY_LOG
RESULT=`mkdir $TMP_DIR 2>> $IMAGEPROXY_LOG`

check_exit_code

# Register a handler to clean up TMP_DIR, upon termination.
trap 'rm -rf $TMP_DIR' USR1 USR2

# Uncompress compressed images before attempting to bundle
if $COMPRESSED_FILESYSTEM; then
    echo "[$DATE] Compressed filesystem specified; preparing to decompress." >> $IMAGEPROXY_LOG
    RESULT=`file -i $IMG_LOCATION 2>> $IMAGEPROXY_LOG`
    check_exit_code

    UNCOMPRESSED_IMG="$TMP_DIR/$(basename $IMG_LOCATION).uncompressed"
    IMG_WORKFILE="$TMP_DIR/$(basename $IMG_LOCATION).tmp"
    if [ -n "$(echo $RESULT | grep -i 'gzip\|compress')" ]; then
        UNCOMPRESS_CMD="gzip -dc"
    elif [ -n "$(echo $RESULT | grep -i bzip2)" ]; then
        UNCOMPRESS_CMD="bzip2 -dc"
    elif [ -n "$(echo $RESULT | grep -i lzma)" ]; then
        UNCOMPRESS_CMD="lzma -dc"
    fi

    if [ -n "$UNCOMPRESS_CMD" ]; then
        echo "[$DATE] Uncompressing image..." >> $IMAGEPROXY_LOG
        echo "[$DATE] $UNCOMPRESS_CMD $IMG_LOCATION > $IMG_WORKFILE" >> $IMAGEPROXY_LOG
        RESULT=`$UNCOMPRESS_CMD $IMG_LOCATION > $IMG_WORKFILE 2>> $IMAGEPROXY_LOG`
        check_exit_code
    else
        echo -n "[$DATE] " >> $IMAGEPROXY_LOG
        echo -n "Unsupported compressed image type. Exiting. " | tee -a $IMAGEPROXY_LOG
        echo "Check $IMAGEPROXY_LOG for more information."
        echo "" >> $IMAGEPROXY_LOG
        kill -USR1 $$
        exit 1
    fi

    # Now, check if the workfile is a tar archive; if so untar it
    RESULT=`file -i $IMG_WORKFILE 2>> $IMAGEPROXY_LOG`
    check_exit_code
    if [ -n "$(echo $RESULT | grep -i 'x-tar')" ]; then
        # OK - we were sent a tarred, zipped filesystem.
        # We operate under the pre-agreed convention that the tar archive will
        # contain a file named "filesystem"
        echo "[$DATE] Uncompressed file is a tar archive. Un-tarring..." >> $IMAGEPROXY_LOG
        mv $IMG_WORKFILE $IMG_WORKFILE.tar
        echo "[$DATE] tar -OSxf $IMG_WORKFILE.tar filesystem > $IMG_WORKFILE" >> $IMAGEPROXY_LOG
        RESULT=`tar -OSxf $IMG_WORKFILE.tar filesystem > $IMG_WORKFILE 2>> $IMAGEPROXY_LOG`
        check_exit_code
        rm $IMG_WORKFILE.tar
    fi

    # Finally, move the image workfile to its final name
    mv $IMG_WORKFILE $UNCOMPRESSED_IMG
 
    IMG_LOCATION=$UNCOMPRESSED_IMG
    echo "[$DATE] Image successfully uncompressed." >> $IMAGEPROXY_LOG
fi

## Add the image into glance
IMG_ID="$BUKKIT_NAME/$(basename $IMG_LOCATION)"
ADD_CMD="openstack image create $IMG_ID --file $IMG_LOCATION $ADD_PARAM --public"
echo "[$DATE] Adding image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $ADD_CMD" >> $IMAGEPROXY_LOG
RESULT=`eval $ADD_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Extract the image's UUID
IMG_UUID=`openstack image list -f value | grep $IMG_ID | awk '{print $1}'`

# Clean up working dir by calling the trap
kill -USR2 $$

## Return the registered image id
echo "$IMG_UUID"
