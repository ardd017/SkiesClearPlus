package com.pokeskies.skiesclear.utils

import com.google.gson.*
import net.minecraft.nbt.*
import java.lang.reflect.Type

class CompoundTagAdaptor : JsonSerializer<CompoundTag>, JsonDeserializer<CompoundTag> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CompoundTag {
        val tag = CompoundTag()
        if (!json.isJsonObject) return tag
        val obj = json.asJsonObject
        for ((key, value) in obj.entrySet()) { // Iterate over every key in the JSON Object
            if (value.isJsonObject) { // If the value is a JSON Object: { }
                val valueObj = value.asJsonObject
                val typeField = valueObj.get("type")
                if (typeField == null) { // Check for a "type" field, if it's not present, we assume it's a nested CompoundTag
                    tag.put(key, deserialize(valueObj, typeOfT, context))
                } else { // If it is present, deserialize the "value" field based on the type specified
                    val valueField = valueObj.get("value")
                    if (valueField != null) {
                        when (typeField.asString) {
                            "byte" -> tag.putByte(key, valueField.asByte)
                            "short" -> tag.putShort(key, valueField.asShort)
                            "int" -> tag.putInt(key, valueField.asInt)
                            "long" -> tag.putLong(key, valueField.asLong)
                            "float" -> tag.putFloat(key, valueField.asFloat)
                            "double" -> tag.putDouble(key, valueField.asDouble)
                            "boolean" -> tag.putBoolean(key, valueField.asBoolean)
                            "string" -> tag.putString(key, valueField.asString)
                            else -> tag.putString(key, valueField.toString())
                        }
                    } else {
                        tag.put(key, deserialize(value, typeOfT, context))
                    }
                }
            } else if (value.isJsonPrimitive) { // Handle primitive values like normal JSON does
                val primitive = value.asJsonPrimitive
                when {
                    primitive.isBoolean -> tag.putBoolean(key, primitive.asBoolean)
                    primitive.isNumber -> putNumber(key, primitive.asString, tag)
                    primitive.isString -> {
                        if (!putTypedString(key, primitive.asString, tag)) {
                            tag.putString(key, primitive.asString)
                        }
                    }
                    else -> tag.putString(key, primitive.toString())
                }
            } else if (value.isJsonArray) { // If its an array, we need to determine the type of the list
                val arr = value.asJsonArray
                if (arr.size() == 0) {
                    tag.put(key, ListTag())
                } else {
                    when (determineListType(arr)) {
                        InternalListType.NUMBER -> {
                            val intList = IntArray(arr.size()) { arr[it].asInt }
                            tag.put(key, IntArrayTag(intList))
                        }
                        InternalListType.STRING -> {
                            val strList = arr.map { it.asString }
                            val tagList = ListTag()
                            strList.forEach { tagList.add(StringTag.valueOf(it)) }
                            tag.put(key, tagList)
                        }
                        InternalListType.BOOLEAN -> {
                            val boolList = BooleanArray(arr.size()) { arr[it].asBoolean }
                            val tagList = ListTag()
                            boolList.forEach { tagList.add(ByteTag.valueOf(it)) } // I hope this is right
                            tag.put(key, tagList)
                        }
                        InternalListType.OBJECT -> {
                            val tagList = ListTag()
                            arr.forEach { tagList.add(deserialize(it, typeOfT, context)) }
                            tag.put(key, tagList)
                        }
                        InternalListType.ARRAY -> {
                            val tagList = ListTag()
                            arr.forEach { tagList.add(deserialize(it, typeOfT, context)) }
                            tag.put(key, tagList)
                        }
                        else -> {
                            // Default to an empty ListTag if the type is unknown
                            tag.put(key, ListTag())
                        }
                    }
                }
            }
        }
        return tag
    }

    override fun serialize(src: CompoundTag, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        for (key in src.allKeys) {
            val element = src[key] ?: continue
            when (element) {
                is ByteTag -> obj.add(key, JsonObject().apply {
                    addProperty("type", "byte")
                    addProperty("value", src.getByte(key))
                })
                is ShortTag -> obj.add(key, JsonObject().apply {
                    addProperty("type", "short")
                    addProperty("value", src.getShort(key))
                })
                is IntTag -> {
                    obj.addProperty(key, src.getInt(key))
                }
                is LongTag -> obj.add(key, JsonObject().apply {
                    addProperty("type", "long")
                    addProperty("value", src.getLong(key))
                })
                // Float and double were missing from this list, so a rule or a saved tag holding one
                // silently lost the key. Neither has an SNBT suffix in the deserializer, hence the
                // explicit `type` form rather than a bare number.
                is FloatTag -> obj.add(key, JsonObject().apply {
                    addProperty("type", "float")
                    addProperty("value", src.getFloat(key))
                })
                is DoubleTag -> obj.add(key, JsonObject().apply {
                    addProperty("type", "double")
                    addProperty("value", src.getDouble(key))
                })
                is StringTag -> {
                    obj.addProperty(key, src.getString(key))
                }
                is CompoundTag -> {
                    obj.add(key, serialize(src.getCompound(key), typeOfSrc, context))
                }
                is IntArrayTag -> {
                    val arr = JsonArray()
                    element.asIntArray.forEach { arr.add(it) }
                    obj.add(key, arr)
                }
                is ListTag -> {
                    val arr = JsonArray()
                    for (i in 0 until element.size) {
                        when (val item = element[i]) {
                            is StringTag -> arr.add(item.asString)
                            is IntTag -> arr.add(item.asInt)
                            is FloatTag -> arr.add(item.asFloat)
                            is DoubleTag -> arr.add(item.asDouble)
                            is ShortTag -> arr.add(item.asShort)
                            is ByteTag -> arr.add(item.asByte)
                            is LongTag -> arr.add(item.asLong)
                            is CompoundTag -> arr.add(serialize(item, typeOfSrc, context))
                            else -> arr.add(item.toString())
                        }
                    }
                    obj.add(key, arr)
                }
            }
        }
        return obj
    }

    /**
     * Stores a JSON number, honouring the SNBT type suffix players copy out of `/data get`.
     *
     * Without the suffix a whole number becomes an [IntTag] and a fractional one a [DoubleTag]. That
     * default is why `{"OnGround":0b}` never matched: vanilla stores `OnGround` as a byte, and
     * `IntTag(0) != ByteTag(0)`. Reading the suffix lets `0b` / `32767s` / `1L` / `1.5f` keep the
     * exact width the game uses, which is what a `#nbt=` rule compares against.
     */
    private fun putNumber(key: String, raw: String, tag: CompoundTag) {
        val body = raw.dropLast(1)
        when (raw.lastOrNull()?.lowercaseChar()) {
            'b' -> tag.putByte(key, (body.toDoubleOrNull() ?: raw.toDoubleOrNull())?.toInt()?.toByte() ?: 0)
            's' -> tag.putShort(key, (body.toDoubleOrNull() ?: raw.toDoubleOrNull())?.toInt()?.toShort() ?: 0)
            'l' -> tag.putLong(key, (body.toDoubleOrNull() ?: raw.toDoubleOrNull())?.toLong() ?: 0L)
            'f' -> tag.putFloat(key, (body.toDoubleOrNull() ?: raw.toDoubleOrNull())?.toFloat() ?: 0f)
            'd' -> tag.putDouble(key, body.toDoubleOrNull() ?: raw.toDoubleOrNull() ?: 0.0)
            else -> {
                val value = raw.toDoubleOrNull() ?: 0.0
                if (raw.contains('.')) tag.putDouble(key, value) else tag.putInt(key, value.toInt())
            }
        }
    }

    /**
     * Interprets a bare JSON string that actually carries an SNBT suffix.
     *
     * `{"NoAI":1b}` reaches this adaptor as the *string* `"1b"`, because `1b` is not valid JSON
     * syntax: Gson reads it leniently. Treating such a value as a string used to produce
     * `StringTag("1b")`, which matches nothing on a real entity. A suffix only counts when the
     * remainder parses as a number, so a genuine string like `"Steve"` or `"1b"` used as text is
     * unaffected unless it consists of digits plus one suffix letter.
     *
     * @return true when the value was stored as a numeric tag and the caller should not also store
     *         it as a string.
     */
    private fun putTypedString(key: String, raw: String, tag: CompoundTag): Boolean {
        if (raw.length < 2) return false
        val suffix = raw.last().lowercaseChar()
        if (suffix !in NUMERIC_SUFFIXES) return false
        if (raw.dropLast(1).toDoubleOrNull() == null) return false
        putNumber(key, raw, tag)
        return true
    }

    // Attempts to determine the type of a list based on its contents. If all elements are of the same type, it returns that type.
    // If any element is of a different type, it defaults to all being treated as a list of strings.
    private fun determineListType(arr: JsonArray): InternalListType {
        val types = arr.map {
            when {
                it.isJsonPrimitive && it.asJsonPrimitive.isNumber -> InternalListType.NUMBER
                it.isJsonPrimitive && it.asJsonPrimitive.isString -> InternalListType.STRING
                it.isJsonPrimitive && it.asJsonPrimitive.isBoolean -> InternalListType.BOOLEAN
                it.isJsonObject -> InternalListType.OBJECT
                it.isJsonArray -> InternalListType.ARRAY
                else -> InternalListType.UNKNOWN
            }
        }.toSet()
        return if (types.size == 1) types.first() else InternalListType.STRING
    }

    private companion object {
        /** SNBT numeric suffixes players copy out of `/data get entity`. */
        val NUMERIC_SUFFIXES: Set<Char> = setOf('b', 's', 'l', 'f', 'd')
    }
}
