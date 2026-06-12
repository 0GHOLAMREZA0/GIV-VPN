package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.extension.toServerConfig
import com.v2ray.ang.service.V2RayVPNService
import com.v2ray.ang.util.*
import kotlinx.coroutines.*

class MainFragment : Fragment() {

    private lateinit var spinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var progressBar: ProgressBar
    private var serverList: List<ServerConfig> = emptyList()
    private var selectedServer: ServerConfig? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinner = view.findViewById(R.id.server_spinner)
        connectButton = view.findViewById(R.id.connect_button)
        progressBar = view.findViewById(R.id.progress_bar)

        connectButton.setOnClickListener {
            selectedServer?.let { server ->
                startVpn(server)
            } ?: Toast.makeText(requireContext(), "لطفاً یک سرور انتخاب کنید", Toast.LENGTH_SHORT).show()
        }

        loadServersWithPing()
    }

    private fun loadServersWithPing() {
        progressBar.visibility = View.VISIBLE
        spinner.isEnabled = false
        connectButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = AppConfig.subscriptionUrl
                if (url.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "آدرس subscription تنظیم نشده", Toast.LENGTH_LONG).show()
                        progressBar.visibility = View.GONE
                    }
                    return@launch
                }

                val subId = SubscriptionManager.getDefaultSubscriptionId()
                if (subId == 0L) {
                    SubscriptionManager.addSubscription(url, "")
                    SubscriptionManager.updateSubscription(subId)
                } else {
                    SubscriptionManager.updateSubscription(subId)
                }
                val allServers = AngConfigManager.getAllServers()
                if (allServers.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "هیچ سروری دریافت نشد", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                    return@launch
                }

                val aliveServers = mutableListOf<ServerConfig>()
                for (server in allServers) {
                    val latency = URLTest.testLatency(server, 2000)
                    if (latency > 0) {
                        aliveServers.add(server)
                    }
                }

                withContext(Dispatchers.Main) {
                    serverList = aliveServers
                    if (serverList.isEmpty()) {
                        Toast.makeText(requireContext(), "هیچ سروری در دسترس نیست", Toast.LENGTH_SHORT).show()
                    } else {
                        val serverNames = serverList.map { it.remarks }
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, serverNames)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinner.adapter = adapter

                        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                selectedServer = serverList[position]
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {
                                selectedServer = null
                            }
                        }
                    }
                    progressBar.visibility = View.GONE
                    spinner.isEnabled = true
                    connectButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    spinner.isEnabled = true
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun startVpn(server: ServerConfig) {
        MMKV.encode(AppConfig.PREF_CURRENT_SERVER, server.toJson())
        val intent = Intent(requireContext(), V2RayVPNService::class.java)
        intent.putExtra("COMMAND", V2RayVPNService.CONNECT)
        requireContext().startService(intent)
    }
}
