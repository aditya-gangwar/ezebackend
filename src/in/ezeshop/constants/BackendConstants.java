package in.ezeshop.constants;

/**
 * This class defines constants that are only relevant for backend code
 * and not for the user apps.
 */
public class BackendConstants {

    /*
     * Backend server settings
     */
    //*
    public static final String APPLICATION_ID = "1E402302-7020-DBCF-FFB3-7220CDC84900";
    public static final String VERSION = "v1";
    public static final String BACKENDLESS_HOST_IP = "35.154.80.2";
    public static final String REST_SECRET_KEY = "8EAA79C5-B199-C27E-FF5C-8EC3540AB500";
    public static final String BACKENDLESS_HOST = "http://"+BACKENDLESS_HOST_IP+":8080/api";
    //*/

    /*
    public static final String APPLICATION_ID = "1E402302-7020-DBCF-FFB3-7220CDC84900";
    public static final String REST_SECRET_KEY = "8EAA79C5-B199-C27E-FF5C-8EC3540AB500";
    public static final String VERSION = "v1";
    public static final String BACKENDLESS_HOST_IP = "tomyecash.in";
    public static final String BACKENDLESS_HOST = "https://"+BACKENDLESS_HOST_IP+":8443/api";
    */

    public static final String BULK_API_URL  = BACKENDLESS_HOST+"/"+VERSION+"/data/bulk/";

    // Constants to identify Testing/Debug scenarios
    //TODO: correct them in final testing and production
    public static final boolean FORCED_DEBUG_LOGS = true;
    public static final boolean DEBUG_MODE = false;
    public static final boolean TESTING_SKIP_SMS = true;
    public static final boolean TESTING_SKIP_DEVICEID_CHECK = true;
    public static final boolean TESTING_USE_FIXED_OTP = false;
    public static final String TESTING_FIXED_OTP_VALUE = "12345";

    // Values used in EoD script
    public static final int RECORDS_DEL_BUFFER_DAYS = 5;
    public static final int WRONG_ATTEMPTS_DEL_DAYS = 5;
    public static final int FAILED_SMS_DEL_DAYS = 5;

    // <m:api name>,<m:start time>,<m:end time>,<execution duration>,<user id>,<user type>,<mchnt id>,<internal user id>,
    // <cust id>,<cust card id>,<api parameters>,<m:success/failure>,<exception code>,<exception msg>,<special flag>
    // 50+10+10+5+10+10+10+10+10+10+50+10+5+100 = ~300 chars
    public static final int BACKEND_EDR_MAX_SIZE = 500;
    public static final String BACKEND_EDR_DELIMETER = "#";
    public static final String BACKEND_EDR_SUB_DELIMETER = ":";
    public static final String BACKEND_EDR_KEY_VALUE_DELIMETER = "_";
    public static final String BACKEND_EDR_RESULT_OK = "SUCCESS";
    public static final String BACKEND_EDR_RESULT_NOK = "FAILURE";
    public static final String BACKEND_EDR_SMS_OK = "SMS_OK";
    public static final String BACKEND_EDR_SMS_NOK = "SMS_NOK";
    // special flags
    public static final String BACKEND_EDR_MANUAL_CHECK = "ManualCheck";
    public static final String BACKEND_EDR_SECURITY_BREACH = "SecurityBreach";
    public static final String BACKEND_EDR_OLD_STATS_RETURNED = "OldStatsReturned";
    // ignored error scenarios
    //public static final String IGNORED_ERROR_OLDCARD_SAVE_FAILED = "OldCardSaveFailed";
    public static final String IGNORED_ERROR_MOBILE_NUM_NA = "MobileNumNotAvailable";
    public static final String IGNORED_ERROR_ACC_STATUS_CHANGE_FAILED = "AccStatusChangeFailed";
    public static final String IGNORED_ERROR_WRONG_ATTEMPT_SAVE_FAILED = "WrongAttemptSaveFailed";
    public static final String IGNORED_ERROR_OTP_DELETE_FAILED = "otpDeleteFailed";
    public static final String IGNORED_ERROR_MCHNT_PASSWD_RESET_FAILED = "mchntPsswdResetFailed";
    public static final String IGNORED_ERROR_CUST_PASSWD_RESET_FAILED = "custPsswdResetFailed";
    public static final String IGNORED_ERROR_CUST_PIN_RESET_FAILED = "custPinResetFailed";
    public static final String IGNORED_ERROR_CUST_WITH_NO_CB_RECORD = "custWithNoCbRecord";
    public static final String IGNORED_ERROR_CB_WITH_NO_CUST = "cbWithNoLinkedCust";
    public static final String IGNORED_ERROR_CB_WITH_NO_MCHNT = "cbWithNoValidMchnt";

