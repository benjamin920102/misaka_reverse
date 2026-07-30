import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.objectweb.asm.*;

public class JarDeobfuscatorEngine {

    private static final Map<String, Method> decryptMethodMap = new ConcurrentHashMap<>();
    private static final Set<String> decryptClassNames = ConcurrentHashMap.newKeySet();

    public static void processAndDumpJar(File srcJar, File destJar, int poolSize) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{srcJar.toURI().toURL()}, JarDeobfuscatorEngine.class.getClassLoader());
             JarFile jar = new JarFile(srcJar)) {

            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                String className = entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6);
                
                try {
                    Class<?> clazz = Class.forName(className, false, classLoader);
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (isDecryptionHelper(m)) {
                            String internalName = entry.getName().substring(0, entry.getName().length() - 6);
                            String key = internalName + "." + m.getName() + Type.getMethodDescriptor(m);
                            decryptMethodMap.put(key, m);
                            decryptClassNames.add(internalName);
                        }
                    }
                } catch (Throwable ignored) { }
            }
        }

        List<JarEntryTask> entriesToProcess = new ArrayList<>();
        try (JarFile jar = new JarFile(srcJar)) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                byte[] bytes;
                try (InputStream is = jar.getInputStream(entry)) {
                    bytes = is.readAllBytes();
                }
                entriesToProcess.add(new JarEntryTask(entry.getName(), bytes));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Future<ProcessedEntry>> futures = new ArrayList<>();

        for (JarEntryTask task : entriesToProcess) {
            futures.add(executor.submit(() -> processEntryData(task)));
        }

        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(destJar)))) {
            for (Future<ProcessedEntry> future : futures) {
                ProcessedEntry result = future.get();
                if (result == null) continue;

                JarEntry newEntry = new JarEntry(result.name);
                jos.putNextEntry(newEntry);
                jos.write(result.data);
                jos.closeEntry();
            }
        } finally {
            executor.shutdown();
        }
    }

    private static ProcessedEntry processEntryData(JarEntryTask task) {
        String name = task.name;

        if (isAllatoriJarEntry(name)) {
            return null;
        }

        if (name.endsWith(".class")) {
            try {
                ClassReader cr = new ClassReader(task.data);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                String classInternal = name.substring(0, name.length() - 6);
                
                AllatoriStringTransformer transformer = new AllatoriStringTransformer(
                        Opcodes.ASM9, cw, classInternal, decryptMethodMap, decryptClassNames);
                cr.accept(transformer, ClassReader.SKIP_FRAMES);
                
                return new ProcessedEntry(name, cw.toByteArray());
            } catch (Throwable t) {
                return new ProcessedEntry(name, task.data);
            }
        } else {
            return new ProcessedEntry(name, task.data);
        }
    }

    private static boolean isAllatoriJarEntry(String entryName) {
        if (entryName.contains("ALLATORIxDEMO")) return true;
        for (String fullClass : decryptClassNames) {
            String path = fullClass + ".class";
            if (entryName.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDecryptionHelper(Method m) {
        if (!Modifier.isStatic(m.getModifiers())) return false;
        if (m.getReturnType() != String.class) return false;
        if (m.getParameterCount() != 1) return false;
        return m.getParameterTypes()[0] == String.class;
    }

    private static class JarEntryTask {
        final String name;
        final byte[] data;

        JarEntryTask(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private static class ProcessedEntry {
        final String name;
        final byte[] data;

        ProcessedEntry(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}