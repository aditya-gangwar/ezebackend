package in.ezeshop.utilities;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.FilePermission;
import com.backendless.HeadersManager;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.InvocationContext;
import com.backendless.servercode.RunnerContext;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.MyErrorParams;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.*;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.messaging.SmsHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

/**
 * Created by adgangwa on 22-05-2016.
 */
public class BackendUtils {

    private static final SimpleDateFormat mSdfDateTimeFilename = new SimpleDateFormat(CommonConstants.DATE_FORMAT_WITH_TIME_FILENAME, CommonConstants.DATE_LOCALE);
    private static final SimpleDateFormat mSdfTimeDay = new SimpleDateFormat(CommonConstants.DATE_FORMAT_DDMMYY, CommonConstants.DATE_LOCALE);

    /*
     * Password & ID generators
     */
    public static String generateTempPassword() {
        // random alphanumeric string
        Random random = new Random();
        char[] id = new char[CommonConstants.PASSWORD_MIN_LEN];
        for (int i = 0; i < CommonConstants.PASSWORD_MIN_LEN; i++) {
            id[i] = BackendConstants.pwdChars[random.nextInt(BackendConstants.pwdChars.length)];
        }
        return new String(id);
    }

    public static String generateMerchantId(MerchantIdBatches batch, String countryCode, long regCounter) {
        // 8 digit merchant id format:
        // <1-3 digit country code> + <0-2 digit range id> + <2 digit batch id> + <3 digit s.no.>
        int serialNo = (int) (regCounter % BackendConstants.MERCHANT_ID_MAX_SNO_PER_BATCH);
        return countryCode + batch.getRangeBatchId() + String.format("%03d", serialNo);
    }

    public static String generateTxnId(String merchantId) {
        // Txn Id : <7 chars for curr time in secs as Base35> + <6 char for merchant id as Base26> = total 13 chars
        long timeSecs = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long mchntIdLong = Long.parseUnsignedLong(merchantId);
        return Base35.fromBase10(timeSecs, 7) + Base25.fromBase10(mchntIdLong, 6);
    }

    public static String generateLogId() {
        // random alphanumeric string
        Random random = new Random();
        char[] id = new char[BackendConstants.LOG_ID_LEN];
        for (int i = 0; i < BackendConstants.LOG_ID_LEN; i++) {
            id[i] = BackendConstants.pwdChars[random.nextInt(BackendConstants.pwdChars.length)];
        }
        return new String(id);
    }

    /*public static String generateMchntOrderId() {
        mSdfTimeDay.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));
        String day = mSdfTimeDay.format(new Date());
        Long orderCnt =  BackendOps.fetchCounterValue(DbConstantsBackend.ORDER_ID_COUNTER);

        // Order Id : <MO>+<4 chars for curr day in ddMMyy format> + <daily counter>
        return CommonConstants.MCHNT_ORDER_ID_PREFIX + Base35.fromBase10(Long.parseLong(day), 4) + orderCnt.toString();
    }*/


    /*
     * Fetches User by given DB 'objectId'
     * If required, compares that uer type is as expected.
     * Checks for fetched user admin status
     * Set appropriate EDR values
     */
    public static BackendlessUser fetchCurrentBLUser(Integer allowedUserType, String[] edr, MyLogger logger, boolean allChild) {
        if( InvocationContext.getUserToken()==null || InvocationContext.getUserToken().isEmpty() ||
                InvocationContext.getUserId()==null||InvocationContext.getUserId().isEmpty() ) {
            throw new BackendlessException(String.valueOf(ErrorCodes.NOT_LOGGED_IN), "");
        }
        HeadersManager.getInstance().addHeader( HeadersManager.HeadersEnum.USER_TOKEN_KEY, InvocationContext.getUserToken() );
        BackendlessUser user = BackendOps.fetchUserByObjectId(InvocationContext.getUserId(), allChild);
        fetchUser(user, allowedUserType, edr, logger);
        return user;
    }

    public static Object fetchCurrentUser(Integer allowedUserType, String[] edr, MyLogger logger, boolean allChild) {
        //logger.debug("In fetchCurrentUser: "+InvocationContext.getUserToken()+", "+InvocationContext.getUserId());
        if( InvocationContext.getUserToken()==null || InvocationContext.getUserToken().isEmpty() ||
                InvocationContext.getUserId()==null||InvocationContext.getUserId().isEmpty() ) {
            throw new BackendlessException(String.valueOf(ErrorCodes.NOT_LOGGED_IN), "");
        }
        HeadersManager.getInstance().addHeader( HeadersManager.HeadersEnum.USER_TOKEN_KEY, InvocationContext.getUserToken() );
        BackendlessUser user = BackendOps.fetchUserByObjectId(InvocationContext.getUserId(), allChild);
        return fetchUser(user, allowedUserType, edr, logger);
    }

    public static Object fetchUser(String objectId, Integer allowedUserType, String[] edr, MyLogger logger, boolean allChild) {
        BackendlessUser user = BackendOps.fetchUserByObjectId(objectId, allChild);
        return fetchUser(user, allowedUserType, edr, logger);
    }

