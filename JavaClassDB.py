#!/usr/bin/env python3
#
# Author: Stefan Broeder <5umm1t@protonmail.com>
#
# OS dependencies: unzip, serialver

import sqlite3
import sys
import os
import hashlib
import tempfile
import subprocess
import re
from pathlib import Path

BASE_DIR = os.path.expanduser('~/.serially')
JAR_DIR  = os.path.join(BASE_DIR, 'jars')
DB_FILE  = os.path.join(BASE_DIR, 'java.sqlite')

def check_filesystem():
    if not Path(BASE_DIR).exists():
        os.mkdir(BASE_DIR)
    if not Path(JAR_DIR).exists():
        os.mkdir(JAR_DIR)
    if not Path(DB_FILE).exists():
        Path(DB_FILE).touch()


def init_db():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()

    # Create tables
    c.execute("CREATE TABLE IF NOT EXISTS jar (filename text, hash text)")
    c.execute("CREATE TABLE IF NOT EXISTS class (jarID integer, fqdn text, serialVersionUID text)")

    conn.commit()
    return conn



def get_hash(filename):
    hash_md5 = hashlib.md5()
    jarfile = os.path.join(JAR_DIR, filename)

    with open(jarfile, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    
    return hash_md5.hexdigest()


def add_jar_to_db(filename, conn):
    jar_hash = get_hash(filename)

    c = conn.cursor()
    c.execute("INSERT INTO jar(filename,hash) VALUES(?,?)", [filename, jar_hash])
    jar_id = c.lastrowid
    conn.commit()
    
    return jar_id


def parse_serialver_output(output):
    regex = r"([-]?\d+)+"
    matches = re.findall(regex, output)
    return matches[0]


def getSerialVersionUID(jar_dir, fqdn):
    try:
        FNULL = open(os.devnull, 'w')
        output = subprocess.check_output(["serialver", "-classpath", jar_dir, fqdn], stderr=FNULL).decode("utf-8") 
    except subprocess.CalledProcessError:
        # Process exited non-zero
        return None

    serialVersionUID = parse_serialver_output(output)
    return serialVersionUID
    

def add_class_to_db(conn, jar_id, fqdn, serialVersionUID):
    c = conn.cursor()
    c.execute("INSERT INTO class(jarID, fqdn, serialVersionUID) VALUES(?,?,?)", [jar_id, fqdn, serialVersionUID])
    conn.commit()


def add_classes_to_db(jar_id, filename, conn):
    jarfile = os.path.join(JAR_DIR, filename)

    with tempfile.TemporaryDirectory() as jar_dir:
        
        FNULL = open(os.devnull, 'w')
        subprocess.run(["unzip", "-d", jar_dir, jarfile], stderr=FNULL, stdout=FNULL)
        
        for root, dirs, files in os.walk(jar_dir):
            for file in files:
                if file.endswith(".class"):
                    
                    class_absolute_path = os.path.join(root, file)                      # /path/to/temp/java/com/Example.class
                    fqdn_slashes        = os.path.relpath(class_absolute_path, jar_dir) # java/com/Example.class
                    fqdn                = fqdn_slashes.replace('/','.')[:-6]            # java.com.Example
                    serialVersionUID    = getSerialVersionUID(jar_dir, fqdn)
                    
                    print("Adding to database: Jar_ID: %s, class: %s, serialVersionUID: %s" % (jar_id, fqdn, serialVersionUID))
                    add_class_to_db(conn, jar_id, fqdn, serialVersionUID)


def jar_exists_in_db(filename, conn):
    c = conn.cursor()
    c.execute("SELECT * FROM jar WHERE filename = ?", [filename])
    return c.fetchone() is not None


def handle_jar(filename, conn):
    if jar_exists_in_db(filename, conn):
        return False
    
    jar_id = add_jar_to_db(filename, conn)
    add_classes_to_db(jar_id, filename, conn)
    return True


def handle_jar_files(conn):
    new_jars_found = False
    for root, dirs, files in os.walk(JAR_DIR):
        for file in files:
            if file.endswith(".jar"):
                if handle_jar(file, conn):
                    new_jars_found = True

    if not new_jars_found:
        print("No new jar files found in "+ JAR_DIR)

def main():
    check_filesystem()
    conn = init_db()
    handle_jar_files(conn)


main()

