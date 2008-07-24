import java.lang.reflect.*;
import java.util.*;

public class Test implements InvocationListener
{
    WeakIdentityHashMap objects = new WeakIdentityHashMap();
    static int counter;
    Stack<String> callStack = new Stack<String>();

    public static void main(String[] args)
        throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException
    {
        InterceptorLoader.addInvocationListener(new Test());

        ClassLoader loader      = new InterceptorLoader("/home/maks/Java Interceptors/Atlantis/build");
        Class<?>    mainClass   = loader.loadClass("Atlantis");
        Method      mainMethod  = mainClass.getDeclaredMethod("main", args.getClass());

        mainMethod.invoke(null, (Object)args);
    }

    synchronized String id(Object instance)
    {
        Object r = objects.get(instance);
        if(r != null)
            return (String)r;

        String className = instance.getClass().getName();
        int n = counter++;
        String id = className + '#' + n;
        objects.put(instance, id);
        return id;
    }

    public void beginInvoke(String className, String methodName, Object instance)
    {
        String current = className + "." + methodName;

        for(int n = 0; n < callStack.size(); ++n)
            System.out.print("..");
        System.out.println(instance != null ? id(instance) + ": " + current : current);

        callStack.push(current);
    }

    public void endInvoke(String className, String methodName, Object instance)
    {
        callStack.pop();
    }
}