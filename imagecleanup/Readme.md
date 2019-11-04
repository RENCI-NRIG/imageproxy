# Image Proxy Cleanup Script

This script is designed to delete images both from disk and Openstack based on their age. Any image older than configured age is deleted both from disk and openstack only if there are no active instances allocated with it.

# Configuration 
It requires following variables to be configured:
```
export OMD_SITE=
export OMD_ROOT=/root/omd
export IMAGE_PROXY_DB='/opt/imageproxy/imageproxy.db'
export EUCA_KEY_DIR=/etc/orca/am+broker-12080/ec2/
```

Age for the image files can be configured in etc/diskspace.conf. Default age is configured as 30 days.
```
# Example diskspace configuration file. If you have all options
# commented out or set to None, the diskspace program does nothing.
#
# To configure the diskspace cleanup, uncomment the options below
# and configure the values to fit your needs.
#
# This cleanup only applies to registered dynamic files, like e.g.
# the nagios archive.
#
# Files older than this amount of seconds will be deleted. In this
# case all files older than 30 days will be deleted.
max_file_age = 2592000
#
# When free space in bytes falls below this value, the diskspace
# cleanup process is started. In this case 5 GB.
min_free_bytes = 5368709120
```

# Execution
Script can be executed as
```
./imgdiskspace -v
```