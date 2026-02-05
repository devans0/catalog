Environment
===========
Configuring the environment for testing involves two crucial steps:

1. Ensuring that the correct JVM/JDK is in use:

 To do this, ensure that the JDK8 binary is in the system path prior to compiling
 or running the program. This is platform dependent, but as an example the system
 PATH on a UNIX system might be altered thusly:

    PATH="$PATH":<path-to-jdk8>

 or on Windows:

    set PATH=%PATH%;<path-to-jdk8>

These commands may be run on the current terminal session prior to compiling or
running the program or they may be configured at the system level.

2. Starting and configuring the PostgreSQL server for use:

This is also platform dependent. Consult the [PostgreSQL documentation](https://www.postgresql.org/docs/current/tutorial-install.html) to find instructions on how to set up the database server. Please note
that if a port other than the default database server port is selected then it must
be configured in the server.properties file by changing the value of db.url to 
match the system settings.

Server Testing
==============
Prior to testing the client some aspects of the server functionality may be tested
independently. Initial server testing involves ensuring that it has the proper 
system footprint. This system was implemented on a Linux host and as such all 
examples and outputs follow UNIX conventions. To verify these same testing steps, 
use the analagous commands appropriate to the platform in use.

The server should be run from the command line or from eclipse. Reminder, the use
of openjdk-8 is necessary for compilation and running this project. If the environment
has not been properly configured for the server, see "Environment" in README.md.

First start the server and ensure that it does not produce any error messages. Next,
run the following commands (or analagous commands per the platform in use) and
inspect that their output is acceptable. Below is the commands used to inspect the
system footprint of the server and their corresponding outputs on the development
system.

Check that the `orbd` process is running on the expected port by running the 
server and inspecting system state:

Check the appropriate port is in use by `orbd`:

    ss -tulpn | grep :1050

Output:

    tcp   LISTEN 0      50      *:1050      *:*    users:(("orbd",pid=76754,fd=19)) 

Check that the database contains expected tables:

    psql -U catalog_server -d catalog_db -c "\dt"

Output:

                     List of tables
     Schema |     Name     | Type  |     Owner
    --------+--------------+-------+----------------
     public | file_entries | table | catalog_server
    (1 row)

Finally close the server (using Ctrl-C on the command line or by terminating it in
Eclipse) and check that the `orbd` process is no longer present. This assumes that
`orbd` was created by the server and that the server is configured to close it upon
program exit:

    ps aux | grep orbd

Output (should only be the grep process):

    dom   77368  0.0  0.0   9284  6192 pts/2    S+   14:48   0:00 grep --color=auto orbd

Reaper Testing
--------------
The reaper daemon can also be tested at this point by manually adding a file listing
to the file_entries table and waiting for it to be successfully reaped from the
table. The reaper will run every 2 minutes by default, but this duration may be
configured to any integer number of minutes in the server.properties file.

Run the server and execute the following SQL against the database manually to enter
a zombie row into the table:

    INSERT INTO file_entries (peer_id, filename, owner_ip, owner_port, last_seen)
    VALUES (gen_random_uuid(), 'ghost.txt', '127.0.0.1', 9000, NOW() - INTERVAL '5 minutes');

Now wait for the reaper to execute and look for the appropriate log entry printed
to the console:

    $ java -cp "$PWD/bin:$PWD/lib/postgresql-42.7.9.jar" catalog_application.server.CatalogServer
    [SYS] Naming service (orbd) starting on port 1050
    [SYS] Local orbd instance started successfully.
    [DB] Database schema verfied.
    Catalog Server registered as 'CatalogService'
    [REAPER] Purged 1 stale file(s).

Next verify that the row is removed from the database:

    catalog_db=# SELECT * FROM file_entries;
     id | file_name | peer_id | owner_ip | owner_port | last_seen
    ----+-----------+---------+----------+------------+-----------
    (0 rows)

Client and System Testing
=========================
Next, the Catalog Client may be tested along with the server. This is accomplished
via a mixture of manual and programmatic testing which aims to verify that the
following features function without error:

1. The user can run the client program and successfully connect to the file server.
2. The user's share directory is listed with the file sharing server properly.
3. Removing files from the user's share directory causes them to stop being shared
with other users.
4. Searching a file name that is known to be registered with the file server returns
its name and allows for initiating a download.
5. Sharing files works as intended: that is, when a file is searched and then selected
for download that the file is successfully transferred from one client's share directory 
to the local client's download directory.

Running the Test Scripts
------------------------
The testing for the total system is accomplished via a suite of test scripts. There
are scripts included in the `test/script` directory and are designed to be invoked
while the current working directory is the project root folder. First ensure that 
the script files are all flagged as executable in the file system and then invoke
them from the root project directory:

    ./test/script/catalog-test.sh    (UNIX)
    .\test\script\catalog-test.ps1 (Windows Powershell)

These testing suites will automatically start the server and then start two clients
in order to facilitate further testing, provided the environment is properly
configured.

Performing the Testing
----------------------
Once the Catalog Server and Clients are running, it is time to test that they function
as expected. Use the following steps to check the functionality of the system:

1. Add a file to the `share1` or `share2` directories and wait for the appropriate
client to make this file available on the Catalog Server.

2. Using the other client, search for and download the file that was placed in the
share directory.

3. Verify that the file now exists in either the `down1` or `down2` directory, as
appropriate.

4. Remove the file from the share folder and wait for the file to be reaped by the
file server. This will be indicated by a console log generated by the server.

5. Attempt to search for the file that is now missing from the system. Verify that
the system prints a message indicating that the file could not be found.

These steps complete the testing for the happy path of the system and ensure basic
functionality. Some additional robustness test cases:

* Share a file and then manually delete the corresponding database row for that file
from the share server by executing a SQL command against the database. Ensure that
the client that is sharing this file refreshes it on its next heartbeat by checking
that it appears in the file share database again.

* Close and then immediately restart a client that is currently sharing a file. This
can be accomplished most easily by running the test setup script, closing one client
and then starting a new client in the root directory in the usual manner. Then, share
a file with the root-directory Client before forcibly killing and immediately it before
starting it again. Then, verify via SQL queries against the database that the shared
file persists throughout this event.

* Run the clients and server before shutting down the server with the clients running
and ensure that the clients gracefully inform the user of the event.

Once all of these test cases have been verified, the system is considered to be
in good working order.
