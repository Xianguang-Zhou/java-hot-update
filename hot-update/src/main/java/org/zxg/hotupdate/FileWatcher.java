/*
 * Copyright (C) 2015 Xianguang Zhou <xianguang.zhou@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.zxg.hotupdate;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Xianguang Zhou <xianguang.zhou@outlook.com>
 */
class FileWatcher implements Runnable {

    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeyToPath;
    private final FileListener fileListener;

    FileWatcher(Path dirPath, FileListener fileListener)
            throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKeyToPath = new HashMap<WatchKey, Path>();
        this.fileListener = fileListener;
        registerAllDir(dirPath);
    }

    private void registerDir(Path dirPath) throws IOException {
        WatchKey watchKey = dirPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        watchKeyToPath.put(watchKey, dirPath);
    }

    private void registerAllDir(final Path startDirPath) throws IOException {
        Files.walkFileTree(startDirPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                registerDir(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void run() {
        while (true) {
            WatchKey watchKey;
            try {
                watchKey = watchService.take();
            } catch (InterruptedException ie) {
                return;
            }

            Path dirPath = watchKeyToPath.get(watchKey);
            if (dirPath == null) {
                continue;
            }

            for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
                WatchEvent.Kind<?> watchEventKind = watchEvent.kind();

                if (watchEventKind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathWatchEvent = (WatchEvent<Path>) watchEvent;
                Path namePath = pathWatchEvent.context();
                Path absolutePath = dirPath.resolve(namePath);

                if (watchEventKind == StandardWatchEventKinds.ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(absolutePath,
                                LinkOption.NOFOLLOW_LINKS)) {
                            registerAllDir(absolutePath);
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (watchEventKind == StandardWatchEventKinds.ENTRY_CREATE) {
                    fileListener.fileCreated(absolutePath);
                } else if (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    fileListener.fileModified(absolutePath);
                } else if (watchEventKind == StandardWatchEventKinds.ENTRY_DELETE) {
                    fileListener.fileDeleted(absolutePath);
                }
            }

            boolean watchKeyValid = watchKey.reset();
            if (!watchKeyValid) {
                watchKeyToPath.remove(watchKey);
            }
        }
    }
}
