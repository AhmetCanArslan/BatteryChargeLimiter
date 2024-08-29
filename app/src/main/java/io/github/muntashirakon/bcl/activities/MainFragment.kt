package io.github.muntashirakon.bcl.activities

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.muntashirakon.bcl.*
import io.github.muntashirakon.bcl.Constants.CHARGE_OFF_KEY
import io.github.muntashirakon.bcl.Constants.CHARGE_ON_KEY
import io.github.muntashirakon.bcl.Constants.DEFAULT_DISABLED
import io.github.muntashirakon.bcl.Constants.DEFAULT_ENABLED
import io.github.muntashirakon.bcl.Constants.DEFAULT_FILE
import io.github.muntashirakon.bcl.Constants.FILE_KEY
import io.github.muntashirakon.bcl.Constants.SAVED_DISABLED_DATA
import io.github.muntashirakon.bcl.Constants.SAVED_ENABLED_DATA
import io.github.muntashirakon.bcl.Utils.getSettings
import io.github.muntashirakon.bcl.settings.PrefsFragment
import java.lang.ref.WeakReference

class MainFragment: Fragment() {
    private val minPicker by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<NumberPicker>(R.id.min_picker)  }
    private val minText by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<TextView>(R.id.min_text) }
    private val maxPicker by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<NumberPicker>(R.id.max_picker) }
    private val maxText by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<TextView>(R.id.max_text) }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { activity?.getSharedPreferences(Constants.SETTINGS, 0) }
    private val statusText by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<TextView>(R.id.status) }
    private val batteryInfo by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<TextView>(R.id.battery_info) }
    private val enableSwitch by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<SwitchMaterial>(R.id.enable_switch) }
    private val disableChargeSwitch by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<SwitchMaterial>(R.id.disable_charge_switch) }
    private val limitByVoltageSwitch by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<SwitchMaterial>(R.id.limit_by_voltage) }
    private val customThresholdEditView by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<EditText>(R.id.voltage_threshold) }
    private val currentThresholdTextView by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<TextView>(R.id.current_voltage_threshold) }
    private val defaultThresholdTextView by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<TextView>(R.id.default_voltage_threshold) }
    private val enablePassThrough by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<SwitchMaterial>(R.id.pass_through) }
    private val customWattThresholdEditView by lazy(LazyThreadSafetyMode.NONE) { view?.findViewById<EditText>(R.id.watt_threshold) }
    private var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var currentThreshold: String
    private val mHandler = MainHandler(this)
    private var prefs: SharedPreferences? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Utils.startServiceIfLimitEnabled(requireContext())
        } else requireActivity().finishAndRemoveTask()
    }

    private class MainHandler(fragment: MainFragment) : Handler(Looper.getMainLooper()) {
        private val mFragment by lazy(LazyThreadSafetyMode.NONE) { WeakReference(fragment) }
        override fun handleMessage(msg: Message) {
            val fragment = mFragment.get()
            if (fragment != null) {
                when (msg.what) {
                    MainActivity.MSG_UPDATE_VOLTAGE_THRESHOLD -> {
                        val voltage = msg.data.getString(MainActivity.VOLTAGE_THRESHOLD)
                        fragment.currentThreshold = voltage!!
                        fragment.currentThresholdTextView?.text = voltage
                        if (fragment.settings?.getString(Constants.DEFAULT_VOLTAGE_LIMIT, null) == null) {
                            fragment.settings?.edit()?.putString(Constants.DEFAULT_VOLTAGE_LIMIT, voltage)?.apply()
                            fragment.defaultThresholdTextView?.text = voltage
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Utils.applyWindowInsetsAsPaddingNoTop(view)
        prefs = Utils.getPrefs(requireContext())
        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PrefsFragment.KEY_TEMP_FAHRENHEIT -> updateBatteryInfo(
                    context?.registerReceiver(
                        null,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    )!!
                )
            }
        }
        prefs?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        customThresholdEditView?.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_GO) {
                hideKeybord()
                customThresholdEditView!!.clearFocus()
                handled = true
            }
            handled
        }

        customWattThresholdEditView?.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_GO) {
                hideKeybord()
                customWattThresholdEditView!!.clearFocus()
                handled = true
                setStatusCTRLFileData()
            }
            handled
        }

        Utils.getCurrentVoltageThresholdAsync(requireContext(), mHandler)

        currentThreshold = settings?.getString(Constants.DEFAULT_VOLTAGE_LIMIT, "4300")!!

        customThresholdEditView?.setText(settings?.getString(Constants.CUSTOM_VOLTAGE_LIMIT, ""))
        defaultThresholdTextView?.text = settings?.getString(Constants.DEFAULT_VOLTAGE_LIMIT, "")

        customThresholdEditView?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val newThreshold = customThresholdEditView?.text.toString()
                if (Utils.isValidVoltageThreshold(newThreshold, currentThreshold)) {
                    settings?.edit()?.putString(Constants.CUSTOM_VOLTAGE_LIMIT, newThreshold)?.apply()
                    Utils.setVoltageThreshold(null, true, v.context, mHandler)
                }
            }
        }

        customWattThresholdEditView?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val newThreshold = customWattThresholdEditView?.text.toString()
                Utils.stopService(requireContext())
                settings?.edit()?.putString(Constants.SAVED_ENABLED_DATA, newThreshold)?.apply()
                getSettings(requireContext())
                    .edit().putString(FILE_KEY, DEFAULT_FILE)
                    .putString(CHARGE_ON_KEY, newThreshold)
                    .putString(CHARGE_OFF_KEY, DEFAULT_DISABLED).apply()
                Utils.startServiceIfLimitEnabled(requireContext())
            }
        }

        val resetBatteryStatsButton = view.findViewById<Button>(R.id.reset_battery_stats)
