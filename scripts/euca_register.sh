#!/bin/bash

# Parameter 1: Image location
# Parameter 2: Bukkit name
# Parameter 3: Image type (filesystem/kernel/ramdisk)
# Parameter 4: Base directory into which to bundle
# Parameter 5: Signature of image
# Parameter 6: Timeout, after which we declare the image to have failed
#              the registration process.

export PATH=${EC2_HOME}/bin:${PATH}

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

## Bundling image
BUNDLE_CMD="euca-bundle-image -d $TMP_DIR -i $IMG_LOCATION $BUNDLE_PARAM"
echo "[$DATE] Bundling image..." >> $IMAGEPROXY_LOG
echo "[$DATE] $BUNDLE_CMD" >> $IMAGEPROXY_LOG
RESULT=`$BUNDLE_CMD 2>> $IMAGEPROXY_LOG`

check_exit_code

echo "[$DATE] $RESULT" >> $IMAGEPROXY_LOG

## Extract the name of the bundled image's manifest file, in preparation for uploading the image
BUNDLE_MANIFEST_NAME=`echo $RESULT 2> /dev/null| grep "Generating manifest" | awk '{print $NF}'`
if [ -z ${BUNDLE_MANIFEST_NAME} ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Unable to bundle image. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    kill -USR1 $$
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
    kill -USR1 $$
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
    kill -USR1 $$
    exit 1
fi

## Verify that the image id has become "available" to use
CHECK_CMD="euca-describe-images | grep $IMAGE_ID | awk '{print substr(\$0, index(\$0,\$4))}' | grep 'available'"
TIMECOUNT=0
RC=1
echo "[$DATE] Polling to ensure that image has become available." >> $IMAGEPROXY_LOG
echo "[$DATE] Timeout occurs after $TIMEOUT seconds." >> $IMAGEPROXY_LOG

# Doing something "clever" again - this loop executes in a background subshell.
# The TIMECOUNT and RC variables exist in parent and child *independently*.
# The trap set in the child is local to the child.
(
    trap 'exit $RC' ALRM
    while true
    do
        STATUS=`eval $CHECK_CMD`
        RC=$?
        if [ $RC -ne 0 ]; then
            TIMECOUNT=$((TIMECOUNT+10))
            sleep 10
        else
            break
        fi

        if [ $TIMECOUNT -ge $TIMEOUT ]; then
            break
        fi
    done
    exit $RC
) &

CHILDPID=$!

# Now, we loop in the parent, checking to see if the child has returned.
# If it has not, we increment TIMECOUNT and sleep.
# If it has, we exit the loop after setting RC to the exit status of the child.
# If we have not exited the loop, and TIMECOUNT has expired,
# we kill the child, and exit the loop.
while true
do
    JOBCOUNT=$(jobs -rp | wc -l)
    if [ $JOBCOUNT -ne 0 ]; then
        TIMECOUNT=$((TIMECOUNT+10))
        sleep 10
    else
        wait $CHILDPID
        RC=$?
        break
    fi

    if [ $TIMECOUNT -ge $TIMEOUT ]; then
        kill -ALRM $CHILDPID
        break
    fi
done

if [ $RC -ne 0 ]; then
    echo -n "[$DATE] " >> $IMAGEPROXY_LOG
    echo -n "Image failed to become available before timeout expired. Exiting. " | tee -a $IMAGEPROXY_LOG
    echo "Check $IMAGEPROXY_LOG for more information."
    echo "" >> $IMAGEPROXY_LOG
    # Make an attempt to clean up the image
    IMG_PREFIX=`basename $BUNDLE_MANIFEST_NAME | awk '{sub(/\.manifest\.xml/, "", $0); print $0}'`
    euca-deregister $IMAGE_ID
    euca-delete-bundle -b $BUKKIT_NAME -p $IMG_PREFIX
    kill -USR1 $$
    exit 1
fi

echo "[$DATE] Image $IMAGE_ID successfully verified as available." >> $IMAGEPROXY_LOG
# Clean up working dir by calling the trap
kill -USR2 $$

## Return the registered image id
echo "$IMAGE_ID"
