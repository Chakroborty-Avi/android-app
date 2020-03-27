package one.mixin.android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.webkit.WebView

class MixinWebView(context: Context, attributeSet: AttributeSet) : WebView(context, attributeSet) {

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) {
            goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
