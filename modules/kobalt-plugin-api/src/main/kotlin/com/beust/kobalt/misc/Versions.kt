package com.beust.kobalt.misc

import com.beust.kobalt.maven.MavenId
import com.google.common.base.CharMatcher
import java.math.BigInteger
import java.util.*

public class Versions {
    companion object {
        /**
         * Turn "6.9.4" into 600090004
         */
        public fun toLongVersion(version: String) : Long {
            val count = version.countChar('.')
            val normalizedVersion = if (count == 2) version else if (count == 1) version + ".0"
            else version + ".0.0"

            fun parseLong(s: String, radix: Int) : Long {
                try {
                    return java.lang.Long.parseLong(s, radix)
                } catch(ex: NumberFormatException) {
                    warn("Couldn't parse version \"${version}\"")
                    return 0L
                }
            }

            return normalizedVersion
                    .split('.')
                    .take(3)
                    .map {
                        val s = CharMatcher.inRange('0', '9').or(CharMatcher.`is`('.')).retainFrom(it)
                        parseLong(s, 10)
                    }
                    .fold(0L, { n, s -> s + n * 10000 })
        }
    }
}

class Version(val version: String, val snapshotTimestamp: String? = null): Comparable<Version> {

    companion object {
        private val comparator = VersionComparator()
        fun of(string: String): Version {
            return Version(string)
        }
    }

    val noSnapshotVersion: String
        get() = version.replace("-SNAPSHOT", "")

    internal val items: List<Item>

    private var hash: Int = -1

    init {
        items = parse(version)
    }

    private fun parse(version: String): List<Item> {
        val items = arrayListOf<Item>()

        val tokenizer = Tokenizer(version)
        while (tokenizer.next()) {
            items.add(tokenizer.toItem())
        }

        trimPadding(items)

        return items
    }

    private fun trimPadding(items: MutableList<Item>) {
        var number: Boolean? = null
        var end = items.size - 1
        for (i in end downTo 1) {
            val item = items[i]
            if (item.isNumber != number) {
                end = i
                number = item.isNumber
            }
            if (end == i && (i == items.size - 1 || items[i - 1].isNumber == item.isNumber) && item.compareTo(null) == 0) {
                items.removeAt(i)
                end--
            }
        }
    }

    override fun compareTo(other: Version) = comparator.compare(this, other)

    override fun equals(other: Any?) = (other is Version) && comparator.compare(this, other) == 0

    override fun hashCode(): Int {
        if ( hash == -1 ) hash = Arrays.hashCode(items.toTypedArray())
        return hash
    }

    override fun toString() = version

    fun isSnapshot() = items.firstOrNull { it.isSnapshot } != null

    fun isRangedVersion() = MavenId.isRangedVersion(version)

    fun select(list: List<Version>): Version? {
        if (!(version.first() in listOf('[', '(') && version.last() in listOf(']', ')'))) {
            return this
        }
        var lowerExclusive = version.startsWith("(")
        var upperExclusive = version.endsWith(")")

        val split = version.drop(1).dropLast(1).split(',')

        val lower = Version.of(split[0].substring(1))
        val upper = if(split.size > 1) {
            Version.of(if (split[1].isNotBlank()) split[1] else Int.MAX_VALUE.toString())
        } else {
            lower
        }
        var filtered = list.filter { comparator.compare(it, lower) >= 0 && comparator.compare(it, upper) <= 0 }
        if (lowerExclusive && lower.equals(filtered.firstOrNull())) {
            filtered = filtered.drop(1)
        }
        if (upperExclusive && upper.equals(filtered.lastOrNull())) {
            filtered = filtered.dropLast(1)
        }

        return filtered.lastOrNull();
    }


}

class VersionComparator: Comparator<Version> {
    override fun compare(left: Version, right: Version): Int {
        val these = left.items
        val those = right.items

        var number = true

        var index = 0
        while (true) {
            if (index >= these.size && index >= those.size) {
                return 0
            } else if (index >= these.size) {
                return -comparePadding(those, index, null)
            } else if (index >= those.size) {
                return comparePadding(these, index, null)
            }

            val thisItem = these[index]
            val thatItem = those[index]

            if (thisItem.isNumber != thatItem.isNumber) {
                if (number == thisItem.isNumber) {
                    return comparePadding(these, index, number)
                } else {
                    return -comparePadding(those, index, number)
                }
            } else {
                val rel = thisItem.compareTo(thatItem)
                if (rel != 0) {
                    return rel
                }
                number = thisItem.isNumber
            }
            index++
        }
    }

    private fun comparePadding(items: List<Item>, index: Int, number: Boolean?): Int {
        var rel = 0
        for (i in index..items.size - 1) {
            val item = items[i]
            if (number != null && number !== item.isNumber) {
                break
            }
            rel = item.compareTo(null)
            if (rel != 0) {
                break
            }
        }
        return normalize(rel)
    }


}
internal class Item(private val kind: Int, private val value: Any) {

    // i.e. kind != string/qualifier
    val isNumber: Boolean
        get() = (kind and KIND_QUALIFIER) == 0

    val isSnapshot: Boolean
        get() = (kind and KIND_QUALIFIER) != 0 && value == Tokenizer.QUALIFIER_SNAPSHOT

