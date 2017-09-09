package in.ezeshop.constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by adgangwa on 10-08-2016.
 */
public class DbConstantsBackend {

    // Table names - for which class name is not same
    public static String CASHBACK_TABLE_NAME = "Cashback";
    public static String TRANSACTION_TABLE_NAME = "Transaction";
    public static String MERCHANT_ID_BATCH_TABLE_NAME = "MerchantIdBatches";
    public static String CARD_ID_BATCH_TABLE_NAME = "CardIdBatches";
    public static String WRONG_ATTEMPTS_TABLE_NAME = "WrongAttempts";
    public static String MCHNT_OPS_TABLE_NAME = "MerchantOps";
    public static String CUST_OPS_TABLE_NAME = "CustomerOps";

    public static String DUMMY_MCHNT_COUNTRY_CODE = "9";

    // Number of Transaction tables currently in DB
    public static int TRANSACTION_TABLE_CNT = 1;

    // Account locked and other reasons
    // Disable specific reason defined in string.xml of apps
    // As such - 'reason' is free text field in all tables
    // However, should be set with care - as it is shown 'as it is' to the user
    public static final String ENABLED_ACTIVE = "enabled";
    public static final String REG_ERROR_REG_FAILED = "Error in registration";

    public static final String LOCKED_WRONG_PASSWORD_LIMIT_RCHD = "Multiple Wrong Password entered";
    public static final String LOCKED_WRONG_PIN_LIMIT_RCHD = "Multiple Wrong PIN entered";
    public static final String LOCKED_WRONG_VERIFICATION_LIMIT_RCHD = "Multiple failure in requested operation verification";
    //public static final String LOCKED_CHANGE_PASSWORD_ATTEMPT_LIMIT_RCHD = "Too many failed Change Password attempts";
    //public static final String LOCKED_FORGOT_USERID_ATTEMPT_LIMIT_RCHD = "Too many failed Forgot UserID attempts";
    //public static final String LOCKED_FORGOT_PIN_ATTEMPT_LIMIT_RCHD = "Too many failed Forgot PIN attempts";
    //public static final String LOCKED_CHANGE_PIN_ATTEMPT_LIMIT_RCHD = "Too many failed Change PIN attempts";

    /*
     * MerchantOps and CustomerOps table
     */
    // 'status' column values
    public static final String USER_OP_STATUS_PENDING = "Pending";
    public static final String USER_OP_STATUS_COMPLETE = "Completed";
    public static final String USER_OP_STATUS_ERROR = "Failed";
    // 'initiatedBy' column values
    public static final String USER_OP_INITBY_MCHNT = "Merchant";
    public static final String USER_OP_INITBY_CUSTOMER = "Customer";
    public static final String USER_OP_INITBY_ADMIN = "MyeCash Admin";
    // 'initiatedVia' column values - valid when initiated by merchant
    public static final String USER_OP_INITVIA_APP = "App";
    public static final String USER_OP_INITVIA_CC = "Call to Customer Care";
    public static final String USER_OP_INITVIA_IVR = "Call to IVR";
    public static final String USER_OP_INITVIA_MANUAL = "Manual application";


    /*
     * Counters
     */
    public static final String CUSTOMER_ID_COUNTER = "customer_id";
    public static final String MERCHANT_ID_COUNTER = "merchant_id";
    public static final String ORDER_ID_COUNTER = "order_id";

    /*
     * WrongAttempts table
     */
    // wrong parameter types
    public static final String WRONG_PARAM_TYPE_PASSWD = "Password";
    public static final String WRONG_PARAM_TYPE_PIN = "PIN";
    public static final String WRONG_PARAM_TYPE_NAME = "Name";
    public static final String WRONG_PARAM_TYPE_DOB = "DateOfBirth";
    public static final String WRONG_PARAM_TYPE_MOBILE = "MobileNumber";

    public static final Map<String, String> paramTypeToAccLockedReason;
    static {
        Map<String, String> aMap = new HashMap<>(10);

        aMap.put(WRONG_PARAM_TYPE_PASSWD, LOCKED_WRONG_PASSWORD_LIMIT_RCHD);
        aMap.put(WRONG_PARAM_TYPE_PIN, LOCKED_WRONG_PIN_LIMIT_RCHD);
        aMap.put(WRONG_PARAM_TYPE_NAME, LOCKED_WRONG_VERIFICATION_LIMIT_RCHD);
        aMap.put(WRONG_PARAM_TYPE_DOB, LOCKED_WRONG_VERIFICATION_LIMIT_RCHD);
        aMap.put(WRONG_PARAM_TYPE_MOBILE, LOCKED_WRONG_VERIFICATION_LIMIT_RCHD);
        //aMap.put(ATTEMPT_TYPE_PASSWORD_RESET, LOCKED_WRONG_VERIFICATION_LIMIT_RCHD);
        //aMap.put(ATTEMPT_TYPE_PASSWORD_CHANGE, LOCKED_CHANGE_PASSWORD_ATTEMPT_LIMIT_RCHD);
        //aMap.put(ATTEMPT_TYPE_PIN_RESET, LOCKED_FORGOT_PIN_ATTEMPT_LIMIT_RCHD);
        //aMap.put(ATTEMPT_TYPE_PIN_CHANGE, LOCKED_CHANGE_PIN_ATTEMPT_LIMIT_RCHD);

        paramTypeToAccLockedReason = Collections.unmodifiableMap(aMap);
    }

    // Merchant Id or Card Id Batch status
    public static final String BATCH_STATUS_AVAILABLE = "available";
    public static final String BATCH_STATUS_OPEN = "open";
    public static final String BATCH_STATUS_CLOSED = "closed";

    // Failed SMS status
    public static final String FAILED_SMS_STATUS_PENDING = "pending";
    public static final String FAILED_SMS_STATUS_SENT = "sent";

    // Backendless Role names
    public static final String ROLE_MERCHANT = "Merchant";

}
