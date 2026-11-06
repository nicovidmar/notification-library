package quality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Compile examples against production classes only, then run their own contract assertions. */
class ExamplesTest {
    @Test
    void allExamplesCompileAndRunWithoutTestDependencies(@TempDir Path output) throws Exception {
        var args = new ArrayList<String>();
        args.addAll(java.util.List.of("--release", "21", "-encoding", "UTF-8",
                "-classpath", "target/classes", "-d", output.toString()));
        java.util.List<Path> sources;
        try (var paths = Files.list(Path.of("examples"))) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(sources.size() >= 3);
        sources.forEach(p -> args.add(p.toString()));
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Full JDK required");
        assertEquals(0, compiler.run(null, null, null, args.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new java.net.URL[] {
                output.toUri().toURL(), Path.of("target/classes").toUri().toURL()
        }, ClassLoader.getPlatformClassLoader())) {
            for (Path source : sources) {
                String name = source.getFileName().toString().replace(".java", "");
                loader.loadClass(name).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        }
    }
}
