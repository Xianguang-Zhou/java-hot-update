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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;

/**
 *
 * @author Xianguang Zhou <xianguang.zhou@outlook.com>
 */
class HotUpdateClassLoader extends ClassLoader implements FileListener {

    private Path classesRootDirPath;
    private Map<String, Class<?>> classNameToClass;

    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    public HotUpdateClassLoader(Path classesRootDirPath) throws IOException,
            InterruptedException {
        super(null);
        this.classesRootDirPath = classesRootDirPath;

        classNameToClass = new HashMap<>();

        Thread thread = new Thread(new FileWatcher(classesRootDirPath, this));
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        synchronized (this) {
            Class<?> klass = classNameToClass.get(name);
            if (klass != null) {
                return klass;
            }

            try {
                return loadClassFromFile(name);
            } catch (IOException ioEx) {
            }
        }
        return getSystemClassLoader().loadClass(name);
    }

    private static String classNameToRelativePath(String className) {
        return className.replaceAll("\\.", "/") + ".class";
    }

    private static String relativePathToClassName(String relativePath) {
        relativePath = relativePath.substring(0, relativePath.length()
                - ".class".length());
        return relativePath.replaceAll("/|\\\\", ".");
    }

    private Class<?> loadClassFromFile(final String className)
            throws IOException {
        String relativePath = classNameToRelativePath(className);
        File file = classesRootDirPath.resolve(relativePath).toFile();
        byte[] bytes = FileUtils.readFileToByteArray(file);

        final String newClassName = className + System.currentTimeMillis();
        final String newClassInternalName = newClassName.replaceAll("\\.", "/");
        final String classInternalName = className.replaceAll("\\.", "/");

        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(classReader,
                ClassWriter.COMPUTE_MAXS);
        RemappingClassAdapter remappingClassAdapter = new RemappingClassAdapter(
                classWriter, new Remapper() {
                    @Override
                    public String map(String type) {
                        if (classInternalName.equals(type)) {
                            return newClassInternalName;
                        } else {
                            return type;
                        }
                    }
                });
        classReader.accept(remappingClassAdapter, Opcodes.ASM5 | ClassReader.EXPAND_FRAMES);
        byte[] newBytes = classWriter.toByteArray();

        Class<?> klass = defineClass(newClassName, newBytes, 0, newBytes.length);
        classNameToClass.put(className, klass);
        return klass;
    }

    @Override
    public void fileCreated(Path path) {
        fileModified(path);
    }

    @Override
    public void fileModified(Path path) {
        try {
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                synchronized (this) {
                    loadClassFromFile(relativePathToClassName(classesRootDirPath
                            .relativize(path).toString()));
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    @Override
    public void fileDeleted(Path path) {
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            synchronized (this) {
                classNameToClass
                        .remove(relativePathToClassName(classesRootDirPath
                                        .relativize(path).toString()));
            }
        }
    }

}
