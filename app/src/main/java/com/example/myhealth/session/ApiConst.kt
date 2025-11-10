package com.example.myhealth.session

object ApiConst {
    // DNS 이슈 우회: IP로 직접 호출 (끝에 / 꼭 유지)
    const val BASE_URL = "http://211.110.140.202/mmapi/"

    // 서버가 가상호스팅이면 Host 헤더 필요
    const val VHOST_HOSTNAME = "xn--v69ak9glzv5va.kro.kr"

    // 개발 중 로그인 화면부터 보기 원하면 true
    const val FORCE_LOGIN_ON_START = true
}
