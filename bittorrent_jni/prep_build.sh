#!/bin/sh
export TRANSMISSION_VERSION=2.13

if [ ! -e external ]; then
	mkdir external
fi
cd external

echo "Fetching transmission-$TRANSMISSION_VERSION..."
WGET=`which wget`
CURL=`which curl`
if [ "$WGET" != "" ] && [ -x $WGET ]; then
	$WGET http://download.m0k.org/transmission/files/transmission-$TRANSMISSION_VERSION.tar.bz2
else if [ "$CURL" != "" ] && [ -x $CURL ]; then
	$CURL http://download.m0k.org/transmission/files/transmission-$TRANSMISSION_VERSION.tar.bz2 > transmission-$TRANSMISSION_VERSION.tar.bz2
	else
		echo "Unable to find wget or curl, exiting"
		exit -1
	fi
fi

if [ "$?" != "0" ]; then
	echo "Unable to retrieve BT code, exiting"
	exit -1
fi

echo
echo "Untarring and re-arranging..."
tar -xjf transmission-$TRANSMISSION_VERSION.tar.bz2

mv transmission-$TRANSMISSION_VERSION/libtransmission ..
mv transmission-$TRANSMISSION_VERSION/third-party ..
mv transmission-$TRANSMISSION_VERSION/m4 ..

cd ..

cd m4
rm libtool.m4 ltoptions.m4 ltsugar.m4 lt~obsolete.m4 ltversion.m4
cd ..

echo
echo "Patching and running autoreconf procedures..."
patch -p1 < transmission_find_port.patch
patch -p1 < transmission_shared_lib.patch
autoreconf --install --force

echo
echo "Done. Now, please run ./configure"
