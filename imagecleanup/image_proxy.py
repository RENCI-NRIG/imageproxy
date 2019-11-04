import sqlite3 as lite
import sys

import logging as LOG
import os
import json
import signal
import time
from os import kill
from signal import alarm, signal, SIGALRM, SIGKILL
from subprocess import *
from subprocess import PIPE, Popen

class Commands:
    @classmethod
    def run_cmd(self, args):
        cmd = args
        print("running command: " + " ".join(cmd))
        p = Popen(cmd, stdout=PIPE, stderr=STDOUT, universal_newlines=True)
        retval = p.communicate()[0]

        return retval

    @classmethod
    def run(self, args, cwd=None, shell=False, kill_tree=True, timeout=-1, env=None):
        '''
        Run a command with a timeout after which it will be forcibly
        killed.

        Mostly from Alex Martelli solution (probably from one of his
        python books) posted on stackoverflow.com
        '''

        class Alarm(Exception):
            pass

        def alarm_handler(signum, frame):
            raise Alarm

        print("run: args= " + str(args))

        # p = Popen(args, shell = shell, cwd = cwd, stdout = PIPE, stderr = PIPE, env = env)
        p = Popen(args, stdout=PIPE, stderr=STDOUT, universal_newlines=True)

        if timeout != -1:
            signal(SIGALRM, alarm_handler)
            alarm(timeout)
        try:
            stdout, stderr = p.communicate()
            if timeout != -1:
                alarm(0)
        except Alarm:
            pids = [p.pid]
            if kill_tree:
                pids.extend(self._get_process_children(p.pid))
            for pid in pids:
                # process might have died before getting to this line
                # so wrap to avoid OSError: no such process
                try:
                    kill(pid, SIGKILL)
                except OSError:
                    pass
            return -9, '', ''
        return p.returncode, stdout, stderr

    @classmethod
    def _get_process_children(self, pid):
        p = Popen('ps --no-headers -o pid --ppid %d' % pid, shell=True,
                  stdout=PIPE, stderr=PIPE, universal_newlines=True)
        stdout, stderr = p.communicate()
        return [int(p) for p in stdout.split()]

    @classmethod
    def source(self, script, update=1):
        pipe = Popen(". %s; env" % script, stdout=PIPE, shell=True, env={'PATH': os.environ['PATH']}, universal_newlines=True)
        data = pipe.communicate()[0]
        env = dict((line.split("=", 1) for line in data.splitlines()))
        if update:
            os.environ.update(env)
        return env

class ImageProxy:
    def __init__(self):
        # Upon import, read in the needed OpenStack credentials from one of the right places.
        if (os.path.isfile(os.environ['EUCA_KEY_DIR'] + "/novarc")):
            Commands.source(os.environ['EUCA_KEY_DIR'] + "/novarc")
        elif (os.path.isfile(os.environ['EUCA_KEY_DIR'] + "/openrc")):
            Commands.source(os.environ['EUCA_KEY_DIR'] + "/openrc")
        else:
            pass

    @classmethod
    def cleanup_images(self, imageId):
        try:
            # openstack server list --image f9515127-8f00-46ec-834e-005b90fd152c --all-projects
            cmd = ["openstack", "server", "list", "--image", imageId, "--all-projects", "-f", "json", "-c", "ID"]
            rtncode, data_stdout, data_stderr = Commands.run(cmd, timeout=60)  # TODO: needs real timeout

            if rtncode != 0:
                print("openstack servers with image could not be determined: " +
                       str(imageId) + ": " + str(cmd) +
                       ", rtncode: " + str(rtncode) +
                       ", data_stdout: " + str(data_stdout) +
                       ", data_stderr: " + str(data_stderr))
                raise Exception(str(cmd))
            server_list=json.loads(data_stdout.strip())
            print(str(len(server_list)) + " VM instances with image=" + imageId + " exist")

            if len(server_list) == 0 :
                cmd = ["openstack", "image", "delete", str(imageId) ]
                rtncode,data_stdout,data_stderr = Commands.run(cmd, timeout=60) #TODO: needs real timeout

                print("rtncode: " + str(rtncode))
                print("data_stdout: " + str(data_stdout))
                print("data_stderr: " + str(data_stderr))

                if rtncode != 0:
                    print("openstack image delete command with non-zero rtncode " +
                            str(project) + ": " + str(cmd) +
                            ", rtncode: " + str(rtncode) +
                            ", data_stdout: " + str(data_stdout) +
                            ", data_stderr: " + str(data_stderr))
            else:
                return False

            return True
        except Exception as e:
            print("cleanup_images: " + str(type(e)) + " : " + str(e))
            return True

    def image_proxy_db_cleanup(self, signature, dbPath):
        retVal = False
        try:
            con = lite.connect(dbPath)
            cur = con.cursor()
            cur.execute("select IMAGE_ID from IMAGE where SIGNATURE='" + signature + "'")
            row = cur.fetchone()
            if row is not None:
                imageId = row[0]
                print ('Openstack Image Id: ' + imageId)
                if self.cleanup_images(str(imageId)) == True:
                    cur.execute("delete from FILE where SIGNATURE='" + signature + "'")
                    cur.execute("delete from IMAGE where SIGNATURE='" + signature + "'")
                    retVal = True
                    con.commit()
            else:
                print ("No image associated with teh signature=" + signature)
                cur.execute("delete from FILE where SIGNATURE='" + signature + "'")
                con.commit()
                retVal = True
        except lite.Error as e:
                if con:
                    con.rollback()

                print e
        finally:
            if con:
                con.close()
        return retVal

EUCA_KEY_DIR='/etc/orca/am+broker-12080/ec2/'
IMAGE_PROXY_DB='/opt/imageproxy/imageproxy.db'
img = ImageProxy()
img.image_proxy_db_cleanup('6ba373ac171fead43ba295f3f9b600f9ead8540f',os.environ['IMAGE_PROXY_DB'])
