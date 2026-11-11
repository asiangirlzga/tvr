package com.example.tvremote.voice

data class TvApp(val label: String, val aliases: List<String>, val link: String)

/**
 * Apps the remote can launch by voice or from the shortcut row.
 * Links use `market://launch?id=<package>` (opens the app if installed) or an https deep link.
 * Package names can differ between TVs and regions; edit this list to match yours.
 */
object TvApps {
    val all: List<TvApp> = listOf(
        TvApp("YouTube", listOf("youtube", "you tube", "यूट्यूब"), "https://www.youtube.com"),
        TvApp("Netflix", listOf("netflix", "नेटफ्लिक्स"), "market://launch?id=com.netflix.ninja"),
        TvApp(
            "Prime Video",
            listOf("prime video", "amazon prime", "prime", "प्राइम वीडियो"),
            "market://launch?id=com.amazon.amazonvideo.livingroom",
        ),
        TvApp(
            "Hotstar",
            listOf("hotstar", "jio hotstar", "disney hotstar", "हॉटस्टार"),
            "market://launch?id=in.startv.hotstar",
        ),
        TvApp("Spotify", listOf("spotify", "स्पॉटिफाई"), "market://launch?id=com.spotify.tv.android"),
        TvApp("Play Store", listOf("play store", "google play", "playstore"), "market://launch?id=com.android.vending"),
    )
}
