/*
 * Copyright (c) 2016, Red Hat, Inc. and/or its affiliates.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;

/**
 * @test
 * @bug 8153925
 * @requires os.family == "windows"
 * @summary repeatedly registers a WatchService for the directory and
 *          then deletes this directory recursively to reproduce the situation
 *          when, due to windows-specific FS locking behaviour, ReadDirectoryChangesW
 *          call in WindowsWatchService.Poller#run() will fail with 'Access is denied'.
 *          After that WindowsWatchService will try to wait on the non-existed
 *          overlapped I/O operation and will hang indefinitely locking the directory handle
 */
public class GetOverlappedResultHangsTest {

    private static final int ITERATIONS_COUNT = 1024;

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory(GetOverlappedResultHangsTest.class.getName()).toFile();
        int accessDeniedCount = 0;
        for(int i = 0; i < ITERATIONS_COUNT; i++) {
            System.out.println("Iteration: [" + i + "] of [" + ITERATIONS_COUNT + "]");
            boolean accessDenied = false;
            WatchService watcher = FileSystems.getDefault().newWatchService();
            try {
                dir.mkdirs();
                dir.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.OVERFLOW);
                modify(dir);
            } catch (Exception e) {
                if ("Access is denied".equals(e.getMessage())) {
                    accessDenied = true;
                    accessDeniedCount += 1;
                }
            } finally {
                if (accessDenied && checkHangs()) {
                    // wait for some time and recheck that it is still hanging
                    sleep(1000);
                    if (checkHangs()) {
                        throw new RuntimeException("Test failed, poller thread hangs" +
                                " on 'GetOverlappedResult' on iteration: [" + i + "]," +
                                " 'Access is denied' errors count: [" + accessDeniedCount + "]");
                    }
                }
                watcher.close();
            }
        }
        recursivelyDelete(dir);
        if (accessDeniedCount > 0) {
            System.out.println("Test passed: 'Access is denied' errors count: [" + accessDeniedCount + "]");
        } else {
            throw new RuntimeException("Test error: cannot reproduce 'Access is denied' error");
        }
    }

    public static void modify(File directory) throws Exception {
        recursivelyDelete(directory);
        File subdir = new File(directory, "subdir");
        subdir.mkdirs();
        File file = new File(subdir, "test");
        file.createNewFile();
    }

    private static boolean recursivelyDelete(File file) {
        boolean ok = true;
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                ok &= recursivelyDelete(f);
            }
        }
        ok &= file.delete();
        return ok;
    }

    private static boolean checkHangs() {
        for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            for (StackTraceElement el : info.getStackTrace()) {
                if ("GetOverlappedResult".equals(el.getMethodName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