    operator fun compareTo(that: Item?): Int {
        var rel: Int
        if (that == null) {
            // null in this context denotes the pad item (0 or "ga")
            when (kind) {
                KIND_MIN -> rel = -1
                KIND_MAX, KIND_BIGINT, KIND_STRING -> rel = 1
                KIND_INT, KIND_QUALIFIER -> rel = value as Int
                else -> throw IllegalStateException("unknown version item kind " + kind)
            }
        } else {
            rel = kind - that.kind
            if (rel == 0) {
                when (kind) {
                    KIND_MAX, KIND_MIN -> {
                    }
                    KIND_BIGINT -> rel = (value as BigInteger).compareTo(that.value as BigInteger)
                    KIND_INT, KIND_QUALIFIER -> rel = (value as Int).compareTo(that.value as Int)
                    KIND_STRING -> rel = (value as String).compareTo(that.value as String, ignoreCase = true)
                    else -> throw IllegalStateException("unknown version item kind " + kind)
                }
            }
        }
        return rel
    }

    override fun equals(other: Any?): Boolean {
        return (other is Item) && compareTo(other as Item?) == 0
    }

    override fun hashCode(): Int {
        return value.hashCode() + kind * 31
    }

    override fun toString(): String {
        return value.toString()
    }

    companion object {
        val KIND_MAX = 8
        val KIND_BIGINT = 5
        val KIND_INT = 4
        val KIND_STRING = 3
        val KIND_QUALIFIER = 2
        val KIND_MIN = 0
        val MAX = Item(KIND_MAX, "max")
        val MIN = Item(KIND_MIN, "min")
    }
}

internal class Tokenizer(version: String) {

    private val version: String

    private var index: Int = 0

    private var token: String = ""

    private var number: Boolean = false

    private var terminatedByNumber: Boolean = false

    init {
        this.version = if (version.length > 0) version else "0"
    }

    operator fun next(): Boolean {
        val n = version.length
        if (index >= n) {
            return false
        }

        var state = -2

        var start = index
        var end = n
        terminatedByNumber = false

        while (index < n) {
            val c = version[index]

            if (c == '.' || c == '-' || c == '_') {
                end = index
                index++
                break
            } else {
                val digit = Character.digit(c, 10)
                if (digit >= 0) {
                    if (state == -1) {
                        end = index
                        terminatedByNumber = true
                        break
                    }
                    if (state == 0) {
                        // normalize numbers and strip leading zeros (prereq for Integer/BigInteger handling)
                        start++
                    }
                    state = if ((state > 0 || digit > 0)) 1 else 0
                } else {
                    if (state >= 0) {
                        end = index
                        break
                    }
                    state = -1
                }
            }
            index++

        }

        if (end - start > 0) {
            token = version.substring(start, end)
            number = state >= 0
        } else {
            token = "0"
            number = true
        }

        return true
    }

    override fun toString(): String {
        return token.toString()
    }

    fun toItem(): Item {
        if (number) {
            try {
                if (token.length < 10) {
                    return Item(Item.KIND_INT, Integer.parseInt(token))
                } else {
                    return Item(Item.KIND_BIGINT, BigInteger(token))
                }
            } catch (e: NumberFormatException) {
                throw IllegalStateException(e)
            }

        } else {
            if (index >= version.length) {
                if ("min".equals(token, ignoreCase = true)) {
                    return Item.MIN
                } else if ("max".equals(token, ignoreCase = true)) {
                    return Item.MAX
                }
            }
            if (terminatedByNumber && token.length == 1) {
                when (token[0]) {
                    'a', 'A' -> return Item(Item.KIND_QUALIFIER, QUALIFIER_ALPHA)
                    'b', 'B' -> return Item(Item.KIND_QUALIFIER, QUALIFIER_BETA)
                    'm', 'M' -> return Item(Item.KIND_QUALIFIER, QUALIFIER_MILESTONE)
                }
            }
            val qualifier = QUALIFIERS[token]
            if (qualifier != null) {
                return Item(Item.KIND_QUALIFIER, qualifier)
            } else {
                return Item(Item.KIND_STRING, token.toLowerCase(Locale.ENGLISH))
            }
        }
    }

    companion object {
        internal val QUALIFIER_ALPHA = -5
        internal val QUALIFIER_BETA = -4
        internal val QUALIFIER_MILESTONE = -3
        internal val QUALIFIER_SNAPSHOT = -1
        private val QUALIFIERS = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)

        init {
            QUALIFIERS.put("alpha", QUALIFIER_ALPHA)
            QUALIFIERS.put("beta", QUALIFIER_BETA)
            QUALIFIERS.put("milestone", QUALIFIER_MILESTONE)
            QUALIFIERS.put("snapshot", QUALIFIER_SNAPSHOT)
            QUALIFIERS.put("cr", -2)
            QUALIFIERS.put("rc", -2)
            QUALIFIERS.put("ga", 0)
            QUALIFIERS.put("final", 0)
            QUALIFIERS.put("", 0)
            QUALIFIERS.put("sp", 1)
        }
    }
}

private fun normalize(value: Int): Int {
    return when {
        value == 0 -> 0
        value > 0 -> 1
        else -> -1
    }
}
