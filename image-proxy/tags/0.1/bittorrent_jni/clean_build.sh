#!/bin/sh

make clean

rm -rf external libtransmission third-party m4 \
       autom4te.cache aclocal.m4 install-sh ltmain.sh missing \
       configure config.guess config.sub config.log config.status depcomp \
       libtool cli/.deps cli/Makefile.in cli/Makefile Makefile.in Makefile
