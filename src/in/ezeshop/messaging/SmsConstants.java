package in.ezeshop.messaging;

/**
 * Created by adgangwa on 12-05-2016.
 */
public class SmsConstants {

    public static String SMSGW_URL_ENCODING = "UTF-8";
    public static String SMSGW_SENDER_ID = "MYCASH";
    public static String COUNTRY_CODE = "91";

    public static String SMSGW_TXTGURU_BASE_URL = "https://www.txtguru.in/imobile/api.php?";
    public static String SMSGW_TXTGURU_USERNAME = "aditya_gang";
    public static String SMSGW_TXTGURU_PASSWORD = "50375135";

    public static String SMSGW_MSG91_BASE_URL = "https://control.msg91.com/api/sendhttp.php?";
    public static String SMSGW_MSG91_AUTHKEY = "115853A9qGXSHBf575aaeb1";
    public static String SMSGW_MSG91_ROUTE_TXN = "4";
    public static String SMSGW_MSG91_ROUTE_PROMOTIONAL = "1";
    /*
     * SMS templates
     */

    // TODO: Review all SMS text

    // SMS against Manual Requests by Merchant
    public static String SMS_ADMIN_MCHNT_SEND_PSWD_RESET_HINT = "Dear MyeCash Merchant - As per request, your password reset hint is '%s'. You can use the same to generate new password. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_ADMIN_MCHNT_MOBILE_CHANGE = "Dear MyeCash Merchant - As per request, registered mobile number of your account '%s' is changed successfully to '%s'. Don't forget to remove any Lost Mobile device from your Trusted Device List. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    //public static String SMS_ADMIN_MCHNT_RESET_TRUSTED_DEVICES = "Dear MyeCash Merchant - As per request, all devices are removed from your 'Trusted Device List'. You can now try login from new device. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_ADMIN_MCHNT_ACC_CLOSURE = "Dear MyeCash Merchant - As per request, we have put your account '%s' under %s days of Account Closure Notice period. No 'Credit' transactions are allowed now. Your account will be automatically removed on %s. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_ADMIN_MCHNT_CANCEL_ACC_CLOSURE = "Dear MyeCash Merchant - As per request, we have cancelled your earlier Account closure request. You can do 'Credit' transactions also now. Thanks. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_ADMIN_MCHNT_ACC_ENABLE = "Dear MyeCash Merchant - As per request, we have enabled your account '%s'. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";

    // SMS against Manual Requests by Customer
    public static String SMS_ADMIN_CUST_RESET_PIN = "Dear MyeCash Customer - As per request, your new PIN after reset is '%s'. You can change it to your choice from App Menu. ALWAYS KEEP YOUR PIN SECRET. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_ADMIN_CUST_MOBILE_CHANGE = "Dear MyeCash Customer - As per request, registered mobile number of your account is changed successfully to '%s'. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_ADMIN_CUST_ACC_ENABLE = "Dear MyeCash Customer - As per request, we have enabled your account '%s'. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";


    // Account Status change SMS
    public static String SMS_ACCOUNT_LOCKED_INTERNAL = "Your MyeCash account '%s' is locked due to multiple wrong verifications. Contact Admin to get it unlocked.";
    public static String SMS_ACCOUNT_LOCKED_PASSWORD = "Your MyeCash account '%s' is locked due to multiple wrong password attempts. It will be unlocked automatically after %s minutes.";
    public static String SMS_ACCOUNT_LOCKED_PIN = "Your MyeCash account '%s' is locked due to multiple wrong PIN attempts. It will be unlocked automatically after %s minutes.";
    public static String SMS_ACCOUNT_LOCKED_VERIFY_FAILED = "Your MyeCash account '%s' is locked due to multiple wrong verifications. It will be unlocked automatically after %s minutes.";
    public static String SMS_ACCOUNT_DISABLE = "Your MyeCash account '%s' is Disabled now. You can reach us for further help.";
    public static String SMS_ACCOUNT_LIMITED_MODE = "Your MyeCash account '%s' is in Limited Mode now. Only Credit transactions are allowed.";
    public static String SMS_ACCOUNT_ENABLE = "Your MyeCash account number '%s' is Enabled now. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    //public static String SMS_ACCOUNT_LOCKED_FORGOT_USERID = "Your MyeCash account '%s' is locked for next %d hours, due to more than allowed wrong 'forgot userId' attempts.";
    //public static String SMS_ACCOUNT_LOCKED_PASSWD_RESET_AGENT = "Your MyeCash agent account '%s' is locked, due to more than allowed wrong 'password reset' attempts.";

    // MyeCash transaction SMS
    public static String SMS_TXN_DEBIT_CL = "MyeCash: %s debited Rs %d from your Member account on %s. New Balance is Rs %d.";
    public static String SMS_TXN_DEBIT_OD = "MyeCash: %s did an Overdraft (on credit) of Rs %d from your Member account on %s. New Balance is Rs %d.";
    public static String SMS_TXN_DEBIT_CL_OD = "MyeCash: %s debited Rs %d and did an Overdraft (on credit) of Rs %d from your Member account on %s. New Balance is Rs %d.";
    public static String SMS_TXN_CREDIT_CL = "MyeCash: %s added Rs %d to your Member account on %s. New Balance is Rs %d.";

