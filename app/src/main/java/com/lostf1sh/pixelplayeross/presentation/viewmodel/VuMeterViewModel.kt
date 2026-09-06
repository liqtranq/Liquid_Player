package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.VuMeterStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VuMeterViewModel @Inject constructor(preferences: UserPreferencesRepository) : ViewModel() {
    val style = preferences.vuMeterStyleFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VuMeterStyle.OFF)
}
