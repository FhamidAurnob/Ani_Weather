package com.example.wuwaweather.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewModelScope
import com.example.wuwaweather.WeatherClient
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.BuildConfig
import com.example.wuwaweather.alerts.AlarmScheduler
import com.example.wuwaweather.widget.VeloraWidget
import com.example.wuwaweather.widget.WidgetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the sky behind the UI is drawn. */
enum class BackgroundMode {
    /** Code-drawn animated scenes. */
    LIVE,
    /** Realistic looping videos. */
    REALISTIC
}

/** One swipeable page: either the device location or a saved city. */
data class PageState(
    val key: String,                    // LOCATION_KEY or the city name
    val isLoading: Boolean = true,
    val bundle: WeatherBundle? = null,
    val error: String? = null
) {
    val isCurrentLocation: Boolean get() = key == LOCATION_KEY

    /** Name to show before the weather has loaded. */
    val displayName: String
        get() = bundle?.current?.city ?: when {
            isCurrentLocation -> tr("My location", "আমার অবস্থান")
            key.startsWith(COORD_PREFIX) -> key.substringAfter('|')
            else -> key
        }
}

/** Saved places chosen from suggestions are stored as "coord:lat,lon|Name". */
const val COORD_PREFIX = "coord:"

/** A city suggestion shown while typing in search. */
data class CitySuggestion(val name: String, val detail: String, val lat: Double, val lon: Double)

const val LOCATION_KEY = "@location"

/** One-shot request for the pager to move to a page. */
data class ScrollRequest(val page: Int, val id: Long = System.nanoTime())

