package in.ezeshop.messaging;

import com.backendless.exceptions.BackendlessException;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.constants.DbConstants;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.constants.BackendConstants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import in.ezeshop.utilities.MyLogger;

import static in.ezeshop.utilities.BackendUtils.stackTraceStr;


/**
 * Created by adgangwa on 12-05-2016.
 */
public class SmsHelper {
    //private static MyLogger mLogger;

    public static boolean sendSMS(String message, String recipients, String[] edr, MyLogger logger, boolean retry) {

        long sTime = System.currentTimeMillis();

        if(BackendConstants.TESTING_SKIP_SMS) {
            logger.info("SMS: " + message);
            return true;
        }
        logger.debug("SMS: " + message);

        HttpURLConnection uc = null;
        try {

            StringBuffer requestUrl = new StringBuffer(SmsConstants.SMSGW_MSG91_BASE_URL)
                    .append("authkey=").append(URLEncoder.encode(SmsConstants.SMSGW_MSG91_AUTHKEY, SmsConstants.SMSGW_URL_ENCODING))
                    .append("&mobiles=").append(URLEncoder.encode(recipients, SmsConstants.SMSGW_URL_ENCODING))
                    .append("&message=").append(URLEncoder.encode(message, SmsConstants.SMSGW_URL_ENCODING))
                    .append("&sender=").append(URLEncoder.encode(SmsConstants.SMSGW_SENDER_ID, SmsConstants.SMSGW_URL_ENCODING))
                    .append("&route=").append(URLEncoder.encode(SmsConstants.SMSGW_MSG91_ROUTE_TXN, SmsConstants.SMSGW_URL_ENCODING))
                    .append("&country=").append(URLEncoder.encode(SmsConstants.COUNTRY_CODE, SmsConstants.SMSGW_URL_ENCODING));

            URL url = new URL(requestUrl.toString());
            logger.debug("SMS URL: " + url.toString());

            uc = (HttpURLConnection) url.openConnection();

            if (uc.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logger.error("Failed to send SMS ("+message+") to "+recipients+". HTTP response: "+uc.getResponseCode());
                edr[BackendConstants.EDR_SMS_STATUS_IDX] = BackendConstants.BACKEND_EDR_SMS_NOK;
                if(retry) {
                    BackendOps.addFailedSms(message, recipients);
                }
                return false;
            }
            logger.debug(uc.getResponseMessage());

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (uc.getInputStream())));

