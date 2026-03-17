package com.db20g.controller.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.db20g.controller.R

class DataToolsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_host)

        if (savedInstanceState == null) {
            val fragment = ToolsFragment().apply {
                arguments = Bundle().apply {
                    putString(ToolsFragment.ARG_MODE, ToolsFragment.MODE_DATA_TOOLS)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }
}
