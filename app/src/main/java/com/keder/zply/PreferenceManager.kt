package com.keder.zply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PreferenceManager {
    private const val PREF_NAME = "ScheduleData"
    private const val KEY_LIST = "schedule_list"

    fun getScheduleList(context : Context) : ArrayList<ScheduleItem>{
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_LIST, null) ?: return ArrayList()

        val list = ArrayList<ScheduleItem>()
        try{
            val jsonArray = JSONArray(jsonString)
            for(i in 0 until jsonArray.length()){
                val obj = jsonArray.getJSONObject(i)
                list.add(ScheduleItem(obj.getString("address"), obj.getString("time")))
            }
        }catch (e: Exception){e.printStackTrace()}
        return list
    }

    fun saveScheduleList(context : Context, list : List<ScheduleItem>){
        val jsonArray = JSONArray()
        for (item in list){
            val obj = JSONObject()
            obj.put("address", item.address)
            obj.put("time", item.time)
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LIST, jsonArray.toString()).apply()
    }
}