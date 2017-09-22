package in.ezeshop.services;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.IBackendlessService;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.DateUtil;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
//import in.ezeshop.database.CardIdBatches;
import in.ezeshop.database.InternalUser;
import in.ezeshop.database.MerchantIdBatches;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;
import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;
import in.ezeshop.utilities.SecurityHelper;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by adgangwa on 19-07-2016.
 */
public class AdminServices implements IBackendlessService {

    private static final String ADMIN_LOGINID = "admin";

    private MyLogger mLogger = new MyLogger("services.AdminServices");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];

    /*
     * Public methods: Backend REST APIs
     */
    public void registerAgent(String argUserId, String mobile, String name, String dob, String pwd) {
        registerInternalUser(argUserId, DbConstants.USER_TYPE_AGENT, mobile, name, dob, pwd);
    }

    public void registerCCUser(String argUserId, String mobile, String name, String dob, String pwd) {
        registerInternalUser(argUserId, DbConstants.USER_TYPE_CC, mobile, name, dob, pwd);
    }

    /*public void registerCCntUser(String argUserId, String mobile, String name, String dob, String pwd) {
        registerInternalUser(argUserId, DbConstants.USER_TYPE_CCNT, mobile, name, dob, pwd);
    }*/


    /*
     * Merchant Manual Request Operations
     */
    public void mchntSendPasswdResetHint(String merchantId, String ticketNum, String reason, String remarks, String adminPwd) {
        execMchntManualOp(DbConstants.OP_SEND_PASSWD_RESET_HINT, merchantId, ticketNum, reason, remarks, "", adminPwd);
    }
    public void mchntEnableAccount(String merchantId, String ticketNum, String reason, String remarks, String adminPwd) {
        execMchntManualOp(DbConstants.OP_ENABLE_ACC, merchantId, ticketNum, reason, remarks, "", adminPwd);
    }
    /*public void mchntResetTrustedDevices(String merchantId, String ticketNum, String reason, String remarks, String adminPwd) {
        execMchntManualOp(DbConstants.OP_RESET_TRUSTED_DEVICES, merchantId, ticketNum, reason, remarks, "", adminPwd);
    }*/
    public void mchntChangeMobileNum(String newMobile, String merchantId, String ticketNum, String reason, String remarks, String adminPwd) {
        execMchntManualOp(DbConstants.OP_CHANGE_MOBILE, merchantId, ticketNum, reason, remarks, newMobile, adminPwd);
    }
    public void mchntAccountClosure(String merchantId, String ticketNum, String reason, String remarks, String adminPwd) {
        execMchntManualOp(DbConstants.OP_ACC_CLOSURE, merchantId, ticketNum, reason, remarks, "", adminPwd);
    }
    public void mchntCancelAccountClosure(String merchantId, String ticketNum, String reason, String remarks, String adminPwd) {
        execMchntManualOp(DbConstants.OP_CANCEL_ACC_CLOSURE, merchantId, ticketNum, reason, remarks, "", adminPwd);
    }

    /*
     * Single function for all Merchant Manual Request Operations
     */
    private void execMchntManualOp(String manualOp, String merchantId, String ticketNum, String reason, String remarks,
                                   String newMobileNum, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "execMchntManualOp:"+manualOp;
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = merchantId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    ticketNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    reason+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    newMobileNum;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            // fetch merchant object
            Merchants merchant = BackendOps.getMerchant(merchantId, true, false);
            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();

            int oldStatus = merchant.getAdmin_status();
            Date oldUpdateTime = merchant.getStatus_update_time();
            String oldMobile = merchant.getMobile_num();
            String oldReason = merchant.getStatus_reason();
            mLogger.debug("oldStatus: "+oldStatus+", oldTime: "+oldUpdateTime.toString());

            // Add merchant op first - then update status
            MerchantOps op = new MerchantOps();
            op.setCreateTime(new Date());
            op.setMerchant_id(merchant.getAuto_id());
            op.setMobile_num(merchant.getMobile_num());
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            op.setTicketNum(ticketNum);
            op.setReason(reason);
            op.setRemarks(remarks);
            op.setAgentId(ADMIN_LOGINID);
            op.setInitiatedBy( DbConstantsBackend.USER_OP_INITBY_MCHNT);
            op.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_MANUAL);
            op.setOp_code(manualOp);
            // set extra params - as per op value
            if(manualOp.equals(DbConstants.OP_CHANGE_MOBILE)) {
                String extraParams = "Old Mobile: "+oldMobile+", New Mobile: "+newMobileNum;
                op.setExtra_op_params(extraParams);
            }
            op = BackendOps.saveMerchantOp(op);

            // process as per possible manual operation
            int newStatus;
            String smsExtraParam = null;
            try {
                switch (manualOp) {
                    case DbConstants.OP_SEND_PASSWD_RESET_HINT:
                        smsExtraParam = merchant.getDob();
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        break;

                    case DbConstants.OP_ENABLE_ACC:
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        break;

                    case DbConstants.OP_CHANGE_MOBILE:
                        // disable if not already disabled
                        // if already disabled - fx will simply return same object
                        //merchant = BackendUtils.setMerchantStatus(merchant, DbConstants.USER_STATUS_DISABLED, reason, mEdr, mLogger);
                        merchant.setMobile_num(newMobileNum);
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        smsExtraParam = oldMobile;
                        break;

                    /*case DbConstants.OP_RESET_TRUSTED_DEVICES:
                        merchant.setTrusted_devices(null);
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        break;*/

                    case DbConstants.OP_ACC_CLOSURE:
                        // merchant should be in active state
                        if(merchant.getAdmin_status() != DbConstants.USER_STATUS_ACTIVE) {
                            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Merchant account is not active");
                        }
                        // disable cb and cl add
                        merchant.setCb_rate("0");
                        merchant.setCl_add_enable(false);
                        merchant.setPrepaidCbRate("0");
                        merchant.setPrepaidCbMinAmt(0);
                        merchant.setRemoveReqDate(new Date());

                        newStatus = DbConstants.USER_STATUS_UNDER_CLOSURE;
                        break;

                    case DbConstants.OP_CANCEL_ACC_CLOSURE:
                        // merchant should be in closure state
                        if(merchant.getAdmin_status() != DbConstants.USER_STATUS_UNDER_CLOSURE) {
                            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Merchant account is not under closure state");
                        }
                        merchant.setRemoveReqDate(null);
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        break;

                    default:
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Invalid Manual operation");
                }

                // any applicable merchant updates done
                if(merchant.getAdmin_status().equals(newStatus)) {
                    // old and new status is same
                    // update merchant object
                    merchant = BackendOps.updateMerchant(merchant);
                } else {
                    // old and new status are different
                    // change status - this will save the updated merchant object too
                    merchant = BackendUtils.setMerchantStatus(merchant, newStatus, reason, mEdr, mLogger);
                }

                // ignore status - as will be entered in failedSMS table and retried itself
                sendMchntManualReqSms(manualOp, merchant, smsExtraParam);

            } catch(Exception e) {
                mLogger.error("execMchntManualOp: Exception while processing manual op: "+manualOp+", "+merchantId);
                // Rollback - delete merchant op added
                try {
                    BackendOps.deleteMerchantOp(op);
                } catch(Exception ex) {
                    mLogger.fatal("execMchntManualOp: Failed to rollback: merchant op deletion failed: "+merchantId);
                    // Rollback also failed
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    throw ex;
                }
                throw e;
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    private boolean sendMchntManualReqSms(String manualOp, Merchants merchant, String extraParam) {
        boolean status = true;

        switch (manualOp) {
            case DbConstants.OP_SEND_PASSWD_RESET_HINT:
                String smsText = String.format(SmsConstants.SMS_ADMIN_MCHNT_SEND_PSWD_RESET_HINT,extraParam);
                status = SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);
                break;

            case DbConstants.OP_ENABLE_ACC:
                smsText = String.format(SmsConstants.SMS_ADMIN_MCHNT_ACC_ENABLE,CommonUtils.getHalfVisibleStr(merchant.getAuto_id()));
                status = SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);
                break;

            case DbConstants.OP_CHANGE_MOBILE:
                // Send SMS on old and new mobile
                smsText = String.format(SmsConstants.SMS_ADMIN_MCHNT_MOBILE_CHANGE,
                        CommonUtils.getHalfVisibleStr(merchant.getAuto_id()),
                        CommonUtils.getHalfVisibleMobileNum(merchant.getMobile_num()));
                status = SmsHelper.sendSMS(smsText, extraParam + "," + merchant.getMobile_num(), mEdr, mLogger, true);
                break;

            /*case DbConstants.OP_RESET_TRUSTED_DEVICES:
                smsText = SmsConstants.SMS_ADMIN_MCHNT_RESET_TRUSTED_DEVICES;
                status = SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);
                break;*/

            case DbConstants.OP_ACC_CLOSURE:
                DateUtil now = new DateUtil(CommonConstants.TIMEZONE);
                now.addDays(MyGlobalSettings.getMchntExpiryDays());
                SimpleDateFormat sdf = new SimpleDateFormat(CommonConstants.DATE_FORMAT_ONLY_DATE_DISPLAY, CommonConstants.DATE_LOCALE);
                sdf.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));

                smsText = String.format(SmsConstants.SMS_ADMIN_MCHNT_ACC_CLOSURE,
                        CommonUtils.getHalfVisibleStr(merchant.getAuto_id()),
                        String.valueOf(MyGlobalSettings.getMchntExpiryDays()),
                        sdf.format(now.getTime()));

                status = SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);
                break;

            case DbConstants.OP_CANCEL_ACC_CLOSURE:
                smsText = SmsConstants.SMS_ADMIN_MCHNT_CANCEL_ACC_CLOSURE;
                status = SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);
                break;

            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Invalid Manual operation");
        }

        return status;
    }

    /*
     * Customer Manual Request Operations
     */
    public void custResetPin(String customerId, String ticketNum, String reason, String remarks, String adminPwd) {
        execCustManualOp(DbConstants.OP_RESET_PIN, customerId, ticketNum, reason, remarks, "", adminPwd);
    }
    public void custEnableAccount(String customerId, String ticketNum, String reason, String remarks, String adminPwd) {
        execCustManualOp(DbConstants.OP_ENABLE_ACC, customerId, ticketNum, reason, remarks, "", adminPwd);
    }
    public void custChangeMobileNum(String newMobile, String customerId, String ticketNum, String reason, String remarks, String adminPwd) {
        execCustManualOp(DbConstants.OP_CHANGE_MOBILE, customerId, ticketNum, reason, remarks, newMobile, adminPwd);
    }

    /*
     * Single function for all Merchant Manual Request Operations
     */
    private void execCustManualOp(String manualOp, String customerId, String ticketNum, String reason, String remarks,
                                   String newMobileNum, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "execCustManualOp:"+manualOp;
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = customerId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    ticketNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    reason+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    newMobileNum;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            // fetch customer object
            Customers customer = BackendOps.getCustomer(customerId, CommonUtils.getCustomerIdType(customerId), false);
            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = customer.getPrivate_id();

            int oldStatus = customer.getAdmin_status();
            Date oldUpdateTime = customer.getStatus_update_time();
            String oldMobile = customer.getMobile_num();
            String oldReason = customer.getStatus_reason();
            mLogger.debug("oldStatus: "+oldStatus+", oldTime: "+oldUpdateTime.toString());

            // Add customer op first - then update status
            CustomerOps op = new CustomerOps();
            op.setCreateTime(new Date());
            op.setPrivateId(customer.getPrivate_id());
            op.setMobile_num(customer.getMobile_num());
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            op.setTicketNum(ticketNum);
            op.setReason(reason);
            op.setRemarks(remarks);
            op.setRequestor_id(ADMIN_LOGINID);
            op.setInitiatedBy( DbConstantsBackend.USER_OP_INITBY_CUSTOMER);
            op.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_MANUAL);
            op.setOp_code(manualOp);
            op.setImgFilename("");
            // set extra params - as per op value
            if(manualOp.equals(DbConstants.OP_CHANGE_MOBILE)) {
                String extraParams = "Old Mobile: "+oldMobile+", New Mobile: "+newMobileNum;
                op.setExtra_op_params(extraParams);
            }
            op = BackendOps.saveCustomerOp(op);

            // process as per possible manual operation
            int newStatus = DbConstants.USER_STATUS_ACTIVE;
            String smsExtraParam = null;
            boolean dntUpdate = false;
            try {
                switch (manualOp) {
                    case DbConstants.OP_RESET_PIN:
                        // For PIN reset request - check if any already pending
                        if( BackendOps.findActiveCustPinResetReqs(customer.getPrivate_id()) != null) {
                            throw new BackendlessException(String.valueOf(ErrorCodes.DUPLICATE_ENTRY), "");
                        }
                        smsExtraParam = SecurityHelper.generateCustPin(customer, mLogger);
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        break;

                    case DbConstants.OP_ENABLE_ACC:
                        newStatus = DbConstants.USER_STATUS_ACTIVE;
                        break;

                    case DbConstants.OP_CHANGE_MOBILE:
                        // disable if not already disabled
                        // if already disabled - fx will simply return same object
                        //merchant = BackendUtils.setMerchantStatus(merchant, DbConstants.USER_STATUS_DISABLED, reason, mEdr, mLogger);
                        customer = changeCustomerMobile(oldMobile, newMobileNum);
                        smsExtraParam = oldMobile;
                        dntUpdate = true;
                        break;

                    default:
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Invalid Manual operation");
                }

                // any applicable merchant updates done
                if(!dntUpdate) {
                    if (customer.getAdmin_status().equals(newStatus)) {
                        // old and new status is same - update customer object only
                        customer = BackendOps.updateCustomer(customer);
                    } else {
                        // old and new status are different
                        // change status - this will save the updated merchant object too
                        BackendUtils.setCustomerStatus(customer, newStatus, reason, mEdr, mLogger);
                    }
                }

                // ignore status - as will be entered in failedSMS table and retried itself
                sendCustManualReqSms(manualOp, customer, smsExtraParam);

            } catch(Exception e) {
                mLogger.error("execCustManualOp: Exception while processing manual op: "+manualOp+", "+customerId);
                // Rollback - delete merchant op added
                try {
                    BackendOps.deleteCustomerOp(op);
                } catch(Exception ex) {
                    mLogger.fatal("execCustManualOp: Failed to rollback: customer op deletion failed: "+customerId);
                    // Rollback also failed
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    throw ex;
                }
                throw e;
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    private Customers changeCustomerMobile(String oldMobile, String newMobile) {

        BackendlessUser custUser = BackendOps.fetchUser(oldMobile, DbConstants.USER_TYPE_CUSTOMER, false);
        Customers customer = (Customers) custUser.getProperty("customer");

        // update mobile number
        custUser.setProperty("user_id", newMobile);
        customer.setMobile_num(newMobile);
        // update status to 'restricted access'
        // not using setCustomerStatus() fx. - to avoid two DB operations
        customer.setAdmin_status(DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY);
        //customer.setStatus_reason("Mobile Number changed in last "+MyGlobalSettings.getCustAccLimitModeHrs()+" hours");
        customer.setStatus_reason("Mobile Number changed recently");
        customer.setStatus_update_time(new Date());

        custUser.setProperty("customer", customer);
        BackendOps.updateUser(custUser);
        return customer;
    }

    private boolean sendCustManualReqSms(String manualOp, Customers customer, String extraParam) {
        boolean status = true;

        switch (manualOp) {
            case DbConstants.OP_RESET_PIN:
                String smsText = String.format(SmsConstants.SMS_ADMIN_CUST_RESET_PIN,extraParam);
                status = SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);
                break;

            case DbConstants.OP_ENABLE_ACC:
                smsText = String.format(SmsConstants.SMS_ADMIN_CUST_ACC_ENABLE,CommonUtils.getHalfVisibleMobileNum(customer.getMobile_num()));
                status = SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);
                break;

            case DbConstants.OP_CHANGE_MOBILE:
                // Send SMS on old and new mobile
                smsText = String.format(SmsConstants.SMS_ADMIN_CUST_MOBILE_CHANGE,
                        CommonUtils.getHalfVisibleMobileNum(customer.getMobile_num()));
                status = SmsHelper.sendSMS(smsText, extraParam + "," + customer.getMobile_num(), mEdr, mLogger, true);
                break;

            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Invalid Manual operation");
        }

        return status;
    }

    /*
     * Merchant ID and Card ID Batch create/open methods
     *
     * Merchant ID Format below:
     * 8 digit - Merchant ID
	 *  : <1-2 digit country code> + <1-2 digit range id> + <2 digit batch id> + <3 digit s.no.>
	 *   : <1-2 digit country code>
		 : 1-99 possible values (0 not valid value)
		 : 1 crore (10 million) merchants per 1 digit country code
		 : 10 lakh (1 million) merchants per 2 digit country code
		 : start with '1' for India
	 *  : <1-2 digit range id>
		 : 1 lakh merchant ids per range id
		 : 2 digit (00-99) i.e. 100 ranges per 1 digit country code
		 : 1 digit (0-9) i.e. 10 ranges per 2 digit country code
		 : start backwards from 'max value' i.e. 99/9 -> 00/0
	 *  : <2 digit batch id>
	     : 00-99 possible values
	     : Batch id 0(i.e. 00) is reserved for now
	     : So, 99 batches per range id + 1 reserved one
	     : So, 99 x 100 = 9,900 batches per 1 digit country code (like India)
	     : start from 01 -> 99
	 *  : <3 digit s.no.>
	     : starts from 000 -> 999
	     : 1000 merchant ids per batch id
	     : 1000 x 99 = 99,000 merchant ids per range id (+1000 from reserved batch id 0)
     */
    public void createMerchantIdBatches(String countryCode, String rangeId, int batchCnt, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "createMerchantIdBatches";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = countryCode+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    rangeId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    batchCnt;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            mLogger.debug("In createMerchantIdBatches: "+countryCode+": "+rangeId);
            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            String tableName = DbConstantsBackend.MERCHANT_ID_BATCH_TABLE_NAME+countryCode;
            // get highest 'batch id' from already created batches - for given range id
            MerchantIdBatches highestBatch = BackendOps.firstMerchantIdBatchByBatchId(tableName, "rangeId = '"+rangeId+"'", true);

            int highestBatchId = (highestBatch!=null) ? highestBatch.getBatchId() : 0;
            if(highestBatchId >= BackendConstants.MERCHANT_ID_MAX_BATCH_ID_PER_RANGE) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "All batches already available: "+countryCode+","+rangeId);
            }

            // batch will start from 1, and not 0, which is reserved for future use
            int startIdx = highestBatchId+1;
            int endIdx = startIdx+batchCnt;
            if(endIdx > BackendConstants.MERCHANT_ID_MAX_BATCH_ID_PER_RANGE) {
                endIdx = BackendConstants.MERCHANT_ID_MAX_BATCH_ID_PER_RANGE;
            }

            // add batches for this range
            // assuming 2 digit batch ids from 01 - 99 (00 reserved for now)
            for(int i=startIdx; i<endIdx; i++) {
                MerchantIdBatches batch = new MerchantIdBatches();
                batch.setStatus(DbConstantsBackend.BATCH_STATUS_AVAILABLE);
                batch.setRangeId(rangeId);
                batch.setBatchId(i);
                String batchId = String.format("%02d",i);
                batch.setRangeBatchId(rangeId+batchId);
                batch.setStatusTime(new Date());
                BackendOps.saveMerchantIdBatch(tableName, batch);
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void openNextMerchantIdBatch(String countryCode, String rangeId, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "openNextMerchantIdBatch";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = countryCode+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    rangeId;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            mLogger.debug("In openNextMerchantIdBatch: "+countryCode+": "+rangeId);
            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            String tableName = DbConstantsBackend.MERCHANT_ID_BATCH_TABLE_NAME+countryCode;

            // get current open batch
            MerchantIdBatches openBatch = BackendOps.fetchMerchantIdBatch(tableName,
                    "status = '"+DbConstantsBackend.BATCH_STATUS_OPEN +"'");
            if(openBatch!=null && !openBatch.getRangeId().equals(rangeId)) {
                // If rangeId of 'current open batch' is different from the one provided
                // first close the current open batch manually, and then try again.
                // Check added just to be more sure - when new range is being opened
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "RangeId of 'current open batch' is different : "+countryCode+","+rangeId);
            }

            // check if current open batch is still empty
            if(openBatch != null) {
                String merchantIdPrefix = countryCode+openBatch.getRangeBatchId();
                int cnt = BackendOps.getMerchantCnt("auto_id like '"+merchantIdPrefix+"%'");
                if(cnt < BackendConstants.MERCHANT_ID_MAX_SNO_PER_BATCH) {
                    throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Current open batch still empty : "+cnt+","+countryCode+","+rangeId);
                }

                // close current open batch
                openBatch.setStatus(DbConstantsBackend.BATCH_STATUS_CLOSED);
                BackendOps.saveMerchantIdBatch(tableName, openBatch);
            }

            // find next available batch
            MerchantIdBatches lowestBatch = BackendOps.firstMerchantIdBatchByBatchId(tableName,
                    "rangeId = '"+rangeId+"' and status = '"+DbConstantsBackend.BATCH_STATUS_AVAILABLE +"'",
                    false);
            if(lowestBatch==null) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "No available batch in given range id : "+countryCode+","+rangeId);
            }

            // update status of the batch
            lowestBatch.setStatus(DbConstantsBackend.BATCH_STATUS_OPEN);
            BackendOps.saveMerchantIdBatch(tableName, lowestBatch);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    /*
    public void createCardIdBatches(String countryCode, String rangeId, int batchCnt, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "createCardIdBatches";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = countryCode+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    rangeId;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            mLogger.debug("In createCardBatches: "+countryCode+": "+rangeId);
            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            String tableName = DbConstantsBackend.CARD_ID_BATCH_TABLE_NAME+countryCode;
            // get highest 'batch id' from already created batches - for given range id
            CardIdBatches highestBatch = BackendOps.firstCardIdBatchByBatchId(tableName, "rangeId = '"+rangeId+"'", true);

            int highestBatchId = (highestBatch!=null) ? highestBatch.getBatchId() : -1;
            if(highestBatchId >= BackendConstants.CARD_ID_MAX_BATCH_ID_PER_RANGE) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "All batches already available: "+countryCode+","+rangeId);
            }

            int startIdx = highestBatchId+1;
            int endIdx = startIdx+batchCnt;
            if(endIdx > BackendConstants.CARD_ID_MAX_BATCH_ID_PER_RANGE) {
                endIdx = BackendConstants.CARD_ID_MAX_BATCH_ID_PER_RANGE;
            }

            // add batches for this range
            // assuming 3 digit batch ids from 000 - 999
            for(int i=startIdx; i<endIdx; i++) {
                CardIdBatches batch = new CardIdBatches();
                batch.setStatus(DbConstantsBackend.BATCH_STATUS_AVAILABLE);
                batch.setRangeId(rangeId);
                batch.setBatchId(i);
                String rangeBatchId = rangeId+String.format("%03d",i);
                batch.setRangeBatchId(rangeBatchId);
                batch.setStatusTime(new Date());
                BackendOps.saveCardIdBatch(tableName, batch);
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void openNextCardIdBatch(String countryCode, String rangeId, String adminPwd, String cardKey) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "openNextCardIdBatch";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = countryCode+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    rangeId;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            mLogger.debug("In openNextCardIdBatch: "+countryCode+": "+rangeId);

            // Get key - before logging as Admin - as getting key will need to login as keyadmin
            //String key = SecurityHelper.getKey(SecurityHelper.MEMBERCARD_KEY_COL_NAME, keyadminPwd, mLogger);

            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            String tableName = DbConstantsBackend.CARD_ID_BATCH_TABLE_NAME+countryCode;

            // Sanity checks for all open card id batches
            // 1) All open batches should have exactly 'CARD_ID_MAX_SNO_PER_BATCH+1' card rows (ir-respective of status)
            // 2) If the open batch has no card in 'FOR PRINT' status - then close the same.
            List<CardIdBatches> batches = BackendOps.fetchOpenCardIdBatches(tableName);
            if(batches!=null) {
                // Open batches available
                for (CardIdBatches batch : batches) {
                    String cardIdPrefix = BackendConstants.MY_CARD_ISSUER_ID + countryCode + batch.getRangeBatchId();
                    int cnt = BackendOps.getCardCnt("card_id like '"+cardIdPrefix+"%'");
                    if(cnt <= BackendConstants.CARD_ID_MAX_SNO_PER_BATCH) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "An open batch partially free : "+cardIdPrefix);
                    }

                    // check if should be closed
                    cnt = BackendOps.getCardCnt("card_id like '"+cardIdPrefix+"%' AND status = "+DbConstants.CUSTOMER_CARD_STATUS_FOR_PRINT);
                    if(cnt==0) {
                        // All cards in this batch are available and provisioned in DB
                        batch.setStatus(DbConstantsBackend.BATCH_STATUS_CLOSED);
                        BackendOps.saveCardIdBatch(tableName, batch);
                    }
                }
            }

            // find next available batch
            CardIdBatches lowestBatch = BackendOps.firstCardIdBatchByBatchId(tableName,
                    "rangeId = '"+rangeId+"' and status = '"+DbConstantsBackend.BATCH_STATUS_AVAILABLE+"'",
                    false);
            if(lowestBatch==null) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "No available batch in given range id : "+countryCode+","+rangeId);
            }

            // to be used for barcode generation
            SecureRandom random = new SecureRandom();

            // create member card rows for this batch
            String cardIdPrefix = BackendConstants.MY_CARD_ISSUER_ID + countryCode + lowestBatch.getRangeBatchId();
            for(int i=BackendConstants.CARD_ID_MIN_SNO_PER_BATCH; i<=BackendConstants.CARD_ID_MAX_SNO_PER_BATCH; i++) {
                CustomerCards card = new CustomerCards();
                String cardNum = cardIdPrefix + String.format("%03d",i);
                card.setCardNum(cardNum);
                card.setCard_id(SecurityHelper.getCardIdFromNum(cardNum, cardKey, mLogger));
                // check if its being decoded properly
                String decodedCardNum = SecurityHelper.getCardNumFromId(card.getCard_id(), cardKey, mLogger);
                if(!decodedCardNum.equals(cardNum)) {
                    throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Decoded cardNum not same as encoded");
                }

                // set barcode number
                char[] id = new char[BackendConstants.CUSTOMER_CARD_BARCODE_SALT_LEN];
                for (int k = 0; k < BackendConstants.CUSTOMER_CARD_BARCODE_SALT_LEN; k++) {
                    id[k] = BackendConstants.onlyNumbers[random.nextInt(BackendConstants.onlyNumbers.length)];
                }
                String barcode = CommonConstants.MEMBER_BARCODE_ID_PREFIX + cardNum + new String(id);
                card.setBarCode(barcode);

                card.setStatus(DbConstants.CUSTOMER_CARD_STATUS_FOR_PRINT);
                card.setStatus_update_time(new Date());
                BackendOps.saveCustomerCard(card);
            }

            // Count how many rows created
            int cnt = BackendOps.getCardCnt("cardNum like '"+cardIdPrefix+"%' AND status = "+DbConstants.CUSTOMER_CARD_STATUS_FOR_PRINT);
            if(cnt != (BackendConstants.CARD_ID_MAX_SNO_PER_BATCH+1)) {
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Not all Card rows are created : "+cnt);
            }

            // update status of the batch
            lowestBatch.setStatus(DbConstantsBackend.BATCH_STATUS_OPEN);
            BackendOps.saveCardIdBatch(tableName, lowestBatch);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }
    */

    public void uploadGlobalSettings(String adminId, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "uploadGlobalSettings";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = "";
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            // Global Settings table should be empty - check for the same
            if(BackendOps.getGlobalSettingCnt() > 0) {
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "GlobalSettings Table is not empty");
            }

            // Loop on Values Map and save all
            for (Map.Entry<String, String> pair : GlobalSettingConstants.valuesGlobalSettings.entrySet()) {
                String key = pair.getKey();

                GlobalSettings setting = new GlobalSettings();
                setting.setName(key);
                setting.setDescription(GlobalSettingConstants.descGlobalSettings.get(key));
                Integer valueType = GlobalSettingConstants.valueTypesGlobalSettings.get(key);
                setting.setValue_datatype(valueType);

                String value = GlobalSettingConstants.valuesGlobalSettings.get(key);
                if(value!=null) {
                    switch (valueType) {
                        case GlobalSettingConstants.DATATYPE_INT:
                            setting.setValue_int(Integer.parseInt(value));
                            break;
                        case GlobalSettingConstants.DATATYPE_BOOLEAN:
                            boolean val = Boolean.parseBoolean(value);
                            setting.setValue_int(val ? 1 : 0);
                            break;
                        case GlobalSettingConstants.DATATYPE_STRING:
                            setting.setValue_string(value);
                            break;
                        case GlobalSettingConstants.DATATYPE_DATE:
                            long epochTime = Long.parseLong(value);
                            setting.setValue_date(new Date(epochTime));
                            break;
                        default:
                            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Invalid Datatype: " + key);
                    }
                }

                if(setting.getDescription()==null || setting.getDescription().isEmpty()) {
                    setting.setUser_visible(false);
                } else {
                    setting.setUser_visible(true);
                }

                BackendOps.saveGlobalSetting(setting);
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void createDummyMerchant(String internalUserId, String adminPwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "createDummyMerchant";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = internalUserId;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,adminPwd);

            // Fetch internal user
            InternalUser user = BackendOps.getInternalUser(internalUserId);
            Merchants merchant = createDummyMchnt(user.getMobile_num(), user.getDob());
            BackendUtils.registerMerchant(merchant, user.getId(), mLogger, mEdr);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    /*
     * Private helper methods
     */
    private void registerInternalUser(String argUserId, int userType, String mobile, String name, String dob, String pwd) {
        long startTime = System.currentTimeMillis();
        try {
            BackendUtils.initAll();
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "registerInternalUser";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = argUserId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    userType+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    mobile+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    name+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                    dob;
            mEdr[BackendConstants.EDR_USER_ID_IDX] = ADMIN_LOGINID;
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(DbConstants.USER_TYPE_ADMIN);
            mLogger.setProperties(ADMIN_LOGINID, DbConstants.USER_TYPE_ADMIN, true);

            mLogger.debug("In registerInternalUser: "+argUserId+": "+mobile);
            //mLogger.debug("Before: "+ HeadersManager.getInstance().getHeaders().toString());

            String prefix = null;
            switch (userType) {
                case DbConstants.USER_TYPE_AGENT:
                    prefix = CommonConstants.PREFIX_AGENT_ID;
                    break;
                case DbConstants.USER_TYPE_CC:
                    prefix = CommonConstants.PREFIX_CC_ID;
                    break;
                /*case DbConstants.USER_TYPE_CCNT:
                    prefix = CommonConstants.PREFIX_CCNT_ID;
                    break;*/
            }
            String userId = prefix+argUserId;
            if(userId.length() != CommonConstants.INTERNAL_USER_ID_LEN) {
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "User ID length is wrong");
            }

            // login using 'admin' user
            BackendOps.loginUser(ADMIN_LOGINID,pwd);
            //mLogger.debug("Before2: "+ HeadersManager.getInstance().getHeaders().toString());

            // Create agent object and register
            InternalUser internalUser = new InternalUser();
            internalUser.setId(userId);
            internalUser.setMobile_num(mobile);
            internalUser.setDob(dob);
            internalUser.setName(name);
            internalUser.setAdmin_status(DbConstants.USER_STATUS_ACTIVE);
            internalUser.setStatus_reason(DbConstantsBackend.ENABLED_ACTIVE);

            BackendlessUser backendlessUser = new BackendlessUser();
            backendlessUser.setProperty("user_id", userId);
            backendlessUser.setPassword(dob);
            backendlessUser.setProperty("user_type", userType);
            backendlessUser.setProperty("internalUser",internalUser);

            // print roles - for debug purpose
            List<String> roles = Backendless.UserService.getUserRoles();
            mLogger.debug("Roles: "+roles.toString());

            // register the user
            backendlessUser = BackendOps.registerUser(backendlessUser);
            mLogger.debug("Internal User Registration successful");

            // assign role
            String role = null;
            switch (userType) {
                case DbConstants.USER_TYPE_AGENT:
                    role = BackendConstants.ROLE_AGENT;
                    break;
                case DbConstants.USER_TYPE_CC:
                    role = BackendConstants.ROLE_CC;
                    break;
                /*case DbConstants.USER_TYPE_CCNT:
                    role = BackendConstants.ROLE_CCNT;
                    break;*/
            }
            try {
                BackendOps.assignRole(userId, role);
            } catch (Exception e) {
                // TODO: add as 'Major' alarm - user to be removed later manually
                throw e;
            }

            // Send sms to the customer with PIN
            String smsText = String.format(SmsConstants.SMS_REG_INTERNAL_USER, userId);
            SmsHelper.sendSMS(smsText, mobile, mEdr, mLogger, true);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendOps.logoutUser();
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    private Merchants createDummyMchnt(String mobile, String dob) {
        // Create merchants object
        Merchants merchant = new Merchants();
        merchant.setName(BackendConstants.DUMMY_MCHNT_NAME);
        merchant.setDisplayImage("");
        merchant.setMobile_num(mobile);
        merchant.setEmail("");
        merchant.setBuss_category("Other");
        Address address = new Address();
        address.setCity(CommonConstants.DUMMY_CITY_NAME);
        address.setState("DummyState");
        address.setLine_1("1234, Dummy Address");
        merchant.setAddress(address);
        merchant.setDob(dob);
        merchant.setContactPhone(mobile);
        merchant.setContactName(BackendConstants.DUMMY_MCHNT_NAME);
        merchant.setRegFormNum(SecurityHelper.generateOTP());
        return merchant;
    }
}

