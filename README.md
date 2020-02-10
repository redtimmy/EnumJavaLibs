# EnumJavaLibs
EnumJavaLibs: Remote Java classpath enumeration via deserialization.

<b>Overview</b>

EnumJavaLibs can be used to discover which libraries are loaded (i.e. available on the classpath) by a remote Java application when it supports deserialization.
<pre>
-----------------
Serially - v1.1
by Stefan Broeder
-----------------
Usage:

EnumJavaLibs {MODE} [OPTIONS]

MODES:
base64                  Write base64 encoded serialized objects to CSV file in current directory
rmi {host} {port}       RMI mode. Connect to RMI endpoint and try deserialization.

OPTIONS:
-f {string}             Only serialize classes from packages which contain the given string (e.g. org.apache.commons)
-d                      Debug mode on
</pre>
For more information about how to use the tool, please see this blog post.

<b>Obtaining jar files</b>

A local repository of jar files is required in ~/.serially/jars. You should run the JavaClassDB python3 script to index the jar files, before running EnumJavaLibs. One way to build a local repository is to download the most popular Java libraries from maven central. I provide a script getPopularJars.py that can be used as follows:
<pre>
./getPopularJars.py 20 | while read x; do wget --quiet -P ~/.serially/jars $x; sleep 5; done
</pre>
This command will scrape the 20 most popular jar files from mavenrepository.com.

Another way to bootstrap your local jar repository if you are using maven, is to copy the jar files you already have in the maven cache (~/.m2/repository)

<B>RMI</B>

To test RMI, I created a docker image you can fetch from [https://cloud.docker.com/repository/docker/summ1t/jmxrmi_vulnerable/general], which spins up a host listening to RMI on port 1617. It's running Java 8u40, which is vulnerable. It has library nanohttpd-2.2.0.jar loaded on the classpath to showcase that EnumJavaLibs is able to discover this library remotely (given that you prepared your local repository with this jar)
