package com.kilkari.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun dateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToDate(v: String?): LocalDate? = v?.let(LocalDate::parse)

    @TypeConverter fun dateTimeToString(v: LocalDateTime?): String? = v?.toString()
    @TypeConverter fun stringToDateTime(v: String?): LocalDateTime? = v?.let(LocalDateTime::parse)
}
