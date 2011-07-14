if [ "$IMAGEPROXY_SRC" == "" ]; then
	echo Please define IMAGEPROXY_SRC
	exit 1
fi

if test -z $AXIS2_HOME; then
    echo Please define AXIS2_HOME
    exit 1
fi

IMGPROXYJAR=imageproxy-1.0-SNAPSHOT.jar

if [ ! -e $IMAGEPROXY_SRC/target/$IMGPROXYJAR ]; then
	echo Please run mvn install
	exit 1
fi

CLASSPATH=.

for i in $(ls $AXIS2_HOME/lib/*.jar)
do
    CLASSPATH=${CLASSPATH}:${i}
done

CLASSPATH=${CLASSPATH}:$IMAGEPROXY_SRC/target/$IMGPROXYJAR

export CLASSPATH

java  orca.imageproxy.client.imageproxyclient $1 $2 $3 $4 $5 $6 $7 $8
