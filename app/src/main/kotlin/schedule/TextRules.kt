package schedule

/** Обозначение отсутствующего значения в файле и в выводе. */
const val MISSING_VALUE = "-"

/** Допустимые годы занятий. */
val VALID_YEARS: IntRange = 1..9999

/**
 * Общие определения «что считается плохим текстом». Ими пользуются и модель (в конструкторе),
 * и парсер (в своём порядке проверок), поэтому их понимание правил не может разойтись.
 */
object TextRules {

    /** Управляющий символ: U+0000..U+001F и U+007F..U+009F (в том числе табуляция и перевод строки). */
    fun hasControlCharacter(value: String): Boolean = value.any { it.isISOControl() }

    /** Пробельный символ в начале или в конце значения (включая неразрывный пробел). */
    fun hasOuterWhitespace(value: String): Boolean =
        value.isNotEmpty() && (value.first().isWhitespace() || value.last().isWhitespace())
}

/**
 * Значение в двойных кавычках, безопасное для однострочного английского сообщения:
 * `\` → `\\`, `"` → `\"`, управляющие и не-ASCII символы → `\uXXXX` на каждый UTF-16 символ.
 * Сами значения модели при этом не меняются — экранируется только текст сообщения.
 */
fun quoted(value: String): String = buildString(value.length + 2) {
    append('"')
    for (ch in value) {
        when {
            ch == '\\' -> append("\\\\")
            ch == '"' -> append("\\\"")
            ch.code < 0x20 || ch.code >= 0x7F -> {
                append("\\u")
                append(ch.code.toString(16).uppercase().padStart(4, '0'))
            }
            else -> append(ch)
        }
    }
    append('"')
}
