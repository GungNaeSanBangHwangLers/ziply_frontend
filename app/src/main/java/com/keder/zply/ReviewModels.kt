package com.keder.zply

data class CardCreationRequest(val companyAddress: String, val houses : List<HouseRequest>)

data class HouseRequest(
    val address : String,
    val visitDateTime : String,
    val latitude : Double = 0.0,
    val longitude : Double = 0.0
)

data class CreateCardResponse(val cardId : String)