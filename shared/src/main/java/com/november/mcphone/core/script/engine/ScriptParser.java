package com.november.mcphone.core.script.engine;

import com.november.mcphone.internal.rhino.javascript.CompilerEnvirons;
import com.november.mcphone.internal.rhino.javascript.Parser;
import com.november.mcphone.internal.rhino.javascript.ast.AstRoot;

/** Keep source offsets consistent with the selected engine's String lexer. */
final class ScriptParser {
    private ScriptParser() {}
    static AstRoot parse(CompilerEnvirons environment, String source) {
        return new Parser(environment).parse(source, "<script>", 1);
    }
}
