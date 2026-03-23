package com.keder.zply

data class ExplorationSession(
    val companyAddress : String,
    val scheduleList : List<ScheduleItem>
)
//data class ScheduleItem (
//    val address : String,
//    val time : String,
//    var dayScore: Int = 0,
//    var nightScore: Int = 0,
//    var dayDesc: String = "",
//    var nightDesc: String = "",
//
//    var walkingTimeMin : Int = 0,
//    var walkingDistanceKm : Double = 0.0,
//    var houseId : Long = 0,
//
//    var rankLabel : String = "",
//
//    var measuredAzimuths : List<Int> = emptyList(),
//    var measuredLight : Float = 0f,
//    var isMeasured : Boolean = false,
//
//    var imageList: List<String> = emptyList()
//)
data class MainCardData(
    val status : String,
    val date: String,
    val location : String,
    val count : Int,
    val cardId : String
)

//data class ReviewCardResponse(
//    val cardId : String,
//    val title : String,
//    val houseCount : Int,
//    val startDate : String,
//    val endDate : String,
//    val status : String
//)
//
//data class UserNameResponse(
//    val name : String,
//)
