package in.ezeshop.timers;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.HeadersManager;
import com.backendless.servercode.annotation.BackendlessTimer;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.DateUtil;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.common.constants.CommonConstants;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.myecash.security.SecurityMisc;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import java.util.Date;

/**
 * EndOfDayTimer is a timer.
 * It is executed according to the schedule defined in Backendless Console. The
 * class becomes a timer by extending the TimerExtender class. The information
 * about the timer, its name, schedule, expiration date/time is configured in
 * the special annotation - BackendlessTimer. The annotation contains a JSON
 * object which describes all properties of the timer.
 */

// TODO: Daily EBS backup should be completed before this
// TODO: No connections from Merchant/Customers should be allowed, when this is running.

// run at 02:00 in GMT = 07:30 in IST
@BackendlessTimer("{'startDate':1490319900000,'frequency':{'schedule':'daily','repeat':{'every':1}},'timername':'EndOfDay'}")
public class EndOfDayTimer extends com.backendless.servercode.extension.TimerExtender
{
    private MyLogger mLogger = new MyLogger("services.EndOfDayTimer");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    @Override
    public void execute( String appVersionId ) throws Exception
    {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "EndOfDayTimer";

        String params = null;
        try {
            // First login
            BackendlessUser user = BackendOps.loginUser("autoAdmin", SecurityMisc.getAutoAdminPasswd());
            BackendUtils.printCtxtInfo(mLogger, null);
            String userToken = HeadersManager.getInstance().getHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY);

            // Create Txn Image directory for today
            boolean status = createTxnImgDir();
            params = "createTxnImgDir"+BackendConstants.BACKEND_EDR_KEY_VALUE_DELIMETER+status+BackendConstants.BACKEND_EDR_SUB_DELIMETER;

            // Delete wrong attempts rows
            int cnt = delWrongAttempts(userToken);
            params = params+"delWrongAttempts"+BackendConstants.BACKEND_EDR_KEY_VALUE_DELIMETER+cnt+BackendConstants.BACKEND_EDR_SUB_DELIMETER;

            // Delete wrong attempts rows
            cnt = delMchntOps(userToken);
            params = params+"delMchntOps"+BackendConstants.BACKEND_EDR_KEY_VALUE_DELIMETER+cnt+BackendConstants.BACKEND_EDR_SUB_DELIMETER;

            // Delete wrong attempts rows
            cnt = delCustOps(userToken);
            params = params+"delCustOps"+BackendConstants.BACKEND_EDR_KEY_VALUE_DELIMETER+cnt+BackendConstants.BACKEND_EDR_SUB_DELIMETER;

            // Delete archived Txns
            String statusStr = delArchivedTxns(userToken);
            params = params+statusStr;

            // Reset Mchnt Id Counter
            Backendless.Counters.reset(DbConstantsBackend.ORDER_ID_COUNTER);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = params;
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = params;
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    private int delCustOps(String userToken) {
        // Build where clause
        DateUtil now = new DateUtil(CommonConstants.TIMEZONE);
        now.removeDays(MyGlobalSettings.getOpsKeepDays()+BackendConstants.RECORDS_DEL_BUFFER_DAYS);
        Date delDate = now.toMidnight().getTime();

        String whereClause = "created < '"+delDate.getTime()+"'";
        try {
            int recDel = BackendOps.doBulkRequest(DbConstantsBackend.CUST_OPS_TABLE_NAME, whereClause, "DELETE",
                    null, userToken, mLogger);
            mLogger.debug("delCustOps: "+recDel);
            return recDel;

        } catch (Exception e) {
            // log and ignore
            BackendUtils.handleException(e,false,mLogger,mEdr);
        }
        return -1;
    }

    private int delMchntOps(String userToken) {
        // Build where clause
        DateUtil now = new DateUtil(CommonConstants.TIMEZONE);
        now.removeDays(MyGlobalSettings.getOpsKeepDays()+BackendConstants.RECORDS_DEL_BUFFER_DAYS);
        Date delDate = now.toMidnight().getTime();

        String whereClause = "created < '"+delDate.getTime()+"'";
        try {
            int recDel = BackendOps.doBulkRequest(DbConstantsBackend.MCHNT_OPS_TABLE_NAME, whereClause, "DELETE",
                    null, userToken, mLogger);
            mLogger.debug("delMchntOps: "+recDel);
            return recDel;

        } catch (Exception e) {
            // log and ignore
            BackendUtils.handleException(e,false,mLogger,mEdr);
        }
        return -1;
    }

    private int delWrongAttempts(String userToken) {
        // Build where clause
        DateUtil now = new DateUtil(CommonConstants.TIMEZONE);
        now.removeDays(BackendConstants.WRONG_ATTEMPTS_DEL_DAYS);
        Date delDate = now.toMidnight().getTime();

        String whereClause = "created < '"+delDate.getTime()+"'";
        try {
            int recDel = BackendOps.doBulkRequest(DbConstantsBackend.WRONG_ATTEMPTS_TABLE_NAME, whereClause, "DELETE",
                    null, userToken, mLogger);
            mLogger.debug("delWrongAttempts: "+recDel);
            return recDel;

        } catch (Exception e) {
            // log and ignore
            BackendUtils.handleException(e,false,mLogger,mEdr);
        }
        return -1;
    }

    private String delArchivedTxns(String userToken) {

        String result = "";
        String whereClause = buildTxnWhereClause();
        for(int i=1; i<= DbConstantsBackend.TRANSACTION_TABLE_CNT; i++) {

            String tableName = DbConstantsBackend.TRANSACTION_TABLE_NAME+String.valueOf(i);
            try {
                int recDel = BackendOps.doBulkRequest(tableName, whereClause, "DELETE",
                        null, userToken, mLogger);

                if(i>1) {
                    result = result + BackendConstants.BACKEND_EDR_SUB_DELIMETER;
                }
                result = result + tableName + BackendConstants.BACKEND_EDR_KEY_VALUE_DELIMETER + recDel;
                mLogger.debug("Deleted archived Txns: "+tableName+" "+recDel);

            } catch (Exception e) {
                // log and ignore
                BackendUtils.handleException(e,false,mLogger,mEdr);
            }
        }
        return null;
    }

    private String buildTxnWhereClause() {
        StringBuilder whereClause = new StringBuilder();

        DateUtil now = new DateUtil(CommonConstants.TIMEZONE);
        // 3 days as buffer
        now.removeDays(MyGlobalSettings.getTxnsIntableKeepDays()+BackendConstants.RECORDS_DEL_BUFFER_DAYS);
        Date txnInDbFrom = now.toMidnight().getTime();

        whereClause.append("archived=").append("true");
        whereClause.append(" AND create_time < '").append(txnInDbFrom.getTime()).append("'");

        return whereClause.toString();
    }

    private boolean createTxnImgDir() {
        try {
            DateUtil now = new DateUtil(CommonConstants.TIMEZONE);
            String fileDir = CommonUtils.getTxnImgDir(now.getTime());
            String filePath = fileDir + CommonConstants.FILE_PATH_SEPERATOR + BackendConstants.DUMMY_FILENAME;
            Backendless.Files.saveFile(filePath, BackendConstants.DUMMY_DATA.getBytes("UTF-8"), true);
            // Give write access to Merchants to this directory
            //FilePermission.WRITE.grantForRole("Merchant", fileDir);
            mLogger.debug("Saved dummy txn image file: " + filePath);
            return true;

        } catch (Exception e) {
            // log and ignore
            BackendUtils.handleException(e,false,mLogger,mEdr);
        }
        return false;
    }

}
