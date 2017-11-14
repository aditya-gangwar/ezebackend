package in.ezeshop.utilities;

import com.backendless.exceptions.BackendlessException;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.CsvConverter;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.common.constants.CommonConstants;
import in.ezeshop.common.constants.DbConstants;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.common.database.*;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.messaging.PushNotifier;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.messaging.SmsHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by adgangwa on 14-11-2017.
 */
public class TxnHelper2 {
    private MyLogger mLogger = new MyLogger("TxnHelper2");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    private Transaction mTransaction;
    private Merchants mMerchant;
    private Customers mCustomer;
    private boolean mValidException;

    private String mMerchantName;
    private String mCustomerId;

    private int mClCredit;
    private int mCbCredit;
    private int mClDebit;
    private int mClOverdraft;
    private int mClBalance;
    private int mPayment;
    private String mTxnDate;

    /*
     * Below are various txn scenarios:
     *
     *  1) Normal Transaction Create
     *          -- handled by this fx. only
     *          -- received Transaction object will have Id == null
     *  2) Customer Order Transaction Create
     *          -- this fx. is not called
     *          -- handled in CreateOrder() in CustomerServices
     *  3) Normal Transaction Update
     *          -- invalid scenario as of now.
     *  4) Customer Order Transaction Update
     *          -- happens against order status updates
     *          -- received Transaction object will have Id == string with orderId
     *          -- also the 'txn status' will be == Pending.
     *          -- order 'cancelled' status though is handled by 'CancelOrder()' in CommonServices
     *          -- rest all order status are handled by this fx. (i.e. accepted, dispatched, delivered)
     *  5) Txn Delete
     *          -- invalid scenario as of now
     */

    public Transaction processTxn(Transaction txn, String argPin, boolean isOtp) throws Exception {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "processTxn";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = CsvConverter.csvStrFromTxn(txn);

        mValidException = false;
        try {
            mLogger.debug("In Transaction processTxn: "+isOtp);
            mTransaction = txn;

            // Fetch merchant - this fx. can only be called by merchant
            mMerchant = (Merchants) BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_MERCHANT, mEdr, mLogger, false);
            String merchantId = mMerchant.getAuto_id();
            mLogger.setProperties(mMerchant.getAuto_id(),DbConstants.USER_TYPE_MERCHANT, mMerchant.getDebugLogs());

            // print roles - for debug purpose
            //BackendUtils.printCtxtInfo(mLogger);

            // Fetch Customer
            mCustomer = BackendOps.getCustomer(mTransaction.getCust_private_id(), CommonConstants.ID_TYPE_AUTO, true);
            mCustomerId = mCustomer.getMobile_num();
            mEdr[BackendConstants.EDR_CUST_ID_IDX] = mCustomerId;

            // Validations
            validateUserStatus();
            validatePinOtp(argPin, isOtp);

            // check if update or new txn case
            boolean newTxnCase = true;
            boolean txnCommitCase = true;

            Transaction dbTxn = null;
            if(mTransaction.getTrans_id()!=null && !mTransaction.getTrans_id().isEmpty()) {
                // transaction update case
                newTxnCase = false;
                dbTxn = BackendOps.fetchTxn(mTransaction.getTrans_id(), mMerchant.getTxn_table(), mMerchant.getCashback_table());
                validateTxnUpdate(dbTxn);
            }

            // set/update transaction fields
            if(newTxnCase) {
                mTransaction.setCreate_time(new Date());
                mTransaction.setTrans_id(IdGenerator.generateTxnId(merchantId));
                mTransaction.setStatus(DbConstants.TRANSACTION_STATUS.Committed.toString());
                // Merchant and Customer data
                mTransaction.setMerchant_id(merchantId);
                mTransaction.setMerchant_name(mMerchant.getName());
                mTransaction.setCust_private_id(mCustomer.getPrivate_id());
                mTransaction.setCust_mobile(mCustomer.getMobile_num());
                // Billing details - remains same as provided in passed object
                // other data
                mTransaction.setArchived(false);
                // Update EDR params to add transaction Id
                mEdr[BackendConstants.EDR_API_PARAMS_IDX]=mEdr[BackendConstants.EDR_API_PARAMS_IDX]+
                        BackendConstants.BACKEND_EDR_SUB_DELIMETER+mTransaction.getTrans_id();
            } else {
                // update txn status
                DbConstants.CUSTOMER_ORDER_STATUS orderStatus = DbConstants.CUSTOMER_ORDER_STATUS.fromString(mTransaction.getCustOrder().getCurrStatus());
                if(orderStatus==DbConstants.CUSTOMER_ORDER_STATUS.Dispatched) {
                    mTransaction.setStatus(DbConstants.TRANSACTION_STATUS.Committed.toString());
                } else {
                    txnCommitCase = false;
                }
                // Merchant and Customer data -- ideally shud be copied from DB object - to avoid malicious attempts
                // but not doing - as already validated for merchant and customer ids
                // Billing details -- no change as updated values are in object passed from app
                // Other details -- no change as updated values are in object passed from app
                mTransaction.setArchived(dbTxn.getArchived());
                // Child objects
                // Update Order
                updateOrder();
            }

            // At this point - mTransaction have all updated values

            // Update associated cashback record - only if final txn commit
            if(txnCommitCase) {
                // cashback object is attached to transaction - only when it is committed
                // so even for txn update - the attached cashback object to dbTxn should be null

                // fetch cashback object
                Cashback cashback = null;
                String whereClause = "rowid = '" + mCustomer.getPrivate_id() + merchantId + "'";
                ArrayList<Cashback> data = BackendOps.fetchCashback(whereClause, mMerchant.getCashback_table(), false);
                if (data != null) {
                    cashback = data.get(0);
                } else {
                    // In app - we fetch cashback before txn commit
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.CUST_NOT_REG_WITH_MCNT), "Customer not registered with this Merchant: "+ whereClause+","+ mMerchant.getCashback_table()+","+merchantId +","+ mCustomerId);
                }

