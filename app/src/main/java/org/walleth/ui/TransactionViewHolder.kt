package org.walleth.ui

import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.view.View
import kotlinx.android.synthetic.main.transaction_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.kethereum.functions.getTokenTransferTo
import org.kethereum.functions.getTokenTransferValue
import org.kethereum.functions.isTokenTransfer
import org.ligi.kaxt.setVisibility
import org.walleth.R
import org.walleth.activities.getTransactionActivityIntentForHash
import org.walleth.data.AppDatabase
import org.walleth.data.addressbook.resolveNameAsync
import org.walleth.data.config.Settings
import org.walleth.data.exchangerate.ExchangeRateProvider
import org.walleth.data.networks.NetworkDefinitionProvider
import org.walleth.data.tokens.getEthTokenForChain
import org.walleth.data.transactions.TransactionEntity
import org.walleth.ui.valueview.ValueViewController

class TransactionViewHolder(itemView: View,
                            private val direction: TransactionAdapterDirection,
                            val networkDefinitionProvider: NetworkDefinitionProvider,
                            private val exchangeRateProvider: ExchangeRateProvider,
                            val settings: Settings) : RecyclerView.ViewHolder(itemView) {


    private val amountViewModel by lazy {
        ValueViewController(itemView.difference, exchangeRateProvider, settings)
    }

    fun bind(transactionWithState: TransactionEntity, appDatabase: AppDatabase) {

        val transaction = transactionWithState.transaction

        val relevantAddress = if (direction == TransactionAdapterDirection.INCOMING) {
            transaction.from
        } else {
            transaction.to
        }

        if (transaction.isTokenTransfer()) {
            appDatabase.addressBook.resolveNameAsync(transaction.getTokenTransferTo()) {
                itemView.address.text = it
            }
            val tokenAddress = transaction.to
            if (tokenAddress != null) {
                { appDatabase.tokens.forAddress(tokenAddress) }.asyncAwaitNonNull { token ->
                    amountViewModel.setValue(transaction.getTokenTransferValue(), token)
                }
            }
        } else {
            amountViewModel.setValue(transaction.value, getEthTokenForChain(networkDefinitionProvider.getCurrent()))
            relevantAddress?.let {
                appDatabase.addressBook.resolveNameAsync(it) {
                    itemView.address.text = it
                }
            }
        }

        itemView.transaction_err.setVisibility(transactionWithState.transactionState.error != null)
        if (transactionWithState.transactionState.error != null) {
            itemView.transaction_err.text = transactionWithState.transactionState.error
        }

        val epochMillis = (transaction.creationEpochSecond ?: 0) * 1000L
        val context = itemView.context
        itemView.date.text = DateUtils.getRelativeDateTimeString(context, epochMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                0
        )

        itemView.transaction_state_indicator.setImageResource(
                when {
                    !transactionWithState.transactionState.isPending -> R.drawable.ic_lock_black_24dp
                    transactionWithState.signatureData == null -> R.drawable.ic_lock_open_black_24dp
                    else -> R.drawable.ic_lock_outline_24dp
                }
        )

        itemView.isClickable = true
        itemView.setOnClickListener {
            transactionWithState.hash.let {
                context.startActivity(context.getTransactionActivityIntentForHash(it))
            }

        }
    }

}

fun <T> (() -> T).asyncAwait(resultCall: (T) -> Unit) {
    GlobalScope.launch(Dispatchers.Main) {
        resultCall(async(Dispatchers.Default) {
            invoke()
        }.await())
    }
}


fun <T> (() -> T?).asyncAwaitNonNull(resultCall: (T) -> Unit) {
    GlobalScope.launch(Dispatchers.Main) {
        async(Dispatchers.Default) {
            invoke()
        }.await()?.let { resultCall(it) }
    }
}