data class WeatherUiState(
    val pages: List<PageState> = emptyList(),
    val currentPage: Int = 0,
    val backgroundMode: BackgroundMode = BackgroundMode.LIVE,
    val units: UnitSystem = UnitSystem.METRIC,
    val showSplash: Boolean = true,
    val isSearching: Boolean = false,
    val suggestions: List<CitySuggestion> = emptyList(),
    val message: String? = null,
    val scrollRequest: ScrollRequest? = null
) {
    val current: PageState? get() = pages.getOrNull(currentPage)
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeatherRepository()
    private val location = LocationProvider(app)
    private val prefs = app.getSharedPreferences("velora_prefs", Context.MODE_PRIVATE)
    private val cache = WeatherCache(app)

    private val _state = MutableStateFlow(
        WeatherUiState(
            backgroundMode = enumPref(KEY_MODE, BackgroundMode.LIVE),
            units = enumPref(KEY_UNITS, UnitSystem.METRIC)
        )
    )
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private val loadJobs = mutableMapOf<String, Job>()
    private var suggestJob: Job? = null

    init {
        val keys = buildList {
            if (prefs.getBoolean(KEY_USE_LOCATION, false) && location.hasPermission()) add(LOCATION_KEY)
            addAll(savedCities())
        }.ifEmpty { listOf(DEFAULT_CITY) }
        // Instant start: show the last saved weather right away, then refresh
        _state.update { it.copy(pages = keys.map { k -> PageState(k, isLoading = true, bundle = cache.get(k)) }) }
        keys.forEach { load(it) }

        // Short brand moment; longer only if there's nothing cached to show yet
        viewModelScope.launch {
            val hasCache = _state.value.pages.firstOrNull()?.bundle != null
            delay(if (hasCache) 700 else 1600)
            while (_state.value.pages.firstOrNull()?.let { it.isLoading && it.bundle == null } == true) delay(100)
            _state.update { it.copy(showSplash = false) }
        }
    }

    // ---------- Location ----------

    /** Ask for permission automatically only once, on first launch. */
    fun shouldAskLocationOnStart(): Boolean =
        !prefs.getBoolean(KEY_ASKED_LOCATION, false) && !location.hasPermission()

    fun hasLocationPermission() = location.hasPermission()

    fun onLocationPermissionResult(granted: Boolean, userInitiated: Boolean) {
        prefs.edit().putBoolean(KEY_ASKED_LOCATION, true).apply()
        if (granted) {
            enableLocation()
        } else if (userInitiated) {
            showMessage(tr("Location permission denied. You can allow it in Settings.", "লোকেশন অনুমতি দেওয়া হয়নি। সেটিংস থেকে অনুমতি দিতে পারেন।"))
        }
    }

    /** Adds (or reloads) the "current location" page at the front and jumps to it. */
    fun enableLocation() {
        prefs.edit().putBoolean(KEY_USE_LOCATION, true).apply()
        _state.update { s ->
            val pages = if (s.pages.any { it.isCurrentLocation }) s.pages
                        else listOf(PageState(LOCATION_KEY)) + s.pages
            s.copy(pages = pages, currentPage = 0, scrollRequest = ScrollRequest(0))
        }
        load(LOCATION_KEY)
    }

    // ---------- Search suggestions ----------

    /** Called on every keystroke; fetches suggestions after a short pause. */
    fun onQueryChanged(query: String) {
        suggestJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(350)
            val results = runCatching {
                WeatherClient.api.geocode(q, apiKey = BuildConfig.OPENWEATHER_API_KEY).body()
            }.getOrNull().orEmpty()
            val list = results.mapNotNull { r ->
                val name = r.name ?: return@mapNotNull null
                val lat = r.lat ?: return@mapNotNull null
                val lon = r.lon ?: return@mapNotNull null
                val country = r.country?.let { java.util.Locale("", it).getDisplayCountry(com.example.wuwaweather.i18n.I18n.locale).ifBlank { it } }
                CitySuggestion(name, listOfNotNull(r.state, country).joinToString(", "), lat, lon)
            }.distinctBy { "${it.name}|${it.detail}" }
            _state.update { it.copy(suggestions = list) }
        }
    }

    fun addSuggestion(s: CitySuggestion) {
        _state.update { it.copy(suggestions = emptyList()) }
        addPlace(key = "$COORD_PREFIX${s.lat},${s.lon}|${s.name}", place = Place.Coords(s.lat, s.lon))
    }

    fun selectPage(index: Int) = _state.update {
        if (index in it.pages.indices) it.copy(currentPage = index, scrollRequest = ScrollRequest(index)) else it
    }

    // ---------- Saved cities ----------

    /** Search: validate the city, then add it as a page (or jump to it if already saved). */
    fun addCity(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        addPlace(key = null, place = Place.City(q))
    }

    /** Validates a place by loading it, then adds it (or jumps to it if already saved). */
    private fun addPlace(key: String?, place: Place) {
        suggestJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, suggestions = emptyList()) }
            try {
                val bundle = repo.load(place, _state.value.units)
                val name = bundle.current.city
                _state.update { s ->
                    val existing = s.pages.indexOfFirst {
                        !it.isCurrentLocation && (it.key == key || it.displayName.equals(name, ignoreCase = true))
                    }
                    if (existing >= 0) {
                        val pages = s.pages.toMutableList().also { it[existing] = PageState(it[existing].key, false, bundle) }
                        s.copy(pages = pages, currentPage = existing, scrollRequest = ScrollRequest(existing), isSearching = false)
                    } else {
                        val pages = s.pages + PageState(key ?: name, false, bundle)
                        s.copy(pages = pages, currentPage = pages.lastIndex, scrollRequest = ScrollRequest(pages.lastIndex), isSearching = false)
                    }
                }
                persistCities()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false) }
                showMessage((e as? WeatherException)?.message ?: tr("Something went wrong. Please try again.", "কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।"))
            }
        }
    }

    fun removePage(key: String) {
        if (key == LOCATION_KEY) prefs.edit().putBoolean(KEY_USE_LOCATION, false).apply()
        loadJobs.remove(key)?.cancel()
        val wasLast = _state.value.pages.size <= 1
        _state.update { s ->
            val pages = s.pages.filterNot { it.key == key }.ifEmpty { listOf(PageState(DEFAULT_CITY)) }
            val page = s.currentPage.coerceAtMost(pages.lastIndex)
            s.copy(pages = pages, currentPage = page, scrollRequest = ScrollRequest(page))
        }
        persistCities()
        // Removing the only page brings back the default city
        if (wasLast) load(DEFAULT_CITY)
    }

    fun onPageChanged(page: Int) = _state.update { it.copy(currentPage = page) }

    // ---------- Settings ----------

    fun toggleUnits() {
        val next = if (_state.value.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC
        prefs.edit().putString(KEY_UNITS, next.name).apply()
        _state.update { it.copy(units = next) }
        refreshAll()
    }

    /** Switches between English and Bangla, then reloads so descriptions are translated too. */
    fun setLanguage(lang: String) {
        val app = getApplication<Application>()
        VeloraSettings.update(app) { it.copy(language = lang) }
        refreshAll()
        runCatching { com.example.wuwaweather.alerts.AlarmScheduler.rescheduleAll(app) }
    }

    fun toggleBackgroundMode() {
        val next = if (_state.value.backgroundMode == BackgroundMode.LIVE) BackgroundMode.REALISTIC else BackgroundMode.LIVE
        prefs.edit().putString(KEY_MODE, next.name).apply()
        _state.update { it.copy(backgroundMode = next) }
    }

    fun refresh(key: String) = load(key)

    private fun refreshAll() = _state.value.pages.forEach { load(it.key) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------- Loading ----------

    private fun load(key: String) {
        loadJobs[key]?.cancel()
        loadJobs[key] = viewModelScope.launch {
            updatePage(key) { it.copy(isLoading = true, error = null) }
            try {
                val place: Place = when {
                    key == LOCATION_KEY ->
                        location.current() ?: throw WeatherException(tr("Couldn't get your location. Is location turned on?", "আপনার অবস্থান পাওয়া যায়নি। লোকেশন চালু আছে কি?"))
                    key.startsWith(COORD_PREFIX) -> {
                        val (lat, lon) = key.removePrefix(COORD_PREFIX).substringBefore('|').split(',').map { it.toDouble() }
                        Place.Coords(lat, lon)
                    }
                    else -> Place.City(key)
                }
                val bundle = repo.load(place, _state.value.units)
                updatePage(key) { it.copy(isLoading = false, bundle = bundle) }
                cache.put(key, bundle)
                if (_state.value.pages.firstOrNull()?.key == key) updateWidget(bundle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = (e as? WeatherException)?.message ?: tr("Something went wrong. Please try again.", "কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।")
                updatePage(key) { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    /** The widget mirrors the first page (your location, or your first saved city). */
    private suspend fun updateWidget(bundle: WeatherBundle) {
        val app = getApplication<Application>()
        val c = bundle.current
        // Prayer times, maps and earthquake alerts all follow this "home" place
        HomeStore.update(app, Home(c.city, c.lat, c.lon, c.tzOffsetSeconds))
        runCatching { AlarmScheduler.rescheduleAll(app) }
        runCatching {
            WidgetSnapshot.save(app, WidgetSnapshot.from(bundle))
            VeloraWidget().updateAll(app)
        }
    }

    private fun updatePage(key: String, transform: (PageState) -> PageState) {
        _state.update { s -> s.copy(pages = s.pages.map { if (it.key == key) transform(it) else it }) }
    }

    private fun showMessage(msg: String) = _state.update { it.copy(message = msg) }

    private fun savedCities(): List<String> =
        prefs.getString(KEY_CITIES, null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            // Migrate from the single "last city" of the previous version
            ?: listOf(prefs.getString(KEY_LAST_CITY, DEFAULT_CITY) ?: DEFAULT_CITY)

    private fun persistCities() {
        cache.retain(_state.value.pages.map { it.key })
        val cities = _state.value.pages.filterNot { it.isCurrentLocation }.map { it.key }
        prefs.edit().putString(KEY_CITIES, cities.joinToString("\n")).apply()
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, default.name)!!) }.getOrDefault(default)

    private companion object {
        const val KEY_CITIES = "saved_cities"
        const val KEY_LAST_CITY = "last_city"
        const val KEY_MODE = "background_mode"
        const val KEY_UNITS = "units"
        const val KEY_USE_LOCATION = "use_location"
        const val KEY_ASKED_LOCATION = "asked_location"
        const val DEFAULT_CITY = "Dhaka"
    }
}
