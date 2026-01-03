package com.keder.zply

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AfterViewPagerAdapter(fragmentActivity : FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when(position){
            0 -> AfterLengthFragment()
            1 -> AfterDirectionFragment()
            2 -> AfterNoiseFragment()
            3 -> AfterLightFragment()
            else -> AfterLengthFragment()
        }
    }
}