package com.oriondev.moneywallet.testing

object SourceScanner {

    fun literalEnd(source: String, at: Int): Int {
        if (source.startsWith("\"\"\"", at)) {
            return rawStringEnd(source, at + 3)
        }
        val c = source[at]
        if (c == '"') {
            return stringEnd(source, at + 1)
        }
        if (c == '\'') {
            return charEnd(source, at + 1)
        }
        return -1
    }

    fun commentEnd(source: String, at: Int): Int {
        if (source.startsWith("//", at)) {
            val newline = source.indexOf('\n', at)
            return if (newline < 0) source.length else newline
        }
        if (source.startsWith("/*", at)) {
            return blockCommentEnd(source, at)
        }
        return -1
    }

    private fun rawStringEnd(source: String, at: Int): Int {
        var at = at
        while (at < source.length) {
            if (source.startsWith("\"\"\"", at)) {
                at += 3
                while (at < source.length && source[at] == '"') {
                    at++
                }
                return at
            }
            if (source.startsWith("\${", at)) {
                at = templateEnd(source, at + 2)
            } else {
                at++
            }
        }
        return source.length
    }

    private fun stringEnd(source: String, at: Int): Int {
        var at = at
        while (at < source.length) {
            val c = source[at]
            if (c == '\\') {
                at += 2
            } else if (c == '"') {
                return at + 1
            } else if (source.startsWith("\${", at)) {
                at = templateEnd(source, at + 2)
            } else if (c == '\n') {
                return at
            } else {
                at++
            }
        }
        return source.length
    }

    private fun templateEnd(source: String, at: Int): Int {
        var at = at
        var depth = 1
        while (at < source.length) {
            var end = literalEnd(source, at)
            if (end < 0) {
                end = commentEnd(source, at)
            }
            if (end >= 0) {
                at = end
                continue
            }
            val c = source[at]
            if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0) {
                    return at + 1
                }
            }
            at++
        }
        return source.length
    }

    private fun charEnd(source: String, at: Int): Int {
        var at = at
        if (at < source.length && source[at] == '\\') {
            at += 2
        } else {
            at++
        }
        while (at < source.length && source[at] != '\'' && source[at] != '\n') {
            at++
        }
        return if (at < source.length && source[at] == '\'') at + 1 else at
    }

    private fun blockCommentEnd(source: String, at: Int): Int {
        var at = at
        var depth = 0
        while (at < source.length) {
            if (source.startsWith("/*", at)) {
                depth++
                at += 2
            } else if (source.startsWith("*/", at)) {
                depth--
                at += 2
                if (depth == 0) {
                    return at
                }
            } else {
                at++
            }
        }
        return source.length
    }
}
