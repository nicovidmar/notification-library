package quality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    private static final String ROOT = "dev.challenge.notifications.";
    private static final Pattern EDGE = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(\\S+)\\s*$");

    private String inspect(Path classes) {
        var output = new StringWriter();
        var writer = new PrintWriter(output);
        var tool = ToolProvider.findFirst("jdeps").orElseThrow(() -> new AssertionError("Full JDK required"));
        assertEquals(0, tool.run(writer, writer, "-verbose:class", "-filter:none", classes.toString()),
                output.toString());
        assertFalse(output.toString().contains("not found"), output.toString());
        return output.toString();
    }

    private List<String> violations(String output) {
        var errors = new ArrayList<String>();
        for (String line : output.lines().toList()) {
            var match = EDGE.matcher(line);
            if (!match.matches()) continue;
            String from = match.group(1), to = match.group(2), module = match.group(3);
            if (!from.startsWith(ROOT)) continue;
            if (!to.startsWith(ROOT)) {
                if (!module.equals("java.base") || to.startsWith("java.net.")) errors.add(line);
                continue;
            }
            String origin = from.substring(ROOT.length());
            String target = to.substring(ROOT.length());
            String group = origin.substring(0, origin.indexOf('.'));
            boolean allowed = switch (group) {
                case "domain" -> target.startsWith("domain.");
                case "api", "spi" -> target.startsWith(group + ".") || target.startsWith("domain.");
                case "templates" -> target.startsWith("templates.") || target.startsWith("domain.");
                case "retry" -> target.startsWith("retry.") || target.startsWith("domain.") || target.startsWith("spi.");
                case "application" -> List.of("application.", "domain.", "api.", "spi.").stream().anyMatch(target::startsWith);
                case "configuration" -> List.of("configuration.", "application.", "domain.", "api.", "spi.").stream().anyMatch(target::startsWith);
                case "channels" -> target.startsWith(origin.substring(0, origin.lastIndexOf('.') + 1)) || target.startsWith("domain.");
                case "adapters" -> target.startsWith(origin.substring(0, origin.lastIndexOf('.') + 1))
                        || target.startsWith("domain.") || target.startsWith("spi.") || target.startsWith("channels.");
                default -> false;
            };
            if (!allowed) errors.add(line);
        }
        return errors;
    }

    @Test
    void compiledDependenciesRespectHexagonalBoundariesAndNeedOnlyJavaBase() {
        String output = inspect(Path.of("target/classes"));
        assertTrue(output.lines().filter(line -> EDGE.matcher(line).matches()).count() > 100,
                "The architectural check must inspect real classes");
        assertEquals(List.of(), violations(output));
    }

    @Test
    void detectsAnActualCompiledDependencyFromDomainToChannel(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Forbidden.java");
        Files.writeString(source, "package dev.challenge.notifications.domain; "
                + "public class Forbidden { public dev.challenge.notifications.channels.email.EmailAddress address; }");
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-classpath", "target/classes", "-d",
                temp.toString(), source.toString()));
        // Supply production classes so dependency resolution cannot hide missing types.
        var output = new StringWriter();
        var writer = new PrintWriter(output);
        assertEquals(0, ToolProvider.findFirst("jdeps").orElseThrow().run(writer, writer,
                "-verbose:class", "-filter:none", "-cp", "target/classes", temp.toString()));
        assertTrue(violations(output.toString()).stream().anyMatch(line ->
                line.contains("Forbidden") && line.contains("EmailAddress")));
    }

    @Test
    void adapterTransportTypesStayInternalAndPublicStateIsFinal() throws Exception {
        try (var paths = Files.walk(Path.of("target/classes/dev/challenge/notifications/adapters"))) {
            var classes = paths.filter(p -> p.toString().endsWith(".class")).toList();
            assertFalse(classes.isEmpty());
            for (Path file : classes) {
                String name = Path.of("target/classes").relativize(file).toString()
                        .replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                if (name.endsWith("Request") || name.endsWith("Response") || name.endsWith("Client")) {
                    assertFalse(java.lang.reflect.Modifier.isPublic(type.getModifiers()), name);
                }
                if (java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
                    for (var field : type.getDeclaredFields()) {
                        assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()), field.toString());
                    }
                }
            }
        }
    }
}