//        val autoResetSwitch = view.findViewById(R.id.auto_stats_reset) as CheckBox
//        val notificationSound = view.findViewById(R.id.notification_sound) as CheckBox

//        autoResetSwitch.isChecked = settings?.getBoolean(AUTO_RESET_STATS, false)
//        notificationSound.isChecked = settings?.getBoolean(NOTIFICATION_SOUND, false)
        maxPicker?.minValue = Constants.MIN_ALLOWED_LIMIT_PC
        maxPicker?.maxValue = Constants.MAX_ALLOWED_LIMIT_PC
        minPicker?.minValue = 0

        enableSwitch?.setOnCheckedChangeListener(switchListener)
        disableChargeSwitch?.setOnCheckedChangeListener(switchListener)
        limitByVoltageSwitch?.setOnCheckedChangeListener(switchListener)
        enablePassThrough?.setOnCheckedChangeListener(switchListener)
        maxPicker?.setOnValueChangedListener { _, _, max ->
            Utils.setLimit(max, settings!!)
            maxText?.text = getString(R.string.limit, max)
            val min = settings?.getInt(Constants.MIN, max - 2)
            minPicker?.maxValue = max
            if (min != null) {
                minPicker?.value = min
            }
            updateMinText(min)
            if (!ForegroundService.isRunning) {
                Utils.startServiceIfLimitEnabled(requireContext())
            }
        }

        minPicker?.setOnValueChangedListener { _, _, min ->
            settings?.edit()?.putInt(Constants.MIN, min)?.apply()
            updateMinText(min)
        }
        resetBatteryStatsButton.setOnClickListener { Utils.resetBatteryStats(requireContext()) }
