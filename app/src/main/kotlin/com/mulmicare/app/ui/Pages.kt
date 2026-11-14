package com.mulmicare.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mulmicare.app.ui.Ui.dp
import com.mulmicare.core.session.BackseatRating
import com.mulmicare.core.session.Rewards
import com.mulmicare.core.session.Screen
import com.mulmicare.core.voice.Passenger

/** 화면에서 서비스로 보내는 동작. */
interface UiActions {
    fun openSetup()
    fun pickSetup(choice: SetupChoice)
    fun startCare()
    fun declineOffer()
    fun skipPractice()
    fun openVoyage()
    fun pickDecoration(name: String)
    fun rate(rating: BackseatRating)
    fun clearRecords()
    fun goHome()
    fun openDevLog()
}

/**
 * 정차 중 화면들. 모두 큰 글자·큰 버튼, 짧은 문구(S3). 주행 중에는 띄우지 않는다(S1).
 */
object Pages {

    fun home(context: Context, state: CareUi.State, actions: UiActions): View = Ui.column(context, 56).apply {
        addView(Ui.title(context, "멀미케어", 44f))
        addView(Ui.spacer(context, 12))
        addView(
            Ui.body(
                context,
                if (state.caring) {
                    "뒷자리 케어 중이에요.\n굽은 길에서 리듬 놀이를 제안하고, 실내 공기를 맞춰요.\n주행 중에는 화면 없이 소리로만 함께해요."
                } else {
                    "내비에 30분 넘는 목적지를 넣으면 출발 전에 케어를 제안해요.\n지금 바로 켤 수도 있어요."
                },
                26f,
            ),
        )
        addView(Ui.spacer(context, 40))
        addView(Ui.row(context).apply {
            if (!state.caring) {
                addView(Ui.button(context, "케어 시작") { actions.openSetup() })
            } else {
                addView(Ui.button(context, "항해 지도 보기") { actions.openVoyage() })
            }
        })
        addView(Ui.spacer(context, 60))
        addView(Ui.body(context, "이동 기록은 이 차 안에만 저장되고 밖으로 보내지 않아요. 아이 이름이나 나이는 받지 않아요.", 18f))
        addView(Ui.row(context).apply {
            addView(link(context, "기록 지우기") { actions.clearRecords() })
            addView(link(context, "개발 로그") { actions.openDevLog() })
        })
    }

    /** F1.3 출발 전 10초 설정. */
    fun setup(context: Context, state: CareUi.State, actions: UiActions): View {
        val c = state.setup
        val card = Ui.column(context, 48).apply {
            background = Ui.rounded(Ui.CARD, 36f * context.dp(1))
            addView(Ui.title(context, "케어를 켤까요?", 40f))
            addView(Ui.body(context, "목적지까지 시간이 걸리네요. 뒷자리를 함께 돌볼게요.", 22f))
            addView(Ui.spacer(context, 20))
            addView(Ui.choiceRow(context, "뒷자리", listOf("아이", "어른"), if (c.passenger == Passenger.CHILD) "아이" else "어른") {
                actions.pickSetup(c.copy(passenger = if (it == "아이") Passenger.CHILD else Passenger.ADULT))
            })
            if (c.passenger == Passenger.CHILD) {
                addView(Ui.choiceRow(context, "리듬 놀이", listOf("제안받기", "묻지 않기"), if (c.proposePlay) "제안받기" else "묻지 않기") {
                    actions.pickSetup(c.copy(proposePlay = it == "제안받기"))
                })
                addView(Ui.choiceRow(context, "종이배", SetupChoice.BOATS, c.boat) { actions.pickSetup(c.copy(boat = it)) })
                addView(Ui.choiceRow(context, "음악", SetupChoice.THEMES, c.theme) { actions.pickSetup(c.copy(theme = it)) })
            }
            addView(Ui.spacer(context, 24))
            addView(Ui.row(context).apply {
                addView(Ui.button(context, "케어 시작") { actions.startCare() })
                addView(View(context), LinearLayout.LayoutParams(context.dp(20), 1))
                addView(Ui.button(context, "오늘은 안 할게요", primary = false) { actions.declineOffer() })
            })
        }
        return centered(context, card)
    }

