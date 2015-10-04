package com.beust.kobalt

import java.util.*


class Banner {
    companion object {
        val BANNERS = arrayOf(
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

        fun get() = BANNERS.get(Random().nextInt(BANNERS.size()))
    }
}

