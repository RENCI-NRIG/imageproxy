#!/bin/bash
#set -x
IMGPROXY_PATH=/opt/imageproxy
AXIS2_HOME=/opt/imageproxy/axis2
JAVA_HOME=/usr/java/latest

pushd $IMGPROXY_PATH
nohup su geni-orca -c "export JAVA_HOME=${JAVA_HOME}; export AXIS2_HOME=${AXIS2_HOME}; ./axis2/bin/axis2server.sh" > logs/axis2server.log &
popd
