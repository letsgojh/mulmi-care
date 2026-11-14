package com.mulmicare.app.ui

import android.os.Handler
import android.os.Looper
import com.mulmicare.core.session.Screen
import com.mulmicare.core.voice.Passenger

/** F1.3 출발 전에 고르는 것. */
data class SetupChoice(
    val passenger: Passenger = Passenger.CHILD,
    val proposePlay: Boolean = true,
    val boat: String = BOATS.first(),
    val theme: String = THEMES.first(),
) {
    companion object {
        val BOATS = listOf("돛단배", "통통배", "카누")
        val THEMES = listOf("바다", "숲", "우주")
    }
}

/**
 * 서비스와 화면이 함께 보는 화면 상태. 어떤 화면을 띄울지는 core(CareSession, CareOffer)가 정하고
 * 서비스가 여기에 반영한다. 화면은 이것만 그린다.
 */
object CareUi {
    sealed interface Page {
        /** 홈: 대기 또는 케어 상태. */
        data object Home : Page

        /** F1.3 출발 전 설정 카드. */
        data object Setup : Page

        /** F2 처음 쓸 때 30초 연습. */
        data object Practice : Page

        /** F8 정차 중 항해 지도. */
        data class Voyage(val screen: Screen.VoyageMap) : Page

        /** F9 도착 후 리포트. */
        data class Report(val screen: Screen.Report) : Page

        /** 개발용 로그. */
        data object DevLog : Page
    }

    data class State(
        val page: Page = Page.Home,
        val caring: Boolean = false,
        val setup: SetupChoice = SetupChoice(),
        val decoration: String? = null,
        /** P단이 아니면 true. 화면은 뒤로 물러난다(S1). */
        val driving: Boolean = false,
    )

    private val main = Handler(Looper.getMainLooper())
    private var listener: ((State) -> Unit)? = null

    /** 멀미케어 화면이 앞에 떠 있는지(onStart~onStop). */
    @Volatile
    var visible = false

    @Volatile
    var state = State()
        private set

    fun update(change: (State) -> State) {
        synchronized(this) { state = change(state) }
        val snapshot = state
        main.post { listener?.invoke(snapshot) }
    }

    fun show(page: Page) = update { it.copy(page = page) }

    fun observe(listener: ((State) -> Unit)?) {
        this.listener = listener
        listener?.invoke(state)
    }
}
