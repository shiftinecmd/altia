package id.reinhart1010.altia

//import kotlinx.android.synthetic.main.fragment_port_item.view.*
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import id.reinhart1010.altia.scanner.PortScanner
import id.reinhart1010.altia.ui.RecyclerViewCommon
import id.reinhart1010.altia.util.AppPreferences
import id.reinhart1010.altia.util.CopyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.nio.charset.Charset

// TODO: Refactor this to separate file
class DeviceInfoJSON(val device: DeviceWithName, ports: List<Port>?) {
    val ports: List<Port>

    init {
        if (ports != null) {
            this.ports = ports
        } else {
            this.ports = listOf<Port>()
        }
    }
}

/**
 * A fragment representing a list of Items.
 * Activities containing this fragment MUST implement the
 * [DeviceInfoFragment.OnListFragmentInteractionListener] interface.
 */
class DeviceInfoFragment : Fragment() {
    val viewModel: id.reinhart1010.altia.ScanViewModel by activityViewModels()
    lateinit var scanAllPortsButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_deviceinfo_list, container, false)
        val recyclerView = view.findViewById<RecyclerViewCommon>(R.id.list)
        val argumentDeviceId = arguments?.getLong("deviceId")!!
        val copyUtil = CopyUtil(view)

        val deviceTypeTextView = view.findViewById<TextView>(R.id.deviceTypeTextView)
        val deviceIpTextView = view.findViewById<TextView>(R.id.deviceIpTextView)
        val deviceNameTextView = view.findViewById<TextView>(R.id.deviceNameTextView)
        val deviceHwAddressTextView = view.findViewById<TextView>(R.id.deviceHwAddressTextView)
        val deviceVendorTextView = view.findViewById<TextView>(R.id.deviceVendorTextView)

        copyUtil.makeTextViewCopyable((deviceTypeTextView))
        copyUtil.makeTextViewCopyable((deviceIpTextView))
        copyUtil.makeTextViewCopyable(deviceNameTextView)
        copyUtil.makeTextViewCopyable(deviceHwAddressTextView)
        copyUtil.makeTextViewCopyable(deviceVendorTextView)

        var exportableDevice: DeviceWithName? = null
        var exportablePorts: List<Port>? = null

        viewModel.deviceDao.getById(argumentDeviceId).observe(viewLifecycleOwner, Observer {
            fetchInfo(it.asDevice)
            exportableDevice = it
            deviceTypeTextView.text = getString(it.deviceType.label)
            deviceIpTextView.text = it.ip.hostAddress
            deviceNameTextView.text = if (it.isScanningDevice) {
                getString(R.string.this_device)
            } else {
                it.deviceName
            }
            deviceHwAddressTextView.text =
                it.hwAddress?.getAddress(AppPreferences(this).hideMacDetails)
            deviceVendorTextView.text = it.vendorName
        })

        val ports = viewModel.portDao.getAllForDevice(argumentDeviceId)

        recyclerView.setHandler(requireContext(), this, object :
            RecyclerViewCommon.Handler<id.reinhart1010.altia.Port>(R.layout.fragment_port_item, ports) {
            override fun shareIdentity(a: id.reinhart1010.altia.Port, b: id.reinhart1010.altia.Port) = a.port == b.port
            override fun areContentsTheSame(a: id.reinhart1010.altia.Port, b: id.reinhart1010.altia.Port) = a == b
            override fun onClickListener(view: View, value: id.reinhart1010.altia.Port) {
                viewModel.viewModelScope.launch(context = Dispatchers.IO) {
                    val ip = viewModel.deviceDao.getByIdNow(value.deviceId).ip
                    val portDescription = PortDescription.Companion.commonPorts.find { it.port == value.port }
                    withContext(Dispatchers.Main) {
                        if (portDescription?.urlSchema == null) {
                            copyUtil.copyText("${ip.hostAddress}:${value.port}")
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse("${portDescription.urlSchema}://${ip}:${value.port}")
                            startActivity(intent)
                        }
                    }
                }
            }

            override fun onLongClickListener(view: View, value: id.reinhart1010.altia.Port): Boolean {
                viewModel.viewModelScope.launch(context = Dispatchers.IO) {
                    val ip = viewModel.deviceDao.getByIdNow(value.deviceId).ip
                    withContext(Dispatchers.Main) {
                        copyUtil.copyText("${ip.hostAddress}:${value.port}")
                    }
                }
                return true
            }

            override fun bindItem(view: View): (value: id.reinhart1010.altia.Port) -> Unit {
                val portNumberTextView: TextView = view.findViewById(R.id.portNumberTextView)
                val protocolTextView: TextView = view.findViewById(R.id.protocolTextView)
                val serviceTextView: TextView = view.findViewById(R.id.serviceNameTextView)

                copyUtil.makeTextViewCopyable(portNumberTextView)
                copyUtil.makeTextViewCopyable(protocolTextView)
                copyUtil.makeTextViewCopyable(serviceTextView)

                return { item ->
                    portNumberTextView.text = item.port.toString()
                    protocolTextView.text = item.protocol.toString()
                    serviceTextView.text = item.description?.serviceName
                }
            }

        })

//        val shareFileButton = view.findViewById<Button>(R.id.buttonShareFile)
        val shareTextButton = view.findViewById<Button>(R.id.buttonShareText)

//        shareFileButton.setOnClickListener {
//            exportablePorts = ports.value
//            if (exportableDevice != null) {
//                shareJSON(exportableDevice!!, exportablePorts, true)
//            }
//        }
        shareTextButton.setOnClickListener {
            exportablePorts = ports.value
            if (exportableDevice != null) {
                shareJSON(exportableDevice!!, exportablePorts, false)
            }
        }

        return view
    }

    fun fetchInfo(device: id.reinhart1010.altia.Device) {
        viewModel.viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PortScanner(device.ip).scanPorts().forEach {
                    launch {
                        val result = it.await()
                        if (result.isOpen) {
                            viewModel.portDao.upsert(
                                Port(
                                    0,
                                    result.port,
                                    result.protocol,
                                    device.deviceId
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shareJSON(deviceInfo: DeviceWithName, portInfo: List<Port>?, asFile: Boolean) {
        val gson: Gson = Gson()

        if (deviceInfo != null) {
            val type: Type = object : TypeToken<DeviceInfoJSON>() {}.type
            val stdJson: String = gson.toJson(DeviceInfoJSON(deviceInfo, portInfo), type)

            val intent = Intent(Intent.ACTION_SEND)
            if (asFile) {
                // TODO: Still buggy
                intent.setType("application/json")

                val charset = Charset.forName("UTF-8")
                val byteBuffer = charset.encode(stdJson)
                val byteArray = ByteArray(byteBuffer.remaining())
                byteBuffer.get(byteArray);
                intent.putExtra(Intent.EXTRA_STREAM, byteArray)
            } else {
                intent.setType("text/plain")
                intent.putExtra(Intent.EXTRA_TEXT, stdJson)
            }
            startActivity(intent);
        }
    }
}