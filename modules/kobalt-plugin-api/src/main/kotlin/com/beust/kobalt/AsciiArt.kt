package com.beust.kobalt

import java.util.*

/**
 * Make Kobalt's output awesome and unique.
 *
 * I spend so much time staring at build outputs I decided I might as well make them pretty.
 * Note that I also experimented with colors but it's hard to come up with a color scheme that
 * will work with all the various backgrounds developers use, so I decided to be conservative
 * and stick to simple red/yellow for errors and warnings.
 *
 * @author Cedric Beust <cedric@beust.com>
 * @since 10/1/2015
 */
class AsciiArt {
    companion object {
        private val BANNERS = arrayOf(
                "              __ __           __              __   __ \n" +
                "             / //_/  ____    / /_   ____ _   / /  / /_\n" +
                "            / ,<    / __ \\  / __ \\ / __ `/  / /  / __/\n" +
                "           / /| |  / /_/ / / /_/ // /_/ /  / /  / /_  \n" +
                "          /_/ |_|  \\____/ /_.___/ \\__,_/  /_/   \\__/  ",

                "            _  __          _               _   _   \n" +
                "           | |/ /   ___   | |__     __ _  | | | |_ \n" +
                "           | ' /   / _ \\  | '_ \\   / _` | | | | __|\n" +
                "           | . \\  | (_) | | |_) | | (_| | | | | |_ \n" +
                "           |_|\\_\\  \\___/  |_.__/   \\__,_| |_|  \\__|  "
        )

        val banner : String get() = BANNERS[Random().nextInt(BANNERS.size)]

//        fun box(s: String) : List<String> = box(listOf(s))

        val horizontalSingleLine = "\u2500\u2500\u2500\u2500\u2500"
        val horizontalDoubleLine = "\u2550\u2550\u2550\u2550\u2550"
        val verticalBar = "\u2551"

//        fun horizontalLine(n: Int) = StringBuffer().apply {
//                repeat(n, { append("\u2500") })
//            }.toString()

        // Repeat
        fun r(n: Int, w: String) : String {
            with(StringBuffer()) {
                repeat(n, { append(w) })
                return toString()
            }
        }

        val h = "\u2550"
        val ul = "\u2554"
        val ur = "\u2557"
        val bottomLeft = "\u255a"
        val bottomRight = "\u255d"

        // Bottom left with continuation
        val bottomLeft2 = "\u2560"
        // Bottom right with continuation
        val bottomRight2 = "\u2563"

        fun upperBox(max: Int) = ul + r(max + 2, h) + ur
        fun lowerBox(max: Int, bl: String = bottomLeft, br : String = bottomRight) = bl + r(max + 2, h) + br

        private fun box(strings: List<String>, bl: String = bottomLeft, br: String = bottomRight) : List<String> {
            val v = verticalBar

            val maxString: String = strings.maxBy { it.length } ?: ""
            val max = maxString.length
            val result = arrayListOf(upperBox(max))
            result.addAll(strings.map { "$v ${center(it, max - 2)} $v" })
            result.add(lowerBox(max, bl, br))
            return result
        }

        fun logBox(strings: List<String>, bl: String = bottomLeft, br: String = bottomRight, indent: Int = 0): String {
            return buildString {
                val boxLines = box(strings, bl, br)
                boxLines.withIndex().forEach { iv ->
                    append(fill(indent)).append(iv.value)
                    if (iv.index < boxLines.size - 1) append("\n")
                }
            }
        }

        fun logBox(s: String, bl: String = bottomLeft, br: String = bottomRight, indent: Int = 0)
            = logBox(listOf(s), bl, br, indent)

        fun fill(n: Int) = buildString { repeat(n, { append(" ")})}.toString()

        fun center(s: String, width: Int) : String {
            val diff = width - s.length
            val spaces = diff / 2 + 1
            return fill(spaces) + s + fill(spaces + if (diff % 2 == 1) 1 else 0)
        }

        const val RESET = "\u001B[0m"
        const val BLACK = "\u001B[30m"
        const val RED = "\u001B[31m"
        const val GREEN = "\u001B[32m"
        const val YELLOW = "\u001B[33m";
        const val BLUE = "\u001B[34m"
        const val PURPLE = "\u001B[35m"
        const val CYAN = "\u001B[36m"
        const val WHITE = "\u001B[37m"

        private fun wrap(s: CharSequence, color: String) = color + s + RESET
        private fun blue(s: CharSequence) = wrap(s, BLUE)
        private fun red(s: CharSequence) = wrap(s, RED)
        private fun yellow(s: CharSequence) = wrap(s, YELLOW)

        fun taskColor(s: CharSequence) = s
        fun errorColor(s: CharSequence) = red(s)
        fun warnColor(s: CharSequence) = red(s)
    }
}

class AsciiTable {
    class Builder {
        private val headers = arrayListOf<String>()
        fun header(name: String) = headers.add(name)
        fun headers(vararg names: String) = headers.addAll(names)

        private val widths = arrayListOf<Int>()
        fun columnWidth(w: Int) : Builder {
            widths.add(w)
            return this
        }

        private val rows = arrayListOf<List<String>>()
        fun addRow(row: List<String>) = rows.add(row)

        private fun col(width: Int, s: String) : String {
            val format = " %1\$-${width.toString()}s"
            val result = String.format(format, s)
            return result
        }

        val vb = AsciiArt.verticalBar

        fun build() : String {
            val formattedHeaders =
                headers.mapIndexed { index, s ->
                    val s2 = col(widths[index], s)
                    s2
                }.joinToString(vb)
            val result = StringBuffer().apply {
                append(AsciiArt.logBox(formattedHeaders, AsciiArt.bottomLeft2, AsciiArt.bottomRight2))
                append("\n")
            }
            var lineLength = 0
            rows.forEachIndexed { _, row ->
                val formattedRow = row.mapIndexed { i, s -> col(widths[i], s) }.joinToString(vb)
                val line = "$vb $formattedRow $vb"
                result.append(line).append("\n")
                lineLength = line.length
            }
            result.append(AsciiArt.lowerBox(lineLength - 4))
            return result.toString()
        }

    }
}