            String output;
            while ((output = br.readLine()) != null) {
                logger.debug("SMS server response: " + output);
            }
            edr[BackendConstants.EDR_SMS_SUBMIT_TIME_IDX] = String.valueOf(System.currentTimeMillis() - sTime);

        } catch (Exception e) {
            logger.error("Failed to send SMS ("+message+") to "+recipients);
            logger.error("Failed to send SMS:"+e.toString());
            logger.error(stackTraceStr(e));
            edr[BackendConstants.EDR_SMS_STATUS_IDX] = BackendConstants.BACKEND_EDR_SMS_NOK;
            if(retry) {
                BackendOps.addFailedSms(message, recipients);
            }
            return false;

        } finally {
            if (uc != null) {
                uc.disconnect();
            }
        }

        edr[BackendConstants.EDR_SMS_STATUS_IDX] = BackendConstants.BACKEND_EDR_SMS_OK;
        return true;
    }

    /*
     * Methods to build SMS texts
     */

    public static String buildNewCardSMS(String userId, String card_num) {
        return String.format(SmsConstants.SMS_CUSTOMER_NEW_CARD, card_num, CommonUtils.getPartialVisibleStr(userId));
    }

    public static String buildMobileChangeSMS(String userId, String mobile_num) {
        return String.format(SmsConstants.SMS_MOBILE_CHANGE, CommonUtils.getPartialVisibleStr(userId), CommonUtils.getPartialVisibleStr(mobile_num));
    }

    public static String buildFirstPwdResetSMS(String userId, String password) {
        return String.format(SmsConstants.SMS_FIRST_PASSWD,userId,password);
    }

    public static String buildUserIdSMS(String userId) {
        return String.format(SmsConstants.SMS_MERCHANT_ID,userId);
    }

    public static String buildPwdChangeSMS(String userId) {
        return String.format(SmsConstants.SMS_PASSWD_CHANGED, CommonUtils.getPartialVisibleStr(userId));
    }

    public static String buildPwdResetSMS(String userId, String password) {
        return String.format(SmsConstants.SMS_PASSWD,CommonUtils.getPartialVisibleStr(userId),password);
    }

    public static String buildCustPinResetSMS(String userId, String pin) {
        return String.format(SmsConstants.SMS_PIN, CommonUtils.getPartialVisibleStr(userId),pin);
    }

    public static String buildPinChangeSMS(String userId) {
        return String.format(SmsConstants.SMS_PIN_CHANGED, CommonUtils.getPartialVisibleStr(userId));
    }

    public static String buildOtpSMS(String userId, String otp, String opCode, String mchntName) {
        switch(opCode) {
            case DbConstants.OP_REG_CUSTOMER:
                return String.format(SmsConstants.SMS_REG_CUST_OTP, otp);

            case DbConstants.OP_LOGIN:
                // OTP to add trusted device
                return String.format(SmsConstants.SMS_LOGIN_OTP, otp, CommonUtils.getPartialVisibleStr(userId));

            case DbConstants.OP_CHANGE_MOBILE:
                return String.format(SmsConstants.SMS_CHANGE_MOB_OTP, otp);

            /*case DbConstants.OP_NEW_CARD:
                return String.format(SmsConstants.SMS_NEW_CARD_OTP, otp, CommonUtils.getPartialVisibleStr(userId));*/

            case DbConstants.OP_RESET_PIN:
                return String.format(SmsConstants.SMS_PIN_RESET_OTP, otp, CommonUtils.getPartialVisibleStr(userId));

            case DbConstants.OP_TXN_COMMIT:
                return String.format(SmsConstants.TXN_COMMIT_OTP, otp, mchntName);

            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Invalid OTP request: "+opCode);
        }
    }
}



//http://txtguru.in/imobile/api.php?username=aditya_gang&password=50375135&source=UPDATE&dmobile=918800191535&message=TEST+SMS+GATEWAY
            /*String requestUrl = SmsConstants.SMSGW_BASE_URL +
                    "username=" + URLEncoder.encode(SmsConstants.SMSGW_USERNAME, SmsConstants.SMSGW_URL_ENCODING) +
                    "&password=" + URLEncoder.encode(SmsConstants.SMSGW_PASSWORD, SmsConstants.SMSGW_URL_ENCODING) +
                    "&source=" + URLEncoder.encode(SmsConstants.SMSGW_SENDER_ID, SmsConstants.SMSGW_URL_ENCODING) +
                    "&dmobile=" + URLEncoder.encode(SmsConstants.COUNTRY_CODE + recipient, SmsConstants.SMSGW_URL_ENCODING) +
                    "&message=" + URLEncoder.encode(message, SmsConstants.SMSGW_URL_ENCODING);*/

            /*
            String requestUrl = SmsConstants.SMSGW_MSG91_BASE_URL +
                    "authkey=" + URLEncoder.encode(SmsConstants.SMSGW_MSG91_AUTHKEY, SmsConstants.SMSGW_URL_ENCODING) +
                    "&mobiles=" + URLEncoder.encode(recipients, SmsConstants.SMSGW_URL_ENCODING) +
                    "&message=" + URLEncoder.encode(message, SmsConstants.SMSGW_URL_ENCODING) +
                    "&sender=" + URLEncoder.encode(SmsConstants.SMSGW_SENDER_ID, SmsConstants.SMSGW_URL_ENCODING) +
                    "&route=" + URLEncoder.encode(SmsConstants.SMSGW_MSG91_ROUTE_TXN, SmsConstants.SMSGW_URL_ENCODING) +
                    "&country=" + URLEncoder.encode(SmsConstants.COUNTRY_CODE, SmsConstants.SMSGW_URL_ENCODING);*/

