package com.november.mcphone.tools.rhino;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Produces the Java 8 engine without a module descriptor or build-time dependencies. */
public final class Java8RhinoProducer {
    static final String VERSION = "1.7.15.1-mcphone.2";
    static final String INPUT_SHA = "f3520a0e2e22d550f1216a456be233eb5385bf08e3219407dece7a16369eb38f";
    static final String SOURCES_SHA = "bb6c4b860ea690199b5da29789244eba2f82c4ced9e11ffa4f1b23e386161e94";
    private static final String ORIGINAL = "org/mozilla/";
    private static final String PRIVATE = "com/november/mcphone/internal/rhino/";
    private static final Pattern QUALIFIED = Pattern.compile("(?<![\\w.$/])org\\.mozilla(?=\\.)");
    private static final Remapper RELOCATE = new Remapper(org.objectweb.asm.Opcodes.ASM9) {
        @Override public String map(String name) { return path(name); }
        @Override public String mapPackageName(String name) { return path(name + "/").replaceFirst("/$", ""); }
        @Override public Object mapValue(Object value) {
            if (value instanceof String text) {
                return text.replace(ORIGINAL, PRIVATE).replace("org.mozilla.", "com.november.mcphone.internal.rhino.");
            }
            return super.mapValue(value);
        }
    };

    private Java8RhinoProducer() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: <Rhino jar> <Rhino sources> <staging directory>");
        var input = Path.of(args[0]);
        var inputSources = Path.of(args[1]);
        var out = Path.of(args[2]);
        require(ArtifactFiles.sha(input).equals(INPUT_SHA), "Unexpected upstream Rhino binary");
        require(ArtifactFiles.sha(inputSources).equals(SOURCES_SHA), "Unexpected upstream Rhino sources");
        var binary = new TreeMap<String, byte[]>();
        var sources = new TreeMap<String, byte[]>();
        var original = ArtifactFiles.read(input);
        for (var entry : original.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue();
            if (name.endsWith(".class")) {
                require(name.startsWith(ORIGINAL), "Unexpected class entry: " + name);
                ClassReader reader = new ClassReader(data);
                require(reader.readUnsignedShort(6) <= 52, "Rhino is not Java 8 compatible: " + name);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassRemapper(writer, RELOCATE), 0);
                data = writer.toByteArray();
            } else if (!name.startsWith(ORIGINAL)) {
                continue;
            }
            add(binary, path(name), data);
        }
        for (var entry : ArtifactFiles.read(inputSources).entrySet()) {
            if (!entry.getKey().startsWith(ORIGINAL)) continue;
            byte[] data = entry.getValue();
            if (entry.getKey().endsWith(".java")) {
                String text = QUALIFIED.matcher(new String(data, StandardCharsets.UTF_8))
                        .replaceAll("com.november.mcphone.internal.rhino");
                // A qualified name stops the pattern above; relocate the Packages example literally.
                text = text.replace("Packages.org.mozilla.", "Packages.com.november.mcphone.internal.rhino.");
                data = bytes(text);
            }
            add(sources, path(entry.getKey()), data);
        }
        byte[] license = original.get("META-INF/LICENSE.txt");
        require(license != null, "Rhino license absent");
        byte[] noticeTools = original.get("META-INF/NOTICE-tools.txt");
        require(noticeTools != null, "Rhino tools notice absent");
        for (var entries : java.util.List.of(binary, sources)) {
            add(entries, "META-INF/LICENSE-MCPhone-Rhino.txt", license);
            if (original.containsKey("META-INF/NOTICE.txt")) add(entries, "META-INF/NOTICE-MCPhone-Rhino.txt", original.get("META-INF/NOTICE.txt"));
            add(entries, "META-INF/NOTICE-tools-MCPhone-Rhino.txt", noticeTools);
            add(entries, "META-INF/MCPhone-Rhino-Changes.txt", bytes("Rhino 1.7.15.1 (MPL 2.0). MCPhone relocated Java types and resources.\n"
                    + "Upstream: https://github.com/mozilla/rhino/tree/Rhino1_7_15_1_Release\n"
                    + "Conversion: tools/private-rhino. Matching sources are distributed alongside this library.\n"));
        }
        add(binary, "META-INF/MANIFEST.MF", bytes("Manifest-Version: 1.0\r\nImplementation-Title: MCPhone private Rhino for Java 8\r\nImplementation-Version: " + VERSION + "\r\n\r\n"));
        var jar = out.resolve("rhino-" + VERSION + ".jar");
        var sourceJar = out.resolve("rhino-" + VERSION + "-sources.jar");
        ArtifactFiles.write(jar, binary);
        ArtifactFiles.write(sourceJar, sources);
        Files.writeString(out.resolve("rhino-" + VERSION + ".pom"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.november.mcphone.internal</groupId><artifactId>rhino</artifactId>
                  <version>1.7.15.1-mcphone.2</version><name>MCPhone private Java 8 Rhino</name>
                  <licenses><license><name>Mozilla Public License 2.0</name>
                    <url>https://www.mozilla.org/MPL/2.0/</url></license></licenses>
                </project>
                """);
        Files.writeString(out.resolve("provenance.properties"), "upstream.coordinate=org.mozilla:rhino:1.7.15.1\n"
                + "upstream.jar.sha256=" + INPUT_SHA + "\nupstream.sources.sha256=" + SOURCES_SHA
                + "\njar.sha256=" + ArtifactFiles.sha(jar) + "\nsources.sha256=" + ArtifactFiles.sha(sourceJar)
                + "\njava.minimum=8\nproducer.asm=9.9.1\n");
        System.out.println("PRIVATE_RHINO_JAVA8 " + VERSION + " SHA256=" + ArtifactFiles.sha(jar));
    }

    private static String path(String name) { return name.startsWith(ORIGINAL) ? PRIVATE + name.substring(ORIGINAL.length()) : name; }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void add(Map<String, byte[]> entries, String name, byte[] data) {
        require(entries.putIfAbsent(name, data) == null, "Duplicate entry after relocation: " + name);
    }
    private static void require(boolean ok, String failure) { if (!ok) throw new IllegalStateException(failure); }
}
