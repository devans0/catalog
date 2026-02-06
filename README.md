Catalog
=======
A server-mediated P2P file sharing system implemented using Java and CORBA.

Compilation and Configuration
=============================
In order to compile and run Catalog, it is necessary to use Java 8. Configuring
the host system to have JDK 8 in its PATH will ensure that it has access to `orbd`
in order to start the ORB service that the system relies upon.

It is necessary to find the location of JDK 8 on the file system and to append this
path to the system PATH variable. This configuration is highly dependent on the
exact platform in use. Consult system documentation for details of how to do so
for the platform in use.

System Configuration
--------------------
#### For the Server
The first step for configuring the Catalog Server system is to install the underlying
database server. This project assumes the use of PostgreSQL as the database
backend. Installation of PostgreSQL is not covered here, but information may be
found in the [PostgreSQL documentation.](https://www.postgresql.org/docs/current/tutorial-install.html)

Once the database server is installed and operational, it is necessary to create
a user for the Catalog Server. By default, Catalog assumes that a database user
`catalog_server` exists. The name of the user associated with Catalog can be
configured in the `server.properties` file by setting the `db.user` property. If
a password is set for the database user, Catalog must be furnished with this
password by setting `db.password` in the same file. If there is no password assigned
to the database user, then it is important to ensure that the database server is
configured to permit password-less connections.

Catalog additionally assumes that the database is running on the same host as the
server process and that PostgreSQL is operating on the default port. If either of
these assumptions do not hold for the deployed system, then the location of the
server must be configured via the `db.url` property. Please note that Catalog has
not been tested with a remote or third-party database server, but it should function
if the database server is not local relative to the Catalog Server.

#### For the Client
The Catalog client does not require any special system setup configuration beyond
what was required for the server. It only requires that Java 8 is present for the
compilation and running of the application.

Compilation
-----------
#### UNIX
Compiling the server may be accomplished via the command line on a Linux system
via the following command:

    javac -d bin -cp "src:lib/*" \
        src/catalog_api/*.java \
        src/catalog_util/*.java \
        src/catalog_application/server/*.java

Simlarly, the client program may be compiled via:

    javac -d bin -cp "src:lib/*" \
        src/catalog_api/*.java \
        src/catalog_util/*.java \
        src/catalog_application/client/*.java

#### Windows
Compiling the server:

    javac -d bin -cp "src:lib/*" `
        src\catalog_api\*.java `
        src\catalog_util\*.java `
        src\catalog_application\server\*.java

Compiling the client:

    javac -d bin -cp "src:lib/*" \
        src/catalog_api/*.java \
        src/catalog_util/*.java \
        src/catalog_application/client/*.java

Running
-------
Below are instructions for running the client and the server manually from the
command line. To ease testing, two scripts have been included that will start the
server and then create two different instances of the client. These scripts and their
usage are detailed in `TESTING.md`

In order to run the compiled programs, execute the following command from the project
root directory, as appropriate for the platform in use.

#### UNIX
Running the server:

    java -cp "bin:lib/*" catalog_application.server.CatalogServer

Running the client with settings derived from client.properties:

    java -cp "bin:lib/*" catalog_application.client.CatalogClient

The client may additionally be furnished with override options via the command
line. It is possible to pass a custom port, share directory, and download directory
to the client that will override the settings of client.properties. This is 
accomplished by the following command:

    java -cp "bin:lib/*" catalog_application.client.CatalogClient [port] [share dir] [download dir]

This feature is very basic and so these fields are position dependent and if any
one of the fields are specified, all three must be specified or the behaviour of
Catalog Client is undefined.

#### Windows
Running the server:

    java -cp "bin;lib/*" catalog_application.server.CatalogServer

Running the client with settings derived from client.properties:

    java -cp "bin:lib/*" catalog_application.client.CatalogClient

The Windows client may be furnished with override options on the command line in
the same way as on UNIX systems.

Usage
=====
Server
------
Using the server is straightforward: simply configure the parameters in server.properties
and start the server using one of the commands above. The server will automatically
connect to the already-running database server, determine whether the necessary table
is present for tracking file shares and create it if necessary. Once it has initialized
itself it will wait for incoming communications from clients.

Client
------
Using the client is generally straightforward as well. Configure the properties of
the client in the client.properties file and run the program. The client will
create a share and download directory, if they do not already exist and then contact
the server to create a connection. 

In order to share a file, copy it into the share directory and wait for the client 
to synchronize the directory. The client will periodically scan the share 
directory and update the share server by registering its contents. 

To stop sharing a file, remove it from that directory; it will disappear from 
the server the next time the client synchronizes with the database.

Searching for files will ask the server to query the file sharing database. Any
matches will be displayed in the table in the interface. Once a file has been shown
in the table it is available for download.

Downloading is as simple as clicking on the desired file and clicking download. It
will then be transferred from the remote client to the local client and placed in
the download directory.