    private static Object fetchUser(BackendlessUser user, Integer allowedUserType, String[] edr, MyLogger logger) {
        edr[BackendConstants.EDR_USER_ID_IDX] = (String) user.getProperty("user_id");
        int userType = (Integer)user.getProperty("user_type");

        edr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(userType);
        // don't check if allowedUserType == null
        if(allowedUserType!=null && allowedUserType!=userType) {
            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
        }

        switch (userType) {
            case DbConstants.USER_TYPE_MERCHANT:
                Merchants merchant = (Merchants) user.getProperty("merchant");
                edr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();
                logger.setProperties(edr[BackendConstants.EDR_USER_ID_IDX], userType, merchant.getDebugLogs());
                // check if merchant is enabled
                BackendUtils.checkMerchantStatus(merchant, edr, logger);
                logger.debug("Fetched merchant: "+merchant.getAuto_id()+", "+merchant.getCashback_table());
                return merchant;

            //case DbConstants.USER_TYPE_CCNT:
            case DbConstants.USER_TYPE_CC:
            case DbConstants.USER_TYPE_AGENT:
                InternalUser internalUser = (InternalUser) user.getProperty("internalUser");
                edr[BackendConstants.EDR_INTERNAL_USER_ID_IDX] = internalUser.getId();
                logger.setProperties(edr[BackendConstants.EDR_USER_ID_IDX], userType, internalUser.getDebugLogs());
                // check if agent is enabled
                BackendUtils.checkInternalUserStatus(internalUser);
                return internalUser;

            case DbConstants.USER_TYPE_CUSTOMER:
                Customers customer = (Customers) user.getProperty("customer");
                edr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();
                logger.setProperties(edr[BackendConstants.EDR_USER_ID_IDX], userType, customer.getDebugLogs());
                // check if customer is enabled
                BackendUtils.checkCustomerStatus(customer, edr, logger);
                logger.debug("Fetched customer: "+customer.getPrivate_id()+", "+customer.getCashback_table());
                return customer;
        }

        throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Operation not allowed to this user");
    }


    /*
     * Functions to check for User Admin Status
     */
    public static void checkMerchantStatus(Merchants merchant, String[] edr, MyLogger logger) {
        Integer errorCode = null;
        String errorMsg = null;

        switch (merchant.getAdmin_status()) {
            case DbConstants.USER_STATUS_REG_ERROR:
                errorCode = ErrorCodes.NO_SUCH_USER;
                errorMsg = "No such registered user";
                break;
            case DbConstants.USER_STATUS_DISABLED:
            //case DbConstants.USER_STATUS_READY_TO_ACTIVE:
                errorCode = ErrorCodes.USER_ACC_DISABLED;
                errorMsg = "Account is not active";
                break;

            case DbConstants.USER_STATUS_LOCKED:
                // Check if temporary blocked duration is over
                if(!checkMchntStatusExpiry(merchant, MyGlobalSettings.getAccBlockMins(DbConstants.USER_TYPE_MERCHANT), edr, logger)) {
                    // expiry duration is not over yet
                    errorCode = ErrorCodes.USER_ACC_LOCKED;
                    errorMsg = "Account is locked";
                }
                break;
        }

        if(errorCode != null) {
            throw new BackendlessException(errorCode.toString(), errorMsg);
        }
    }

    public static void checkCustomerStatus(Customers customer, String[] edr, MyLogger logger) {
        Integer errorCode = null;
        String errorMsg = null;
        switch(customer.getAdmin_status()) {
            case DbConstants.USER_STATUS_REG_ERROR:
                errorCode = ErrorCodes.NO_SUCH_USER;
                errorMsg = "No such registered user";
                break;
            case DbConstants.USER_STATUS_DISABLED:
            //case DbConstants.USER_STATUS_READY_TO_ACTIVE:
                errorCode = ErrorCodes.USER_ACC_DISABLED;
                errorMsg = "Account is not active";
                break;

            case DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY:
                // Credit txns and data view is allowed when in this mode
                // Debit txns and profile/setting changes (like pin, passwd change etc) are not allowed
                // As this fx. does not have all this info to decide - so it will not raise any exception
                // calling fx. should check it itself for this status
                // However, if 'restricted duration' is passed, the status will be changed to 'Enabled' here only
                checkCustStatusExpiry(customer, MyGlobalSettings.getCustAccLimitModeMins(), edr, logger);
                break;

            case DbConstants.USER_STATUS_LOCKED:
                // Check if temporary blocked duration is over
                if(!checkCustStatusExpiry(customer, MyGlobalSettings.getAccBlockMins(DbConstants.USER_TYPE_CUSTOMER), edr, logger)) {
                    // expiry duration is not over yet
                    errorCode = ErrorCodes.USER_ACC_LOCKED;
                    errorMsg = "Account is locked";
                }
                break;

            case DbConstants.USER_STATUS_ACTIVE:
                break;

            default:
                errorCode = ErrorCodes.NO_SUCH_USER;
                errorMsg = "Invalid customer state: "+customer.getAdmin_status();
                edr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                break;
        }
        if(errorCode != null) {
            throw new BackendlessException(errorCode.toString(), errorMsg);
        }
    }

