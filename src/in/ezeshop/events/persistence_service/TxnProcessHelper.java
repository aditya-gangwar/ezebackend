package in.ezeshop.events.persistence_service;

import com.backendless.exceptions.BackendlessException;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.CsvConverter;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.messaging.PushNotifier;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.*;
import in.ezeshop.messaging.SmsConstants;

import java.text.SimpleDateFormat;
import java.util.*;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

/**
 * Created by adgangwa on 13-05-2016.
 */
public class TxnProcessHelper {

    private MyLogger mLogger = new MyLogger("events.TxnTableEventHelper");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    private Merchants mMerchant;
    private Customers mCustomer;
    private Transaction mTransaction;
    private boolean mValidException;

    private String mMerchantId;
    private String mCustomerId;

    private int cl_credit;
    private int cb_credit;
    private int cl_debit;
    private int cl_overdraft;
    //private int cb_debit;
    private int cl_balance;
    //private int cb_balance;
    private String merchantName;
    private String txnDate;


    //public Transaction handleTxnCommit(String userToken, String userId, Transaction txn, boolean saveAlso, boolean sendSMS) {
    public Transaction handleTxnCommit(String userId, String txnCsvStr, String argPin, boolean isOtp, boolean saveAlso, boolean sendSMS) throws Exception {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "handleTxnCommit";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = txnCsvStr;

        mValidException = false;
        try {
            mLogger.debug("In Transaction handleTxnCommit: "+isOtp);
            /*if(userToken!=null) {
                HeadersManager.getInstance().addHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY, userToken);
            }*/

            //mTransaction = txn;
            mTransaction = CsvConverter.txnFromCsvStr(txnCsvStr);
            mTransaction.setCpin(argPin);

            // Fetch mMerchant
            //mMerchant = (Merchants) BackendUtils.fetchUser(userId,DbConstants.USER_TYPE_MERCHANT, mEdr, mLogger, false);
            mMerchant = (Merchants) BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_MERCHANT, mEdr, mLogger, false);
            mMerchantId = mMerchant.getAuto_id();
            mLogger.setProperties(mMerchant.getAuto_id(),DbConstants.USER_TYPE_MERCHANT, mMerchant.getDebugLogs());

            // print roles - for debug purpose
            BackendUtils.printCtxtInfo(mLogger);
            /*List<String> roles = Backendless.UserService.getUserRoles();
            mLogger.debug("Roles: "+roles.toString());*/

            // credit txns not allowed under expiry duration
            // txn cancellation is allowed
            if(mMerchant.getAdmin_status()== DbConstants.USER_STATUS_UNDER_CLOSURE &&
                    (mTransaction.getCb_credit() > 0 ||
                            mTransaction.getCl_credit() > 0 ||
                            mTransaction.getExtra_cb_credit() > 0 ) ) {
                // ideally it shudn't reach here - as both cl and cb settings are disabled and not allowed to be edited in app
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.ACC_UNDER_EXPIRY), "");
            }

            // Common processing for Transaction Commit and Cancel
            mTransaction.setMerchant_id(mMerchantId);
            commonTxnProcessing(isOtp);

            // update mCustomer for txn table
            //updateTxnTables(mCustomer, mMerchant.getTxn_table());

            // Fetch cashback record
            String whereClause = "rowid = '" + mCustomer.getPrivate_id() + mMerchantId + "'";
            Cashback cashback = null;
            ArrayList<Cashback> data = BackendOps.fetchCashback(whereClause, mMerchant.getCashback_table(), false);
            if (data != null) {
                cashback = data.get(0);

                // Integrity checks related to amount
                //if(mTransaction.getCl_debit() > (cashback.getCl_credit()-cashback.getCl_debit()) ) {
                if(mTransaction.getCl_debit() > CommonUtils.getAccBalance(cashback) ) {
                    // already checked in app - so shouldn't reach here at first place
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.ACCOUNT_NOT_ENUF_BALANCE), "");
                }
                /*if(mTransaction.getCb_debit() > (cashback.getCb_credit()-cashback.getCb_debit()) ) {
                    // already checked in app - so shouldn't reach here at first place
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.CB_NOT_ENUF_BALANCE), "");
                }*/

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

                /*cashback.setCb_credit( cashback.getCb_credit() +
                        (mTransaction.getCb_credit()+mTransaction.getExtra_cb_credit()) );
                cashback.setCb_debit(cashback.getCb_debit() + mTransaction.getCb_debit());
                cashback.setCb_billed(cashback.getCb_billed() + mTransaction.getCb_billed());*/

                // add/update transaction fields
                mTransaction.setCust_mobile(mCustomer.getMobile_num());
                //mTransaction.setCust_mobile(CommonUtils.getHalfVisibleMobileNum(mCustomer.getMobile_num()));
                // update cardId with cardNum
                // mCustomer.getMembership_card() wont be null - as this would have been already verified in commonTxnProcessing()
                /*if(mTransaction.getUsedCardId()==null || mTransaction.getUsedCardId().isEmpty()) {
                    mTransaction.setUsedCardId("");
                } else {
                    mTransaction.setUsedCardId(mCustomer.getMembership_card().getCardNum());
                }*/
                mTransaction.setCust_private_id(mCustomer.getPrivate_id());
                mTransaction.setMerchant_id(mMerchantId);
                mTransaction.setMerchant_name(mMerchant.getName());
                mTransaction.setTrans_id(IdGenerator.generateTxnId(mMerchantId));
                mEdr[BackendConstants.EDR_API_PARAMS_IDX]=mEdr[BackendConstants.EDR_API_PARAMS_IDX]+
                        BackendConstants.BACKEND_EDR_SUB_DELIMETER+mTransaction.getTrans_id();
                mTransaction.setCreate_time(new Date());
                mTransaction.setArchived(false);
                /*if(mTransaction.getImgFileName()!=null && !mTransaction.getImgFileName().isEmpty()) {
                    // calling app will need to upload 'card image file' with this name
                    mTransaction.setImgFileName(BackendUtils.getTxnImgFilename(mTransaction.getTrans_id()));
                }*/

                // update txn dependent cb fields
                cashback.setLastTxnId(mTransaction.getTrans_id());
                cashback.setLastTxnTime(mTransaction.getCreate_time());

                // following are uses to adding cashback object to txn:
                // 1) both txn and cashback, will get updated in one go - thus saving rollback scenarios
                // 2) updated cashback object will be automatically returned,
                // along with updated transaction object to calling app,
                // which can thus use it to display updated balance - instead of fetching cashback again.
                // 3) cashback object in transaction will be used in afterCreate() of txn table too - to get updated balance
                mTransaction.setCashback(cashback);

                if(saveAlso) {
                    mTransaction = BackendOps.updateTxn(mTransaction, mMerchant.getTxn_table(), mMerchant.getCashback_table());
                }

                // any exception after above (like in sending sms) - should not result in failure msg to the caller
                // as the txn got committed

                if(sendSMS) {
                    try {
                        buildAndSendTxnMsgs();
                        // no exception - means function execution success
                        mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

                    } catch (Throwable ex) {
                        if(ex instanceof Exception) {
                            BackendUtils.handleException((Exception)ex, false, mLogger, mEdr);
                        } else {
                            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
                            mLogger.error("Exception in " + mEdr[BackendConstants.EDR_API_NAME_IDX] + ": " + ex.toString());
                        }
                        // ignore and dont throw it
                    }
                }

            } else {
                // In app - we fetch cashback before txn commit
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.CUST_NOT_REG_WITH_MCNT), "Customer not registered with this Merchant: "+ whereClause+","+ mMerchant.getCashback_table()+","+mMerchantId +","+ mCustomerId);
            }

            return mTransaction;

        } catch(Exception e) {
            BackendUtils.handleException(e, mValidException,mLogger,mEdr);
            /*if(e instanceof BackendlessException) {
                throw BackendUtils.getNewException((BackendlessException) e);
            }*/
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    /*
     * Transaction creation is not done as API i.e. from servercode, only due to this function.
     * As currently this fx. gets called in 'async' mode - while if we do it from API (i.e. sending SMS) it will be 'sync'
     * Thus it saves us few millisecs while creating txn.
     */
    /*public void handleAfterCreate(RunnerContext context, Transaction txn, ExecutionResult<Transaction> result) {
        BackendUtils.initAll();
        mTransaction = txn;
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "txn-afterCreate";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = mTransaction.getMerchant_id()+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                mTransaction.getCust_mobile()+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                mTransaction.getCust_private_id()+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                mTransaction.getTrans_id();

        try {
            //mLogger.debug("In Transaction handleAfterCreate");
            // If transaction creation successful send SMS to mCustomer
            if(result.getException()==null) {
                buildAndSendTxnMsgs();
                mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            } else {
                mEdr[BackendConstants.EDR_EXP_CODE_IDX] = String.valueOf(result.getException().getCode());
                mEdr[BackendConstants.EDR_EXP_CODE_IDX] = result.getException().getExceptionMessage();
                mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
            }

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }*/

    /*public Transaction cancelTxn(String ctxtUserId, String txnId, String cardId, String pin, boolean isOtp) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "cancelTxn";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = txnId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+cardId;

        mValidException = false;
        try {
            mLogger.debug("In cancelTxn");

            // Fetch mMerchant
            mMerchant = (Merchants) BackendUtils.fetchUser(ctxtUserId,DbConstants.USER_TYPE_MERCHANT, mEdr, mLogger, false);
            mMerchantId = mMerchant.getAuto_id();
            mLogger.setProperties(mMerchantId,DbConstants.USER_TYPE_MERCHANT, mMerchant.getDebugLogs());

            // fetch txn
            mTransaction = BackendOps.fetchTxn(txnId, mMerchant.getTxn_table(), mMerchant.getCashback_table());
            if(!mTransaction.getMerchant_id().equals(mMerchantId) ) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Txn does not below to this Merchant: "+txnId);
            }

            if(mTransaction.getArchived()) {
                // I shudn't be here
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "This transaction cannot be cancelled.");
            }

            // set cancel time - to indicate cancellation of this txn in further processing
            mTransaction.setCancelTime(new Date());
            // set for verification purpose in commonTxnProcessing - values will be rolled back after that
            String oldCardId = mTransaction.getUsedCardId();
            mTransaction.setUsedCardId(cardId);
            String oldPin = mTransaction.getCpin();
            mTransaction.setCpin(pin);

            // do common processing
            commonTxnProcessing(isOtp);
            // restore values
            //mTransaction.setUsedCardId(oldCardId);
            mTransaction.setCpin(oldPin);
            //mTransaction.setCanImgFileName(BackendUtils.getTxnCancelImgFilename(mTransaction.getTrans_id()));

            // Fetch cashback record
            Cashback cashback = mTransaction.getCashback();
            if (cashback != null) {
                // update amounts in cashback object

                // Cannot cancel txn - if not enough CB available
                // i.e. the customer used the cashback after this txn
                // 'extra cashback' cannot be cancelled - so not checking for the same
                if(mTransaction.getCb_credit() > (cashback.getCb_credit()-cashback.getCb_debit()) ) {
                    throw new BackendlessException(String.valueOf(ErrorCodes.CB_NOT_ENUF_BALANCE), "");
                }

                // 'cl credit' is not changed in txn cancellation
                //cashback.setCl_credit(cashback.getCl_credit() + transaction.getCl_credit());
                // any 'cl debit' in this txn, will be returned
                cashback.setCl_debit(cashback.getCl_debit() - mTransaction.getCl_debit());

                // 'cb credit on Bill' will be deducted
                // 'extra cashback' cannot be cancelled - as 'add cash' cant be
                cashback.setCb_credit(cashback.getCb_credit() - mTransaction.getCb_credit());
                // any 'cb debit' will be returned
                cashback.setCb_debit(cashback.getCb_debit() - mTransaction.getCb_debit());

                // reverse bill amounts too
                cashback.setTotal_billed(cashback.getTotal_billed() - mTransaction.getTotal_billed());
                cashback.setCb_billed(cashback.getCb_billed() - mTransaction.getCb_billed());

                // update txn dependent cb fields
                cashback.setLastTxnId(mTransaction.getTrans_id());
                cashback.setLastTxnTime(mTransaction.getCreate_time());

                // cashback will be updated along with txn
                mTransaction.setCashback(cashback);
                BackendOps.updateTxn(mTransaction, mMerchant.getTxn_table(), mMerchant.getCashback_table());

                // Build SMS
                merchantName = mTransaction.getMerchant_name().toUpperCase(Locale.ENGLISH);
                SimpleDateFormat sdf = new SimpleDateFormat(CommonConstants.DATE_FORMAT_WITH_TIME, CommonConstants.DATE_LOCALE);
                sdf.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));
                String txnTime = sdf.format(mTransaction.getCreate_time());
                cb_balance = cashback.getCb_credit() - cashback.getCb_debit();
                cl_balance = cashback.getCl_credit() - cashback.getCl_debit();

                String smsText = String.format(SmsConstants.SMS_TXN_CANCEL,merchantName,txnTime,cl_balance,cb_balance);
                if(smsText!=null) {
                    // Send SMS through HTTP
                    SmsHelper.sendSMS(smsText,mTransaction.getCust_mobile(), mEdr, mLogger, true);
                }

            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Txn commit: No cashback object found: "+ mMerchantId +","+ mCustomerId);
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return mTransaction;

        } catch(Exception e) {
            BackendUtils.handleException(e, mValidException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }

    }*/

    /*
     * Private helper methods
     */
    private void commonTxnProcessing(boolean isOtp) {

        // Fetch Customer
        mCustomer = BackendOps.getCustomer(mTransaction.getCust_private_id(), CommonConstants.ID_TYPE_AUTO, true);
        mCustomerId = mCustomer.getMobile_num();
        mEdr[BackendConstants.EDR_CUST_ID_IDX] = mCustomerId;

        // check if mCustomer is enabled
        BackendUtils.checkCustomerStatus(mCustomer, mEdr, mLogger);

        // if Customer in 'restricted access' mode - allow only credit txns
        // txn cancellation is also not allowed
/*        if(mCustomer.getAdmin_status()==DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY &&
                ( mTransaction.getCl_debit()>0 || mTransaction.getCb_debit()>0 ||
                        mTransaction.getCancelTime()!=null )) {*/
        if(mCustomer.getAdmin_status()==DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY &&
                ( mTransaction.getCl_debit()>0 || mTransaction.getCl_overdraft()>0 )) {
            mValidException = true; // to avoid logging of this exception
            throw new BackendlessException(String.valueOf(ErrorCodes.LIMITED_ACCESS_CREDIT_TXN_ONLY), "");
        }

        // Any one of customer card or PIN is required
/*        if( (mTransaction.getUsedCardId()==null || mTransaction.getUsedCardId().isEmpty()) &&
                (mTransaction.getCpin()==null || mTransaction.getCpin().isEmpty()) &&
                CommonUtils.txnVerifyReq(mMerchant, mTransaction) ) {*/
        if( ((mTransaction.getCpin()==null || mTransaction.getCpin().isEmpty()) &&
                CommonUtils.txnVerifyReq(mMerchant, mTransaction) ) ) {
            // both card and PIN are not provided
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), "PIN Missing: " + mCustomerId);
        }

        // If here - means atleast one of card or pin is provided or is not required

        // verify card if provided
        /*if(mTransaction.getUsedCardId()!=null && !mTransaction.getUsedCardId().isEmpty()) {
            if(mCustomer.getMembership_card()==null) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_CARD), "Card Mismatch: " + mCustomerId);
            }

            mEdr[BackendConstants.EDR_CUST_CARD_NUM_IDX] = mCustomer.getMembership_card().getCardNum();
            if(!mTransaction.getUsedCardId().equals(mCustomer.getMembership_card().getCard_id())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_CARD), "Card Mismatch: " + mCustomerId);
            }
            BackendUtils.checkCardForUse(mCustomer.getMembership_card());
        }*/

        // verify PIN if provided
        if (mTransaction.getCpin() != null && !mTransaction.getCpin().isEmpty()) {

            //BackendUtils.printCtxtInfo(mLogger);
            if(isOtp) {
                if(!BackendOps.validateOtp(mCustomer.getMobile_num(), DbConstants.OP_TXN_COMMIT, mTransaction.getCpin(), mEdr, mLogger)) {
                    mLogger.debug("Wrong OTP: "+mTransaction.getCpin()+","+mCustomer.getMobile_num());
                    mValidException = true;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_OTP), "");
                }
                mTransaction.setCpin(DbConstants.TXN_CUSTOMER_OTP_USED);

            } else {
                if (!SecurityHelper.verifyCustPin(mCustomer, mTransaction.getCpin(), mLogger)) {
                    int cnt = BackendUtils.handleWrongAttempt(mCustomerId, mCustomer, DbConstants.USER_TYPE_CUSTOMER,
                            DbConstantsBackend.WRONG_PARAM_TYPE_PIN, DbConstants.OP_TXN_COMMIT, mEdr, mLogger);
                    mValidException = true;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), String.valueOf(cnt));
                }
                mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_USED);
            }
            /*if (!SecurityHelper.verifyCustPin(mCustomer, mTransaction.getCpin(), mLogger)) {
                int cnt = BackendUtils.handleWrongAttempt(mCustomerId, mCustomer, DbConstants.USER_TYPE_CUSTOMER,
                        DbConstantsBackend.WRONG_PARAM_TYPE_PIN, DbConstants.OP_TXN_COMMIT, mEdr, mLogger);
                mValidException = true; // to avoid logging of this exception
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), String.valueOf(cnt));
            } else {
                mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_USED);
            }*/
        } else {
            mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_NOT_USED);
        }

        // check if card required and provided and matches
        /*if(BackendUtils.customerCardRequired(mTransaction)) {
            if(mTransaction.getUsedCardId()!=null && !mTransaction.getUsedCardId().isEmpty()) {
                if(!mTransaction.getUsedCardId().equals(mCustomer.getMembership_card().getCard_id())) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_CARD), "Card Mismatch: " + mCustomerId);
                }
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_CARD), "Card Missing: " + mCustomerId);
            }
        }

        // check that card is not disabled - only if used
        if(mTransaction.getUsedCardId()!=null && !mTransaction.getUsedCardId().isEmpty()) {
            mEdr[BackendConstants.EDR_CUST_CARD_NUM_IDX] = mCustomer.getMembership_card().getCardNum();
            BackendUtils.checkCardForUse(mCustomer.getMembership_card());
        }

        // verify PIN
        if(CommonUtils.customerPinRequired(mMerchant, mTransaction)) {
            //mLogger.debug("Customer PIN is required");
            if (mTransaction.getCpin() != null) {
                if (!SecurityHelper.verifyCustPin(mCustomer, mTransaction.getCpin(), mLogger)) {
                    int cnt = BackendUtils.handleWrongAttempt(mCustomerId, mCustomer, DbConstants.USER_TYPE_CUSTOMER,
                            DbConstantsBackend.WRONG_PARAM_TYPE_PIN, DbConstants.OP_TXN_COMMIT, mEdr, mLogger);
                    mValidException = true; // to avoid logging of this exception
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), String.valueOf(cnt));
                } else {
                    mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_USED);
                }
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), "PIN Missing: " + mCustomerId);
            }
        } else {
            mTransaction.setCpin(DbConstants.TXN_CUSTOMER_PIN_NOT_USED);
        }*/
    }

    /*private Customers updateTxnTables(Customers customer, String mchntTable) {

        String currTables = customer.getTxn_tables();
        if(!currTables.contains(mchntTable)) {
            String newTables = currTables+CommonConstants.CSV_DELIMETER+mchntTable;
            mLogger.debug("Setting new Txn tables for mCustomer: "+newTables+","+currTables);
            customer.setTxn_tables(newTables);
            // update mCustomer object
            return BackendOps.saveCustomer(customer);
        }
        return null;
    }*/

    private void buildAndSendTxnMsgs()
    {
        cl_credit = mTransaction.getCl_credit();
        cb_credit = mTransaction.getCb_credit() + mTransaction.getExtra_cb_credit();
        cl_debit = mTransaction.getCl_debit();
        cl_overdraft = mTransaction.getCl_overdraft();

        // Build Msg Text
        Cashback cashback = mTransaction.getCashback();
        merchantName = mTransaction.getMerchant_name().toUpperCase(Locale.ENGLISH);
        cl_balance = CommonUtils.getAccBalance(cashback);
        SimpleDateFormat sdf = new SimpleDateFormat(CommonConstants.DATE_FORMAT_ONLY_DATE_BACKEND, CommonConstants.MY_LOCALE);
        sdf.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));
        txnDate = sdf.format(mTransaction.getCreate_time());

        String text = buildMsg();
        if(text!=null) {

            // Always send the Push Notification to Customer
            PushNotifier.pushNotification(text,text,mCustomer.getMsgDevId(),mEdr,mLogger);

            // Send SMS only in cases of 'debit' or 'add cash' > configured amounts
            if( cl_debit > BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT
                    || cl_overdraft > 0
                    || cl_credit > BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT
                    ) {
                // Send SMS through HTTP
                SmsHelper.sendSMS(text,mCustomer.getMobile_num(), mEdr, mLogger, true);
            }
        }
    }

    private String buildMsg() {
        String sms=null;

        if(cl_debit> BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT && cl_overdraft <= 0) {
            sms = String.format(SmsConstants.SMS_TXN_DEBIT_CL,merchantName,cl_debit,txnDate,cl_balance);

        } else if(cl_debit <= 0 && cl_overdraft > 0) {
            sms = String.format(SmsConstants.SMS_TXN_DEBIT_OD,merchantName,cl_overdraft,txnDate,cl_balance);

        } else if(cl_debit > 0 && cl_overdraft > 0) {
            sms = String.format(SmsConstants.SMS_TXN_DEBIT_CL_OD,merchantName,cl_debit,cl_overdraft,txnDate,cl_balance);

        } else if(cl_credit>BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT) {
            sms = String.format(SmsConstants.SMS_TXN_CREDIT_CL,merchantName,cl_credit,txnDate,cl_balance);

        }

        /*if(cl_debit>BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT && cb_debit>BackendConstants.SEND_TXN_SMS_CB_MIN_AMOUNT) {
            sms = String.format(SmsConstants.SMS_TXN_DEBIT_CL_CB,merchantName,cl_debit,cb_debit,txnDate,cl_balance,cb_balance);

        } else if(cl_credit> BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT && cb_debit>BackendConstants.SEND_TXN_SMS_CB_MIN_AMOUNT) {
            sms = String.format(SmsConstants.SMS_TXN_CREDIT_CL_DEBIT_CB,merchantName,cl_credit,cb_debit,txnDate,cl_balance,cb_balance);

        } else if(cl_credit>BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT) {
            sms = String.format(SmsConstants.SMS_TXN_CREDIT_CL,merchantName,cl_credit,txnDate,cl_balance,cb_balance);

        } else if(cl_debit> BackendConstants.SEND_TXN_SMS_CL_MIN_AMOUNT) {
            sms = String.format(SmsConstants.SMS_TXN_DEBIT_CL,merchantName,cl_debit,txnDate,cl_balance,cb_balance);

        } else if(cb_debit> BackendConstants.SEND_TXN_SMS_CB_MIN_AMOUNT) {
            sms = String.format(SmsConstants.SMS_TXN_DEBIT_CB,merchantName,cb_debit,txnDate,cl_balance,cb_balance);
        }*/
        return sms;
    }

}
