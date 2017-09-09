package in.ezeshop.timers;

import com.backendless.BackendlessUser;
import com.backendless.HeadersManager;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.annotation.BackendlessTimer;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

import java.util.ArrayList;
import java.util.Date;

/**
 * MerchantPasswdResetTimer is a timer.
 * It is executed according to the schedule defined in Backendless Console. The
 * class becomes a timer by extending the TimerExtender class. The information
 * about the timer, its name, schedule, expiration date/time is configured in
 * the special annotation - BackendlessTimer. The annotation contains a JSON
 * object which describes all properties of the timer.
 */
/*
 * Timer duration should be 1/6th of the configured merchant passwd reset cool off mins value
 * So, if cool off mins is 60, then timer should run every 10 mins
 */
@BackendlessTimer("{'startDate':1464294360000,'frequency':{'schedule':'custom','repeat':{'every':600}},'timername':'MerchantPasswdReset'}")
public class MerchantPasswdResetTimer extends com.backendless.servercode.extension.TimerExtender
{
    private MyLogger mLogger = new MyLogger("services.MerchantPasswdResetTimer");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    @Override
    public void execute( String appVersionId ) throws Exception
    {
        long startTime = System.currentTimeMillis();
        BackendUtils.initAll();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "MerchantPasswdResetTimer";
        mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_MERCHANT);

        boolean anyError = false;
        boolean writeEdr = false;
        try {
            mLogger.debug("In MerchantPasswdResetTimer execute");
            /*
             * An issue was observed wherein the 'user-token' header remains set,
             * if the time run happens after 'Txn Commit' from Merchant App.
             * This lead to the processing assuming {Merchant, AuthenticatedUser} roles - and hence permission issues
             * So, user-token header is explicitly removed here.
             */
            mLogger.debug("Before: " + HeadersManager.getInstance().getHeaders().toString());
            HeadersManager.getInstance().removeHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY);
            mLogger.debug("After: " + HeadersManager.getInstance().getHeaders().toString());

            // Fetch all 'pending' merchant password reset operations
            ArrayList<MerchantOps> ops = BackendOps.fetchMerchantOps(mchntPwdResetWhereClause());
            if (ops != null) {
                writeEdr = true;
                mLogger.debug("Fetched password reset ops: " + ops.size());
                mEdr[BackendConstants.EDR_API_PARAMS_IDX] = ops.size()+BackendConstants.BACKEND_EDR_SUB_DELIMETER;

                for (MerchantOps op:ops) {
                    if(handlePasswdReset(op)) {
                        mLogger.debug("Processed passwd reset op for: " + op.getMerchant_id());
                    } else {
                        anyError = true;
                    }
                }
            }

            // no exception - means function execution success
            if(anyError) {
                mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_NOK;
            } else {
                mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            }

        } catch(Exception e) {
            anyError = true;
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, (writeEdr||anyError)?mEdr:null);
        }
    }

    private boolean handlePasswdReset(MerchantOps op) {
        try {
            // fetch user with the given id with related merchant object
            BackendlessUser user = BackendOps.fetchUser(op.getMerchant_id(), DbConstants.USER_TYPE_MERCHANT, false);
            Merchants merchant = (Merchants) user.getProperty("merchant");
            // check admin status
            BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);

            // generate password
            String passwd = BackendUtils.generateTempPassword();

            // update user account for the password
            user.setPassword(passwd);
            BackendOps.updateUser(user);
            mLogger.debug("Updated merchant for password reset: " + merchant.getAuto_id());

            // Send SMS through HTTP
            String smsText = SmsHelper.buildPwdResetSMS(op.getMerchant_id(), passwd);
            // set Retry flag ON - however raise exception so as next resetTimer in queue dont try
            if (!SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true)) {
                throw new BackendlessException(String.valueOf(ErrorCodes.SEND_SMS_FAILED), "");
            }
            mLogger.debug("Sent password reset SMS: " + merchant.getAuto_id());

            // Change merchant op status
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            BackendOps.saveMerchantOp(op);

        } catch(Exception e) {
            // ignore exception - mark op as failed
            mLogger.error("Exception in handlePasswdReset: "+e.toString(),e);
            mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_MCHNT_PASSWD_RESET_FAILED;

            try {
                op.setOp_status(DbConstantsBackend.USER_OP_STATUS_ERROR);
                BackendOps.saveMerchantOp(op);
            } catch(Exception ex) {
                // ignore
                mLogger.error("Exception in handlePasswdReset: Rollback Failed: "+e.toString(),ex);
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
            }
            return false;
        }
        return true;
    }

    private String mchntPwdResetWhereClause() {
        StringBuilder whereClause = new StringBuilder();

        whereClause.append("op_code = '").append(DbConstants.OP_RESET_PASSWD).append("'");
        whereClause.append(" AND op_status = '").append(DbConstantsBackend.USER_OP_STATUS_PENDING).append("'");

        // Records between last (cool off mins - timer duration) to (cool off mins)
        // So, if 'cool off mins = 60' and thus timer duration being 1/6th of it is 10 mins
        // and say if timer runs at 2:00 PM, then this run should process all requests
        // created between (2:00 PM - (60+10)) to (2:00 PM - 60)
        // i.e. between 12:50 PM - 1:00 PM
        // accordingly, next timer run at 2:10 shall pick records created between 1:00 PM and 1:10 PM
        // Thus, no two consecutive runs will pick same records and thus never clash.

        long now = new Date().getTime();
        long endTime = now - (MyGlobalSettings.getMchntPasswdResetMins()*CommonConstants.MILLISECS_IN_MINUTE);
        long startTime = endTime - (GlobalSettingConstants.MERCHANT_PASSWORD_RESET_TIMER_INTERVAL*CommonConstants.MILLISECS_IN_MINUTE);

        whereClause.append(" AND createTime >= ").append(startTime);
        whereClause.append(" AND createTime < ").append(endTime);

        //mLogger.debug("whereClause: "+whereClause.toString());
        return whereClause.toString();
    }

}

    /*
    private String buildWhereClause() {
        StringBuilder whereClause = new StringBuilder();

        // for particular merchant
        whereClause.append("op_code = '").append(DbConstantsBackend.OP_RESET_PASSWD).append("'");
        whereClause.append(" AND op_status = '").append(DbConstantsBackend.USER_OP_STATUS_PENDING).append("'");
        // older than configured cool off period
        long time = (new Date().getTime()) - (MyGlobalSettings.MERCHANT_PASSWORD_RESET_COOL_OFF_MINS * 60 * 1000);

        whereClause.append(" AND created < ").append(time);

        mLogger.debug("where clause: "+whereClause.toString());
        return whereClause.toString();
    }*/

