package net.stits.utils

import java.util.*

fun <E> List<E>.randomOrNull(): E? = if (isNotEmpty()) get(Random().nextInt(size)) else null
fun choice(): Boolean = Random().nextBoolean()

/**
 * Bad functions. They doesn't report when types of lists are incompatible
 */
fun <T> Collection<T>.contentEquals(other: @UnsafeVariance Collection<T>): Boolean = this.size == other.size && this.containsAll(other)
fun <T> Collection<T>.equalsIgnoreSize(other: @UnsafeVariance Collection<T>): Boolean {
    return if (this.size <= other.size) {
        this.mapIndexed { index, it -> other.elementAt(index) == it }.all { it }
    } else
        other.mapIndexed { index, it -> this.elementAt(index) == it }.all { it }
}