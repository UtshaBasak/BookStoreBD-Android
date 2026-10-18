package io.github.utshabasak.bookstorebd.core.model

/**
 * The text with every digit written the ASCII way: a Bangla keyboard types
 * "০১৭১২৩৪৫৬৭৮", which the shop reads as 01712345678.
 */
fun String.asciiDigits(): String = map { if (it.isDigit()) '0' + it.digitToInt() else it }.joinToString("")

/** Only the digits, in ASCII: for numbers, codes and prices typed in either script. */
fun String.digitsOnly(): String = filter(Char::isDigit).asciiDigits()
