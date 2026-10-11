package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The condition grades a seller can give a second-hand copy, as the website lists them. */
val BOOK_CONDITIONS = listOf("mint", "very good", "good", "fair", "poor")

/** A grade in the app's language; an unknown one as the seller wrote it. */
@Composable
fun conditionLabel(grade: String): String =
    when (grade.lowercase()) {
        "mint" -> stringResource(R.string.core_ui_condition_mint)
        "very good" -> stringResource(R.string.core_ui_condition_very_good)
        "good" -> stringResource(R.string.core_ui_condition_good)
        "fair" -> stringResource(R.string.core_ui_condition_fair)
        "poor" -> stringResource(R.string.core_ui_condition_poor)
        else -> grade
    }
