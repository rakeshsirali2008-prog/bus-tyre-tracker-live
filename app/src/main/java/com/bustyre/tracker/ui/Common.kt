package com.bustyre.tracker.ui

import androidx.compose.ui.graphics.Color
import com.bustyre.tracker.data.CONDITION_NEW
import com.bustyre.tracker.data.CONDITION_RESOLE
import com.bustyre.tracker.data.TyreEntity

/** 1 star = green (best), 2 = yellow (good), 3 = red (worst). */
fun ratingColor(rating: Int?): Color? = when (rating) {
    1 -> Color(0xFFA5D6A7)
    2 -> Color(0xFFFFF59D)
    3 -> Color(0xFFEF9A9A)
    else -> null
}

fun conditionLabel(condition: String?): String = when (condition) {
    CONDITION_NEW -> "New"
    CONDITION_RESOLE -> "Resole"
    else -> "Not set"
}

fun ratingLabel(rating: Int?): String = when (rating) {
    1 -> "1 ⭐ Best"
    2 -> "2 ⭐ Good"
    3 -> "3 ⭐ Worst"
    else -> "Not set"
}

/** Text shown on one tyre row. */
fun tyreSummary(tyre: TyreEntity): String =
    if (tyre.position == "SPARE" && tyre.present != true) {
        if (tyre.present == false) "Not present" else "Not asked yet"
    } else {
        "${conditionLabel(tyre.condition)}  ·  ${ratingLabel(tyre.rating)}"
    }
