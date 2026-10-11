package io.github.utshabasak.bookstorebd.core.scanner

/** ISBNs as books print them under their barcode: EAN-13 codes that start with 978 or 979. */
object Isbn {
    private const val LENGTH = 13
    private const val DIGITS = 10
    private const val ODD_WEIGHT = 3

    /** The ISBN a barcode carries, or null when it is another product's code or was misread. */
    fun fromBarcode(text: String): String? =
        text.takeIf { (it.startsWith("978") || it.startsWith("979")) && isValid13(it) }

    /** Whether [code] is thirteen digits whose last is the check digit of the twelve before it. */
    fun isValid13(code: String): Boolean {
        if (code.length != LENGTH || !code.all { it in '0'..'9' }) return false
        val sum = code.take(LENGTH - 1).withIndex().sumOf { (index, digit) -> (digit - '0') * if (index % 2 == 0) 1 else ODD_WEIGHT }
        return (DIGITS - sum % DIGITS) % DIGITS == code.last() - '0'
    }
}
