Server Testing
==============
As the server was implemented prior to the implementation of a client to access
it, initial server testing involves ensuring that it has the proper system footprint.
This system was implemented on a Linux box and as such all examples and outputs
follow UNIX conventions. To verify these same testing steps, use the analagous commands
appropriate to the platform in use.

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

Finally close the server (using Ctrl-C on the command line or by terminating in
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

    INSERT INTO file_entries (owner_id, filename, ip_address, port, last_seen)
    VALUES (gen_random_uuid(), 'ghost.txt', '127.0.0.1', 9000, NOW - INTERVAL '5 minutes');

Now wait for the reaper to execute and look for the appropriate log entry printed
to the console:

    [REAPER] Purged 1 stale file(s).
