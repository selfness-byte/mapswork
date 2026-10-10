package com.selfness.mapswork.ui

import com.selfness.mapswork.core.*
import com.selfness.mapswork.location.*

fun ValidationError.description(): String = when (this) {
    ValidationError.NAME -> "상점 이름을 1~80자로 입력해 주세요."
    ValidationError.LOCATION -> "지도에서 상점 위치를 골라 주세요."
    ValidationError.RADIUS -> "알림 반경을 다시 선택해 주세요."
    ValidationError.ACTIVE_LIMIT -> "알림은 최대 100곳까지 켤 수 있어요. 다른 상점의 알림을 끄거나 이 상점을 알림 없이 저장해 주세요."
    ValidationError.NOT_FOUND -> "이미 삭제된 상점이에요. 목록에서 다시 확인해 주세요."
}

fun Readiness.description(): String = when (this) {
    Readiness.READY -> "필요한 권한이 준비됐어요"
    Readiness.UNSUPPORTED -> "Android 8.0 이상 휴대폰에서 사용할 수 있어요"
    Readiness.PRECISE_LOCATION -> "정확한 위치 권한이 필요해요"
    Readiness.BACKGROUND_LOCATION -> "앱을 닫아도 알림을 받으려면 위치를 항상 허용해 주세요"
    Readiness.NOTIFICATIONS -> "근처 알림을 허용해 주세요"
    Readiness.LOCATION_SERVICES -> "휴대폰의 위치 사용을 켜 주세요"
    Readiness.PLAY_SERVICES -> "Google Play 서비스를 사용할 수 없어요"
}

fun RegistrationStatus.description(): String = when (phase) {
    RegistrationPhase.CHECKING -> "알림 준비 중"
    RegistrationPhase.PAUSED -> "근처 알림이 꺼져 있어요"
    RegistrationPhase.EMPTY -> "알림을 켤 상점을 등록해 주세요"
    RegistrationPhase.READY -> "${registeredCount}곳의 근처 알림이 준비됐어요"
    RegistrationPhase.BLOCKED -> reason?.description() ?: "알림 설정을 확인해 주세요"
    RegistrationPhase.ERROR -> message ?: "위치 감지를 준비하지 못했어요"
}

fun radiusLabel(meters: Int) = if (meters == 1000) "1km" else "${meters}m"
