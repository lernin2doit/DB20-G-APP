package com.db20g.controller.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 8

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LiveFragment()
            1 -> ChannelsFragment()
            2 -> RepeaterSearchFragment()
            3 -> RoutePlannerFragment()
            4 -> EmergencyFragment()
            5 -> BluetoothPttFragment()
            6 -> SettingsFragment()
            7 -> ToolsFragment()
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }

    companion object {
        val TAB_TITLES = arrayOf("Live", "Channels", "Repeaters", "Routes", "Emergency", "Bluetooth", "Settings", "Tools")
    }
}
