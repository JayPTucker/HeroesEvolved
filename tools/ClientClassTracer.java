import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Java agent for server runs in development (see tools/client-class-tracer.gradle). A real dedicated server has no
 * client classes at all, so loading one there crashes it; a development server has them, so the same mistake passes
 * every test. This logs the first load of each client-only class, with the stack that asked for it, to the file named in
 * the agent's argument; the Gradle script then fails the run if our code or vanilla's client package shows up.
 * It changes nothing it sees.
 */
public final class ClientClassTracer {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static final String[] CLIENT = {
        "net/minecraft/client/", "com/mojang/blaze3d/", "com/mojang/realmsclient/", "net/neoforged/neoforge/client/"
    };

    private ClientClassTracer() {
    }

    public static void premain(String file, Instrumentation instrumentation) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(file, false), true, StandardCharsets.UTF_8);
        out.println("# client classes loaded by this JVM, first load of each, with the stack that loaded it");
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (name == null || redefined != null) return null;
                for (String prefix : CLIENT) {
                    if (name.startsWith(prefix) && SEEN.add(name)) {
                        StringBuilder entry = new StringBuilder("LOAD ").append(name).append('\n');
                        for (StackTraceElement frame : new Throwable().getStackTrace())
                            entry.append("    at ").append(frame).append('\n');
                        synchronized (out) {
                            out.print(entry);
                        }
                        break;
                    }
                }
                return null;
            }
        });
    }
}
