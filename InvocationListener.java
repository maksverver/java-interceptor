public interface InvocationListener
{
    void beginInvoke(String className, String methodName, Object inst);
    void endInvoke(String className, String methodName, Object inst);
}