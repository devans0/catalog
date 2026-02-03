# catalog
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

Server
------

### System Configuration

The first step for configuring the Catalog Server system is to install the underlying
database server. This project assumes the use of PostgreSQL as the database
backend. Installation of PostgreSQL is not covered here, but information may be
found in the [PostgreSQL documentation.](https://www.postgresql.org/docs/current/tutorial-install.html)

Once the database server is installed and operational, it is necessary to create
a user for the Catalog Server. By default, Catalog assumes that a database user
`catalog_server` exists. The name of the user associated with Catalog can be
configured in the `server.properties` file by setting the `db.user` property. If
a password is set for the database user, Catalog must be furnished with this
password by setting `db.password` in the same file.

Catalog additionally assumes that the database is running on the same host as the
server process and that PostgreSQL is opeerating on the default port. If either of
these assumptions do not hold for the deployed system, then the location of the
server must be configured via the `db.url` property.

### Compilation

Compiling the server may be accomplished via the command line on a Linux system
via the following command:

    javac -d bin -cp "lib/postgresql-42.7.9.jar:src" \
        src/catalog_api/*.java \
        src/catalog_util/*.java \
        src/catalog_application/server/*.java
