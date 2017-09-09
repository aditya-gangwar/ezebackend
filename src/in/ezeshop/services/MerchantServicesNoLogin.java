package in.ezeshop.services;

import com.backendless.BackendlessUser;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.IBackendlessService;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.MyErrorParams;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import java.util.Date;
import java.util.List;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

/**
 * Created by adgangwa on 14-07-2016.
 */
public class MerchantServicesNoLogin implements IBackendlessService {

    private MyLogger mLogger = new MyLogger("services.MerchantServicesNoLogin");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];

    /*
     * Public methods: Backend REST APIs
     */
    public void setDeviceForLogin(String loginId, String deviceInfo, String rcvdOtp) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "setDeviceForLogin";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = loginId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                deviceInfo+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                rcvdOtp;

        try {
            if (deviceInfo == null || deviceInfo.isEmpty()) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "");
            }

            //mLogger.debug("In setDeviceForLogin: " + loginId + ": " + deviceInfo);
            //mLogger.debug(InvocationContext.asString());
            //mLogger.debug("Before: "+HeadersManager.getInstance().getHeaders().toString());

            // fetch merchant
            Merchants merchant = BackendOps.getMerchant(loginId, false, false);
            mLogger.setProperties(merchant.getAuto_id(), DbConstants.USER_TYPE_MERCHANT, merchant.getDebugLogs());
            mEdr[BackendConstants.EDR_USER_ID_IDX] = merchant.getAuto_id();
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_MERCHANT);
            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();

            // deviceInfo format (from app): <device id>,<manufacturer>,<model>,<os version>
            // add time and otp at the end
            if (rcvdOtp == null || rcvdOtp.isEmpty()) {
                deviceInfo = deviceInfo+","+String.valueOf(System.currentTimeMillis())+",";
            } else {
                deviceInfo = deviceInfo+","+String.valueOf(System.currentTimeMillis())+","+rcvdOtp;
            }

            // Update device Info in merchant object
            merchant.setTempDevId(deviceInfo);
            BackendOps.updateMerchant(merchant);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void resetMerchantPwd(String userId, String deviceId, String dob) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "resetMerchantPwd";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = userId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                deviceId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                dob;
        boolean validException = false;

        try {
            mLogger.debug("In resetMerchantPwd: " + userId + ": " + deviceId);
            //mLogger.debug("Before: " + InvocationContext.asString());
            //mLogger.debug("Before: " + HeadersManager.getInstance().getHeaders().toString());

            // check if any request already pending
            if( BackendOps.findActiveMchntPwdResetReqs(userId) != null) {
                validException = true;
                throw new BackendlessException(String.valueOf(ErrorCodes.DUPLICATE_ENTRY), "");
            }

            // fetch user with the given id with related merchant object
            BackendlessUser user = BackendOps.fetchUser(userId, DbConstants.USER_TYPE_MERCHANT, false);
            int userType = (Integer)user.getProperty("user_type");
            if(userType != DbConstants.USER_TYPE_MERCHANT) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED),userId+" is not a merchant.");
            }

            Merchants merchant = (Merchants) user.getProperty("merchant");
            mLogger.setProperties(merchant.getAuto_id(), DbConstants.USER_TYPE_MERCHANT, merchant.getDebugLogs());
            mEdr[BackendConstants.EDR_USER_ID_IDX] = (String)user.getProperty("user_id");
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(userType);
            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();

            // check admin status
            BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);

            // Check if from trusted device
            List<MerchantDevice> trustedDevices = merchant.getTrusted_devices();
            // don't check if trusted device list empty
            // Can happen in 2 cases:
            // 1) first login after merchant is registered
            // 2) first login after 'reset trusted devices' from backend by admin - based on manual request by merchant
            if (trustedDevices!=null && trustedDevices.size() > 0) {
                if (!BackendUtils.isTrustedDevice(deviceId, trustedDevices)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "");
                }
            }

            // check for 'extra verification'
            String storedDob = merchant.getDob();
            if (storedDob == null || !storedDob.equalsIgnoreCase(dob)) {

                validException = true;
                int cnt = BackendUtils.handleWrongAttempt(userId, merchant, DbConstants.USER_TYPE_MERCHANT,
                        DbConstantsBackend.WRONG_PARAM_TYPE_DOB, DbConstants.OP_RESET_PASSWD, mEdr, mLogger);
                throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_DOB), String.valueOf(cnt));
            }

            // For new registered merchant - send the password immediately
            if (!merchant.getFirst_login_ok()) {
                generateFirstMchntPasswd(user, merchant);
                mLogger.debug("Processed passwd reset op for: " + merchant.getAuto_id());
            } else {
                // create row in MerchantOps table
                MerchantOps op = new MerchantOps();
                op.setCreateTime(new Date());
                op.setMerchant_id(merchant.getAuto_id());
                op.setMobile_num(merchant.getMobile_num());
                op.setOp_code(DbConstants.OP_RESET_PASSWD);
                op.setOp_status(DbConstantsBackend.USER_OP_STATUS_PENDING);
                op.setInitiatedBy(DbConstantsBackend.USER_OP_INITBY_MCHNT);
                op.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_APP);

                BackendOps.saveMerchantOp(op);
                mLogger.debug("Processed passwd reset op for: " + merchant.getAuto_id());

                // Change password
                user.setPassword(BackendUtils.generateTempPassword());
                user = BackendOps.updateUser(user);

                // Send SMS to inform
                //Integer mins = MyGlobalSettings.getMchntPasswdResetMins() + GlobalSettingConstants.MERCHANT_PASSWORD_RESET_TIMER_INTERVAL;
                Integer mins = MyGlobalSettings.getMchntPasswdResetMins();
                String smsText = String.format(SmsConstants.SMS_PASSWD_RESET_SCHEDULED,
                        CommonUtils.getPartialVisibleStr(op.getMerchant_id()), mins);
                // ignore error
                SmsHelper.sendSMS(smsText, op.getMobile_num(), mEdr, mLogger, true);

                validException = true;
                String errMsg = (new MyErrorParams(ErrorCodes.OP_SCHEDULED, -1,
                        //CommonUtils.roundUpTo(MyGlobalSettings.getMchntPasswdResetMins()+GlobalSettingConstants.MERCHANT_PASSWORD_RESET_TIMER_INTERVAL,5),
                        mins,
                        "")).toCsvString();
                throw new BackendlessException(String.valueOf(ErrorCodes.OP_SCHEDULED), errMsg);
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void sendMerchantId(String mobileNum, String deviceId) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "sendMerchantId";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = mobileNum;

        boolean validException = false;
        try {
            mLogger.debug("In sendMerchantId: " + mobileNum);

            // fetch user with the registered mobile number
            //Merchants merchant = BackendOps.getMerchantByMobile(mobileNum);
            Merchants merchant = BackendOps.getMerchant(mobileNum, true, false);
            mLogger.setProperties(merchant.getAuto_id(), DbConstants.USER_TYPE_MERCHANT, merchant.getDebugLogs());
            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();
            // check admin status
            BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);

            // Check if from trusted device
            // don't check for first time after merchant is registered
            /*List<MerchantDevice> trustedDevices = merchant.getTrusted_devices();
            if (merchant.getFirst_login_ok()) {
                if (!BackendUtils.isTrustedDevice(deviceId, trustedDevices)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "");
                }
            }*/

            // check for 'extra verification'
            String mobile = merchant.getMobile_num();
            if (mobile == null || !mobile.equalsIgnoreCase(mobileNum)) {
                int cnt = BackendUtils.handleWrongAttempt(merchant.getAuto_id(), merchant, DbConstants.USER_TYPE_MERCHANT,
                        DbConstantsBackend.WRONG_PARAM_TYPE_MOBILE, DbConstants.OP_FORGOT_USERID, mEdr, mLogger);
                validException = true;
                throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_MOBILE), String.valueOf(cnt));
            }

            // send merchant id by SMS
            String smsText = SmsHelper.buildUserIdSMS(merchant.getAuto_id());
            if (!SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, false)) {
                throw new BackendlessException(String.valueOf(ErrorCodes.SEND_SMS_FAILED), "");
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    /*
     * Private helper methods
     */
    private void generateFirstMchntPasswd(BackendlessUser user, Merchants merchant) {
        // generate password
        String passwd = BackendUtils.generateTempPassword();
        // update user account for the password
        user.setPassword(passwd);
        BackendOps.updateUser(user);

        // Send SMS through HTTP
        String smsText = SmsHelper.buildFirstPwdResetSMS(merchant.getAuto_id(), passwd);
        if( !SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, false) ){
            throw new BackendlessException(String.valueOf(ErrorCodes.SEND_SMS_FAILED), "");
        }
        //mLogger.debug("Sent first password reset SMS: "+merchant.getAuto_id());
    }
}