    public static void checkInternalUserStatus(InternalUser agent) {
        switch (agent.getAdmin_status()) {
            case DbConstants.USER_STATUS_DISABLED:
                throw new BackendlessException(String.valueOf(ErrorCodes.USER_ACC_DISABLED), "");
            case DbConstants.USER_STATUS_LOCKED:
                throw new BackendlessException(String.valueOf(ErrorCodes.USER_ACC_LOCKED), "");
        }
    }

    private static boolean checkCustStatusExpiry(Customers customer, int mins, String[] edr, MyLogger logger) {
        Date blockedTime = customer.getStatus_update_time();
        if (blockedTime != null && blockedTime.getTime() > 0) {
            // check for temp blocking duration expiry
            Date now = new Date();
            long timeDiff = now.getTime() - blockedTime.getTime();
            long allowedDuration = mins * CommonConstants.MILLISECS_IN_MINUTE;

            if (timeDiff > allowedDuration) {
                try {
                    setCustomerStatus(customer, DbConstants.USER_STATUS_ACTIVE, DbConstantsBackend.ENABLED_ACTIVE, edr, logger);
                } catch (Exception e) {
                    // Failed to auto unlock the account
                    // Ignore for now - and let the operation proceed - but raise alarm for manual correction
                    edr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    edr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_ACC_STATUS_CHANGE_FAILED;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean checkMchntStatusExpiry(Merchants merchant, int mins, String[] edr, MyLogger logger) {
        Date blockedTime = merchant.getStatus_update_time();
        if (blockedTime != null && blockedTime.getTime() > 0) {
            // check for temp blocking duration expiry
            Date now = new Date();
            long timeDiff = now.getTime() - blockedTime.getTime();
            long allowedDuration = mins * CommonConstants.MILLISECS_IN_MINUTE;

            if (timeDiff > allowedDuration) {
                try {
                    setMerchantStatus(merchant, DbConstants.USER_STATUS_ACTIVE, DbConstantsBackend.ENABLED_ACTIVE, edr, logger);
                } catch (Exception e) {
                    // Failed to auto unlock the account
                    // Ignore for now - and let the operation proceed - but raise alarm for manual correction
                    edr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    edr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_ACC_STATUS_CHANGE_FAILED;
                }
            } else {
                return false;
            }
        }
        return true;
    }


    /*
     * Functions to check Membership Card status
     */
    /*
    public static void checkCardForUse(CustomerCards card) {
        switch(card.getStatus()) {
            case DbConstants.CUSTOMER_CARD_STATUS_DISABLED:
                throw new BackendlessException(String.valueOf(ErrorCodes.CARD_DISABLED), "");

            case DbConstants.CUSTOMER_CARD_STATUS_FOR_PRINT:
            //case DbConstants.CUSTOMER_CARD_STATUS_WITH_AGENT:
            case DbConstants.CUSTOMER_CARD_STATUS_NEW:
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_CARD), "");

            case DbConstants.CUSTOMER_CARD_STATUS_WITH_MERCHANT:
                throw new BackendlessException(String.valueOf(ErrorCodes.CARD_NOT_REG_WITH_CUST), "");
        }
    }

    public static void checkCardForAllocation(CustomerCards card, String merchantId, String[] edr, MyLogger logger) {
        switch(card.getStatus()) {
            case DbConstants.CUSTOMER_CARD_STATUS_ACTIVE:
                throw new BackendlessException(String.valueOf(ErrorCodes.CARD_ALREADY_IN_USE), "");

            case DbConstants.CUSTOMER_CARD_STATUS_DISABLED:
                throw new BackendlessException(String.valueOf(ErrorCodes.CARD_DISABLED), "");

            //case DbConstants.CUSTOMER_CARD_STATUS_WITH_AGENT:
            case DbConstants.CUSTOMER_CARD_STATUS_NEW:
            case DbConstants.CUSTOMER_CARD_STATUS_FOR_PRINT:
                edr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_CARD), "");

            case DbConstants.CUSTOMER_CARD_STATUS_WITH_MERCHANT:
                break;
        }
    }*/


    /*
     * Handles 'wrong attempt' by any type of user
     */
    public static int handleWrongAttempt(String userId, Object userObject, int userType,
                                          String wrongParamType, String opCode, String[] edr, MyLogger logger) {
        // check similar wrong attempts today
        int cnt = BackendOps.getWrongAttemptCnt(userId, wrongParamType);
        int confMaxAttempts = MyGlobalSettings.getWrongAttemptLimit(userType);

        logger.debug("In handleWrongAttempt: "+cnt+", "+confMaxAttempts);

        if(cnt >= confMaxAttempts) {
            // lock user account - if 'max attempts per day' crossed
            try {
                switch(userType) {
                    case DbConstants.USER_TYPE_MERCHANT:
                        // if already locked - simply return and dont throw 'attempt exceed' exception
                        // This way
                        setMerchantStatus((Merchants)userObject, DbConstants.USER_STATUS_LOCKED,
                                DbConstantsBackend.paramTypeToAccLockedReason.get(wrongParamType), edr, logger);
                        break;
                    case DbConstants.USER_TYPE_CUSTOMER:
                        setCustomerStatus((Customers) userObject, DbConstants.USER_STATUS_LOCKED,
                                DbConstantsBackend.paramTypeToAccLockedReason.get(wrongParamType), edr, logger);
                        break;
                    case DbConstants.USER_TYPE_CC:
                    case DbConstants.USER_TYPE_AGENT:
                    //case DbConstants.USER_TYPE_CCNT:
                        setAgentStatus((InternalUser) userObject, DbConstants.USER_STATUS_LOCKED,
                                DbConstantsBackend.paramTypeToAccLockedReason.get(wrongParamType), edr, logger);
                        break;
                }
            } catch (Exception e) {
                // ignore the failure to lock the account
                logger.error("Exception in handleWrongAttempt: "+e.toString());
                logger.error(stackTraceStr(e));
                edr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_ACC_STATUS_CHANGE_FAILED;
            }
            // throw max attempt limit reached exception
            throw new BackendlessException(String.valueOf(ErrorCodes.FAILED_ATTEMPT_LIMIT_RCHD), "");

        } else {
            // limit not crossed yet - add row for this occurance
            WrongAttempts attempt = new WrongAttempts();
            attempt.setUser_id(userId);
            attempt.setParam_type(wrongParamType);
            attempt.setOpCode(opCode);
            //attempt.setAttempt_cnt(0);
            attempt.setUser_type(userType);
            try {
                BackendOps.saveWrongAttempt(attempt);
            } catch(Exception e) {
                // ignore exception
                logger.error("Exception in handleWrongAttempt: "+e.toString());
                logger.error(stackTraceStr(e));
                edr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_WRONG_ATTEMPT_SAVE_FAILED;
            }

            // return available attempts
            return (confMaxAttempts - cnt);
        }
    }


    /*
     * Sets user admin status to given value, and saves in DB
     * Sends SMS only if given stats is DISABLED or LOCKED
     */
    public static Merchants setMerchantStatus(Merchants merchant, int status, String reason, String[] edr, MyLogger logger) {
        if(status == merchant.getAdmin_status()) {
            return merchant;
        }
        // update merchant account
        merchant.setAdmin_status(status);
        merchant.setStatus_reason(reason);
        merchant.setStatus_update_time(new Date());
        merchant = BackendOps.updateMerchant(merchant);

        // Generate SMS to inform the same - only when acc is locked or disabled
        String smsText = null;
        if(status==DbConstants.USER_STATUS_LOCKED) {
            smsText = getAccLockedSmsText(merchant.getAuto_id(), DbConstants.USER_TYPE_MERCHANT, reason);
            SmsHelper.sendSMS(smsText, merchant.getMobile_num(), edr, logger, true);

        } else if(status==DbConstants.USER_STATUS_DISABLED) {
            smsText = String.format(SmsConstants.SMS_ACCOUNT_DISABLE, CommonUtils.getHalfVisibleStr(merchant.getAuto_id()));
            SmsHelper.sendSMS(smsText, merchant.getMobile_num(), edr, logger, true);
        }

        return merchant;
    }

    public static void setCustomerStatus(Customers customer, int status, String reason, String[] edr, MyLogger logger) {
        if(status == customer.getAdmin_status()) {
            return;
        }
        // update customer account
        customer.setAdmin_status(status);
        customer.setStatus_reason(reason);
        customer.setStatus_update_time(new Date());
        customer = BackendOps.updateCustomer(customer);

        // Generate SMS to inform the same - only when acc is locked or disabled
        String smsText = null;
        if(status==DbConstants.USER_STATUS_LOCKED) {
            smsText = getAccLockedSmsText(customer.getMobile_num(), DbConstants.USER_TYPE_MERCHANT, reason);
            SmsHelper.sendSMS(smsText, customer.getMobile_num(), edr, logger, true);
        } else if(status==DbConstants.USER_STATUS_DISABLED) {
            smsText = String.format(SmsConstants.SMS_ACCOUNT_DISABLE, CommonUtils.getHalfVisibleMobileNum(customer.getMobile_num()));
            SmsHelper.sendSMS(smsText, customer.getMobile_num(), edr, logger, true);
        }
    }

    public static void setAgentStatus(InternalUser agent, int status, String reason, String[] edr, MyLogger logger) {
        if(status == agent.getAdmin_status()) {
            return;
        }
        // update account
        agent.setAdmin_remarks("Last status was "+DbConstants.userStatusDesc[agent.getAdmin_status()]);
        agent.setAdmin_status(status);
        agent.setStatus_reason(reason);
        agent = BackendOps.updateInternalUser(agent);

        // Generate SMS to inform the same - only when acc is locked or disabled
        String smsText = null;
        if(status==DbConstants.USER_STATUS_LOCKED) {
            smsText = getAccLockedSmsText(agent.getId(), DbConstants.USER_TYPE_MERCHANT, reason);
            SmsHelper.sendSMS(smsText, agent.getMobile_num(), edr, logger, true);
        } else if(status==DbConstants.USER_STATUS_DISABLED) {
            smsText = String.format(SmsConstants.SMS_ACCOUNT_DISABLE, CommonUtils.getHalfVisibleStr(agent.getId()));
            SmsHelper.sendSMS(smsText, agent.getMobile_num(), edr, logger, true);
        }
    }

    private static String getAccLockedSmsText(String userId, int userType, String statusReason) {

        if(userType==DbConstants.USER_TYPE_AGENT ||
                userType==DbConstants.USER_TYPE_CC
                //|| userType==DbConstants.USER_TYPE_CCNT
                ) {
            return String.format(SmsConstants.SMS_ACCOUNT_LOCKED_INTERNAL, CommonUtils.getHalfVisibleStr(userId));
        }

        switch(statusReason) {
            case DbConstantsBackend.LOCKED_WRONG_PASSWORD_LIMIT_RCHD:
                return String.format(SmsConstants.SMS_ACCOUNT_LOCKED_PASSWORD, CommonUtils.getHalfVisibleStr(userId), MyGlobalSettings.getAccBlockMins(userType));
            case DbConstantsBackend.LOCKED_WRONG_PIN_LIMIT_RCHD:
                return String.format(SmsConstants.SMS_ACCOUNT_LOCKED_PIN, CommonUtils.getHalfVisibleStr(userId), MyGlobalSettings.getAccBlockMins(userType));
            case DbConstantsBackend.LOCKED_WRONG_VERIFICATION_LIMIT_RCHD:
                return String.format(SmsConstants.SMS_ACCOUNT_LOCKED_VERIFY_FAILED, CommonUtils.getHalfVisibleStr(userId), MyGlobalSettings.getAccBlockMins(userType));
        }

        return null;
    }


    /*
     * Get User ID type - depending upon the length
     */
    public static int getMerchantIdType(String id) {
        switch (id.length()) {
            case CommonConstants.MOBILE_NUM_LENGTH:
                return CommonConstants.ID_TYPE_MOBILE;
            case CommonConstants.MERCHANT_ID_LEN:
                return CommonConstants.ID_TYPE_AUTO;
            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid Merchant ID: "+id);
        }
    }

    public static int getCustomerIdType(String id) {
        switch (id.length()) {
            case CommonConstants.MOBILE_NUM_LENGTH:
                return CommonConstants.ID_TYPE_MOBILE;
            case CommonConstants.CUSTOMER_INTERNAL_ID_LEN:
                return CommonConstants.ID_TYPE_AUTO;
            /*case CommonConstants.CUSTOMER_CARDID_LEN:
                return CommonConstants.ID_TYPE_CARD;*/
            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid Customer ID: "+id);
        }
    }

    /*public static boolean isCardNum(String id) {
        return (id.length()==CommonConstants.CUSTOMER_CARDID_LEN);
    }*/

    /*
     * Get User type - depending upon the length of ID
     */
    public static int getUserType(String userdId) {
        switch(userdId.length()) {
            case CommonConstants.MERCHANT_ID_LEN:
                return DbConstants.USER_TYPE_MERCHANT;
            case CommonConstants.INTERNAL_USER_ID_LEN:
                if(userdId.startsWith(CommonConstants.PREFIX_AGENT_ID)) {
                    return DbConstants.USER_TYPE_AGENT;
                } else if(userdId.startsWith(CommonConstants.PREFIX_CC_ID)) {
                    return DbConstants.USER_TYPE_CC;
                } /*else if(userdId.startsWith(CommonConstants.PREFIX_CCNT_ID)) {
                    return DbConstants.USER_TYPE_CCNT;
                } */else {
                    throw new BackendlessException(String.valueOf(ErrorCodes.USER_WRONG_ID_PASSWD),"Invalid user type for id: "+userdId);
                }
            case CommonConstants.CUSTOMER_INTERNAL_ID_LEN:
            case CommonConstants.MOBILE_NUM_LENGTH:
                return DbConstants.USER_TYPE_CUSTOMER;
            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER),"Invalid user type for id: "+userdId);
        }
    }

    /*
     * Checks if for particular transaction - Card/PIN is required
     * The logic in these fxs. should match that in App
     */
    /*public static boolean customerPinRequired(Merchants merchant, Transaction txn) {
        int cl_credit_threshold = (merchant.getCl_credit_limit_for_pin()<0) ? MyGlobalSettings.CL_CREDIT_LIMIT_FOR_PIN : merchant.getCl_credit_limit_for_pin();
        int cl_debit_threshold = (merchant.getCl_debit_limit_for_pin()<0) ? MyGlobalSettings.CL_DEBIT_LIMIT_FOR_PIN : merchant.getCl_debit_limit_for_pin();
        int cb_debit_threshold = (merchant.getCb_debit_limit_for_pin()<0) ? MyGlobalSettings.CB_DEBIT_LIMIT_FOR_PIN : merchant.getCb_debit_limit_for_pin();

        int higher_debit_threshold = Math.max(cl_debit_threshold, cb_debit_threshold);

        return (txn.getCl_credit() > cl_credit_threshold ||
                txn.getCl_debit() > cl_debit_threshold ||
                txn.getCb_debit() > cb_debit_threshold ||
                (txn.getCl_debit()+txn.getCb_debit()) > higher_debit_threshold);
    }*/

    /*public static boolean customerCardRequired(Transaction txn) {
        return ( (txn.getCl_debit()>0 && MyGlobalSettings.getCardReqAccDebit())
                || (txn.getCb_debit()>0 && MyGlobalSettings.getCardReqCbRedeem())
                //|| txn.getCancelTime()!=null
        );
    }*/

    /*
     * Functions to handle exceptions
     */
    public static BackendlessException getNewException(BackendlessException be) {
        // to be removed once issue is fixed on backendless side
        // currently for 'custom error code' getCode() always returns 0 - from event handlers
        /*return new BackendlessException(be.getCode(),
                CommonConstants.PREFIX_ERROR_CODE_AS_MSG+CommonConstants.SPECIAL_DELIMETER +
                        be.getCode()+CommonConstants.SPECIAL_DELIMETER +
                        be.getMessage());*/

        int attempts = -1;
        try {
            attempts = Integer.parseInt(be.getMessage()); //will also get checked if msg is valid integer
        } catch(Exception e) {
            // ignore
        }

        MyErrorParams params = new MyErrorParams(Integer.parseInt(be.getCode()),attempts,-1,"");
        return new BackendlessException(be.getCode(),params.toCsvString());
    }

    public static void handleException(Throwable e, boolean validException, MyLogger logger, String[] edr) {
        try {

            if(e instanceof Error) {
                // not exception, but JVM error
                edr[BackendConstants.EDR_EXP_EXPECTED] = String.valueOf(false);
                edr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
                logger.fatal("Exception in " + edr[BackendConstants.EDR_API_NAME_IDX] + ": " + e.toString());
                logger.fatal(stackTraceStr(e));

            } else {

                // We are not able to mark all validExceptions - so checking again here
                if (!validException) {
                    if (e instanceof BackendlessException) {
                        // Below exceptions are considered valid and not logged
                        int errorCode = Integer.valueOf(((BackendlessException) e).getCode());
                        if (errorCode == ErrorCodes.FAILED_ATTEMPT_LIMIT_RCHD ||
                                errorCode == ErrorCodes.USER_ACC_LOCKED ||
                                errorCode == ErrorCodes.USER_ACC_DISABLED) {
                            validException = true;
                        }
                    }
                }

                edr[BackendConstants.EDR_EXP_EXPECTED] = String.valueOf(validException);

                if (validException) {
                    edr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
                    logger.debug(stackTraceStr(e));
                } else {
                    edr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
                    logger.error("Exception in " + edr[BackendConstants.EDR_API_NAME_IDX] + ": " + e.toString());
                    logger.error(stackTraceStr(e));
                }

                //edr[BackendConstants.EDR_EXP_MSG_IDX] = e.getMessage().replaceAll(",", BackendConstants.BACKEND_EDR_SUB_DELIMETER);
                edr[BackendConstants.EDR_EXP_MSG_IDX] = e.getMessage();
                if (e instanceof BackendlessException) {
                    String errCode = ((BackendlessException) e).getCode();
                    edr[BackendConstants.EDR_EXP_CODE_IDX] = errCode;
                    edr[BackendConstants.EDR_EXP_CODE_NAME] = ErrorCodes.appErrorNames.get(errCode);
                } else {
                    edr[BackendConstants.EDR_EXP_CODE_NAME] = e.getClass().getSimpleName();
                    edr[BackendConstants.EDR_EXP_CODE_IDX] = edr[BackendConstants.EDR_EXP_CODE_NAME];
                }
            }

        } catch(Throwable ex) {
            logger.fatal("Exception in handleException: " + ex.toString());
            logger.fatal(stackTraceStr(ex));
        }
    }

    public static void finalHandling(long startTime, MyLogger logger, String[] edr) {
        try {
            if(edr!=null) {
                long endTime = System.currentTimeMillis();
                long execTime = endTime - startTime;
                edr[BackendConstants.EDR_END_TIME_IDX] = String.valueOf(endTime);
                edr[BackendConstants.EDR_EXEC_DURATION_IDX] = String.valueOf(execTime);
                //logger.debug(edr[BackendConstants.EDR_USER_TYPE_IDX]);
                logger.edr(edr);
            }
            //logger.flush();
        } catch(Throwable e) {
            logger.fatal("Exception in finalHandling: " + e.toString());
            logger.fatal(stackTraceStr(e));
        }
    }

    public static String stackTraceStr(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e1 : e.getStackTrace()) {
            sb.append("\t at ").append(e1.toString()).append(CommonConstants.NEWLINE_SEP);
        }
        return sb.toString();
    }

    public static void writeOpNotAllowedEdr(MyLogger logger, String[] mEdr) {
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(System.currentTimeMillis());
        mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
        mEdr[BackendConstants.EDR_EXP_CODE_IDX] = String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED);
        mEdr[BackendConstants.EDR_EXP_MSG_IDX] = mEdr[BackendConstants.EDR_API_NAME_IDX]+" not allowed.";
        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
        logger.edr(mEdr);
        //logger.flush();
    }


