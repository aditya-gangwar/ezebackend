package in.ezeshop.timers;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.HeadersManager;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.annotation.BackendlessTimer;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.SecurityHelper;
import in.ezeshop.common.database.CustomerOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

/**
 * CustomerPasswdPinResetTimer is a timer.
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
@BackendlessTimer("{'startDate':1464294360000,'frequency':{'schedule':'custom','repeat':{'every':600}},'timername':'CustomerPasswdReset'}")
public class CustomerPasswdPinResetTimer extends com.backendless.servercode.extension.TimerExtender
{
    private MyLogger mLogger = new MyLogger("services.CustomerPasswdPinResetTimer");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    @Override
    public void execute( String appVersionId ) throws Exception
    {
        long startTime = System.currentTimeMillis();
        BackendUtils.initAll();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "CustomerPasswdPinResetTimer";
        mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_CUSTOMER);

        boolean anyError = false;
        boolean writeEdr = false;
        try {
            mLogger.debug("In CustomerPasswdPinResetTimer execute");
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
            ArrayList<CustomerOps> ops = BackendOps.fetchCustomerOps(custPwdResetWhereClause());
            if (ops != null) {
                writeEdr = true;
                mLogger.debug("Fetched password-pin reset ops: " + ops.size());
                mEdr[BackendConstants.EDR_API_PARAMS_IDX] = String.valueOf(ops.size());

                for (CustomerOps op:ops) {
                    // fetch customer
                    Customers customer = BackendOps.getCustomer(op.getPrivateId(), CommonConstants.ID_TYPE_AUTO, false);
                    // check admin status
                    BackendUtils.checkCustomerStatus(customer, mEdr, mLogger);

                    if(op.getOp_code().equals(DbConstants.OP_RESET_PASSWD)) {
                        if(!handlePasswdReset(op, customer)) {
                            anyError = true;
                        }
                    } else {
                        if(!handlePinReset(op, customer)) {
                            anyError = true;
                        }
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

    private boolean handlePinReset(CustomerOps op, Customers customer) {
        try {
            // generate pin
            String newPin = SecurityHelper.generateCustPin(customer, mLogger);

            // update user account for the PIN
            BackendOps.saveCustomer(customer);

            // print roles - for debug purpose
            List<String> roles = Backendless.UserService.getUserRoles();
            mLogger.debug("Roles: "+roles.toString());

            // Change customer op status
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            BackendOps.saveCustomerOp(op);

            // Send SMS through HTTP
            String smsText = SmsHelper.buildCustPinResetSMS(customer.getMobile_num(), newPin);
            // keep retry flag ON - but return exception, so as next reset in queue are not retried
            if (!SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true)) {
                throw new BackendlessException(String.valueOf(ErrorCodes.SEND_SMS_FAILED), "");
            }
            mLogger.debug("Sent PIN reset SMS: " + customer.getMobile_num());

        } catch(Exception e) {
            // ignore exception - mark op as failed
            mLogger.error("Exception in handlePinReset: "+e.toString(),e);
            mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_CUST_PIN_RESET_FAILED;

            try {
                op.setOp_status(DbConstantsBackend.USER_OP_STATUS_ERROR);
                BackendOps.saveCustomerOp(op);
            } catch(Exception ex) {
                // ignore
                mLogger.error("Exception in handlePinReset: Rollback Failed: "+e.toString(),ex);
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
            }
            return false;
        }
        return true;
    }

    private boolean handlePasswdReset(CustomerOps op, Customers customer) {
        try {
            // fetch user with the given id with related customer object
            BackendlessUser user = BackendOps.fetchUser(customer.getMobile_num(), DbConstants.USER_TYPE_CUSTOMER, false);

            // generate password
            String passwd = BackendUtils.generateTempPassword();

            // update user account for the password
            user.setPassword(passwd);
            BackendOps.updateUser(user);
            mLogger.debug("Updated customer for password reset: " + customer.getMobile_num());

            // print roles - for debug purpose
            List<String> roles = Backendless.UserService.getUserRoles();
            mLogger.debug("Roles: "+roles.toString());

            // Change merchant op status
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            BackendOps.saveCustomerOp(op);

            // Send SMS through HTTP
            String smsText = SmsHelper.buildPwdResetSMS(customer.getMobile_num(), passwd);
            // keep retry flag ON - but return exception, so as next reset in queue are not retried
            if (!SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true)) {
                throw new BackendlessException(String.valueOf(ErrorCodes.SEND_SMS_FAILED), "");
            }
            mLogger.debug("Sent password reset SMS: " + customer.getMobile_num());

        } catch(Exception e) {
            // ignore exception - mark op as failed
            mLogger.error("Exception in handlePasswdReset: "+e.toString(),e);
            mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_CUST_PASSWD_RESET_FAILED;

            try {
                op.setOp_status(DbConstantsBackend.USER_OP_STATUS_ERROR);
                BackendOps.saveCustomerOp(op);
            } catch(Exception ex) {
                // ignore
                mLogger.error("Exception in handlePasswdReset: Rollback Failed: "+e.toString(),ex);
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
            }
            return false;
        }
        return true;
    }

    private String custPwdResetWhereClause() {
        StringBuilder whereClause = new StringBuilder();

        whereClause.append("op_code IN ('").append(DbConstants.OP_RESET_PASSWD).append("','").append(DbConstants.OP_RESET_PIN).append("')");
        whereClause.append(" AND op_status = '").append(DbConstantsBackend.USER_OP_STATUS_PENDING).append("'");

        // Records between last (cool off mins - timer duration) to (cool off mins)
        // So, if 'cool off mins = 60' and thus timer duration being 1/6th of it is 10 mins
        // and say if timer runs at 2:00 PM, then this run should process all requests
        // created between (2:00 PM - 60) to (2:00 PM - (60+10))
        // i.e. between 1:00 PM - 1:10 PM
        // accordingly, next timer run at 2:10 shall pick records created between 1:10 PM and 1:20 PM
        // Thus, no two consecutive runs will pick same records and thus never clash.

        long now = new Date().getTime();
        long endTime = now - (MyGlobalSettings.getCustPasswdResetMins()*CommonConstants.MILLISECS_IN_MINUTE);
        long startTime = endTime - (GlobalSettingConstants.CUSTOMER_PASSWORD_RESET_TIMER_INTERVAL*CommonConstants.MILLISECS_IN_MINUTE);

        whereClause.append(" AND createTime >= ").append(startTime);
        whereClause.append(" AND createTime < ").append(endTime);

        //mLogger.debug("whereClause: "+whereClause.toString());
        return whereClause.toString();
    }
}
