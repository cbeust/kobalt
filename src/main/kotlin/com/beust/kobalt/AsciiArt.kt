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

        val banner : String get() = BANNERS.get(Random().nextInt(BANNERS.size))

        fun box(s: String) : List<String> {
            val ul = "\u2554"
            val ur = "\u2557"
            val h = "\u2550"
            val v = "\u2551"
            val bl = "\u255a"
            val br = "\u255d"

            fun r(n: Int, w: String) : String {
                with(StringBuffer()) {
                    repeat(n, { append(w) })
                    return toString()
                }
            }

            return arrayListOf(
                    ul + r(s.length + 2, h) + ur,
                    "$v $s $v",
                    bl + r(s.length + 2, h) + br)
        }

        val defaultLog : (s: String) -> Unit = { log(1, "          $it") }
        fun logBox(s: String, print: (String) -> Unit = defaultLog) {
            box(s).forEach {
                print(it)
            }
        }
    }
}

