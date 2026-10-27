package com.galleryorganizer.domain

internal class JsonParseException(message: String) : IllegalArgumentException(message)

/**
 * Minimal strict JSON parser for fixed domain documents. Returns nested
 * Map/List/String/Long/Double/Boolean/null values; no external dependency.
 */
internal object MiniJson {
    fun parse(input: String): Any? {
        val reader = Reader(input)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw JsonParseException("Unexpected trailing content.")
        return value
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index] in " \t\r\n") index++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw JsonParseException("Unexpected end of document.")
            return when (text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when {
                    consume(',') -> Unit
                    consume('}') -> return result
                    else -> throw JsonParseException("Expected ',' or '}' at $index.")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (consume(']')) return result
            while (true) {
                result += readValue()
                skipWhitespace()
                when {
                    consume(',') -> Unit
                    consume(']') -> return result
                    else -> throw JsonParseException("Expected ',' or ']' at $index.")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < text.length) {
                when (val c = text[index++]) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(readEscape())
                    '\t', '\n', '\r' ->
                        throw JsonParseException("Unescaped control character.")
                    else -> builder.append(c)
                }
            }
            throw JsonParseException("Unterminated string.")
        }

        private fun readEscape(): Char {
            if (atEnd()) throw JsonParseException("Unterminated escape.")
            return when (val c = text[index++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) throw JsonParseException("Short unicode escape.")
                    val hex = text.substring(index, index + 4)
                    index += 4
                    hex.toIntOrNull(16)?.toChar()
                        ?: throw JsonParseException("Invalid unicode escape: $hex.")
                }
                else -> throw JsonParseException("Invalid escape: $c.")
            }
        }

        private fun readBoolean(): Boolean = when {
            consumeLiteral("true") -> true
            consumeLiteral("false") -> false
            else -> throw JsonParseException("Invalid literal at $index.")
        }

        private fun readNull(): Any? {
            if (!consumeLiteral("null")) throw JsonParseException("Invalid literal at $index.")
            return null
        }

        private fun readNumber(): Number {
            val start = index
            if (text[index] == '-') index++
            if (index >= text.length) throw JsonParseException("Invalid number.")
            when {
                text[index] == '0' -> index++
                text[index].isDigit() -> {
                    while (index < text.length && text[index].isDigit()) index++
                }
                else -> throw JsonParseException("Invalid number at $index.")
            }
            var floating = false
            if (index < text.length && text[index] == '.') {
                floating = true
                index++
                if (index >= text.length || !text[index].isDigit()) {
                    throw JsonParseException("Invalid fraction at $index.")
                }
                while (index < text.length && text[index].isDigit()) index++
            }
            if (index < text.length && text[index] in "eE") {
                floating = true
                index++
                if (index < text.length && text[index] in "+-") index++
                if (index >= text.length || !text[index].isDigit()) {
                    throw JsonParseException("Invalid exponent at $index.")
                }
                while (index < text.length && text[index].isDigit()) index++
            }
            val raw = text.substring(start, index)
            return if (floating) raw.toDouble() else raw.toLong()
        }

        private fun consumeLiteral(literal: String): Boolean {
            if (text.startsWith(literal, index)) {
                index += literal.length
                return true
            }
            return false
        }

        private fun expect(expected: Char) {
            skipWhitespace()
            if (atEnd() || text[index] != expected) {
                throw JsonParseException("Expected '$expected' at $index.")
            }
            index++
        }

        private fun consume(expected: Char): Boolean {
            if (!atEnd() && text[index] == expected) {
                index++
                return true
            }
            return false
        }
    }
}
