package com.keder.zply

data class ExplorationSession(
    val companyAddress : String,
    val scheduleList : List<ScheduleItem>
)
data class ScheduleItem (
    val address : String,
    val time : String,
    var dayScore: Int = 0,
    var nightScore: Int = 0,
    var dayDesc: String = "",
    var nightDesc: String = "",

    var measuredAzimuths : List<Int> = emptyList(),
    var measuredLight : Float = 0f,
    var isMeasured : Boolean = false,

    var imageList: List<String> = emptyList()
)
data class MainCardData(
    val status : String,
    val date: String,
    val location : String,
    val count : Int,
    val sessionIndex : Int,
)
