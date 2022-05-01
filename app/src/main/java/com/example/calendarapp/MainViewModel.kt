package com.example.calendarapp

import android.app.Application
import android.icu.util.Calendar
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.joda.time.DateTime
import java.util.*
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {
    var minutes = 0
    var hours = 0

    val dateLiveData = MutableLiveData<DateTime>()
    val randomDatesLiveData = SingleLiveEvent<MutableList<DateTime>>()

    fun setTime(min: Int, hour: Int) {
        minutes = min
        hours = hour
    }

    fun setDate(year: Int, month: Int, day: Int) {
        dateLiveData.value = DateTime(year, month+1, day, hours, minutes)
    }

    fun generateRandomDates(limit: Int) {
        val randomizedStartDates = mutableListOf<Long>()
        repeat(limit) {
            var eventCalendar = getScheduledEventInstance(calendarType = Calendar.DAY_OF_WEEK, randCalendar = MainActivity.RandomCalendar.Day)
            eventCalendar = getScheduledEventInstance(eventCalendar, Calendar.HOUR_OF_DAY, MainActivity.RandomCalendar.Hour)
            eventCalendar = getScheduledEventInstance(eventCalendar, Calendar.MINUTE, MainActivity.RandomCalendar.Minute)
            randomizedStartDates.add(eventCalendar.timeInMillis)
        }
        randomDatesLiveData.value = mutableListOf<DateTime>().apply {
            randomizedStartDates.forEach { add(DateTime(it)) }
        }
    }

    private fun getScheduledEventInstance(calendarInstance: Calendar? = null, calendarType: Int, randCalendar: MainActivity.RandomCalendar) =
        (calendarInstance ?: Calendar.getInstance(Locale.getDefault())).apply {
            set(calendarType, getRandomNum(randCalendar))
        }

    private fun getRandomNum(randCalendar: MainActivity.RandomCalendar): Int = Random.let {
        val (start, end) = when (randCalendar) {
            MainActivity.RandomCalendar.Day -> RandomizedStartEnd(0, 7)
            MainActivity.RandomCalendar.Hour -> RandomizedStartEnd(9, 17)
            else -> RandomizedStartEnd(0, 60)
        }
        it.nextInt(start, end)
    }
}

data class RandomizedStartEnd(val start: Int, val end: Int)
