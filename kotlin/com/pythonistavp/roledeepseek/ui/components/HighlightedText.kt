package com.pythonistavp.roledeepseek.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pythonistavp.roledeepseek.util.FuzzyMatch

/** Текст с подсветкой найденных при поиске фрагментов. */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val annotated = remember(text, query, highlight) {
        buildAnnotatedString {
            append(text)
            if (query.isNotBlank()) {
                FuzzyMatch.highlightRanges(text, query).forEach { range ->
                    if (range.first >= 0 && range.last < text.length) {
                        addStyle(
                            SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold),
                            range.first,
                            range.last + 1,
                        )
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}
