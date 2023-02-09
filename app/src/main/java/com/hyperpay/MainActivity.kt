package com.hyperpay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.devmarvel.creditcardentry.library.CreditCardForm
import com.oppwa.mobile.connect.exception.PaymentError
import com.oppwa.mobile.connect.exception.PaymentException
import com.oppwa.mobile.connect.payment.CheckoutInfo
import com.oppwa.mobile.connect.payment.card.CardPaymentParams
import com.oppwa.mobile.connect.provider.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*

class MainActivity : BasePaymentActivity(), ITransactionListener {

    private lateinit var checkoutId: String
    private var paymentProvider: OppPaymentProvider? = null
    private var isMonthValid = false
    private var isYearValid: Boolean = false
    private val currentYear = 0

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        paymentProvider = OppPaymentProvider(this, Connect.ProviderMode.TEST)
        paymentProvider!!.setThreeDSWorkflowListener { this }

        initViews()
    }

    override fun onCheckoutIdReceived(checkoutId: String?) {
        super.onCheckoutIdReceived(checkoutId)

        checkoutId?.let {
            this.checkoutId = checkoutId
            requestCheckoutInfo(checkoutId)
        }
    }

    //region ITransactionListener methods
    override fun paymentConfigRequestSucceeded(checkoutInfo: CheckoutInfo) {
        /* Get the resource path from checkout info to request the payment status later */
        resourcePath = checkoutInfo.resourcePath

        runOnUiThread {
            showConfirmationDialog(
                checkoutInfo.amount.toString(),
                checkoutInfo.currencyCode!!
            )
        }
    }

    override fun paymentConfigRequestFailed(paymentError: PaymentError) {
        hideProgressBar()
        showErrorDialog(paymentError)
    }

    @ExperimentalCoroutinesApi
    override fun transactionCompleted(transaction: Transaction) {
        if (transaction.transactionType == TransactionType.SYNC) {
            /* check the status of synchronous transaction */
            requestPaymentStatus(resourcePath!!)
        } else {
            /* wait fot the callback in the onNewIntent() */
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(transaction.redirectUrl)))
        }
    }

    override fun transactionFailed(transaction: Transaction, paymentError: PaymentError) {
        hideProgressBar()
        showErrorDialog(paymentError)
    }
    //endregion

    @ExperimentalCoroutinesApi
    private fun initViews() {
        validateMonth()
        validateYear()
        validateCVV()

       /* holder_edit_text.setText(Constants.Config.CARD_HOLDER_NAME)
        number_text_input_layout!!.setCardNumber(Constants.Config.CARD_NUMBER)
        expiry_month_edit_text.setText(Constants.Config.CARD_EXPIRY_MONTH)
        expiry_year_edit_text.setText(Constants.Config.CARD_EXPIRY_YEAR)
        cvv_edit_text.setText(Constants.Config.CARD_CVV)*/
        progressBar = progress_bar_custom_ui
        number_text_input_layout.setOnCardValidCallback {
            expiry_month_edit_text.requestFocus() }

        button_pay_now.setOnClickListener {

                if (paymentProvider != null && checkFields()) {
                    requestCheckoutId()

            }
        }
    }


    private fun validateMonth() {
        val calendar = Calendar.getInstance()
        expiry_month_edit_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                if (editable.length >= 2) {
                    var goNext = true
                    val month = editable.toString().toInt()
                    if (month > 12 || month == 0) {
                        expiry_year_edit_text.error = getString(R.string.invalid_expiry_month)
                        goNext = false
                    }
                    isMonthValid = goNext
                    if (goNext) expiry_year_edit_text.requestFocus()
                } else {
                    isMonthValid = false
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    private fun validateYear() {
        expiry_year_edit_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                expiry_year_edit_text.removeTextChangedListener(this)
                if (editable.length >= 2) {
                    val year = editable.toString().toInt()
                    if (year < currentYear) {
                        expiry_year_edit_text.setText(currentYear.toString())
                        expiry_year_edit_text.setSelection(editable.length)
                    } else {
                        cvv_edit_text.requestFocus()
                    }
                    isYearValid = true
                } else {
                    isYearValid = false
                }
                expiry_year_edit_text.addTextChangedListener(this)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }


    private fun validateCVV() {
        cvv_edit_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                if (editable != null && editable.length >= 3) {
                    if (!CardPaymentParams.isCvvValid(cvv_edit_text.text.toString())) {
                        return
                    }
                    number_text_input_layout.requestFocus()
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    private fun checkFields(): Boolean {
        if (holder_edit_text.text.isEmpty() ||
            expiry_month_edit_text.text.isEmpty() ||
            expiry_year_edit_text.text.isEmpty() ||
            cvv_edit_text.text.isEmpty()) {
            if (number_text_input_layout.isCreditCardValid){
                showAlertDialog(R.string.error_empty_fields)
                return false
            }
        }

        return true
    }

    private fun requestCheckoutInfo(checkoutId: String) {
        try {
            paymentProvider!!.requestCheckoutInfo(checkoutId, this)
        } catch (e: PaymentException) {
            e.message?.let { showAlertDialog(it) }
        }
    }

    private fun pay(checkoutId: String) {
        try {
            val paymentParams = createPaymentParams(checkoutId)
            paymentParams.shopperResultUrl =
                getString(R.string.custom_ui_callback_scheme) + "://callback"
            val transaction = Transaction(paymentParams)

            paymentProvider?.submitTransaction(transaction, this)
        } catch (e: PaymentException) {
            showErrorDialog(e.error)
        }
    }

    private fun createPaymentParams(checkoutId: String): CardPaymentParams {
        val cardHolder = holder_edit_text.text.toString()
        val cardNumber = number_text_input_layout.creditCard.cardNumber
        val cardExpiryMonth = expiry_month_edit_text.text.toString()
        val cardExpiryYear = expiry_year_edit_text.text.toString()
        val cardCVV = cvv_edit_text.text.toString()

        return CardPaymentParams(
            checkoutId,
            Constants.Config.CARD_BRAND,
            cardNumber,
            cardHolder,
            cardExpiryMonth,
            "20$cardExpiryYear",
            cardCVV
        )
    }

    private fun showConfirmationDialog(amount: String, currency: String) {
        AlertDialog.Builder(this)
            .setMessage(
                String.format(
                    getString(R.string.message_payment_confirmation),
                    amount,
                    currency
                )
            )
            .setPositiveButton(R.string.button_ok) { _, _ -> pay(checkoutId) }
            .setNegativeButton(R.string.button_cancel) { _, _ -> hideProgressBar() }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        runOnUiThread { showAlertDialog(message) }
    }

    private fun showErrorDialog(paymentError: PaymentError) {
        runOnUiThread { showErrorDialog(paymentError.errorMessage) }
    }
}

private fun CreditCardForm.setCardNumber(cardNumber: String) {

}