    /*
     * Other Miscellaneous functions
     */
    public static boolean isTrustedDevice(String deviceId, List<MerchantDevice> trustedDevices) {

        if(BackendConstants.TESTING_SKIP_DEVICEID_CHECK) {
            return true;
        }
        //List<MerchantDevice> trustedDevices = merchant.getTrusted_devices();
        if (trustedDevices != null &&
                (deviceId != null && !deviceId.isEmpty())) {
            for (MerchantDevice device : trustedDevices) {
                //if (device.getDevice_id().equals(deviceId)) {
                if (SecurityHelper.verifyDeviceId(device, deviceId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getMchntDpFilename(String mchntId) {
        return "dp_" + String.valueOf(System.currentTimeMillis()) + "_" + mchntId + "."+CommonConstants.PHOTO_FILE_FORMAT;
    }

    /*public static String getTxnImgFilename(String txnId) {
        return CommonConstants.PREFIX_TXN_IMG_FILE_NAME +txnId+"."+CommonConstants.PHOTO_FILE_FORMAT;
    }

    public static String getTxnCancelImgFilename(String txnId) {
        return CommonConstants.PREFIX_TXN_CANCEL_IMG_FILE_NAME +txnId+"."+CommonConstants.PHOTO_FILE_FORMAT;
    }

    public static String getCustOpImgFilename(String opCode, String custPrivateId) {
        mSdfDateTimeFilename.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));
        String time = mSdfDateTimeFilename.format(new Date());
        String filename = opCode+"_"+custPrivateId+"_"+time+"."+CommonConstants.PHOTO_FILE_FORMAT;
        return filename.replace(" ","_");
    }*/

    public static void printCtxtInfo(MyLogger logger, RunnerContext context) {
        logger.debug("Headers: "+HeadersManager.getInstance().getHeaders().toString());
        if(context!=null) {
            logger.debug("RunnerContext: " + context.toString());
        }
        List<String> roles = Backendless.UserService.getUserRoles();
        logger.debug("Roles: "+roles.toString());
    }

    public static void printCtxtInfo(MyLogger logger) {
        logger.debug("Headers: "+HeadersManager.getInstance().getHeaders().toString());
        //logger.debug("RunnerContext: "+context.toString());
        logger.debug( "InvocationContext: "+ InvocationContext.asString() );
        List<String> roles = Backendless.UserService.getUserRoles();
        logger.debug("Roles: "+roles.toString());
    }

    // Register Merchant
    public static String registerMerchant(Merchants merchant, String agentId, MyLogger mLogger, String[] mEdr) {
        boolean regDone = false;
        String merchantId = null;

        try {
            // Fetch city
            Cities city = BackendOps.fetchCity(merchant.getAddress().getCity());

            // get open merchant id batch
            String countryCode = city.getCountryCode();
            String batchTableName = DbConstantsBackend.MERCHANT_ID_BATCH_TABLE_NAME+countryCode;
            String whereClause = "status = '"+DbConstantsBackend.BATCH_STATUS_OPEN +"'";
            MerchantIdBatches batch = BackendOps.fetchMerchantIdBatch(batchTableName,whereClause);
            if(batch == null) {
                throw new BackendlessException(String.valueOf(ErrorCodes.MERCHANT_ID_RANGE_ERROR),
                        "No open merchant id batch available: "+batchTableName+","+whereClause);
            }

            // get merchant counter value and use the same to generate merchant id
            Long merchantCnt =  BackendOps.fetchCounterValue(DbConstantsBackend.MERCHANT_ID_COUNTER);
            mLogger.debug("Fetched merchant cnt: "+merchantCnt);
            // generate merchant id
            merchantId = BackendUtils.generateMerchantId(batch, countryCode, merchantCnt);
            mLogger.debug("Generated merchant id: "+merchantId);

            // rename mchnt dp to include 'merchant id'
            if(!merchant.getDisplayImage().isEmpty()) {
                String currFilePath = CommonConstants.MERCHANT_DISPLAY_IMAGES_DIR + merchant.getDisplayImage();
                String newName = BackendUtils.getMchntDpFilename(merchantId);
                mLogger.debug(merchant.getDisplayImage() + "," + newName + "," + currFilePath);
                BackendOps.renameFile(currFilePath, newName);
                merchant.setDisplayImage(newName);
                mLogger.debug("File rename done");
            }

            // set or update other fields
            merchant.setAuto_id(merchantId);
            merchant.setAdmin_status(DbConstants.USER_STATUS_ACTIVE);
            merchant.setStatus_reason(DbConstantsBackend.ENABLED_ACTIVE);
            merchant.setStatus_update_time(new Date());
            merchant.setLastRenewDate(new Date());
            //merchant.setMobile_num(merchant.getMobile_num());
            merchant.setFirst_login_ok(false);
            merchant.setAgentId(agentId);
            merchant.setCl_credit_limit_for_pin(-1);
            merchant.setCl_debit_limit_for_pin(-1);
            //merchant.setCb_debit_limit_for_pin(-1);
            // set cashback and transaction table names
            merchant.setCashback_table(DbConstantsBackend.CASHBACK_TABLE_NAME + city.getCbTableCode());
            BackendOps.describeTable(merchant.getCashback_table()); // just to check that the table exists
            merchant.setTxn_table(DbConstantsBackend.TRANSACTION_TABLE_NAME + city.getCbTableCode());
            BackendOps.describeTable(merchant.getTxn_table()); // just to check that the table exists

            // generate and set password
            String pwd = BackendUtils.generateTempPassword();
            mLogger.debug("Generated passwd: "+pwd);

            BackendlessUser user = new BackendlessUser();
            user.setProperty("user_id", merchantId);
            user.setPassword(pwd);
            user.setProperty("user_type", DbConstants.USER_TYPE_MERCHANT);
            user.setProperty("merchant", merchant);

            user = BackendOps.registerUser(user);
            regDone = true;
            mLogger.debug("Register success");
            // register successful - can write to edr now
            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();

            try {
                BackendOps.assignRole(merchantId, BackendConstants.ROLE_MERCHANT);

            } catch(Exception e) {
                mLogger.fatal("Failed to assign role to merchant user: "+merchantId+","+e.toString());
                rollbackRegister(merchantId, mLogger, mEdr);
                throw e;
            }

            // create directory for 'txnCsv' files
            String fileDir = null;
            String filePath = null;
            try {
                fileDir = CommonUtils.getMerchantTxnDir(merchantId);
                filePath = fileDir + CommonConstants.FILE_PATH_SEPERATOR+BackendConstants.DUMMY_FILENAME;
                // saving dummy files to create parent directories
                Backendless.Files.saveFile(filePath, BackendConstants.DUMMY_DATA.getBytes("UTF-8"), true);
                // Give this merchant permissions for this directory
                FilePermission.READ.grantForUser( user.getObjectId(), fileDir);
                //FilePermission.DELETE.grantForUser( user.getObjectId(), fileDir);
                //FilePermission.WRITE.grantForUser( user.getObjectId(), fileDir);
                mLogger.debug("Saved dummy txn csv file: " + filePath);

            } catch(Exception e) {
                mLogger.fatal("Failed to create merchant directories: "+merchantId+","+e.toString());
                rollbackRegister(merchantId, mLogger, mEdr);
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), e.toString());
            }

            // send SMS with user id
            String smsText = String.format(SmsConstants.SMS_MERCHANT_ID_FIRST, merchantId);
            SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);

            return merchantId;

        } catch(Exception e) {
            if(merchantId!=null && !merchantId.isEmpty() && regDone) {
                rollbackRegister(merchantId, mLogger, mEdr);
                //BackendOps.decrementCounterValue(DbConstantsBackend.MERCHANT_ID_COUNTER);
            }
            throw e;
        }
    }
    private static void rollbackRegister(String mchntId, MyLogger mLogger, String[] mEdr) {
        // TODO: add as 'Major' alarm - user to be removed later manually
        // rollback to not-usable state
        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
        try {
            Merchants merchant = BackendOps.getMerchant(mchntId, false, false);
            BackendUtils.setMerchantStatus(merchant, DbConstants.USER_STATUS_REG_ERROR, DbConstantsBackend.REG_ERROR_REG_FAILED,
                    mEdr, mLogger);
        } catch(Exception ex) {
            // ignore any exception
            mLogger.fatal("registerMerchant: Merchant Rollback failed: "+ex.toString());
            mLogger.error(BackendUtils.stackTraceStr(ex));
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
        }
    }


    public static void initAll() {

        MyGlobalSettings.initSync(MyGlobalSettings.RunMode.backend);

        // Table to class mapping - for backendless
        //Backendless.Data.mapTableToClass("CustomerCards", CustomerCards.class);
        //Backendless.Data.mapTableToClass("CustomerCardsNew", CustomerCardsNew.class);
        Backendless.Data.mapTableToClass("Customers", Customers.class);
        Backendless.Data.mapTableToClass("Merchants", Merchants.class);
        Backendless.Data.mapTableToClass("CustomerOps", CustomerOps.class);
        Backendless.Data.mapTableToClass("AllOtp", AllOtp.class);
        Backendless.Data.mapTableToClass("Counters", Counters.class);
        Backendless.Data.mapTableToClass("MerchantOps", MerchantOps.class);
        Backendless.Data.mapTableToClass("WrongAttempts", WrongAttempts.class);
        Backendless.Data.mapTableToClass("MerchantDevice", MerchantDevice.class);
        Backendless.Data.mapTableToClass("InternalUser", InternalUser.class);
        Backendless.Data.mapTableToClass("BusinessCategories", BusinessCategories.class);
        Backendless.Data.mapTableToClass("Address", Address.class);
        Backendless.Data.mapTableToClass("Cities", Cities.class);
        //Backendless.Data.mapTableToClass("MerchantOrders", MerchantOrders.class);

        //Backendless.Data.mapTableToClass("MerchantIdBatches1", MerchantIdBatches.class);
        //Backendless.Data.mapTableToClass("MerchantIdBatches99", MerchantIdBatches.class);

        /*Backendless.Data.mapTableToClass( "Transaction1", Transaction.class );
        Backendless.Data.mapTableToClass( "Cashback1", Cashback.class );
        Backendless.Data.mapTableToClass( "Transaction2", Transaction.class );
        Backendless.Data.mapTableToClass( "Cashback2", Cashback.class );
        Backendless.Data.mapTableToClass( "Transaction3", Transaction.class );
        Backendless.Data.mapTableToClass( "Cashback3", Cashback.class );
        Backendless.Data.mapTableToClass( "Transaction4", Transaction.class );
        Backendless.Data.mapTableToClass( "Cashback4", Cashback.class );
        Backendless.Data.mapTableToClass( "Transaction5", Transaction.class );
        Backendless.Data.mapTableToClass( "Cashback5", Cashback.class );
        Backendless.Data.mapTableToClass( "Transaction6", Transaction.class );
        Backendless.Data.mapTableToClass( "Cashback6", Cashback.class );*/
    }

}