    /** F2 아이를 위한 30초 연습. [caption]과 [boat]는 MainActivity가 순서대로 바꾼다. */
    fun practice(context: Context, state: CareUi.State, boat: BoatView, caption: TextView, actions: UiActions): View =
        Ui.column(context, 48).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(Ui.title(context, "리듬 놀이 연습", 36f))
            boat.decoration = state.decoration
            addView(boat, LinearLayout.LayoutParams(context.dp(360), context.dp(300)))
            caption.textSize = 30f
            caption.setTextColor(Ui.INK)
            caption.gravity = Gravity.CENTER
            addView(caption, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(Ui.spacer(context, 24))
            addView(Ui.button(context, "건너뛰기", primary = false) { actions.skipPractice() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }

    /** F8.2, F8.3 항해 지도와 꾸미기. */
    fun voyage(context: Context, state: CareUi.State, screen: Screen.VoyageMap, actions: UiActions): View =
        Ui.column(context, 40).apply {
            val today = screen.rounds.sumOf { it.stars }
            addView(Ui.title(context, "오늘의 항해", 38f))
            addView(Ui.body(context, "오늘 지킨 별 ${today}개 · 모은 별 ${screen.totalStars}개", 24f, Ui.INK))
            addView(VoyageMapView(context).apply {
                rounds = screen.rounds
                decoration = state.decoration
            }, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(380)))
            addView(decorations(context, state, screen.totalStars, screen.unlocked, actions))
            addView(Ui.spacer(context, 16))
            addView(Ui.button(context, "닫기", primary = false) { actions.goHome() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }

    /** F9 도착 후 리포트. */
    fun report(context: Context, state: CareUi.State, screen: Screen.Report, rated: Boolean, actions: UiActions): View =
        Ui.column(context, 40).apply {
            val r = screen.report
            addView(
                Ui.title(
                    context,
                    if (r.rounds.isEmpty()) "도착! 오늘은 리듬 놀이 없이 다녀왔어요" else "도착! 오늘은 ${r.rounds.size}판, 별 ${r.starsKept}개를 지켰어요",
                    34f,
                ),
            )
            addView(Ui.body(context, "물결이 높을수록 그 판에서 많이 흔들렸어요.", 22f))
            addView(WaveView(context).apply { rounds = r.rounds }, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(260)))
            addView(decorations(context, state, screen.totalStars, screen.unlocked, actions))
            addView(Ui.spacer(context, 16))
            if (!rated) {
                addView(Ui.body(context, "뒷자리는 어땠나요?", 26f, Ui.INK))
                addView(Ui.spacer(context, 10))
                addView(Ui.row(context).apply {
                    addView(Ui.button(context, "괜찮았어요") { actions.rate(BackseatRating.FINE) })
                    addView(View(context), LinearLayout.LayoutParams(context.dp(20), 1))
                    addView(Ui.button(context, "힘들어했어요", primary = false) { actions.rate(BackseatRating.STRUGGLED) })
                })
            } else {
                addView(Ui.body(context, "고마워요. 다음 이동의 실내 케어에 반영할게요.", 24f, Ui.INK))
                addView(Ui.spacer(context, 10))
                addView(Ui.button(context, "닫기", primary = false) { actions.goHome() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            }
        }

    fun devLog(context: Context, logView: TextView, actions: UiActions): View = Ui.column(context, 24).apply {
        addView(Ui.button(context, "돌아가기", primary = false) { actions.goHome() })
        logView.textSize = 14f
        logView.setTextColor(Ui.INK)
        addView(ScrollView(context).apply { addView(logView) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    /** F5.8 열린 꾸미기와 다음 꾸미기까지 남은 별. */
    private fun decorations(context: Context, state: CareUi.State, total: Int, unlocked: List<String>, actions: UiActions): View =
        Ui.column(context, 0).apply {
            val next = Rewards.STARS_PER_REWARD - total % Rewards.STARS_PER_REWARD
            val more = if (unlocked.size < Rewards.ITEMS.size) " · 별 ${next}개 더 모으면 새 꾸미기가 열려요" else ""
            addView(Ui.body(context, "종이배 꾸미기$more", 22f, Ui.INK))
            if (unlocked.isNotEmpty()) {
                addView(Ui.choiceRow(context, "", unlocked, state.decoration ?: "") { actions.pickDecoration(it) })
            }
        }

    private fun link(context: Context, text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 18f
        setTextColor(Ui.SEA)
        setPadding(0, context.dp(12), context.dp(32), context.dp(12))
        setOnClickListener { onClick() }
    }

    private fun centered(context: Context, child: View): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        setPadding(context.dp(40), context.dp(30), context.dp(40), context.dp(30))
        addView(child, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }
}
