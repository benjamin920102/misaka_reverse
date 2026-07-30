import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java -jar allatori-deobfuscator.jar <input-jar> <output-jar>");
            System.exit(1);
        }

        File src = new File(args[0]);
        File dest = new File(args[1]);

        if (!src.exists()) {
            System.err.println("Error: Input file does not exist: " + src.getAbsolutePath());
            System.exit(1);
        }

        File parentDir = dest.getAbsoluteFile().getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        JarDeobfuscatorEngine.processAndDumpJar(src, dest);
    }
}