//        autoResetSwitch.setOnCheckedChangeListener { _, isChecked ->
//            settings.edit().putBoolean(AUTO_RESET_STATS, isChecked).apply() }
//        notificationSound.setOnCheckedChangeListener { _, isChecked ->
//            settings.edit().putBoolean(NOTIFICATION_SOUND, isChecked).apply() }

        setStatusCTRLFileData()

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        setStatusCTRLFileData()
    }

    override fun onStart() {
        super.onStart()
        context?.registerReceiver(charging, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        // the limits could have been changed by an Intent, so update the UI here
        updateUi()
    }

    override fun onStop() {
        context?.unregisterReceiver(charging)
        super.onStop()
    }

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onDestroy()
    }

    //OnCheckedChangeListener for Switch elements
    private val switchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        when (buttonView.id) {
            R.id.enable_switch -> {
                settings?.edit()?.putBoolean(Constants.CHARGE_LIMIT_ENABLED, isChecked)?.apply()
                if (isChecked) {
                    Utils.startServiceIfLimitEnabled(requireContext())
                    disableSwitches(listOf(disableChargeSwitch, limitByVoltageSwitch))
                } else {
                    Utils.stopService(requireContext())
                    enableSwitches(listOf(disableChargeSwitch, limitByVoltageSwitch))
                }
                EnableWidget.updateWidget(requireContext(), isChecked)
            }
            R.id.disable_charge_switch -> {
                if (isChecked) {
                    Utils.changeState(requireContext(), Utils.CHARGE_OFF)
                    settings?.edit()?.putBoolean(Constants.DISABLE_CHARGE_NOW, true)?.apply()
                    disableSwitches(listOf(enableSwitch, limitByVoltageSwitch))
                } else {
                    Utils.changeState(requireContext(), Utils.CHARGE_ON)
                    settings?.edit()?.putBoolean(Constants.DISABLE_CHARGE_NOW, false)?.apply()
                    enableSwitches(listOf(enableSwitch, limitByVoltageSwitch))
                }
            }
            R.id.limit_by_voltage -> {
                if (isChecked) {
                    Utils.setVoltageThreshold(
                        settings?.getString(Constants.CUSTOM_VOLTAGE_LIMIT, Constants.DEFAULT_VOLTAGE_THRESHOLD_MV),
                        false, requireContext(), mHandler
                    )
                    settings?.edit()?.putBoolean(Constants.LIMIT_BY_VOLTAGE, true)?.apply()
                    disableSwitches(listOf(enableSwitch, disableChargeSwitch))
                } else {
                    Utils.setVoltageThreshold(
                        settings?.getString(Constants.DEFAULT_VOLTAGE_LIMIT, "4300"),
                        false, requireContext(), mHandler
                    )
                    settings?.edit()?.putBoolean(Constants.LIMIT_BY_VOLTAGE, false)?.apply()
                    enableSwitches(listOf(enableSwitch, disableChargeSwitch))
                }
            }
            R.id.pass_through -> {
                if (isChecked) {
                    Utils.changeState(requireContext(), Utils.PASS_THROUGH_OFF)
                    Utils.stopService(requireContext())
                    settings?.edit()?.putBoolean(Constants.PASS_THROUGH_ENABLED, true)?.apply()
                    settings?.edit()?.putString(Constants.SAVED_PATH_DATA, DEFAULT_FILE)?.apply()
                    settings?.edit()?.putString(Constants.SAVED_ENABLED_DATA, DEFAULT_DISABLED)?.apply()
                    settings?.edit()?.putString(Constants.SAVED_DISABLED_DATA, DEFAULT_DISABLED)?.apply()
                    getSettings(requireContext())
                        .edit().putString(FILE_KEY, DEFAULT_FILE)
                        .putString(CHARGE_ON_KEY, DEFAULT_DISABLED)
                        .putString(CHARGE_OFF_KEY, DEFAULT_DISABLED).apply()

                    Utils.startServiceIfLimitEnabled(requireContext())
                    disableSwitches(listOf(enableSwitch, disableChargeSwitch, limitByVoltageSwitch))
                    setStatusCTRLFileData()
                } else {
                    Utils.changeState(requireContext(), Utils.PASS_THROUGH_ON)
                    settings?.edit()?.putBoolean(Constants.PASS_THROUGH_ENABLED, false)?.apply()
                    settings?.edit()?.putString(Constants.SAVED_ENABLED_DATA, DEFAULT_ENABLED)?.apply()
                    getSettings(requireContext())
                        .edit().putString(FILE_KEY, DEFAULT_FILE)
                        .putString(CHARGE_ON_KEY, DEFAULT_ENABLED)
                        .putString(CHARGE_OFF_KEY, DEFAULT_DISABLED).apply()
                    enableSwitches(listOf(enableSwitch, disableChargeSwitch, limitByVoltageSwitch))
                    setStatusCTRLFileData()
                }
            }
        }
    }

    //to update battery status on UI
    private val charging = object : BroadcastReceiver() {
        private var previousStatus = BatteryManager.BATTERY_STATUS_UNKNOWN

        override fun onReceive(context: Context, intent: Intent) {
            val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            if (currentStatus != previousStatus) {
                previousStatus = currentStatus
                when (currentStatus) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> {
                        statusText?.setText(R.string.charging)
                        statusText?.setTextColor(ContextCompat.getColor(context, R.color.darkGreen))
                    }
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                        statusText?.setText(R.string.discharging)
                        statusText?.setTextColor(ContextCompat.getColor(context, R.color.orange))
                    }
                    BatteryManager.BATTERY_STATUS_FULL -> {
                        statusText?.setText(R.string.full)
                        statusText?.setTextColor(ContextCompat.getColor(context, R.color.darkGreen))
                    }
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                        statusText?.setText(R.string.not_charging)
                        statusText?.setTextColor(ContextCompat.getColor(context, R.color.orange))
                    }
                    else -> {
                        statusText?.setText(R.string.unknown)
                        statusText?.setTextColor(ContextCompat.getColor(context, R.color.red))
                    }
                }
            }
            updateBatteryInfo(intent)
        }
    }

    private fun updateBatteryInfo(intent: Intent) {
        batteryInfo?.text = String.format(
            " (%s)", Utils.getBatteryInfo(
                requireContext(), intent,
                prefs?.getBoolean(PrefsFragment.KEY_TEMP_FAHRENHEIT, false)!!
            )
        )
    }

    private fun hideKeybord() {
        val inputManager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (inputManager.isAcceptingText) {
            inputManager.hideSoftInputFromWindow(activity?.currentFocus?.windowToken, 0)
        }
    }

    private fun disableSwitches(switches: List<SwitchMaterial?>) {
        for (switch in switches) {
            switch?.isEnabled = false
        }
    }

    private fun enableSwitches(switches: List<SwitchMaterial?>) {
        for (switch in switches) {
            switch?.isEnabled = true
        }
    }

    private fun updateMinText(min: Int?) {
        when (min) {
            0 -> minText?.setText(R.string.no_recharge)
            else -> minText?.text = getString(R.string.recharge_below, min)
        }
    }

    private fun setStatusCTRLFileData() {
        val statusCTRLData = view?.findViewById<TextView>(R.id.status_ctrl_data)
        statusCTRLData?.text = String.format(
            "%s, %s, %s",
            Utils.getCtrlFileData(requireContext()),
            Utils.getCtrlEnabledData(requireContext()),
            Utils.getCtrlDisabledData(requireContext())
        )
    }

    private fun updateUi() {
        enableSwitch?.isChecked = settings?.getBoolean(Constants.CHARGE_LIMIT_ENABLED, false) == true
        disableChargeSwitch?.isChecked = settings?.getBoolean(Constants.DISABLE_CHARGE_NOW, false) == true
        limitByVoltageSwitch?.isChecked = settings?.getBoolean(Constants.LIMIT_BY_VOLTAGE, false) == true
        enablePassThrough?.isChecked = settings?.getBoolean(Constants.PASS_THROUGH_ENABLED, false) == true
        val max = settings?.getInt(Constants.LIMIT, 80) ?: 80
        val min = settings?.getInt(Constants.MIN, max - 2) ?: (max - 2)
        maxPicker?.value = max
        maxText?.text = getString(R.string.limit, max)
        minPicker?.maxValue = max
        minPicker?.value = min
        updateMinText(min)
    }
}