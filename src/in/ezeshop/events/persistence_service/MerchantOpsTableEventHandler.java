package in.ezeshop.events.persistence_service;

import com.backendless.exceptions.BackendlessException;
import com.backendless.persistence.BackendlessDataQuery;
import com.backendless.servercode.RunnerContext;
import com.backendless.servercode.annotation.Asset;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.common.database.MerchantOps;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

/**
 * MerchantOpsTableEventHandler handles events for all entities. This is accomplished
 * with the @Asset( "MerchantOps" ) annotation.
 * The methods in the class correspond to the events selected in Backendless
 * Console.
 */
@Asset( "MerchantOps" )
public class MerchantOpsTableEventHandler extends com.backendless.servercode.extension.PersistenceExtender<MerchantOps>
{
    private MyLogger mLogger = new MyLogger("events.MerchantOpsTableEventHandler");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    @Override
    public void beforeFirst( RunnerContext context ) throws Exception
    {
        // block for not-authenticated user
        // this event handler does not get called, if find done from servercode
        if(context.getUserToken()==null) {
            //initCommon();
            //mLogger.error("In beforeLast: find attempt by not-authenticated user.");
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "MerchantOps-beforeFirst";
            BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);

            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
        }
    }

    @Override
    public void beforeFind( RunnerContext context, BackendlessDataQuery query ) throws Exception
    {
        // block for not-authenticated user
        // this event handler does not get called, if find done from servercode
        if(context.getUserToken()==null) {
            //initCommon();
            //mLogger.error("In beforeLast: find attempt by not-authenticated user.");
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "MerchantOps-beforeFind";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = query.getWhereClause();
            BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);

            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
        }
    }

    @Override
    public void beforeLast( RunnerContext context ) throws Exception
    {
        // block for not-authenticated user
        // this event handler does not get called, if find done from servercode
        if(context.getUserToken()==null) {
            //initCommon();
            //mLogger.error("In beforeLast: find attempt by not-authenticated user.");
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "MerchantOps-beforeLast";
            BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
        }
    }

    /*
    private void initCommon() {
        // Init logger and utils
        Backendless.Logging.setLogReportingPolicy(BackendConstants.LOG_POLICY_NUM_MSGS, BackendConstants.LOG_POLICY_FREQ_SECS);
        Logger logger = Backendless.Logging.getLogger("com.myecash.services.MerchantOpsTableEventHandler");
        mLogger = new MyLogger(logger);
    }*/

}
/*
    @Override
    public void beforeCreate( RunnerContext context, MerchantOps merchantops) throws Exception
    {
        initCommon();
        mLogger.debug("In MerchantOpsTableEventHandler: beforeCreate");
        mLogger.debug(context.toString());

        // this will ensure that backend operations are executed, as logged-in user who called this api using generated SDK
        //HeadersManager.getInstance().addHeader( HeadersManager.HeadersEnum.USER_TOKEN_KEY, InvocationContext.getUserToken() );

        String otp = merchantops.getOtp();
        if(otp==null || otp.isEmpty()) {
            // First run, generate OTP if all fine

            // Fetch merchant
            String userid = merchantops.getMerchant_id();
            Merchants merchant = mBackendOps.getMerchant(userid, false);
            if(merchant==null) {
                CommonUtils.throwException(mLogger,mBackendOps.mLastOpStatus, mBackendOps.mLastOpErrorMsg, false);
            }

            // check if merchant is enabled
            String status = CommonUtils.checkMerchantStatus(merchant);
            if( status != null) {
                CommonUtils.throwException(mLogger,status, "Merchant account is not active", false);
            }

            // Validate based on given current number
            String oldMobile = merchantops.getMobile_num();
            String newMobile = merchantops.getExtra_op_params();
            if(!merchant.getMobile_num().equals(oldMobile)) {
                CommonUtils.throwException(mLogger,BackendResponseCodes.VERIFICATION_FAILED, "Wrong old mobile number", false);
            }

            // Generate OTP and send SMS
            AllOtp newOtp = new AllOtp();
            newOtp.setUser_id(userid);
            newOtp.setMobile_num(newMobile);
            newOtp.setOpcode(merchantops.getOp_code());
            newOtp = mBackendOps.generateOtp(newOtp);
            if(newOtp == null) {
                // failed to generate otp
                CommonUtils.throwException(mLogger,BackendResponseCodes.OTP_GENERATE_FAILED, "OTP generate failed", false);
            }

            // OTP generated successfully - return exception to indicate so
            CommonUtils.throwException(mLogger,BackendResponseCodes.OTP_GENERATED, "OTP generated successfully", true);

        } else {
            // Second run, as OTP available
            AllOtp fetchedOtp = mBackendOps.fetchOtp(merchantops.getMerchant_id());
            if( fetchedOtp == null ||
                    !mBackendOps.validateOtp(fetchedOtp, otp) ) {
                CommonUtils.throwException(mLogger,BackendResponseCodes.WRONG_OTP, "Wrong OTP provided: "+otp, false);
            }
            // remove PIN and OTP from the object
            merchantops.setOtp(null);
            merchantops.setOp_status(DbConstants.MERCHANT_OP_STATUS_OTP_MATCHED);

            mLogger.debug("OTP matched for given merchant operation: "+merchantops.getMerchant_id()+", "+merchantops.getOp_code());
        }

        //Backendless.Logging.flush();
    }

    @Override
    public void afterCreate( RunnerContext context, MerchantOps merchantops, ExecutionResult<MerchantOps> result ) throws Exception
    {
        initCommon();
        mLogger.debug("In MerchantOpsTableEventHandler: afterCreate");

        // this will ensure that backend operations are executed, as logged-in user who called this api using generated SDK
        //HeadersManager.getInstance().addHeader( HeadersManager.HeadersEnum.USER_TOKEN_KEY, InvocationContext.getUserToken() );

        String opcode = merchantops.getOp_code();
        switch(opcode) {
            case DbConstants.OP_CHANGE_MOBILE:
                changeMerchantMobile(merchantops);
                break;
            default:
                mLogger.error("Invalid Merchant operation: "+opcode);
        }

        //Backendless.Logging.flush();
    }


    private void initCommon() {
        // Init logger and utils
        Backendless.Logging.setLogReportingPolicy(BackendConstants.LOG_POLICY_NUM_MSGS, BackendConstants.LOG_POLICY_FREQ_SECS);
        mLogger = Backendless.Logging.getLogger("com.myecash.services.CustomerOpsTableEventHandler");
        mBackendOps = new BackendOps(mLogger);
    }

    private void changeMerchantMobile(MerchantOps merchantOp) {
        // Fetch merchant
        String userid = merchantOp.getMerchant_id();
        Merchants merchant = mBackendOps.getMerchant(userid, false);
        if(merchant==null) {
            CommonUtils.throwException(mLogger,mBackendOps.mLastOpStatus, mBackendOps.mLastOpErrorMsg, false);
        }

        // check if merchant is enabled
        String status = CommonUtils.checkMerchantStatus(merchant);
        if( status != null) {
            CommonUtils.throwException(mLogger,status, "Merchant account is not active", false);
        }

        // Update with new mobile number
        merchant.setMobile_num(merchantOp.getExtra_op_params());
        merchant = mBackendOps.updateMerchant(merchant);
        if(merchant == null) {
            CommonUtils.throwException(mLogger,mBackendOps.mLastOpStatus, mBackendOps.mLastOpErrorMsg, false);
        }
    }
*/