package net.sixik.ga_utils.javatogpu.runtime;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class GpuMethodBodyRewriter {

    private static final String LEGACY_GPU_ANNOTATION_DESCRIPTOR = "Lnet/sixik/ga_utils/javatogpu/api/anotations/GPU;";
    private static final String CANONICAL_GPU_ANNOTATION_DESCRIPTOR = "Lnet/sixik/ga_utils/javatogpu/api/annotations/GPU;";
    private static final String CLINIT_NAME = "<clinit>";
    private static final String INIT_NAME = "<init>";

    public int rewriteDirectory(Path classesDirectory) throws IOException {
        int rewrittenCount = 0;
        try (Stream<Path> paths = Files.walk(classesDirectory)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                rewrittenCount += rewriteClassFile(classFile) ? 1 : 0;
            }
        }
        return rewrittenCount;
    }

    public boolean rewriteClassFile(Path classFile) throws IOException {
        byte[] originalBytes = Files.readAllBytes(classFile);
        byte[] rewrittenBytes = rewriteClass(originalBytes);
        if (rewrittenBytes == null) {
            return false;
        }
        Files.write(classFile, rewrittenBytes);
        return true;
    }

    public byte[] rewriteClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        RewritingClassVisitor visitor = new RewritingClassVisitor(writer);
        reader.accept(visitor, 0);
        return visitor.rewritten ? writer.toByteArray() : null;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: <classes-directory>");
        }

        Path classesDirectory = Path.of(args[0]);
        int rewrittenCount = new GpuMethodBodyRewriter().rewriteDirectory(classesDirectory);
        System.out.println("Rewritten @GPU methods in " + rewrittenCount + " class file(s)");
    }

    private static final class RewritingClassVisitor extends ClassVisitor {

        private final List<MethodRewritePlan> rewritePlans = new ArrayList<>();
        private String ownerInternalName;
        private boolean rewritten;

        private RewritingClassVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            ownerInternalName = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (CLINIT_NAME.equals(name) || INIT_NAME.equals(name) || (access & Opcodes.ACC_ABSTRACT) != 0 || (access & Opcodes.ACC_NATIVE) != 0) {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            return new MethodAnnotationScanner(access, name, descriptor, signature, exceptions, this);
        }

        private void register(MethodRewritePlan plan) {
            rewritePlans.add(plan);
        }

        private ClassVisitor delegate() {
            return cv;
        }

        @Override
        public void visitEnd() {
            for (MethodRewritePlan plan : rewritePlans) {
                String launcherInternalName = GpuLauncherNaming.launcherInternalName(ownerInternalName, plan.methodName());
                MethodVisitor delegate = cv.visitMethod(plan.access(), plan.methodName(), plan.methodDescriptor(), plan.signature(), plan.exceptions());
                GeneratorAdapter generator = new GeneratorAdapter(delegate, plan.access(), plan.methodName(), plan.methodDescriptor());
                generator.visitCode();

                Type[] argumentTypes = Type.getArgumentTypes(plan.methodDescriptor());
                for (int index = 0; index < argumentTypes.length; index++) {
                    generator.loadArg(index);
                }

                generator.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        launcherInternalName,
                        "invoke",
                        plan.methodDescriptor(),
                        false
                );
                generator.returnValue();
                generator.endMethod();
                rewritten = true;
            }

            super.visitEnd();
        }
    }

    private static final class MethodAnnotationScanner extends MethodVisitor {

        private final int access;
        private final String name;
        private final String descriptor;
        private final String signature;
        private final String[] exceptions;
        private final RewritingClassVisitor owner;
        private final MethodNode methodNode;
        private boolean gpuAnnotated;

        private MethodAnnotationScanner(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions,
                RewritingClassVisitor owner
        ) {
            super(Opcodes.ASM9, new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions));
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
            this.owner = owner;
            this.methodNode = (MethodNode) mv;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (LEGACY_GPU_ANNOTATION_DESCRIPTOR.equals(descriptor)
                    || CANONICAL_GPU_ANNOTATION_DESCRIPTOR.equals(descriptor)) {
                gpuAnnotated = true;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitEnd() {
            if (gpuAnnotated) {
                owner.register(new MethodRewritePlan(access, name, descriptor, signature, exceptions));
            } else {
                methodNode.accept(owner.delegate());
            }
            super.visitEnd();
        }
    }

    private record MethodRewritePlan(
            int access,
            String methodName,
            String methodDescriptor,
            String signature,
            String[] exceptions
    ) {
    }
}
