package dev.openbuds.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openbuds.app.OpenBudsApp
import dev.openbuds.app.data.BudsApi
import dev.openbuds.app.data.BudsRepository
import dev.openbuds.app.data.BudsState
import dev.openbuds.app.data.BudsUiState
import dev.openbuds.app.data.Command
import dev.openbuds.app.protocol.BudsEvent
import dev.openbuds.app.protocol.Eq
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.protocol.FitStatus
import dev.openbuds.app.protocol.KeyFn
import dev.openbuds.app.protocol.Protocol
import dev.openbuds.app.service.BudsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** UI state of the earbud fit test. */
sealed interface FitTestUiState {
    /** No fit test has been started. */
    data object Idle : FitTestUiState

    /** The fit test is running on the earbuds. */
    data object Running : FitTestUiState

    /** One earbud is not inserted, so the fit test cannot complete. */
    data object NotInserted : FitTestUiState

    /** The fit test failed or timed out. */
    data object Failed : FitTestUiState

    /** The fit test finished with [left] and [right] results. */
    data class Done(
        val left: FitStatus,
        val right: FitStatus,
    ) : FitTestUiState
}

/** Holds the earbud state for the UI and forwards user actions to [BudsRepository]. */
class BudsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: BudsRepository = (application as OpenBudsApp).repository
    private val _isRefreshing = MutableStateFlow(false)
    private val _fitTest = MutableStateFlow<FitTestUiState>(FitTestUiState.Idle)
    private var fitTimeoutJob: Job? = null
    private val phoneRinger = (application as OpenBudsApp).phoneRinger

    /** Connection lifecycle and the last state read from the earbuds. */
    val uiState: StateFlow<BudsUiState> = repository.state

    /** True while [reload] is in progress; drives the pull-to-refresh indicator. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Progress and result of the earbud fit test. */
    val fitTest: StateFlow<FitTestUiState> = _fitTest.asStateFlow()

    /** True while the phone is ringing for the find-my-phone feature. */
    val isRinging: StateFlow<Boolean> = phoneRinger.isRinging

    init {
        viewModelScope.launch {
            repository.events.collect { event ->
                if (event is BudsEvent.FitTest && _fitTest.value is FitTestUiState.Running) onFitResult(event)
            }
        }
        viewModelScope.launch {
            uiState
                .map { it.buds?.features?.get(Feature.FIND_MY_PHONE) == true }
                .distinctUntilChanged()
                .collect { on -> if (on) BudsService.start(getApplication()) }
        }
    }

    /** Looks for bonded earbuds and connects to the only one, or asks the user to choose. */
    fun refresh() {
        repository.refresh()
    }

    /** Re-reads the full earbud state; drives the pull-to-refresh indicator. */
    fun reload() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.reload()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Connects to the bonded earbuds with [address]. */
    fun selectDevice(address: String) {
        repository.select(address)
    }

    /** Connects again to the last selected device. */
    fun reconnect() {
        repository.reconnect()
    }

    /** Sets the noise-control mode; [mode] is one of the Noise.* values. */
    fun setNoiseMode(mode: Int) {
        repository.apply(BudsApi.setNoise(mode)) { it.copy(noise = mode) }
    }

    /** Switches feature [id] (a Feature.* value) on or off. */
    fun setFeature(
        id: Int,
        on: Boolean,
    ) {
        repository.apply(BudsApi.setFeature(id, on)) { it.copy(features = it.features + (id to on)) }
    }

    /** Selects an EQ preset. */
    fun setEq(preset: Int) {
        repository.apply(BudsApi.setEq(preset)) { it.copy(eq = preset) }
    }

    /** Turns the volume enhancer on or off. */
    fun setHighVolume(on: Boolean) {
        repository.apply(BudsApi.setHighVolume(on)) { it.copy(volumeEnhancer = on) }
    }

    /** Starts the earbud fit test; [fitTest] stays Running until a result arrives or the test times out. */
    fun startFitTest() {
        if (_fitTest.value is FitTestUiState.Running) return
        _fitTest.value = FitTestUiState.Running
        repository.apply(BudsApi.fitTest(true)) { it }
        fitTimeoutJob?.cancel()
        fitTimeoutJob =
            viewModelScope.launch {
                delay(FIT_TEST_TIMEOUT_MS)
                if (_fitTest.value is FitTestUiState.Running) _fitTest.value = FitTestUiState.Failed
            }
    }

    /** Stops a running test on the earbuds, cancels its timeout and returns [fitTest] to Idle. */
    fun resetFitTest() {
        val wasRunning = _fitTest.value is FitTestUiState.Running
        fitTimeoutJob?.cancel()
        fitTimeoutJob = null
        _fitTest.value = FitTestUiState.Idle
        if (wasRunning) repository.apply(BudsApi.fitTest(false)) { it }
    }

    /** Sets a dynamic slider: [type] is 1 (low), 2 (mid) or 3 (high), [value] is -5..5. */
    fun setDynamicSlider(
        type: Int,
        value: Int,
    ) {
        repository.apply(BudsApi.setDynamicSlider(type, value)) { it.copy(dynamic = it.dynamic + (type to value)) }
    }

    /** Stores six custom EQ gains (-6..6), creating the custom EQ first when the earbuds do not have one. */
    fun setCustomEq(gains: List<Int>) {
        val buds = repository.state.value.buds
        val freqs = bandFreqs(buds)
        val commands =
            buildList<Command> {
                if (buds?.customEq == null) add(BudsApi.createCustomEq(freqs))
                add(BudsApi.updateCustomEq(gains, freqs))
            }
        val stored = gains.toList()
        repository.applyAll(commands) { it.copy(customEq = stored, eq = EQ_CUSTOM) }
    }

    /** Sets the custom EQ bands to zero and switches back to the Clear Bass preset. */
    fun resetCustomEq() {
        val buds = repository.state.value.buds
        val freqs = bandFreqs(buds)
        val exists = buds?.customEq != null
        val commands =
            buildList<Command> {
                if (exists) add(BudsApi.updateCustomEq(freqs.map { 0 }, freqs))
                add(BudsApi.setEq(Eq.CLEAR_BASS))
            }
        repository.applyAll(commands) { it.copy(customEq = it.customEq?.map { 0 }, eq = Eq.CLEAR_BASS) }
    }

    /** Assigns [fn] to [button] for the press [action]. */
    fun setKey(
        button: Int,
        action: Int,
        fn: Int,
    ) {
        repository.apply(BudsApi.setKey(button, action, fn)) {
            it.copy(keys = it.keys + (BudsApi.keyId(button, action) to fn))
        }
    }

    /** Sets the noise-control button cycle to [mask] (CycleBit.* bits) and maps the button to it. */
    fun setNoiseCycle(mask: Int) {
        val commands =
            listOf(
                BudsApi.setNoiseCycle(mask),
                BudsApi.setKey(CYCLE_BUTTON, CYCLE_ACTION, KeyFn.NOISE_CONTROL),
            )
        repository.applyAll(commands) {
            it.copy(
                noiseCycle = mask,
                keys = it.keys + (BudsApi.keyId(CYCLE_BUTTON, CYCLE_ACTION) to KeyFn.NOISE_CONTROL),
            )
        }
    }

    /** Connects (action 1) or deletes (action 2) the paired device with [mac] in protocol byte order. */
    fun multiConnect(
        mac: ByteArray,
        action: Int,
    ) {
        repository.apply(BudsApi.multiConnect(mac, action)) { it }
    }

    /** Turns the find-my-phone feature on or off and starts or stops [BudsService] to match. */
    fun setFindMyPhone(on: Boolean) {
        setFeature(Feature.FIND_MY_PHONE, on)
        if (on) BudsService.start(getApplication()) else BudsService.stop(getApplication())
    }

    /** Starts or stops the test ring on this phone, used to check the find-my-phone sound. */
    fun toggleTestRing() {
        phoneRinger.toggle()
    }

    private fun bandFreqs(buds: BudsState?): List<Int> =
        buds?.customEqFreqs?.takeIf { it.size == Protocol.EQ_FREQS.size } ?: Protocol.EQ_FREQS.toList()

    private fun onFitResult(event: BudsEvent.FitTest) {
        val left = event.statusOf(1)
        val right = event.statusOf(2)
        if (left == FitStatus.INTERRUPTED || right == FitStatus.INTERRUPTED) return
        fitTimeoutJob?.cancel()
        fitTimeoutJob = null
        _fitTest.value =
            when {
                left == FitStatus.NOT_INSERTED || right == FitStatus.NOT_INSERTED -> FitTestUiState.NotInserted
                left == FitStatus.FAILED || right == FitStatus.FAILED -> FitTestUiState.Failed
                else -> FitTestUiState.Done(left, right)
            }
    }

    private companion object {
        /** Time after which a fit test with no result is reported as failed. */
        const val FIT_TEST_TIMEOUT_MS = 15_000L

        /** EQ id reported by the earbuds when the custom EQ is active. */
        const val EQ_CUSTOM = 0x04

        /** Button and press action whose function is the noise-control cycle. */
        const val CYCLE_BUTTON = 4
        const val CYCLE_ACTION = 1
    }
}
