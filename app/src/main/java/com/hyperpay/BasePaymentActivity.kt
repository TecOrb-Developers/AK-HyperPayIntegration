package com.hyperpay

import android.content.Intent
import android.os.Bundle
import com.oppwa.mobile.connect.checkout.dialog.CheckoutActivity
import com.oppwa.mobile.connect.checkout.meta.CheckoutSettings
import com.oppwa.mobile.connect.checkout.meta.CheckoutSkipCVVMode
import com.oppwa.mobile.connect.exception.PaymentError
import com.oppwa.mobile.connect.provider.Connect
import com.oppwa.mobile.connect.provider.Transaction
import com.oppwa.mobile.connect.provider.TransactionType
import com.oppwa.mobile.connect.utils.googlepay.CardPaymentMethodJsonBuilder
import com.oppwa.mobile.connect.utils.googlepay.PaymentDataRequestJsonBuilder
import com.oppwa.mobile.connect.utils.googlepay.TransactionInfoJsonBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONArray
import com.oppwa.msa.MerchantServerApplication
import com.oppwa.msa.ServerMode

private const val STATE_RESOURCE_PATH = "STATE_RESOURCE_PATH"

open class BasePaymentActivity : BaseActivity() {
    protected var resourcePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            resourcePath = savedInstanceState.getString(STATE_RESOURCE_PATH)
        }
    }

    @ExperimentalCoroutinesApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        /* Check of the intent contains the callback scheme */
        if (resourcePath != null && hasCallBackScheme(intent)) {
            requestPaymentStatus(resourcePath!!)
        }
    }

    @ExperimentalCoroutinesApi
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        /* Override onActivityResult to get notified when the checkout process is done */
        if (requestCode == CheckoutActivity.REQUEST_CODE_CHECKOUT) {
            when (resultCode) {
                CheckoutActivity.RESULT_OK -> {
                    /* Transaction completed */
                    val transaction: Transaction = data!!.getParcelableExtra(
                            CheckoutActivity.CHECKOUT_RESULT_TRANSACTION)!!
                    resourcePath = data.getStringExtra(CheckoutActivity.CHECKOUT_RESULT_RESOURCE_PATH)
                    /* Check the transaction type */
                    if (transaction.transactionType == TransactionType.SYNC) {
                        /* Check the status of synchronous transaction */
                        requestPaymentStatus(resourcePath!!)
                    } else {
                        /* Asynchronous transaction is processed in the onNewIntent() */
                        hideProgressBar()
                    }
                }
                CheckoutActivity.RESULT_CANCELED -> hideProgressBar()
                CheckoutActivity.RESULT_ERROR -> {
                    hideProgressBar()
                    val error: PaymentError = data!!.getParcelableExtra(
                            CheckoutActivity.CHECKOUT_RESULT_ERROR)!!
                    showAlertDialog(error.errorMessage)
                }
            }
        }
    }

    open fun onCheckoutIdReceived(checkoutId: String?) {
        if (checkoutId == null) {
            hideProgressBar()
            showAlertDialog(R.string.error_message)
        }
    }

    protected fun requestCheckoutId() {
        showProgressBar()
        MerchantServerApplication.requestCheckoutId(
                Constants.Config.AMOUNT,
                Constants.Config.CURRENCY,
                "PA",
                ServerMode.TEST,
                mapOf("notificationUrl" to Constants.NOTIFICATION_URL)
        ) { checkoutId, _ -> runOnUiThread { onCheckoutIdReceived(checkoutId) } }
    }

    protected fun requestPaymentStatus(resourcePath: String) {
        MerchantServerApplication.requestPaymentStatus(
                resourcePath
        ) { isSuccessful, _ -> runOnUiThread { onPaymentStatusReceived(isSuccessful) } }
    }

    protected fun createCheckoutSettings(checkoutId: String, callbackScheme: String): CheckoutSettings {
        return CheckoutSettings(checkoutId, Constants.Config.PAYMENT_BRANDS,
                Connect.ProviderMode.TEST)
                .setSkipCVVMode(CheckoutSkipCVVMode.FOR_STORED_CARDS)
                .setShopperResultUrl("$callbackScheme://callback")
                .setGooglePayPaymentDataRequestJson(getGooglePayPaymentDataRequestJson())
    }

    private fun hasCallBackScheme(intent: Intent): Boolean {
        return intent.scheme == getString(R.string.checkout_ui_callback_scheme) ||
                intent.scheme == getString(R.string.payment_button_callback_scheme) ||
                intent.scheme == getString(R.string.custom_ui_callback_scheme)
    }

    private fun onPaymentStatusReceived(isSuccessful: Boolean) {
        hideProgressBar()
        val message = if (isSuccessful) {
            R.string.message_successful_payment
        } else {
            R.string.message_unsuccessful_payment
        }

        showAlertDialog(message)
    }

    private fun getGooglePayPaymentDataRequestJson() : String {
        val allowedPaymentMethods = JSONArray()
                .put(
                    CardPaymentMethodJsonBuilder()
                        .setAllowedAuthMethods(JSONArray()
                                .put("PAN_ONLY")
                                .put("CRYPTOGRAM_3DS")
                        )
                        .setAllowedCardNetworks(JSONArray()
                                .put("VISA")
                                .put("MASTERCARD")
                                .put("AMEX")
                                .put("DISCOVER")
                                .put("JCB")
                        )
                        .setGatewayMerchantId(Constants.MERCHANT_ID)
                        .toJson()
                )

        val transactionInfo = TransactionInfoJsonBuilder()
                .setCurrencyCode(Constants.Config.CURRENCY)
                .setTotalPriceStatus("FINAL")
                .setTotalPrice(Constants.Config.AMOUNT)
                .toJson()

        val paymentDataRequest = PaymentDataRequestJsonBuilder()
                .setAllowedPaymentMethods(allowedPaymentMethods)
                .setTransactionInfo(transactionInfo)
                .toJson()

        return paymentDataRequest.toString()
    }
}