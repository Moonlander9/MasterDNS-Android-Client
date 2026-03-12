package com.masterdnsvpn.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.masterdnsvpn.android.ui.screens.HOME_PRIMARY_ACTION_TAG
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsBottomNavigationTabs() {
        composeRule.onNodeWithTag("tab_home", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("tab_routing", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("tab_config", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("tab_scanner", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("tab_logs", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun homeShowsPrimaryAction() {
        composeRule.onNodeWithTag(HOME_PRIMARY_ACTION_TAG).assertIsDisplayed()
    }
}
