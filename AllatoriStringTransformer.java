import org.objectweb.asm.*;
import java.lang.reflect.Method;
import java.util.*;

public class AllatoriStringTransformer extends ClassVisitor {
    private final Map<String, Method> decryptMethodMap;
    private final Set<String> decryptClassNames;
    private final String className;

    public AllatoriStringTransformer(int api, ClassVisitor cv, String className, Map<String, Method> decryptMethodMap, Set<String> decryptClassNames) {
        super(api, cv);
        this.className = className;
        this.decryptMethodMap = decryptMethodMap;
        this.decryptClassNames = decryptClassNames;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (isAllatoriClass(name)) {
            return;
        }
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        String methodKey = className.replace('/', '.') + "." + name + descriptor;
        
        if (isDecryptMethod(methodKey)) {
            return null;
        }

        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(api, mv) {
            private String pendingLdcString = null;

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof String) {
                    pendingLdcString = (String) value;
                }
                super.visitLdcInsn(value);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                String methodKey = owner.replace('/', '.') + "." + name + descriptor;

                if (opcode == Opcodes.INVOKESTATIC && isDecryptMethod(methodKey)) {
                    Method decryptMethod = findDecryptMethod(methodKey);
                    
                    if (decryptMethod != null && pendingLdcString != null) {
                        try {
                            decryptMethod.setAccessible(true);
                            String decrypted = (String) decryptMethod.invoke(null, pendingLdcString);

                            if (decrypted != null && decrypted.endsWith("ALLATORIxDEMO")) {
                                decrypted = decrypted.substring(0, decrypted.length() - "ALLATORIxDEMO".length());
                            }

                            super.visitInsn(Opcodes.POP);
                            super.visitLdcInsn(decrypted);
                            
                            pendingLdcString = null;
                            return;
                        } catch (Exception e) {
                            System.err.println("[-] Decryption failed for " + methodKey + ": " + e.getMessage());
                        }
                    }
                }

                this.pendingLdcString = null;
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (isAllatoriClass(type)) {
                    return;
                }
                super.visitTypeInsn(opcode, type);
            }
        };
    }

    private boolean isDecryptMethod(String methodKey) {
        if (decryptMethodMap.containsKey(methodKey)) {
            return true;
        }
        return methodKey.endsWith("ALLATORIxDEMO") || methodKey.contains("ALLATORIxDEMO");
    }

    private Method findDecryptMethod(String methodKey) {
        if (decryptMethodMap.containsKey(methodKey)) {
            return decryptMethodMap.get(methodKey);
        }
        for (Map.Entry<String, Method> entry : decryptMethodMap.entrySet()) {
            if (entry.getKey().endsWith("ALLATORIxDEMO") || methodKey.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isAllatoriClass(String internalName) {
        if (internalName == null) return false;
        String norm = internalName.replace('/', '.');
        if (norm.endsWith("ALLATORIxDEMO") || norm.contains("ALLATORIxDEMO")) {
            return true;
        }
        for (String deco : decryptClassNames) {
            if (norm.contains(deco)) {
                return true;
            }
        }
        return false;
    }
}