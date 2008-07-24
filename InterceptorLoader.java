import org.objectweb.asm.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class InterceptorLoader extends ClassLoader
{
    protected class InterceptorClassAdapter extends ClassAdapter
    {
        boolean finished;
        String className;

        InterceptorClassAdapter(ClassVisitor cv, String className)
        {
            super(cv);
            this.className = className;
        }

        public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions )
        {
            return new InterceptorMethodAdapter(
                cv.visitMethod(access, name, desc, signature, exceptions),
                className, name, (access & Opcodes.ACC_STATIC) != 0 );
        }

        public void visitEnd()
        {
            finished = true;
            cv.visitEnd();
        }

        public boolean isFinished() {
            return finished;
        }
    }

    protected class InterceptorMethodAdapter extends MethodAdapter
    {
        String className, methodName;
        boolean methodStatic, instrumented;
        Label tryLabel     = new Label(),
              catchLabel   = new Label(),
              finallyLabel = new Label();

        static final int minStack = 3, minLocals = 4;

        InterceptorMethodAdapter( MethodVisitor mv, String className,
                                  String methodName, boolean methodStatic )
        {
            super(mv);
            this.className    = className;
            this.methodName   = methodName;
            this.methodStatic = methodStatic;
        }

        void generateHeader()
        {
            instrumented = true;

            mv.visitLdcInsn(className);
            mv.visitLdcInsn(methodName);
            if(this.methodStatic)
                mv.visitInsn(Opcodes.ACONST_NULL);
            else
                mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn( Opcodes.INVOKESTATIC,
                                Type.getInternalName(InterceptorLoader.class),
                                "beginInvoke",
                                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V" );

            mv.visitLabel(tryLabel);
        }

        void generateFooter()
        {
            mv.visitLabel(catchLabel);
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitInsn(Opcodes.RETURN);

            mv.visitLabel(finallyLabel);
            mv.visitVarInsn(Opcodes.ASTORE, 1);

            mv.visitLdcInsn(className);
            mv.visitLdcInsn(methodName);
            if(this.methodStatic)
                mv.visitInsn(Opcodes.ACONST_NULL);
            else
                mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn( Opcodes.INVOKESTATIC,
                                Type.getInternalName(InterceptorLoader.class),
                                "endInvoke",
                                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V" );

            mv.visitVarInsn(Opcodes.RET, 1);

            mv.visitTryCatchBlock(tryLabel, catchLabel, catchLabel, null);
        }

        public void visitMaxs(int maxStack, int maxLocals)
        {
            mv.visitMaxs( maxStack < minStack ? minStack : maxStack,
                          maxLocals < minLocals ? minLocals : maxLocals);
        }

        public void visitCode()
        {
            mv.visitCode();

            if(!"<init>".equals(methodName))
                generateHeader();

        }

        public void visitMethodInsn(int opcode, String owner, String name, String desc)
        {
            mv.visitMethodInsn(opcode, owner, name, desc);
            if(opcode == Opcodes.INVOKESPECIAL && !instrumented)
                generateHeader();
        }

        public void visitInsn(int opcode)
        {
            if(instrumented)
            {
                switch(opcode)
                {
                case Opcodes.IRETURN:
                    mv.visitVarInsn(Opcodes.ISTORE, 2);
                    mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
                    mv.visitVarInsn(Opcodes.ILOAD, 2);
                    break;

                case Opcodes.LRETURN:
                    mv.visitVarInsn(Opcodes.LSTORE, 2);
                    mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
                    mv.visitVarInsn(Opcodes.LLOAD, 2);
                    break;
    
                case Opcodes.FRETURN:
                    mv.visitVarInsn(Opcodes.FSTORE, 2);
                    mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
                    mv.visitVarInsn(Opcodes.FLOAD, 2);
                    break;
    
                case Opcodes.DRETURN:
                    mv.visitVarInsn(Opcodes.DSTORE, 2);
                    mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
                    mv.visitVarInsn(Opcodes.DLOAD, 2);
                    break;
    
                case Opcodes.ARETURN:
                    mv.visitVarInsn(Opcodes.ASTORE, 2);
                    mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    break;
    
                case Opcodes.RETURN:
                    mv.visitJumpInsn(Opcodes.JSR, finallyLabel);
                    break;
                }
            }

            mv.visitInsn(opcode);
        }

        public void visitEnd()
        {
            if(instrumented)
                generateFooter();
            mv.visitEnd();
        }

    }


    static ArrayList<InvocationListener> listeners = new ArrayList<InvocationListener>();
    String basePath;

    static void addInvocationListener(InvocationListener listener)
    {
        listeners.add(listener);
    }

    static void removeInvocationListener(InvocationListener listener)
    {
        listeners.remove(listener);
    }

    public InterceptorLoader(ClassLoader parent, String basePath)
    {
        super(parent);
        this.basePath = basePath;
    }

    public InterceptorLoader(String basePath)
    {
        super();
        this.basePath = basePath;
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException
    {
        return loadClass(name, false);
    }
 
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
        Class<?> result = findLoadedClass(name);

        if(result != null)
        {
            // Class was already loaded
            return result;
        }

        try
        {
            String relPath = name.replace('.', '/').concat(".class"),
                   absPath = basePath + '/' + relPath;

            FileInputStream inputStream = new FileInputStream(absPath);
            ClassReader classReader = new ClassReader(inputStream);
            ClassWriter classWriter = new ClassWriter(false);
            InterceptorClassAdapter classAdapter = new InterceptorClassAdapter(classWriter, name);

            while(!classAdapter.isFinished())
                classReader.accept(classAdapter, true);
            byte[] bytes = classWriter.toByteArray();
            result = defineClass(name, bytes, 0, bytes.length);

            if(resolve)
                resolveClass(result);

            System.err.println("InterceptorLoader: class '" + name + "' sucessfully loaded.");
        }
        catch(FileNotFoundException e)
        {
            System.err.print("InterceptorLoader: file not found for class '" + name + " '");
        }
        catch(IOException e)
        {
            System.err.print("InterceptorLoader: error reading from file for class '" + name + "' ");
        }
        catch(ClassFormatError e)
        {
            System.err.print("InterceptorLoader: malformed bytecode for class '" + name + "' ");
        }

        if(result == null)
        {
            System.err.println("using default class loader instead.");
            result = super.loadClass(name, resolve);
        }

        return result;
    }

    public static void beginInvoke(String className, String methodName, Object inst)
    {
        for(InvocationListener listener : listeners)
        {
            try {
                listener.beginInvoke(className, methodName, inst);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void endInvoke(String className, String methodName, Object inst)
    {
        for(InvocationListener listener : listeners)
        {
            try {
                listener.endInvoke(className, methodName, inst);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

};

