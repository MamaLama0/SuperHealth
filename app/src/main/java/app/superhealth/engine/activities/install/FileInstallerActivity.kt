/*  Copyright (C) 2025-2026 José Rebelo, Thomas Kuehne

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.activities.install

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.AbstractGBActivity
import app.superhealth.engine.adapter.DeviceInstallAdapter
import app.superhealth.engine.databinding.ActivityFileInstallerBinding
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.DeviceService.EXTRA_OPTIONS
import app.superhealth.engine.util.kotlin.getParcelableArrayListCompat
import app.superhealth.engine.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory

class FileInstallerActivity : AbstractGBActivity() {
    private val viewModel: FileInstallerViewModel by viewModels {
        FileInstallerViewModelFactory(
            GBApplication.app(),
            intent.getParcelableCompat<Bundle>(EXTRA_OPTIONS) ?: Bundle.EMPTY
        )
    }

    private lateinit var binding: ActivityFileInstallerBinding
    private lateinit var deviceAdapter: DeviceInstallAdapter

    private val installIntents: MutableMap<GBDevice, Intent> = mutableMapOf()
    private var files: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileInstallerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        files = when {
            intent.data != null -> listOfNotNull(intent.data)
            intent.action == Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListCompat<Uri>(Intent.EXTRA_STREAM)
                .orEmpty()

            else -> listOfNotNull(intent.getParcelableCompat<Uri>(Intent.EXTRA_STREAM))
        }

        // Set up the RecyclerView
        deviceAdapter = DeviceInstallAdapter { selectedDevice ->
            launchInstallActivity(selectedDevice, installIntents[selectedDevice])
        }
        binding.recyclerViewDevices.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(this@FileInstallerActivity)
        }

        viewModel.findCompatibleDevices(files)
        viewModel.uiState.observe(this) { state ->
            updateUi(state)
        }
    }

    private fun updateUi(state: InstallDeviceUiState) {
        // Hide everything initially, then show the relevant one
        binding.fileInstallerLoadingProgress.visibility = View.GONE
        binding.recyclerViewDevices.visibility = View.GONE
        binding.textInstallerStatus.visibility = View.GONE

        when (state) {
            is InstallDeviceUiState.Loading -> {
                binding.fileInstallerLoadingProgress.visibility = View.VISIBLE
                LOG.debug("Loading compatible devices...")
            }

            is InstallDeviceUiState.Success -> {
                if (state.results.isEmpty()) { // Ideally this should be caught by Error state
                    binding.textInstallerStatus.text = getString(
                        R.string.fwinstaller_no_compatible_device_found,
                        files.joinToString()
                    )
                    binding.textInstallerStatus.visibility = View.VISIBLE
                } else {
                    binding.recyclerViewDevices.visibility = View.VISIBLE
                    installIntents.clear()
                    installIntents.putAll(state.results)
                    deviceAdapter.submitList(state.results.map { pair -> pair.first })
                    if (state.results.size == 1) {
                        // Single device was found - start the install activity directly
                        val (device, installIntent) = state.results[0]
                        launchInstallActivity(device, installIntent)
                    }
                }
            }

            is InstallDeviceUiState.Error -> {
                binding.textInstallerStatus.text = state.message
                binding.textInstallerStatus.visibility = View.VISIBLE
                LOG.error("Error: ${state.message}")
            }

            is InstallDeviceUiState.NoUri -> {
                // Should never happen
                @SuppressLint("SetTextI18n")
                binding.textInstallerStatus.text = "No file URI provided to install."
                binding.textInstallerStatus.visibility = View.VISIBLE
                LOG.warn("No URI provided.")
            }
        }
    }

    private fun launchInstallActivity(selectedDevice: GBDevice, installIntent: Intent?) {
        if (installIntent == null) {
            // This should never happen
            Toast.makeText(this, "Failed to find install activity", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        LOG.info("Launching install activity {} for {}", installIntent.component?.className, selectedDevice)

        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
        startActivity(installIntent)
        finish()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(FileInstallerActivity::class.java)
    }
}


// FileInstallerViewModelFactory remains the same
class FileInstallerViewModelFactory(
    private val application: GBApplication,
    private val fwOptions: Bundle
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileInstallerViewModel::class.java)) {
            return FileInstallerViewModel(application, fwOptions) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
