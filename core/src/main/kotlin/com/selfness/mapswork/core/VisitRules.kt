package com.selfness.mapswork.core

data class VisitDecision(val shop: Shop, val shouldNotify: Boolean)

object VisitRules {
    const val COOLDOWN_MILLIS = 1_800_000L

    fun transition(shop: Shop, transition: Transition, nowMillis: Long): VisitDecision {
        if (!shop.enabled) return VisitDecision(shop, false)
        if (transition == Transition.EXIT) {
            return VisitDecision(shop.copy(visitState = VisitState.OUTSIDE), false)
        }
        if (shop.visitState == VisitState.INSIDE) return VisitDecision(shop, false)
        val last = shop.lastNotifiedAtMillis
        val notify = last == null || (nowMillis >= last && nowMillis - last >= COOLDOWN_MILLIS)
        return VisitDecision(
            shop.copy(visitState = VisitState.INSIDE, lastNotifiedAtMillis = if (notify) nowMillis else last),
            notify,
        )
    }
}
