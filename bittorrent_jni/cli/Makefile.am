AM_CPPFLAGS = \
    -I@top_srcdir@ \
    -D__TRANSMISSION__

AM_CFLAGS = \
    @LIBEVENT_CFLAGS@ \
    @OPENSSL_CFLAGS@ \
    @LIBCURL_CFLAGS@ \
    @ZLIB_CFLAGS@ \
    @JAVA_CFLAGS@ \
    @PTHREAD_CFLAGS@
######    @LIBSQLITE3_LIBS@

AM_LDFLAGS = \
    @ZLIB_LDFLAGS@

bin_PROGRAMS = seeding

seeding_SOURCES = seeding.c

seeding_LDADD = \
    $(top_builddir)/libtransmission/libtransmission.la \
    $(top_builddir)/third-party/libnatpmp/libnatpmp.la \
    $(top_builddir)/third-party/miniupnp/libminiupnp.la \
    @DHT_LIBS@ \
    @LIBEVENT_LIBS@ \
    @LIBCURL_LIBS@ \
    @OPENSSL_LIBS@ \
    @INTLLIBS@ \
    @ZLIB_LIBS@ \
    @PTHREAD_LIBS@
#####    @LIBSQLITE3_LIBS@

lib_LTLIBRARIES = libbtclient.la

libbtclient_la_SOURCES = client.c

libbtclient_la_LIBADD = \
    $(top_builddir)/libtransmission/libtransmission.la \
    $(top_builddir)/third-party/libnatpmp/libnatpmp.la \
    $(top_builddir)/third-party/miniupnp/libminiupnp.la \
    @DHT_LIBS@ \
    @LIBEVENT_LIBS@ \
    @LIBCURL_LIBS@ \
    @OPENSSL_LIBS@ \
    @INTLLIBS@ \
    @ZLIB_LIBS@ \
    @PTHREAD_LIBS@
