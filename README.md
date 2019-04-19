# imageproxy
ImageProxy for ORCA

ImageProxy is a mechanism used to make the same guest image available across multiple independent cloud sites. It downloads images named by URLs and registers them with the site's cloud provider using local user credentials. ImageProxy is implemented as an Axis2 SOAP service that, when provided with an URL for an image and a globally unique signature to name it, returns the EMI of the registered image. If the image is not present within the system, it is downloaded and registered; otherwise the EMI of the previously registered image is returned.
 
ImageProxy can be installed on Openstack head node or on any other host that has nova tools installed. When using ImageProxy with ORCA you should use the same nova credentials for ImageProxy as are used for ORCA.
 
## Installation 
Download the source code. The environment variable IMAGEPROXY_SRC denotes the location of the source, in this document.
```
$ git clone https://github.com/RENCI-NRIG/imageproxy.git
$ cd imageproxy 
$ export IMAGEPROXY_SRC=`pwd`
```
### Configuration
Alter the properties file "$IMAGEPROXY_SRC/src/main/resources/orca/imageproxy/imageproxy-settings.properties" as desired for your site, e.g.
```
# relative to installation directory IMAGEPROXY_HOME
registerScriptProperty = scripts/register.sh

# euca bukkit name
eucaBukkitName = imageproxy.bukkit

# testmode - use image url as EMI, do not perform any downloads
impTestMode = false

# how long to sleep for in test mode (to pretend to download an image) sec?
testModeSleep = 30

# the total disk space to cache the images (the unit is GB, the default size is 1GB)
spacesize = 10

# the database file used to store the metadata of images
db.imageproxy.db = imageproxy.db
```
 
Be sure to set impTestMode to "false" for a production environment.
Be sure to set eucaBukkitName to a unique, unused name, or to a bucket name "owned" by the NOVA credentials you plan to use with ImageProxy.

### Environment variables
Define the following two environment variables as appropriate for your site. 
```
$ export IMAGEPROXY_HOME=/opt/imageproxy
$ export PROXY_EUCA_KEY_DIR=$IMAGEPROXY_HOME/ec2
```
### AXIS
ImageProxy requires using an Axis2-capable servlet container. You can use the one that comes with the Axis2 distribution or you can add Axis2 support to Tomcat. Download Axis2 (either the binary distribution for stand-alone operation or WAR for running under Tomcat) from [Axis2 download site](http://axis.apache.org/axis2/java/core/download.cgi).
At this time, we advise using a standalone Axis2 container.

Define AXIS2_HOME environment variable 
```
$ export AXIS2_HOME=/opt/imageproxy/axis-{version}
```
#### For standalone operation, 
- Unzip Axis2 zip into $IMAGEPROXY_HOME
- Modify startup scripts for Axis2 or Tomcat
- Modify the Axis2 start script (axis2server.sh in $AXIS2_HOME/bin) to include the following environment variable definitions:
```
export IMAGEPROXY_HOME="/opt/imageproxy"

export PROXY_EUCA_KEY_DIR="$IMAGEPROXY_HOME/ec2"

export IMAGEPROXY_LOG="$IMAGEPROXY_HOME/logs/register.log"
```
- The following line is needed to ensure Axis2 does not run out of file descriptors. Please ensure that your environment permits increasing this limit.
```
ulimit -n 4096
```
#### For deployment under Tomcat
- Place the Axis2 war into the webapps directory in your Tomcat installation. 
- Ensure that Tomcat is running, and test the installation by checking the following URL: http://$TOMCAT_HOST:$TOMCAT_PORT/axis2

- Modify the Tomcat startup.sh script to include the following environment variable definitions:
```
export IMAGEPROXY_HOME="/opt/imageproxy"

export LD_LIBRARY_PATH="$IMAGEPROXY_HOME/lib:$LD_LIBRARY_PATH"

export PROXY_EUCA_KEY_DIR="$IMAGEPROXY_HOME/ec2"

export IMAGEPROXY_LOG="$IMAGEPROXY_HOME/logs/register.log"
```
### SQL
Download and install SQLLite from [Sqlite download site](http://www.sqlite.org/download.html)
Test to check if installed
```
$ sqlite3 -version 3.7.5 
```
### Build ImageProxy components
Compiling the SOAP webservice
```
$ cd $IMAGEPROXY_SRC

$ mvn install axis2-aar:aar
```
### NOVA
Install Nova credentials under $PROXY_EUCA_KEY_DIR by unzipping the Nova credentials archive in that directory. For ORCA deployments, use the same credentials as for ORCA user in Nova

### Scripts
Install the scripts under $IMAGEPROXY_HOME/scripts (the location of the scripts can be adjusted in imageproxy-settings.properties file prior to compilation and deployment, however the path is always relative to $IMAGEPROXY_HOME).
```
$ mkdir $IMAGEPROXY_HOME/scripts
$ cp $IMAGEPROXY_SRC/scripts/nova_register.sh $IMAGEPROXY_HOME/scripts/register.sh
$ cp $IMAGEPROXY_SRC/scripts/nova_deregister.sh $IMAGEPROXY_HOME/scripts/deregister.sh
 ```
NOTE1: mkdir $IMAGEPROXY_HOME/logs; mkdir $IMAGEPROXY_HOME/settings; if they don't exist

### Deploy the service to Axis2 or Tomcat
- For standalone Axis2 (default port 8080), copy the jar file into the services directory and start the service  
```
$ cp target/imageproxy-1.0-SNAPSHOT.jar $AXIS2_HOME/repository/services/

$ cd $AXIS2_HOME; nohup bin/axis2server.sh &
```
- For running under Tomcat, copy the aar file into the services directory and restart Tomcat
```
$ cp target/imageproxy-1.0-SNAPSHOT.aar $TOMCAT_ROOT/webapps/axis2/WEB-INF/services

$ $TOMCAT_ROOT/shutdown.sh; $TOMCAT_ROOT/startup.sh
```