                updateCashback(cashback);
                // following are uses to adding cashback object to txn:
                // 1) both txn and cashback, will get updated in one go - thus saving rollback scenarios
                // 2) updated cashback object will be automatically returned,
                // along with updated transaction object to calling app,
                // which can thus use it to display updated balance - instead of fetching cashback again.
                // 3) cashback object in transaction will be used in afterCreate() of txn table too - to get updated balance
                //
                // As 'Transaction' table will be exported to CSV file at EoD
                // so there wont be much back-pointers in 'Cashback' table pointing to 'Transaction' table row
                mTransaction.setCashback(cashback);
            }

            // update/create txn
            mTransaction = BackendOps.saveTransaction(mTransaction, mMerchant.getTxn_table(), mMerchant.getCashback_table());

            try {
                // send sms/in-app notification to customer
                sendMsgToCust(txnCommitCase);
                // no exception - means function execution success
                mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

            } catch (Throwable ex) {
                if(ex instanceof Exception) {
                    BackendUtils.handleException(ex, false, mLogger, mEdr);
                } else {
                    mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
                    mLogger.error("Exception in " + mEdr[BackendConstants.EDR_API_NAME_IDX] + ": " + ex.toString());
                }
                // ignore and dont throw it
            }

            return mTransaction;

        } catch(Exception e) {
            BackendUtils.handleException(e, mValidException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }


    /*
     * Private helper methods
     */
    private void updateOrder() {
        CustomerOrder order = mTransaction.getCustOrder();
        DbConstants.CUSTOMER_ORDER_STATUS orderStatus = DbConstants.CUSTOMER_ORDER_STATUS.fromString(order.getCurrStatus());
        switch (orderStatus) {
            case New:
                order.setCurrStatus(DbConstants.CUSTOMER_ORDER_STATUS.Accepted.toString());
                order.setAcceptTime(new Date());
                break;
            case Accepted:
                order.setCurrStatus(DbConstants.CUSTOMER_ORDER_STATUS.Dispatched.toString());
                order.setDispatchTime(new Date());
                break;
            case Dispatched:
                order.setCurrStatus(DbConstants.CUSTOMER_ORDER_STATUS.Delivered.toString());
                order.setDeliverTime(new Date());
                break;
            default:
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid Customer Order status");
        }
        order.setStatusChgByUserType(DbConstants.USER_TYPE_MERCHANT);
        order.setPrevStatus(orderStatus.toString());
    }

    private void updateCashback(Cashback cashback) {
        // validate for balance
        if(mTransaction.getCl_debit() > CommonUtils.getAccBalance(cashback) ) {
            // already checked in app - so shouldn't reach here at first place
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.ACCOUNT_NOT_ENUF_BALANCE), "");
        }

        // update amounts in cashback object
        // 3 types of credit
        cashback.setCl_credit(cashback.getCl_credit() + mTransaction.getCl_credit());
        // 2 types of debit
        cashback.setCl_debit(cashback.getCl_debit() + mTransaction.getCl_debit());
        cashback.setCl_overdraft(cashback.getCl_overdraft() + mTransaction.getCl_overdraft());

        // check for cash account limit - after above amount update only
        // only if some add to account is happening
        int accBalance = CommonUtils.getAccBalance(cashback);
        if ( accBalance > MyGlobalSettings.getCashAccLimit()
                || Math.abs(accBalance) > MyGlobalSettings.getAccOverdraftLimit() ) {
            // already checked in app - so shouldn't reach here at first place
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.CASH_ACCOUNT_LIMIT_RCHD), "Cash account limit reached: " + mCustomerId);
        }

        // Cashback credit intentionally done, after checking for above limits
        cashback.setCb_credit(cashback.getCb_credit() + mTransaction.getCb_credit());
        cashback.setExtra_cb_credit(cashback.getExtra_cb_credit() + mTransaction.getExtra_cb_credit());
        cashback.setTotal_billed(cashback.getTotal_billed() + mTransaction.getTotal_billed());

        // update txn dependent cb fields
        cashback.setLastTxnId(mTransaction.getTrans_id());
        cashback.setLastTxnTime(mTransaction.getCreate_time());
    }

    private void validateUserStatus() {
        // validations for merchant
        // credit txns not allowed under expiry duration
        if(mMerchant.getAdmin_status()== DbConstants.USER_STATUS_UNDER_CLOSURE &&
                (mTransaction.getCb_credit() > 0 ||
                        mTransaction.getCl_credit() > 0 ||
                        mTransaction.getExtra_cb_credit() > 0 ) ) {
            // ideally it shudn't reach here - as both cl and cb settings are disabled and not allowed to be edited in app
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.ACC_UNDER_EXPIRY), "");
        }

        // validations for customer
        // check if mCustomer is enabled
        BackendUtils.checkCustomerStatus(mCustomer, mEdr, mLogger);

        // if Customer in 'restricted access' mode - allow only credit txns
        if(mCustomer.getAdmin_status()==DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY &&
                ( mTransaction.getCl_debit()>0 || mTransaction.getCl_overdraft()>0 )) {
            mValidException = true; // to avoid logging of this exception
            throw new BackendlessException(String.valueOf(ErrorCodes.LIMITED_ACCESS_CREDIT_TXN_ONLY), "");
        }
    }

    private void validatePinOtp(String argOtp, boolean isOtp) {
        // See if PIN/OTP is required
        if(CommonUtils.txnVerifyReq(mMerchant, mTransaction)) {
            // if here - means txn verification is required
            if(argOtp==null || argOtp.isEmpty()) {
                // PIN/OTP not available - ideally shudn't reach here - as already checked in app
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), "PIN Missing: " + mCustomerId);

            } else {
                // PIN/OTP available - verify the same
                if(isOtp) {
                    if(!BackendOps.validateOtp(mCustomer.getMobile_num(), DbConstants.OP_TXN_COMMIT, argOtp, mEdr, mLogger)) {
                        mLogger.debug("Wrong OTP: "+argOtp+","+mCustomer.getMobile_num());
                        mValidException = true;
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_OTP), "");
                    }
                    mTransaction.setCpin(DbConstants.TXN_CUSTOMER_OTP_USED);

                } else {
                    if (!SecurityHelper.verifyCustPin(mCustomer, argOtp, mLogger)) {
                        int cnt = BackendUtils.handleWrongAttempt(mCustomerId, mCustomer, DbConstants.USER_TYPE_CUSTOMER,
                                DbConstantsBackend.WRONG_PARAM_TYPE_PIN, DbConstants.OP_TXN_COMMIT, mEdr, mLogger);
                        mValidException = true;
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), String.valueOf(cnt));
                    }
                    mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_USED);
                }
            }
        } else {
            mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_NOT_USED);
        }
    }

    private void validateTxnUpdate(Transaction dbTxn) {
        if(CommonUtils.isOnlineOrderTxn(mTransaction)) {
            if(mTransaction.getCustOrder()==null) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Customer order object is not available");
            }
            // check for transaction status
            if(!mTransaction.getStatus().equals(DbConstants.TRANSACTION_STATUS.Pending.toString())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Update not allowed for completed transactions.");
            }
            // check for merchant id
            if(!dbTxn.getMerchant_id().equals(mTransaction.getMerchant_id())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Merchant Id is not matching");
            }
            // check for customer id
            if(!dbTxn.getCust_private_id().equals(mTransaction.getCust_private_id())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Customer Id is not matching");
            }
        } else {
            // Txn update is allowed only for customer order scenarios
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Transaction update allowed only for Customer order cases.");
        }
    }

    private void sendMsgToCust(boolean txnCommitCase) {
        // Always send in-app notification - if possible
        // Send SMS only if final commit case or in-app notify is not possible

        mMerchantName = mTransaction.getMerchant_name().toUpperCase(Locale.ENGLISH);
        mCbCredit = mTransaction.getCb_credit() + mTransaction.getExtra_cb_credit();

        if(txnCommitCase) {
            // Set variables to be used to build message
            mClCredit = mTransaction.getCl_credit();
            mClDebit = mTransaction.getCl_debit();
            mClOverdraft = mTransaction.getCl_overdraft();
            mClBalance = CommonUtils.getAccBalance(mTransaction.getCashback());
            SimpleDateFormat sdf = new SimpleDateFormat(CommonConstants.DATE_FORMAT_ONLY_DATE_BACKEND, CommonConstants.MY_LOCALE);
            sdf.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));
            mTxnDate = sdf.format(mTransaction.getCreate_time());

            String text = buildTxnCommitMsg();
            // Send SMS only in cases of 'debit' or 'add cash' > configured amounts
            if( mClDebit > BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT
                    || mClOverdraft > 0
                    || mClCredit > BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT
                    ) {
                // Send SMS through HTTP
                SmsHelper.sendSMS(text,mCustomer.getMobile_num(), mEdr, mLogger, true);
            }
            // Always send the Push Notification to Customer
            PushNotifier.pushNotification(text,text,mCustomer.getMsgDevId(),mEdr,mLogger);

        } else {
            // Send order status change message
            mPayment = mTransaction.getPaymentAmt();
            String text = buildOrderStatusMsg();
            if(mCustomer.getMsgDevId()==null || mCustomer.getMsgDevId().isEmpty()) {
                SmsHelper.sendSMS(text,mCustomer.getMobile_num(), mEdr, mLogger, true);
            } else {
                PushNotifier.pushNotification(text,text,mCustomer.getMsgDevId(),mEdr,mLogger);
            }
        }
    }

    private String buildTxnCommitMsg() {
        String text=null;

        if(mClDebit > BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT && mClOverdraft <= 0) {
            text = String.format(SmsConstants.SMS_TXN_DEBIT_CL, mMerchantName, mClDebit, mTxnDate, mClBalance);

        } else if(mClDebit <= 0 && mClOverdraft > 0) {
            text = String.format(SmsConstants.SMS_TXN_DEBIT_OD, mMerchantName, mClOverdraft, mTxnDate, mClBalance);

        } else if(mClDebit > 0 && mClOverdraft > 0) {
            text = String.format(SmsConstants.SMS_TXN_DEBIT_CL_OD, mMerchantName, mClDebit, mClOverdraft, mTxnDate, mClBalance);

        } else if(mClCredit >BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT) {
            text = String.format(SmsConstants.SMS_TXN_CREDIT_CL, mMerchantName, mClCredit, mTxnDate, mClBalance);

        }
        return text;
    }

    private String buildOrderStatusMsg() {
        String text = null;

        DbConstants.CUSTOMER_ORDER_STATUS orderStatus = DbConstants.CUSTOMER_ORDER_STATUS.fromString(mTransaction.getCustOrder().getCurrStatus());
        switch (orderStatus) {
            case Accepted:
                text = String.format(SmsConstants.MSG_ORDER_ACCEPT_TO_CUST,
                        mTransaction.getTrans_id(), mMerchantName, mPayment, mCbCredit);
                break;
            case Dispatched:
                text = String.format(SmsConstants.MSG_ORDER_DISPATCH_TO_CUST,
                        mTransaction.getTrans_id(), mMerchantName, mPayment, mCbCredit);
                break;
            case Delivered:
                text = String.format(SmsConstants.MSG_ORDER_DELIVER_TO_CUST,
                        mTransaction.getTrans_id(), mMerchantName);
                break;
            default:
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Invalid Customer Order status");
        }
        return text;
    }

}
