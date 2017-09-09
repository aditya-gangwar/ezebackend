package in.ezeshop.events.persistence_service;

import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.RunnerContext;
import com.backendless.servercode.annotation.Asset;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.common.database.Transaction;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

/**
* Transaction1TableEventHandler handles events for all entities. This is accomplished
* with the @Asset( "Transaction1" ) annotation.
* The methods in the class correspond to the events selected in Backendless
* Console.
*/
@Asset( "Transaction1" )
public class Transaction1TableEventHandler extends com.backendless.servercode.extension.PersistenceExtender<Transaction>
{
    /*@Override
    public void beforeCreate( RunnerContext context, Transaction transaction) throws Exception
    {
        Backendless.Data.mapTableToClass("Transaction1", Transaction.class);
        TxnProcessHelper txnEventHelper = new TxnProcessHelper();
        txnEventHelper.handleTxnCommit(context.getUserToken(), context.getUserId(), transaction, false, false);
    }

    @Async
    @Override
    public void afterCreate( RunnerContext context, Transaction transaction, ExecutionResult<Transaction> result ) throws Exception
    {
        Backendless.Data.mapTableToClass("Transaction1", Transaction.class);
        TxnProcessHelper txnEventHelper = new TxnProcessHelper();
        txnEventHelper.handleAfterCreate(context, transaction, result);
    }*/

    @Override
    public void beforeUpdate( RunnerContext context, Transaction transaction ) throws Exception
    {
        MyLogger mLogger = new MyLogger("events.TransactionEventHandler");
        String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

        // update not allowed from app - return exception
        // beforeUpdate is not called, if update is done from server code
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "txn-beforeUpdate";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = transaction.getMerchant_id();
        BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
    }
}