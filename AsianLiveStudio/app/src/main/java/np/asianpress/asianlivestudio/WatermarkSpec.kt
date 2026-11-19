package np.asianpress.asianlivestudio

/**
 * Branding is stored in normalized coordinates (0f..1f), not screen pixels.
 * This lets the same studio layout scale across phone brands and video sizes.
 *
 * Rendering into the actual recorded/streamed video is a separate pipeline stage;
 * this class is the shared configuration model for that renderer.
 */
data class WatermarkSpec(
    val businessName: String,
    val phoneNumber: String,
    val logoUri: String? = null,
    val tickerText: String = "",
    val anchor: Anchor = Anchor.TOP_LEFT,
    val xFraction: Float = 0.04f,
    val yFraction: Float = 0.04f,
    val logoWidthFraction: Float = 0.18f,
    val opacity: Float = 1f,
    val showBusinessName: Boolean = true,
    val showPhoneNumber: Boolean = true,
    val showTicker: Boolean = true
) {
    init {
        require(xFraction in 0f..1f)
        require(yFraction in 0f..1f)
        require(logoWidthFraction in 0.02f..0.8f)
        require(opacity in 0f..1f)
    }

    enum class Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
}

/**
 * A destination must be configured by the user. Never hard-code or log stream keys.
 * Production builds should encrypt the key at rest using Android Keystore.
 */
data class StreamDestination(
    val displayName: String,
    val rtmpUrl: String,
    val streamKey: String,
    val platform: Platform
) {
    enum class Platform { YOUTUBE, FACEBOOK, CUSTOM_RTMP }

    fun isConfigured(): Boolean =
        rtmpUrl.startsWith("rtmp://") || rtmpUrl.startsWith("rtmps://")
}
