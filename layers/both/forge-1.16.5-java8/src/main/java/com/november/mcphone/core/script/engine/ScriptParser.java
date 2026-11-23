package com.november.mcphone.core.script.engine;

import com.november.mcphone.internal.rhino.javascript.CompilerEnvirons;
import com.november.mcphone.internal.rhino.javascript.Parser;
import com.november.mcphone.internal.rhino.javascript.ast.AstRoot;

/** Rhino 1.7's String lexer counts code points; its Reader lexer preserves UTF-16 offsets. */
final class ScriptParser {
    private ScriptParser() {}
    static AstRoot parse(CompilerEnvirons environment, String source) {
        try {
            return new Parser(environment).parse(new java.io.StringReader(source), "<script>", 1);
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException("StringReader failed", impossible);
        }
    }
}