    /*public static String SMS_TXN_DEBIT_CL_CB = "MyeCash: %s debited Rs %d from your Account and Rs %d from Cashback on %s. Balance- Account:Rs %d, Cashback:Rs %d.";
    public static String SMS_TXN_CREDIT_CL_DEBIT_CB = "MyeCash: %s added Rs %d to your Account and redeemed Rs %d from Cashback on %s. Balance- Account:Rs %d, Cashback:Rs %d.";

    public static String SMS_TXN_CREDIT_CL = "MyeCash: %s added Rs %d to your Account on %s. Balance: Account:Rs %d, Cashback:Rs %d.";
    public static String SMS_TXN_DEBIT_CL = "MyeCash: %s debited Rs %d from your Account on %s. Balance: Account:Rs %d, Cashback:Rs %d.";
    public static String SMS_TXN_DEBIT_CB = "MyeCash: %s redeemed Rs %d from your Cashback on %s. Balance: Account:Rs %d, Cashback:Rs %d";

    public static String SMS_TXN_CANCEL = "MyeCash: %s cancelled transaction done at %s. Balance: Account:Rs %d, Cashback:Rs %d";*/

    // Password/PIN messages
    public static String SMS_FIRST_PASSWD = "Dear User - Welcome to MyeCash family !! Your User ID is '%s'. Your Password is '%s'. PLZ DO CHANGE PASSWORD AFTER FIRST LOGIN.";
    public static String SMS_PASSWD_RESET_SCHEDULED = "Password reset request for MyeCash account '%s' is accepted. New password will be sent on registered mobile number after %s minutes.";
    public static String SMS_PASSWD = "Dear User - New password for your MyeCash account %s is '%s'. Please do Change your Password immediately after Login. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_PASSWD_CHANGED = "Dear User - Password changed successfully for your MyeCash account %s. PLS CALL CUSTOMER CARE IF NOT DONE BY YOU.";

    // OTP messages
    //public static String SMS_OTP = "You have initiated '%s' for user %s. OTP is '%s' and valid for %d mins only. PLS CALL CUSTOMER IF NOT DONE BY YOU.";
    //public static String SMS_REG_CUST_OTP = "Use OTP %s to register as MyeCash Customer. Valid for next %d minutes. Always enter OTP yourself in Merchant device.";
    public static String SMS_REG_CUST_OTP = "Please use OTP %s to register to MyeCash Rewards Program. You can check more program details and terms at http://www.myecash.in.";
    public static String SMS_LOGIN_OTP = "Login attempt detected from Unknown Device. Use OTP '%s' to add it as 'Trusted Device' for your account '%s'. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_CHANGE_MOB_OTP = "Use OTP %s to verify this Mobile number. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    //public static String SMS_NEW_CARD_OTP = "Use OTP %s to authenticate new Membership Card for account %s. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_PIN_RESET_OTP = "Use OTP %s to authenticate PIN Reset for customer account %s. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String TXN_COMMIT_OTP = "Use OTP %s to authenticate your transaction at %s. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";

    public static String SMS_PIN_INIT = "Dear User - %s have initiated 'PIN Reset' for your MyeCash account '%s'. New PIN will be sent to your registered mobile number after %s minutes. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_PIN = "Dear User - MyeCash PIN for your account '%s' is '%s'. ALWAYS KEEP YOUR PIN SECRET. PLS CALL CUSTOMER CARE IF NOT DONE BY YOU.";
    public static String SMS_PIN_CHANGED = "MyeCash PIN changed successfully for user %s. PLS CALL CUSTOMER CARE IF NOT DONE BY YOU.";

    // Registration / User ID messages
    public static String SMS_MERCHANT_ID = "Your MyeCash Merchant ID is %s. PLS CALL CUSTOMER CARE IF NOT REQUESTED BY YOU.";
    public static String SMS_MERCHANT_ID_FIRST = "Dear Merchant - Welcome to MyeCash family. Your Merchant ID for login is %s. Happy Customers to you.";
    //public static String SMS_CUSTOMER_REGISTER = "Dear %s - Welcome to MyeCash family. Your registered Mobile number is '%s'. To manage your account, download MyeCash Customer App from Android store.";
    public static String SMS_CUSTOMER_REGISTER = "Dear %s - Welcome to MyeCash family. To manage your account, download MyeCash Customer App from Android store. To opt out of program, contact our customer care at %s.";
    public static String SMS_REG_INTERNAL_USER = "Dear User - Welcome to MyeCash family!! Your User ID is %s, and your password is your DOB in DDMMYYYY format. PLS CHANGE YOUR PASSWORD IMMEDIATELY AFTER LOGIN.";

    // Mobile/Card change messages
    public static String SMS_MOBILE_CHANGE = "Registered Mobile number of your MyeCash account '%s' is changed successfully to '%s'. PLS CALL CUSTOMER IMMEDIATELY IF NOT DONE BY YOU.";
    //public static String SMS_CUSTOMER_NEW_CARD = "You have registered new card with number %s to your account %s. PLS CALL CUSTOMER IMMEDIATELY IF NOT DONE BY YOU.";
    //public static String SMS_DISABLE_CARD = "Dear %s - Your MyeCash Customer Card bearing number '%s' is Disabled now. Please get new Card from any MyeCash Merchant.";

}
