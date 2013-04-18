JBossBot
========

The source for 'jbossbot' on irc.freenode.org.

How to build
============

To build you need to have zwitserloot installed in your local maven repository:

    $ git clone https://github.com/rzwitserloot/com.zwitserloot.json
    $ cd com.zwitserloot.json
    $ ant
    $ mvn install:install-file -Dfile=dist/com.zwitserloot.json.jar -DgroupId=com.zwitserloot.json -DartifactId=json -Dversion=1.0 -Dpackaging=jar
   
After that you can build jbossbot:

    $ git clone git://github.com/jbossbot/jbossbot.git
    $ cd jbossbot
    $ mvn veirfy

How to run
==========

You need a registered nick to use for testing. jbossbot will not join
channels before your user is registered.

Once that is done you need to create a file named
'jbossbot.properties' which specify the nick and nicksrv password to
use (you can use 'sample-jbossbot.properties' to see the various
properties)

After that you should be able to run by doing this:

    $ mvn exec:java -Dexec.mainClass=org.jboss.bot.Main

Other settings
==============

Use the source...
