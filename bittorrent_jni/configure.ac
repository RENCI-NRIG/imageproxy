m4_define([user_agent_prefix],[2.13])
m4_define([peer_id_prefix],[-TR2130-])

AC_INIT([imageproxy],
        [0.1],
        [https://geni-orca.renci.org/trac/newticket])
AC_SUBST(USERAGENT_PREFIX,[user_agent_prefix])
AC_SUBST(PEERID_PREFIX,[peer_id_prefix])

m4_ifdef([AM_SILENT_RULES],[AM_SILENT_RULES([yes])])

AC_CONFIG_MACRO_DIR([m4])

dnl AM_CONFIG_HEADER(config.h)
AC_CONFIG_SRCDIR(libtransmission/transmission.h)
AM_INIT_AUTOMAKE([1.9 tar-ustar])
AC_PROG_LIBTOOL

if test m4_substr(peer_id_prefix,6,1) = "0"; then
  supported_build=yes
  CPPFLAGS="$CPPFLAGS -DNDEBUG"
else
  supported_build=no
  if test "x$GCC" = "xyes" ; then
    CFLAGS="$CFLAGS -g -O0"
    CXXFLAGS="$CXXFLAGS -g -O0"
  fi
fi
AM_CONDITIONAL(TR_UNSTABLE, test "x$supported_build" = "xno")

##
##
##   MANDATORY for everything
##
##
CURL_MINIMUM=7.15.4
AC_SUBST(CURL_MINIMUM)
LIBEVENT_MINIMUM=1.4.9
AC_SUBST(LIBEVENT_MINIUM)
OPENSSL_MINIMUM=0.9.4
AC_SUBST(OPENSSL_MINIMUM)

AC_PROG_CC
AC_PROG_CXX
AC_C_INLINE
if test "x$GCC" = "xyes" ; then

    CFLAGS="$CFLAGS -std=gnu99 -ggdb3 -Wall -W -Wpointer-arith -Wformat-security -Wcast-align -Wundef -Wcast-align -Wstrict-prototypes -Wmissing-declarations -Wmissing-format-attribute -Wredundant-decls -Wnested-externs -Wunused-parameter -Wwrite-strings -Waggregate-return -Winline -Wfloat-equal"

    dnl figure out gcc version
    AC_MSG_CHECKING([gcc version])
    GCC_VERSION=`$CC -dumpversion`
    GCC_MAJOR=`echo $GCC_VERSION | cut -d . -f1`
    GCC_MINOR=`echo $GCC_VERSION | cut -d . -f2`
    GCC_VERSION_NUM=`(expr $GCC_MAJOR "*" 100 + $GCC_MINOR) 2>/dev/null`

    AC_MSG_RESULT($GCC_VERSION)
    if test $GCC_VERSION_NUM -ge 304; then
        dnl these were added in 3.4
        CFLAGS="$CFLAGS -Wextra -Wdeclaration-after-statement -Winit-self"
    fi
    if test $GCC_VERSION_NUM -ge 403; then
        dnl these were added in 4.3
        CFLAGS="$CFLAGS -Wvariadic-macros"
    fi
fi

AC_HEADER_STDC
AC_HEADER_TIME

AC_CHECK_FUNCS([iconv_open pread pwrite lrintf strlcpy daemon dirname basename strcasecmp localtime_r fallocate64 posix_fallocate memmem strsep strtold syslog valloc getpagesize posix_memalign])
AC_PROG_INSTALL
AC_PROG_MAKE_SET
ACX_PTHREAD

if test "x$ac_cv_func_strtold" != "xyes" ; then
    CPPFLAGS="$CPPFLAGS -Dstrtold=strtod"
fi

AC_SEARCH_LIBS(cos, [m])
AC_SEARCH_LIBS([socket], [socket net])
AC_SEARCH_LIBS([gethostbyname], [nsl bind])
PKG_CHECK_MODULES(OPENSSL, [openssl >= $OPENSSL_MINIMUM], , [CHECK_SSL()])
PKG_CHECK_MODULES(LIBCURL, [libcurl >= $CURL_MINIMUM])
AC_PATH_ZLIB

AC_SYS_LARGEFILE
AC_CHECK_FUNCS([lseek64])


dnl ----------------------------------------------------------------------------
dnl
dnl posix_fadvise

dnl can posix_fadvise be used
AC_CHECK_DECLS(posix_fadvise, [], [], [
#define _XOPEN_SOURCE 600
#include <fcntl.h>])
AC_CHECK_FUNCS([posix_fadvise])


dnl ----------------------------------------------------------------------------
dnl
dnl file monitoring for the daemon

AC_CHECK_HEADER([sys/inotify.h], 
                [AC_CHECK_FUNC([inotify_init],[have_inotify="yes"],[have_inotify="no"])], 
                [have_inotify="no"]) 
AC_ARG_WITH([inotify], 
            [AS_HELP_STRING([--with-inotify],[Enable inotify support (default=auto)])], 
            [want_inotify=${withval}], 
            [want_inotify=${have_inotify}]) 
if test "x$want_inotify" = "xyes" ; then 
    if test "x$have_inotify" = "xyes"; then 
      AC_DEFINE([WITH_INOTIFY],[1]) 
    else 
      AC_MSG_ERROR(inotify not found!) 
    fi 
fi 
 
AC_CHECK_HEADER([sys/event.h], 
                [AC_CHECK_FUNC([kqueue],[have_kqueue="yes"],[have_kqueue="no"])], 
                [have_kqueue="no"]) 
AC_ARG_WITH([kqueue], 
            [AS_HELP_STRING([--with-kqueue],[Enable kqueue support (default=auto)])], 
            [want_kqueue=${withval}], 
            [want_kqueue=${have_kqueue}]) 
if test "x$want_kqueue" = "xyes" ; then 
    if test "x$have_kqueue" = "xyes"; then 
      AC_DEFINE([WITH_KQUEUE],[1]) 
    else 
      AC_MSG_ERROR(kqueue not found!) 
    fi 
fi 

AC_CHECK_HEADERS([xfs/xfs.h])


dnl ----------------------------------------------------------------------------
dnl
dnl va_copy

AC_MSG_CHECKING([how to copy va_list])
AC_TRY_LINK([#include <stdarg.h>], [va_list ap1, ap2; va_copy(ap1, ap2);],
    AC_MSG_RESULT([va_copy]),
        [ AH_TEMPLATE([va_copy], [define if va_copy is not available])
        AC_TRY_LINK([#include <stdarg.h>], [va_list ap1, ap2; __va_copy(ap1, ap2);],
        [ AC_DEFINE([va_copy], [__va_copy])
        AC_MSG_RESULT([__va_copy])],
        [ AC_DEFINE([va_copy(dest,src)], [memcpy(&dest,&src,sizeof(va_list))])
        AC_MSG_RESULT([memcpy])]
    )
])


dnl libevent likes to link against librt if possible
dnl for clock_gettime() and clock_settime()
dnl TODO(libevent2): this can probably be removed after
dnl we switch to libevent2, since it will have PKG_CONFIG
AC_CHECK_LIB([rt],
             [clock_gettime],
             [libevent_extra_libs="-lrt"],
             [libevent_extra_libs=""])


dnl libevent
dnl if the user specified LIBEVENT_LIBS or
if test -n "$LIBEVENT_LIBS"; then
    user_specified_libevent=yes
elif test -n "$LIBEVENT_CFLAGS"; then
    user_specified_libevent=yes
fi
if test "x$user_specified_libevent" = "xyes"; then
    AC_MSG_NOTICE([Using user-specified LIBEVENT_LIBS and LIBEVENT_CFLAGS])
else
    AC_CHECK_LIB([event],[evutil_vsnprintf],
                 [],
                 [AC_MSG_ERROR(libevent $LIBEVENT_MINIMUM or higher not found!)],
                 [$libevent_extra_libs])
    AC_CHECK_HEADER([event-config.h],[],
                    [AC_MSG_ERROR(event-config.h not found!)])
    LIBEVENT_CFLAGS=""
    LIBEVENT_LIBS="-levent $libevent_extra_libs"
fi
AC_ARG_VAR([LIBEVENT_CFLAGS], [C compiler flags for LIBEVENT, overriding pkg-config])dnl
AC_ARG_VAR([LIBEVENT_LIBS], [linker flags for LIBEVENT, overriding pkg-config])dnl

dnl ---------------------------------------------------------------------------
dnl libsqlite3
dnl if the user specified LIBSQLITE3_LIBS or
if test -n "$LIBSQLITE3_LIBS"; then
    user_specified_libsqlite3=yes
elif test -n "$LIBSQLITE3_CFLAGS"; then
    user_specified_libsqlite3=yes
fi
if test "x$user_specified_libsqlite3" = "xyes"; then
    AC_MSG_NOTICE([Using user-specified LIBSQLITE3_LIBS and LIBSQLITE3_CFLAGS])
else
    AC_CHECK_LIB([sqlite3],[sqlite3_open_v2],
                 [],
                 [AC_MSG_ERROR(libsqlite3 not found!)],
		 [-lsqlite3])
    AC_CHECK_HEADER([sqlite3.h],[],
                    [AC_MSG_ERROR(sqlite3.h not found!)])
    LIBSQLITE3_CFLAGS=""
    LIBSQLITE3_LIBS="-lsqlite3"
fi
AC_ARG_VAR([LIBSQLITE3_CFLAGS], [C compiler flags for LIBSQLITE3, overriding pkg-config])dnl
AC_ARG_VAR([LIBSQLITE3_LIBS], [linker flags for LIBSQLITE3, overriding pkg-config])dnl

dnl ----------------------------------------------------------------------------
dnl
dnl  Java - in truth, all we need is jni.h

AC_ARG_WITH([jdk],
            [AS_HELP_STRING([--with-jdk=DIR],[Path to JDK])],
            [jdk_dir=${withval}],
            [jdk_dir=])
JAVAINCDIR="" 
if test -z "$jdk_dir"; then
    JAVAINCDIR="/usr/j2sdk*/include /usr/local/j2sdk*/include /usr/jdk*/include /usr/local/jdk*/include /opt/j2sdk*/include /opt/jdk*/include /usr/java/include /usr/java/j2sdk*/include /usr/java/jdk*/include /usr/local/java/include /opt/java/include /usr/include/java /usr/local/include/java /usr/lib/java/include /System/Library/Frameworks/JavaVM.framework/Headers/"
else
    JAVAINCDIR="${jdk_dir}/include"
fi
AC_MSG_CHECKING(for java include file jni.h)
JAVA_CFLAGS=""
for d in $JAVAINCDIR ; do
    if test -r $d/jni.h ; then
        AC_MSG_RESULT($d)
        JAVAINCDIR=$d
        JAVA_CFLAGS="-I$d"
        break
    fi
done
dnl Bomb out if no Java includes found.
if test "$JAVA_CFLAGS" = ""; then
    AC_MSG_ERROR(JDK header files not found. A valid JDK is required.)
else
dnl now look for <arch>/jni_md.h
    AC_MSG_CHECKING(for java include file jni_md.h)
    JAVAMDDIR=`find $JAVAINCDIR -follow -name jni_md.h -print`
    if test "$JAVAMDDIR" = "" ; then
        AC_MSG_ERROR(jni.h found, but jni_md.h was not. Please verify your JDK is installed correctly.)
    else
        JAVAMDDIR=`dirname $JAVAMDDIR`
        JAVA_CFLAGS="${JAVA_CFLAGS} -I$JAVAMDDIR"
        AC_MSG_RESULT($JAVAMDDIR)
    fi
fi
AC_SUBST(JAVA_CFLAGS)

dnl ----------------------------------------------------------------------------
dnl
dnl  dht

DHT_CFLAGS="-I\$(top_srcdir)/third-party/dht"
DHT_LIBS="\$(top_builddir)/third-party/dht/libdht.la"
AC_SUBST(DHT_CFLAGS)
AC_SUBST(DHT_LIBS)

dnl ----------------------------------------------------------------------------
dnl
dnl  Intltool

#IT_PROG_INTLTOOL([0.35.0],[no-xml])
AC_CHECK_HEADERS([libintl.h])
AC_SUBST(INTLLIBS)

dnl ----------------------------------------------------------------------------
dnl
dnl  platform-specific stuff.

AC_CANONICAL_HOST
have_darwin="no"
have_msw="no"
case $host_os in

   *mingw32*)
     have_msw="yes"
     CXXFLAGS="$CXXFLAGS -mms-bitfields -mwin32 -mwindows"
     CPPFLAGS="$CPPFLAGS -DWIN32 -D_WIN32 -DWIN32_LEAN_AND_MEAN"
     LIBS="$LIBS -liphlpapi -lshell32 -lws2_32"
     transmissionlocaledir="locale"
     if test -z "$host_alias"; then
       hostaliaswindres=
     else
       hostaliaswindres="$host_alias-windres";
     fi
     AC_CHECK_TOOL(WINDRES, windres)
     ;;

  *darwin*)
    have_darwin="yes"
    CFLAGS="-DMACOSX $CFLAGS"
    ;;

esac

if test "x$have_darwin" = "xyes"; then
    AC_DEFINE([HAVE_DARWIN], 1)
fi
if test "x$have_msw" = "xyes"; then
    AC_DEFINE([HAVE_MSW], 1)
fi
AM_CONDITIONAL(WIN32, test "x$have_msw" = "xyes")

dnl ----------------------------------------------------------------------------
dnl
dnl  Generate the output

AC_CONFIG_FILES([Makefile
                 cli/Makefile
                 libtransmission/Makefile
                 third-party/Makefile
                 third-party/miniupnp/Makefile
                 third-party/libnatpmp/Makefile
                 third-party/dht/Makefile])

ac_configure_args="$ac_configure_args --enable-static --disable-shared -q"
AC_OUTPUT

echo "
   Configuration complete. Type \"make\" to build.
"
