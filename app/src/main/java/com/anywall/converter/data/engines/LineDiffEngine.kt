package com.anywall.converter.data.engines

/**
 * Motor comparador local de diferencias línea por línea (LCS - Longest Common Subsequence)
 * 100% Kotlin puro para comparar archivos de texto, código, CSV, JSON, XML o texto extraído de PDFs (v0.3).
 */
object LineDiffEngine {

    enum class DiffType(val prefixSymbol: String) {
        EQUAL(" "),
        ADDED("+"),
        REMOVED("-")
    }

    data class DiffLine(
        val type: DiffType,
        val leftLineNumber: Int?,
        val rightLineNumber: Int?,
        val text: String
    )

    data class DiffReport(
        val lines: List<DiffLine>,
        val addedCount: Int,
        val removedCount: Int,
        val unchangedCount: Int
    ) {
        fun toUnifiedDiffText(leftName: String, rightName: String): String {
            return buildString {
                appendLine("--- $leftName")
                appendLine("+++ $rightName")
                appendLine("@@ +$addedCount / -$removedCount / =$unchangedCount @@")
                for (line in lines) {
                    val lNum = line.leftLineNumber?.toString()?.padStart(4, ' ') ?: "    "
                    val rNum = line.rightLineNumber?.toString()?.padStart(4, ' ') ?: "    "
                    appendLine("$lNum $rNum ${line.type.prefixSymbol} ${line.text}")
                }
            }.trimEnd()
        }
    }

    fun computeDiff(leftText: String, rightText: String, maxLines: Int = 600): DiffReport {
        val leftLines = leftText.replace("\r\n", "\n").replace("\r", "\n").lines().take(maxLines)
        val rightLines = rightText.replace("\r\n", "\n").replace("\r", "\n").lines().take(maxLines)

        val n = leftLines.size
        val m = rightLines.size
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                if (leftLines[i] == rightLines[j]) {
                    dp[i][j] = 1 + dp[i + 1][j + 1]
                } else {
                    dp[i][j] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val result = ArrayList<DiffLine>(n + m)
        var i = 0
        var j = 0
        var added = 0
        var removed = 0
        var equal = 0

        while (i < n && j < m) {
            if (leftLines[i] == rightLines[j]) {
                result.add(
                    DiffLine(
                        type = DiffType.EQUAL,
                        leftLineNumber = i + 1,
                        rightLineNumber = j + 1,
                        text = leftLines[i]
                    )
                )
                equal++
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(
                    DiffLine(
                        type = DiffType.REMOVED,
                        leftLineNumber = i + 1,
                        rightLineNumber = null,
                        text = leftLines[i]
                    )
                )
                removed++
                i++
            } else {
                result.add(
                    DiffLine(
                        type = DiffType.ADDED,
                        leftLineNumber = null,
                        rightLineNumber = j + 1,
                        text = rightLines[j]
                    )
                )
                added++
                j++
            }
        }

        while (i < n) {
            result.add(
                DiffLine(
                    type = DiffType.REMOVED,
                    leftLineNumber = i + 1,
                    rightLineNumber = null,
                    text = leftLines[i]
                )
            )
            removed++
            i++
        }

        while (j < m) {
            result.add(
                DiffLine(
                    type = DiffType.ADDED,
                    leftLineNumber = null,
                    rightLineNumber = j + 1,
                    text = rightLines[j]
                )
            )
            added++
            j++
        }

        return DiffReport(
            lines = result,
            addedCount = added,
            removedCount = removed,
            unchangedCount = equal
        )
    }
}