    // array indexes giving position of EDR fields
    public static final int EDR_API_NAME_IDX = 0;
    public static final int EDR_START_TIME_IDX = 1;
    public static final int EDR_END_TIME_IDX = 2;
    public static final int EDR_EXEC_DURATION_IDX = 3;
    public static final int EDR_USER_ID_IDX = 4;
    public static final int EDR_USER_TYPE_IDX = 5;
    public static final int EDR_MCHNT_ID_IDX = 6;
    public static final int EDR_INTERNAL_USER_ID_IDX = 7;
    public static final int EDR_CUST_ID_IDX = 8;
    public static final int EDR_CUST_CARD_NUM_IDX = 9;
    public static final int EDR_RESULT_IDX = 10;
    public static final int EDR_EXP_EXPECTED = 11;
    public static final int EDR_EXP_CODE_IDX = 12;
    public static final int EDR_EXP_CODE_NAME = 13;
    public static final int EDR_EXP_MSG_IDX = 14;
    public static final int EDR_IGNORED_ERROR_IDX = 15;
    public static final int EDR_SPECIAL_FLAG_IDX = 16;
    public static final int EDR_SMS_STATUS_IDX = 17;
    public static final int EDR_API_PARAMS_IDX = 18;
    public static final int EDR_SMS_SUBMIT_TIME_IDX = 19;
    public static final int BACKEND_EDR_MAX_FIELDS = 20;

    //public static final String TIMEZONE = "Asia/Kolkata";
    public static final String DUMMY_DATA = "This is dummy file. Please ignore.";
    public static final String DUMMY_FILENAME = "dummy.txt";

    public static final int LOG_ID_LEN = 8;
    public static final int CUSTOMER_CARD_BARCODE_SALT_LEN = 5;

    // used in generating temporary passwords
    // not using 'o','l','1','0' in pwdChars to avoid confusion
    public static final char[] pwdChars = "abcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    public static final char[] onlyNumbers = "0123456789".toCharArray();
    // used in generating random transaction ids, passwords and PINs
    public static final char[] txnChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();


    public static final int SEND_TXN_SMS_CL_MIN_AMOUNT = 10;
    public static final int SEND_TXN_SMS_CB_MIN_AMOUNT = 10;

    public static final int LOG_POLICY_NUM_MSGS = 1;
    public static final int LOG_POLICY_FREQ_SECS = 0;

    //public static String PASSWORD_RESET_USER_ID = "00";
    //public static String PASSWORD_RESET_USER_PWD = "aditya123";

    public static final String ROLE_MERCHANT = "Merchant";
    public static final String ROLE_CUSTOMER = "Customer";
    public static final String ROLE_AGENT = "Agent";
    public static final String ROLE_CC = "CustomerCare";
    public static final String ROLE_CCNT = "CardController";

    public static final String DUMMY_MCHNT_NAME = "DUMMY MERCHANT";
    public static final String DUMMY_MCHNT_COUNTRY_CODE = "99";

    public static final int DEVICE_INFO_VALID_SECS = 600;

    // Merchant id constants
    public static final int MERCHANT_ID_MAX_BATCH_ID_PER_RANGE = 99; // 2 digit batchId
    public static final int MERCHANT_ID_MAX_SNO_PER_BATCH = 1000; // 3 digit serialNo

    public static final String MY_CARD_ISSUER_ID = "51";
    public static final int CARD_ID_MAX_BATCH_ID_PER_RANGE = 999; // 3 digit batchId
    public static final int CARD_ID_MIN_SNO_PER_BATCH = 0; // 3 digit serialNo
    public static final int CARD_ID_MAX_SNO_PER_BATCH = 999; // 3 digit serialNo
}
