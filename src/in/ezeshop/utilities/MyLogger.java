package in.ezeshop.utilities;

import com.backendless.Backendless;
import com.backendless.logging.Logger;
import in.ezeshop.common.constants.CommonConstants;
import in.ezeshop.common.constants.DbConstants;
import in.ezeshop.constants.BackendConstants;

/**
 * Created by adgangwa on 19-08-2016.
 */
public class MyLogger {
    
    private Logger mLogger;
    private Logger mEdrLogger;
    private StringBuilder mSb;

    private String mLogId;
    private String mUserId ="";
    private String mUserType ="";
    private boolean mDebugLogs;

    public MyLogger(String loggerName) {
        Backendless.Logging.setLogReportingPolicy(BackendConstants.LOG_POLICY_NUM_MSGS, BackendConstants.LOG_POLICY_FREQ_SECS);

        mLogger = Backendless.Logging.getLogger(loggerName);
        mEdrLogger = Backendless.Logging.getLogger("utilities.edr");
        mLogId = IdGenerator.generateLogId();
        mDebugLogs = BackendConstants.FORCED_DEBUG_LOGS;

        if(BackendConstants.DEBUG_MODE) {
            mSb = new StringBuilder();
        }
    }

    public void setProperties(String userId, int userType, boolean argDebugLogs) {
        mUserId = userId;
        mUserType = DbConstants.userTypeDesc[userType];
        if(BackendConstants.FORCED_DEBUG_LOGS) {
            mDebugLogs = true;
        } else {
            mDebugLogs = argDebugLogs;
        }
    }

    public void debug(String msg) {
        msg = mLogId +" | "+ mUserId +" | "+ mUserType +" | "+msg;
        if(mDebugLogs) {
            mLogger.debug(msg);
        }

        if(BackendConstants.DEBUG_MODE) {
            msg = "Debug | "+msg;
            //System.out.println(msg);
            mSb.append(CommonConstants.NEWLINE_SEP).append(msg).append(",").append(mDebugLogs);
        }
    }

    public void info(String msg) {
        msg = mLogId +" | "+ mUserId +" | "+ mUserType +" | "+msg;
        mLogger.error(msg);
        if(BackendConstants.DEBUG_MODE) {
            msg = "Info | "+msg;
            //System.out.println(msg);
            mSb.append(CommonConstants.NEWLINE_SEP).append(msg);
        }
    }

    public void error(String msg) {
        msg = mLogId +" | "+ mUserId +" | "+ mUserType +" | "+msg;
        mLogger.error(msg);
        if(BackendConstants.DEBUG_MODE) {
            msg = "Error | "+msg;
            //System.out.println(msg);
            mSb.append(CommonConstants.NEWLINE_SEP).append(msg);
        }
    }

    public void error(String msg, Exception e) {
        msg = mLogId +" | "+ mUserId +" | "+ mUserType +" | "+msg;
        mLogger.error(msg);
        mLogger.error(e.getMessage());
        mLogger.error(BackendUtils.stackTraceStr(e));
        if(BackendConstants.DEBUG_MODE) {
            msg = "Error | "+msg;
            //System.out.println(msg);
            mSb.append(CommonConstants.NEWLINE_SEP).append(msg);
        }
    }

    public void fatal(String msg) {
        msg = mLogId +" | "+ mUserId +" | "+ mUserType +" | "+msg;
        mLogger.fatal(msg);
        if(BackendConstants.DEBUG_MODE) {
            msg = "Fatal | "+msg;
            //System.out.println(msg);
            mSb.append(CommonConstants.NEWLINE_SEP).append(msg);
        }
    }

    public void warn(String msg) {
        msg = mLogId +" | "+ mUserId +" | "+ mUserType +" | "+msg;
        mLogger.warn(msg);
        if(BackendConstants.DEBUG_MODE) {
            msg = "Warning | "+msg;
            //System.out.println(msg);
            mSb.append(CommonConstants.NEWLINE_SEP).append(msg);
        }
    }

    public void edr(String[] edrData) {
        // replace constants with corresponding string values
        if(edrData[BackendConstants.EDR_USER_TYPE_IDX]!=null) {
            edrData[BackendConstants.EDR_USER_TYPE_IDX] = DbConstants.userTypeDesc[Integer.parseInt(edrData[BackendConstants.EDR_USER_TYPE_IDX])];
        }

        StringBuilder sbEdr = new StringBuilder(BackendConstants.BACKEND_EDR_MAX_SIZE);
        for (String s: edrData)
        {
            if(s==null) {
                sbEdr.append(BackendConstants.BACKEND_EDR_DELIMETER);
            } else {
                sbEdr.append(s).append(BackendConstants.BACKEND_EDR_DELIMETER);
            }
        }

        String edr = mLogId +" | EDR | "+sbEdr.toString();
        if( edrData[BackendConstants.EDR_RESULT_IDX].equals(BackendConstants.BACKEND_EDR_RESULT_NOK) ||
                (edrData[BackendConstants.EDR_SPECIAL_FLAG_IDX]!=null && !edrData[BackendConstants.EDR_SPECIAL_FLAG_IDX].isEmpty()) ||
                (edrData[BackendConstants.EDR_IGNORED_ERROR_IDX]!=null && !edrData[BackendConstants.EDR_IGNORED_ERROR_IDX].isEmpty()) ) {
            mEdrLogger.error(edr);
        } else {
            mEdrLogger.info(edr);
        }
        if(BackendConstants.DEBUG_MODE) {
            mSb.append(CommonConstants.NEWLINE_SEP).append(edr);
        }
    }

    public void flush() {
        //Backendless.Logging.flush();
        try {
            if (BackendConstants.DEBUG_MODE) {
                String filePath = CommonConstants.MERCHANT_LOGGING_ROOT_DIR + "myBackendLog.txt";
                Backendless.Files.saveFile(filePath, mSb.toString().getBytes("UTF-8"), true);
            }
        } catch(Exception e) {
            //ignore exception
        }
    }
}
