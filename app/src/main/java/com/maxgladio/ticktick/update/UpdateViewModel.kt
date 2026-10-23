package com.maxgladio.ticktick.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
fun installedVersion(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutableState.asStateFlow()

    fun check() {
        if (mutableState.value == UpdateState.Checking) return
        mutableState.value = UpdateState.Checking
        viewModelScope.launch {
            mutableState.value = withContext(Dispatchers.IO) {
                GitHubUpdates().check(installedVersion(getApplication()))
            }
        }
    }
}
