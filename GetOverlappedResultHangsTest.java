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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @test
 * @bug 8153925
 * @summary concurrently registers a WatchService for the directory and
 *          deletes/re-creates this directory to reproduce the situation
 *          when, due to windows-specific FS locking behaviour, ReadDirectoryChangesW
 *          call in WindowsWatchService.Poller#run() will fail with 'Access is denied'.
 *          After that WindowsWatchService will try to wait on the non-existed
 *          overlapped I/O operation and will hang indefinitely locking the directory 
 *          handle (test will hang in that case).
 */
public class GetOverlappedResultHangsTest {
    
    private static final int ITERATIONS_COUNT = 1024;

    public static void main(String[] args) throws Exception {        
        ExecutorService pool = Executors.newCachedThreadPool();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("work");
            dir.toFile().deleteOnExit();
            final Path fdir = dir;
            pool.submit(() -> openAndCloseWatcherWork(fdir));
            pool.submit(() -> deleteAndRecreateDirectoryWork(fdir));
        } finally {
            pool.shutdown();
        }
        
        boolean exited = pool.awaitTermination(5L, TimeUnit.MINUTES);
        deleteRecursiveQuietly(dir);
        if (!exited) {
            throw new RuntimeException("Thread pool did not terminate");
        }        
    }
    
    private static void openAndCloseWatcherWork(Path dir) {
        for (int i = 0; i < ITERATIONS_COUNT; i++) {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.OVERFLOW);
            } catch (Exception e) {
                // quiet
            }
        }
    }

    private static void deleteAndRecreateDirectoryWork(Path dir) {
        for (int i = 0; i < ITERATIONS_COUNT; i++) {
            try {
                deleteRecursiveQuietly(dir);        
                Path subdir = dir.resolve("subdir");
                Files.createDirectories(subdir);
                Path file = subdir.resolve("test");
                Files.createFile(file);
            } catch (Exception e) {
                // quiet
            }
        }
    }

    private static void deleteRecursiveQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path pa : stream) {
                        deleteRecursiveQuietly(pa);
                    }
                }
            }
            Files.delete(path);
        } catch (Exception e) {
            // quiet
        }
    }
}
