package com.db20g.controller.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 6

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LiveFragment()
            1 -> ChannelsFragment()
            2 -> RepeaterSearchFragment()
            3 -> RoutePlannerFragment()
            4 -> SettingsFragment()
            5 -> EmergencyFragment()
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }

    companion object {
        val TAB_TITLES = arrayOf("Live", "Channels", "Repeaters", "Routes", "Settings", "Emergency")
        const val EMERGENCY_TAB_POSITION = 5
    }
}
