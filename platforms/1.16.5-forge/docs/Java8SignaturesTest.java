package com.november.mcphone.core.script.pkg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.Security;
import java.util.Arrays;

/** Runs unchanged on the legacy provider and the JDK provider, including cross-runtime fixtures. */
public final class Java8SignaturesTest {
    private static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static int checks;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        java.security.Provider[] before = Security.getProviders();
        KeyPair pair = Signatures.generate();
        byte[] signature = Signatures.sign(pair.getPrivate(), DIGEST);
        check(pair.getPublic().getEncoded().length == 44, "canonical X.509 public key");
        check(signature.length == 64, "Ed25519 signature size");
        check(Signatures.verify(pair.getPublic(), DIGEST, signature), "generated key roundtrip");
        check(Signatures.verify(Signatures.publicKey(pair.getPublic().getEncoded()), DIGEST, signature), "public key import");
        check(Arrays.equals(signature, Signatures.sign(Signatures.privateKey(pair.getPrivate().getEncoded()), DIGEST)), "private key import");
        check(!Signatures.verify(pair.getPublic(), DIGEST + "x", signature), "altered digest");
        byte[] bad = signature.clone(); bad[0] ^= 1;
        check(!Signatures.verify(pair.getPublic(), DIGEST, bad), "altered signature");
        check(!Signatures.verify(pair.getPublic(), DIGEST, new byte[63]), "truncated signature");
        byte[] identity = pair.getPublic().getEncoded().clone();
        Arrays.fill(identity, 12, 44, (byte) 0); identity[12] = 1;
        try { Signatures.publicKey(identity); throw new AssertionError("small-order key accepted"); }
        catch (PackageError expected) { checks++; }
        check(Arrays.equals(before, Security.getProviders()), "global provider list unchanged");
        if (args.length == 2) {
            Path dir = Paths.get(args[1]);
            if ("write".equals(args[0])) {
                Files.createDirectories(dir);
                Files.write(dir.resolve("public.der"), pair.getPublic().getEncoded());
                Files.write(dir.resolve("private.der"), pair.getPrivate().getEncoded());
                Files.write(dir.resolve("signature.bin"), signature);
            } else if ("verify".equals(args[0])) {
                byte[] expected = Files.readAllBytes(dir.resolve("signature.bin"));
                check(Signatures.verify(Signatures.publicKey(Files.readAllBytes(dir.resolve("public.der"))), DIGEST, expected), "foreign runtime verify");
                check(Arrays.equals(expected, Signatures.sign(Signatures.privateKey(Files.readAllBytes(dir.resolve("private.der"))), DIGEST)), "foreign runtime private key/signature");
            } else throw new IllegalArgumentException(args[0]);
        }
        System.out.println("Java8SignaturesTest " + checks + " checks, Java " + System.getProperty("java.version"));
    }
}
