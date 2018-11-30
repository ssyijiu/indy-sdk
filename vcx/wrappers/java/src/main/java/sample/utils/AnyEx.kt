package utils

import com.google.gson.Gson
import java.lang.reflect.Type

private val gson = Gson()

fun Any.toJson() = gson.toJson(this)!!

fun ByteArray.toUTF8() = String(this, Charsets.UTF_8)

fun <T> ByteArray.toObject(classOfT: Class<T>) = toUTF8().toObject(classOfT)

fun <T> String.toObject(classOfT: Class<T>) = gson.fromJson(this, classOfT)!!

fun <T> String.toObject(typeOfT: Type) = gson.fromJson<T>(this, typeOfT)!!
