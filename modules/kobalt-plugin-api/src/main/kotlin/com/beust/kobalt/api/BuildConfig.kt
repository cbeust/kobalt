package com.beust.kobalt.api

class BuildConfig {
    class Field(val name: String, val type: String, val value: Any) {
        override fun hashCode() = name.hashCode()
        override fun equals(other: Any?) = (other as Field).name == name
    }

    val fields = arrayListOf<Field>()

    fun field(type: String, name: String, value: Any) {
        fields.add(Field(name, type, value))
    }
}