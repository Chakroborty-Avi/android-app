package one.mixin.android.ui.landing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import one.mixin.android.R
import one.mixin.android.extension.replaceFragment
import one.mixin.android.job.MixinJobManager
import one.mixin.android.ui.common.BaseActivity
import org.jetbrains.anko.doAsync
import javax.inject.Inject

class LandingActivity : BaseActivity() {

    companion object {
        const val ARGS_PIN = "args_pin"

        fun show(context: Context) {
            val intent = Intent(context, LandingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }

        fun show(context: Context, verificationCode: String) {
            val intent = Intent(context, LandingActivity::class.java).apply {
                putExtra(ARGS_PIN, verificationCode)
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var jobManager: MixinJobManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        val pin = intent.getStringExtra(ARGS_PIN)
        if (pin == null) {
            doAsync { jobManager.clear() }
        }
        val fragment = MobileFragment.newInstance(pin)
        replaceFragment(fragment, R.id.container, MobileFragment.TAG)
    }
}