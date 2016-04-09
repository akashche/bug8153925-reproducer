jtreg test for the JDK-8153925 bug
==================================

This project contains a reproducer for the OpenJDK bug [8153925](https://bugs.openjdk.java.net/browse/JDK-8153925).

Reproducer repeatedly registers a [WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html) 
for the directory and then deletes this directory
recursively to reproduce the situation when, due to windows-specific FS locking behaviour, 
[ReadDirectoryChangesW](http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/5d5b55014d0d/src/windows/classes/sun/nio/fs/WindowsWatchService.java#l621)
call in `WindowsWatchService.Poller#run()` will fail with 'Access is denied'.

After that `WindowsWatchService` will try to wait calling [GetOverlappedResult](http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/5d5b55014d0d/src/windows/classes/sun/nio/fs/WindowsWatchService.java#l460) 
on the non-existed
overlapped I/O operation and will hang indefinitely locking the directory handle.

This issue is from JBoss IDE, original investigation and initial working reproducer was done by Thomas Mäder, see:

 - https://issues.jboss.org/browse/JBIDE-22145
 - https://issues.jboss.org/browse/JBIDE-22078

How to run
----------

Running using [jtreg](http://openjdk.java.net/jtreg/):

    java -jar path/to/jtreg.jar -jdk:path/to/jdk GetOverlappedResultHangsTest.java

License information
-------------------

This project is released under the [GNU General Public License, version 2](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).

Changelog
---------

**2016-04-09**

 * initial public version
