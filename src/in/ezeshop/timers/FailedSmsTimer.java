package in.ezeshop.timers;

import com.backendless.servercode.annotation.BackendlessTimer;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.database.FailedSms;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import java.util.ArrayList;

/**
 * FailedSmsTimer is a timer.
 * It is executed according to the schedule defined in Backendless Console. The
 * class becomes a timer by extending the TimerExtender class. The information
 * about the timer, its name, schedule, expiration date/time is configured in
 * the special annotation - BackendlessTimer. The annotation contains a JSON
 * object which describes all properties of the timer.
 */
@BackendlessTimer("{'startDate':1479923220000,'frequency':{'schedule':'custom','repeat':{'every':120}},'timername':'FailedSms'}")
public class FailedSmsTimer extends com.backendless.servercode.extension.TimerExtender
{

    private MyLogger mLogger = new MyLogger("services.FailedSmsTimer");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    @Override
    public void execute( String appVersionId ) throws Exception
    {
        //BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "FailedSmsTimer";
        //mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);

        boolean anyError = false;
        boolean writeEdr = false;
        try {
            //mLogger.debug("In FailedSmsTimer execute");
            //mLogger.debug("Before: " + HeadersManager.getInstance().getHeaders().toString());

            // Fetch all failed SMS
            ArrayList<FailedSms> smses = BackendOps.findRecentFailedSms();
            if (smses != null) {
                writeEdr = true;
                mLogger.debug("Fetched failed SMS: " + smses.size());
                mEdr[BackendConstants.EDR_API_PARAMS_IDX] = String.valueOf(smses.size());

                for (FailedSms sms:smses) {
                    // try to resend the SMS
                    if(SmsHelper.sendSMS(sms.getText(), sms.getRecipients(), mEdr, mLogger, false)) {
                        // update status if success
                        sms.setStatus(DbConstantsBackend.FAILED_SMS_STATUS_SENT);
                        BackendOps.saveFailedSms(sms);
                    } else {
                        anyError = true;
                    }
                }
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            anyError = true;
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, (writeEdr||anyError)?mEdr:null);
        }
    }
}
