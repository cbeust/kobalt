package com.beust.kobalt

import com.beust.kobalt.misc.log
import java.util.*


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

//        fun horizontalLine(n: Int) = StringBuffer().apply {
//                repeat(n, { append("\u2500") })
//            }.toString()

        fun box(strings: List<String>) : List<String> {
            val ul = "\u2554"
            val ur = "\u2557"
            val h = horizontalDoubleLine
            val v = "\u2551"
            val bl = "\u255a"
            val br = "\u255d"

            fun r(n: Int, w: String) : String {
                with(StringBuffer()) {
                    repeat(n, { append(w) })
                    return toString()
                }
            }

            val maxString: String = strings.maxBy { it.length } ?: ""
            val max = maxString.length
            val result = arrayListOf(ul + r(max + 2, h) + ur)
            result.addAll(strings.map { "$v ${center(it, max - 2)} $v" })
            result.add(bl + r(max + 2, h) + br)
            return result
        }

        private fun fill(n: Int) = StringBuffer().apply { repeat(n, { append(" ")})}.toString()

        val defaultLog : (s: String) -> Unit = { log(1, "          $it") }

        fun logBox(strings: List<String>, print: (String) -> Unit = defaultLog) {
            box(strings).forEach {
                print(it)
            }
        }

        fun logBox(s: String, print: (String) -> Unit = defaultLog) {
            logBox(listOf(s), print)
        }

        fun center(s: String, width: Int) : String {
            val diff = width - s.length
            val spaces = diff / 2 + 1
            return fill(spaces) + s + fill(spaces + if (diff % 2 == 1) 1 else 0)
        }
    }
}

