package in.ezeshop.services;

import com.backendless.BackendlessUser;
import com.backendless.HeadersManager;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.IBackendlessService;
import com.backendless.servercode.InvocationContext;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.MyErrorParams;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.AllOtp;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.SecurityHelper;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.common.database.CustomerOps;
import in.ezeshop.database.InternalUser;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;
import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

import java.util.Date;
import java.util.List;

/**
 * Created by adgangwa on 19-07-2016.
 */

public class CommonServices implements IBackendlessService {

    private MyLogger mLogger = new MyLogger("services.CommonServices");;
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];

    /*
     * Public methods: Backend REST APIs
     */
    public void isSessionValid() {
        // Just to check if user have valid session
        BackendUtils.initAll();
        try {
            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        }
    }

    public void changePassword(String userId, String oldPasswd, String newPasswd) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "changePassword";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = userId;

        boolean validException = false;
        try {
            mLogger.debug("In changePassword: "+userId);

            // Login to verify the old password
            // Note: afterLogin event handler will not get called - so 'trusted device' check will not happen
            // As event handlers are not called - for API calls made from server code.
            // In normal situation, this is not an issue - as user can call 'change password' only after login
            // However, in hacked situation, 'trusted device' check wont happen - ignoring this for now.
            BackendlessUser user = null;
            int userType = -1;
            boolean verifyFailed = false;
            try {
                user = BackendOps.loginUser(userId, oldPasswd);
                userType = (Integer)user.getProperty("user_type");
            } catch (BackendlessException e) {
                // mark for wrong attempt handling
                verifyFailed = true;
            }
            mEdr[BackendConstants.EDR_USER_ID_IDX] = userId;
            if(userType==-1) {
                // exception in login scenario
                userType = BackendUtils.getUserType(userId);
            }
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(userType);

            // Find mobile number
            String mobileNum = null;
            switch(userType) {
                case DbConstants.USER_TYPE_MERCHANT:
                    mLogger.debug("Usertype is Merchant");
                    mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = userId;

                    if(verifyFailed) {
                        validException = true;
                        Merchants merchant = BackendOps.getMerchant(userId, false, false);
                        int cnt = BackendUtils.handleWrongAttempt(userId, merchant, userType,
                                DbConstantsBackend.WRONG_PARAM_TYPE_PASSWD, DbConstants.OP_CHANGE_PASSWD, mEdr, mLogger);
                        throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_PASSWD), String.valueOf(cnt));

                    } else {
                        BackendOps.loadMerchant(user);
                        Merchants merchant = (Merchants)user.getProperty("merchant");
                        mLogger.setProperties(merchant.getAuto_id(), DbConstants.USER_TYPE_MERCHANT, merchant.getDebugLogs());
                        mobileNum = merchant.getMobile_num();
                    }
                    break;

                case DbConstants.USER_TYPE_CUSTOMER:
                    mLogger.debug("Usertype is Customer");

                    if(verifyFailed) {
                        validException = true;
                        Customers customer = BackendOps.getCustomer(userId, CommonConstants.ID_TYPE_MOBILE, false);
                        int cnt = BackendUtils.handleWrongAttempt(userId, customer, userType,
                                DbConstantsBackend.WRONG_PARAM_TYPE_PASSWD, DbConstants.OP_CHANGE_PASSWD, mEdr, mLogger);
                        mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();
                        throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_PASSWD), String.valueOf(cnt));

                    } else {
                        BackendOps.loadCustomer(user);
                        Customers customer = (Customers)user.getProperty("customer");
                        mLogger.setProperties(customer.getPrivate_id(), DbConstants.USER_TYPE_CUSTOMER, customer.getDebugLogs());
                        mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();
                        mobileNum = customer.getMobile_num();
                    }
                    break;

                case DbConstants.USER_TYPE_AGENT:
                    mLogger.debug("Usertype is Agent");
                    if(verifyFailed) {
                        validException = true;
                        InternalUser agent = BackendOps.getInternalUser(userId);
                        int cnt = BackendUtils.handleWrongAttempt(userId, agent, userType,
                                DbConstantsBackend.WRONG_PARAM_TYPE_PASSWD, DbConstants.OP_CHANGE_PASSWD, mEdr, mLogger);
                        throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_PASSWD), String.valueOf(cnt));

                    } else {
                        BackendOps.loadInternalUser(user);
                        InternalUser agent = (InternalUser)user.getProperty("internalUser");
                        mLogger.setProperties(agent.getId(), DbConstants.USER_TYPE_AGENT, agent.getDebugLogs());
                        mEdr[BackendConstants.EDR_INTERNAL_USER_ID_IDX] = agent.getId();
                        mobileNum = agent.getMobile_num();
                    }
                    break;
            }
            mLogger.debug("changePassword: User mobile number: "+mobileNum);

            // Change password
            user.setPassword(newPasswd);
            user = BackendOps.updateUser(user);

            // Send SMS through HTTP
            if(mobileNum!=null) {
                String smsText = SmsHelper.buildPwdChangeSMS(userId);
                SmsHelper.sendSMS(smsText, mobileNum, mEdr, mLogger, true);
            } else {
                mLogger.error("In changePassword: mobile number is null");
                mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_MOBILE_NUM_NA;
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

    public Merchants getMerchant(String merchantId) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getMerchant";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = merchantId;

        try {
            mLogger.debug("In getMerchant");

            // Send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediately after
            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, true);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            Merchants merchant = null;
            if (userType == DbConstants.USER_TYPE_MERCHANT) {
                merchant = (Merchants) userObj;
                if (!merchant.getAuto_id().equals(merchantId)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),
                            "Provided Merchant Id don't match: " + merchantId);
                }
            } else if (userType == DbConstants.USER_TYPE_CC ||
                    userType == DbConstants.USER_TYPE_AGENT) {
                merchant = BackendOps.getMerchant(merchantId, true, true);
                mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // remove sensitive info
            merchant.setTempDevId("");
            List<MerchantDevice> devices = merchant.getTrusted_devices();
            for (MerchantDevice device : devices) {
                //device.setDevice_id("");
                device.setNamak("");
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return merchant;
        } catch (Exception e) {
            BackendUtils.handleException(e, false, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }

    public Customers getCustomer(String custId) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getCustomer";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = custId;

        boolean validException = false;
        try {
            mLogger.debug("In getCustomer");

            // Send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediatly after
            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, true);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            Customers customer = null;
            //CustomerCards card = null;
            if (userType == DbConstants.USER_TYPE_CUSTOMER) {
                customer = (Customers) userObj;
                //card = customer.getMembership_card();
                if (!customer.getMobile_num().equals(custId)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),
                            "Invalid customer id provided: " + custId);
                }

                // Mask info not valid for customer user
                /*if(card!=null) {
                    card.setCcntId("");
                    card.setAgentId("");
                    card.setMchntId("");
                    card.setCustId("");
                }*/
            } else if (userType == DbConstants.USER_TYPE_CC) {
                try {
                    customer = BackendOps.getCustomer(custId, CommonUtils.getCustomerIdType(custId), true);
                    //card = customer.getMembership_card();
                } catch(BackendlessException e) {
                    if(e.getCode().equals(String.valueOf(ErrorCodes.NO_SUCH_USER))) {
                        // CC agent may enter wrong customer id by mistake
                        validException = true;
                    }
                    throw e;
                }
                mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();

            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // remove common sensitive info from the object
            customer.setTxn_pin("");
            customer.setNamak("");

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return customer;
        } catch (Exception e) {
            BackendUtils.handleException(e, validException, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }

    public String getCustomerId(String custMobile) {
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getCustomerId";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = custMobile;

        try {
            HeadersManager.getInstance().addHeader( HeadersManager.HeadersEnum.USER_TOKEN_KEY, InvocationContext.getUserToken() );
            // fetch record from customer table
            Customers customer = BackendOps.getCustomer(custMobile, CommonConstants.ID_TYPE_MOBILE, false);
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return customer.getPrivate_id();
        } catch (Exception e) {
            BackendUtils.handleException(e, false, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }


    /*
     * //OP_NEW_CARD - Need Mobile, PIN and OTP on registered number
     * //OP_CHANGE_MOBILE - Need Mobile, CardId, PIN and OTP on new number - only from customer app
     * OP_CHANGE_MOBILE - Need Mobile, PIN and OTP on new number - only from customer app
     * //OP_RESET_PIN - Need CardId and OTP on registered number (OTP not required if done by customer himself from app)
     * OP_RESET_PIN - Need Customer DoB (comes in argCardId variable) - only from customer app
     * OP_CHANGE_PIN - Need PIN(existing) - only from customer app
     */
    public String execCustomerOp(String opCode, String mobileNum, String argCardId, String otp, String argPin, String opParam) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "execCustomerOp";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = opCode+BackendConstants.BACKEND_EDR_SUB_DELIMETER +
                mobileNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                //argCardId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                otp+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                argPin+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                opParam;

        boolean validException = false;

        try {
            // Both merchant and customer users are allowed
            //mLogger.debug("Context: "+InvocationContext.asString());
            //mLogger.debug("Headers: "+ HeadersManager.getInstance().getHeaders().toString());

            /*if(!InvocationContext.getUserToken().equals(HeadersManager.getInstance().getHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY))) {
                mLogger.debug("User token not in sync case: "+InvocationContext.getUserToken()+", "+HeadersManager.getInstance().getHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY));
                HeadersManager.getInstance().addHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY, InvocationContext.getUserToken());
            }*/

            // We need the 'user' object also for 'change mobile' scenario
            // so, not using 'fetchCurrentUser' function - rather directly fetching object
            //BackendlessUser user = BackendOps.fetchUserByObjectId(InvocationContext.getUserId(), false);
            BackendlessUser user = BackendUtils.fetchCurrentBLUser(null, mEdr, mLogger, false);
            mEdr[BackendConstants.EDR_USER_ID_IDX] = (String) user.getProperty("user_id");
            int userType = (Integer)user.getProperty("user_type");
            mEdr[BackendConstants.EDR_USER_TYPE_IDX] = String.valueOf(userType);

            String merchantId = null;
            String merchantName = null;
            Customers customer = null;
            BackendlessUser custUser = null;
            CustomerOps customerOp = null;
            //String cardIdDb = null;
            //CustomerCards newCard = null;
            switch (userType) {
                case DbConstants.USER_TYPE_MERCHANT:
                    // OP_CHANGE_PIN not allowed to merchant
                    //if( opCode.equals(DbConstants.OP_CHANGE_PIN) || opCode.equals(DbConstants.OP_CHANGE_MOBILE) || opCode.equals(DbConstants.OP_RESET_PIN)) {
                    //if( !opCode.equals(DbConstants.OP_NEW_CARD) ) {
                        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
                    //}
                    /*Merchants merchant = (Merchants) user.getProperty("merchant");
                    merchantId = merchant.getAuto_id();
                    merchantName = merchant.getName();
                    mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();
                    mLogger.setProperties(mEdr[BackendConstants.EDR_USER_ID_IDX], userType, merchant.getDebugLogs());
                    // check if merchant is enabled
                    BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);

                    // Fetch Customer user
                    custUser = BackendOps.fetchUser(mobileNum, DbConstants.USER_TYPE_CUSTOMER, false);
                    customer = (Customers) custUser.getProperty("customer");
                    if(customer.getMembership_card()!=null) {
                        cardIdDb = customer.getMembership_card().getCard_id();
                    }
                    break;*/

                case DbConstants.USER_TYPE_CUSTOMER:
                    // OP_NEW_CARD not allowed from customer app
                    /*if(opCode.equals(DbConstants.OP_NEW_CARD)) {
                        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
                    }*/
                    custUser = user;
                    customer = (Customers) user.getProperty("customer");
                    if(!mobileNum.isEmpty() && !mobileNum.equals(customer.getMobile_num())) {
                        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
                    }

                    // as customer enters card number and dont scan the card
                    /*if(customer.getMembership_card()!=null) {
                        cardIdDb = customer.getMembership_card().getCardNum();
                    }*/
                    mLogger.setProperties(mEdr[BackendConstants.EDR_USER_ID_IDX], userType, customer.getDebugLogs());
                    break;

                default:
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
            }

            mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();
            /*if(customer.getMembership_card()!=null) {
                mEdr[BackendConstants.EDR_CUST_CARD_NUM_IDX] = customer.getMembership_card().getCardNum();
            }*/

            // check if customer is enabled
            BackendUtils.checkCustomerStatus(customer, mEdr, mLogger);
            // if customer still in 'restricted access' mode - dont allow customer ops
            if(customer.getAdmin_status()==DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY) {
                validException = true;
                throw new BackendlessException(String.valueOf(ErrorCodes.LIMITED_ACCESS_CREDIT_TXN_ONLY), "");
            }

            // OTP not required for following:
            // - 'Change PIN' operation
            // - 'Reset PIN' by customer himself from app
            // for others generate the same, if not provided
            if ( (otp == null || otp.isEmpty()) &&
                    !opCode.equals(DbConstants.OP_CHANGE_PIN) &&
                    !(opCode.equals(DbConstants.OP_RESET_PIN) && userType==DbConstants.USER_TYPE_CUSTOMER) ) {

                // Verify against provided card ids
                /*if (opCode.equals(DbConstants.OP_NEW_CARD)) {
                    // check new card exists and is in correct state
                    //newCard = BackendOps.getCustomerCard(argCardId, true);
                    newCard = BackendOps.getCustomerCard(argCardId, true);
                    BackendUtils.checkCardForAllocation(newCard, merchantId, mEdr, mLogger);

                }*/
                // Commented out - as card number is removed from customer ops - as card is now optional
                /*else {
                    // Card Id should be correct and card in correct state
                    if(cardIdDb.equals(argCardId)) {
                        BackendUtils.checkCardForUse(customer.getMembership_card());
                    } else {
                        validException = true;
                        int cnt = BackendUtils.handleWrongAttempt(mobileNum, customer, DbConstants.USER_TYPE_CUSTOMER,
                                DbConstantsBackend.WRONG_PARAM_TYPE_CARDID, opCode, mEdr, mLogger);
                        throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_CARDID), String.valueOf(cnt));
                    }
                }*/

                // verify against provided mobile number
                if (opCode.equals(DbConstants.OP_CHANGE_MOBILE)) {
                    // check that new mobile is not already registered for some other customer
                    Customers newCust = null;
                    try {
                        newCust = BackendOps.getCustomer(opParam, CommonConstants.ID_TYPE_MOBILE, false);
                    } catch (BackendlessException be) {
                        // No such customer exist - we can proceed
                    }
                    if(newCust!=null) {
                        // If here - means customer exist - return error
                        mLogger.debug("Customer already registered: "+opParam+","+customer.getMobile_num());
                        throw new BackendlessException(String.valueOf(ErrorCodes.MOBILE_ALREADY_REGISTERED), "");
                    }
                }

                // Don't verify PIN for 'reset PIN' operation
                if (!opCode.equals(DbConstants.OP_RESET_PIN) &&
                        !SecurityHelper.verifyCustPin(customer, argPin, mLogger)) {

                    validException = true;
                    int cnt = BackendUtils.handleWrongAttempt(mobileNum, customer, DbConstants.USER_TYPE_CUSTOMER,
                            DbConstantsBackend.WRONG_PARAM_TYPE_PIN, opCode, mEdr, mLogger);
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), String.valueOf(cnt));
                }

                // Generate OTP and send SMS
                AllOtp newOtp = new AllOtp();
                newOtp.setUser_id(mobileNum);
                if (opCode.equals(DbConstants.OP_CHANGE_MOBILE)) {
                    newOtp.setMobile_num(opParam);
                } else {
                    newOtp.setMobile_num(customer.getMobile_num());
                }
                newOtp.setOpcode(opCode);
                BackendOps.generateOtp(newOtp,"",mEdr,mLogger);

                // OTP generated successfully - return exception to indicate so
                validException = true;
                throw new BackendlessException(String.valueOf(ErrorCodes.OTP_GENERATED), "");

            } else {
                // Second run, as OTP available OR
                // First run for following: (so no OTP verification req for these)
                // - 'change PIN' case OR
                // - 'reset PIN' from customer app

                // Below next 2 verifications will be repeat for 'second run' scenarios
                // But as these ops are not very frequent - so let them run again
                // For above mentioned 2 First run scenarios - these will be required

                // Don't verify QR card# for 'new card' operation
                // Commented out - as card number is removed from customer ops - as card is now optional
                /*if (!opCode.equals(DbConstants.OP_NEW_CARD)) {
                    if(cardIdDb.equals(argCardId)) {
                        BackendUtils.checkCardForUse(customer.getMembership_card());
                    } else {
                        validException = true;
                        int cnt = BackendUtils.handleWrongAttempt(mobileNum, customer, DbConstants.USER_TYPE_CUSTOMER,
                                DbConstantsBackend.WRONG_PARAM_TYPE_CARDID, opCode, mEdr, mLogger);
                        throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_CARDID), String.valueOf(cnt));
                    }
                }*/

                if(opCode.equals(DbConstants.OP_RESET_PIN)) {
                    // match customer name for verification
                    // app sends name in place of card id
                    if(!argCardId.equalsIgnoreCase(customer.getDob())) {
                        validException = true;
                        int cnt = BackendUtils.handleWrongAttempt(mobileNum, customer, DbConstants.USER_TYPE_CUSTOMER,
                                DbConstantsBackend.WRONG_PARAM_TYPE_NAME, opCode, mEdr, mLogger);
                        throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_NAME), String.valueOf(cnt));
                    }
                }

                // Don't verify PIN for 'reset PIN' operation
                if (!opCode.equals(DbConstants.OP_RESET_PIN) &&
                        !SecurityHelper.verifyCustPin(customer, argPin, mLogger)) {

                    validException = true;
                    int cnt = BackendUtils.handleWrongAttempt(mobileNum, customer, DbConstants.USER_TYPE_CUSTOMER,
                            DbConstantsBackend.WRONG_PARAM_TYPE_PIN, opCode, mEdr, mLogger);
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_PIN), String.valueOf(cnt));
                }

                // For PIN reset request - check if any already pending
                if( opCode.equals(DbConstants.OP_RESET_PIN) &&
                        BackendOps.findActiveCustPinResetReqs(customer.getPrivate_id()) != null) {
                    validException = true;
                    throw new BackendlessException(String.valueOf(ErrorCodes.DUPLICATE_ENTRY), "");
                }

                // Verify OTP - only for second run scenarios
                // i.e. do not check for:
                // - change PIN' case AND
                // - 'reset PIN' from customer app
                if( !opCode.equals(DbConstants.OP_CHANGE_PIN) &&
                        !(opCode.equals(DbConstants.OP_RESET_PIN) && userType==DbConstants.USER_TYPE_CUSTOMER) ) {
                    if(!BackendOps.validateOtp(mobileNum, opCode, otp, mEdr, mLogger)) {
                        validException = true;
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_OTP), "");
                    }
                } else {
                    mLogger.debug("opcode: "+opCode+", userType: "+userType);
                }

                // First add to the Merchant Ops table, and then do the actual update
                // Doing so, as its easy to rollback by deleting added customerOp record
                // then the other way round.
                // Need to ensure that CustomerOp table record is always there, in case update is successful
                customerOp = new CustomerOps();
                customerOp.setCreateTime(new Date());
                customerOp.setPrivateId(customer.getPrivate_id());
                customerOp.setOp_code(opCode);
                customerOp.setMobile_num(customer.getMobile_num());
                if(userType==DbConstants.USER_TYPE_CUSTOMER) {
                    customerOp.setInitiatedBy(DbConstantsBackend.USER_OP_INITBY_CUSTOMER);
                    customerOp.setImgFilename("");
                    customerOp.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_APP);
                    customerOp.setRequestor_id(customer.getPrivate_id());
                } else {
                    //customerOp.setInitiatedBy(DbConstantsBackend.USER_OP_INITBY_MCHNT);
                    customerOp.setInitiatedBy(merchantName);
                    customerOp.setRequestor_id(merchantId);
                    // leave empty, so as not visible in Customer App
                    customerOp.setInitiatedVia("");
                    //customerOp.setImgFilename(BackendUtils.getCustOpImgFilename(opCode, customer.getPrivate_id()));
                }
                if(opCode.equals(DbConstants.OP_RESET_PIN)) {
                    customerOp.setOp_status(DbConstantsBackend.USER_OP_STATUS_PENDING);
                } else {
                    customerOp.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
                }

                // set extra params in presentable format
                /*if(opCode.equals(DbConstants.OP_NEW_CARD)) {
                    newCard = BackendOps.getCustomerCard(argCardId, true);
                    String extraParams = "Old Card: "+
                            ((customer.getMembership_card()==null)?"NA":customer.getMembership_card().getCardNum())+
                            ", New Card: "+newCard.getCardNum();
                    customerOp.setExtra_op_params(extraParams);
                    customerOp.setReason(opParam);

                } else*/ if(opCode.equals(DbConstants.OP_CHANGE_MOBILE)) {
                    String extraParams = "Old Mobile: "+customer.getMobile_num()+", New Mobile: "+customer.getMobile_num();
                    customerOp.setExtra_op_params(extraParams);
                }
                customerOp = BackendOps.saveCustomerOp(customerOp);

                // Do the actual Update now
                try {
                    switch (opCode) {
                        /*case DbConstants.OP_NEW_CARD:
                            changeCustomerCard(customer, merchantId, newCard, opParam);
                            break;*/

                        case DbConstants.OP_CHANGE_MOBILE:
                            if (opParam == null || opParam.isEmpty() || opParam.length() != CommonConstants.MOBILE_NUM_LENGTH) {
                                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid new Mobile value");
                            }
                            changeCustomerMobile(custUser, opParam);
                            break;

                        case DbConstants.OP_RESET_PIN:
                            resetCustomerPin(customer, merchantName);
                            validException = true;
                            // send imageFilename as part of exception - hack as no value can be returned in this case
                            String errMsg = (new MyErrorParams(ErrorCodes.OP_SCHEDULED, -1,
                                    //CommonUtils.roundUpTo(MyGlobalSettings.getCustPasswdResetMins()+GlobalSettingConstants.CUSTOMER_PASSWORD_RESET_TIMER_INTERVAL,5),
                                    MyGlobalSettings.getCustPasswdResetMins(),
                                    customerOp.getImgFilename())).toCsvString();
                            throw new BackendlessException(String.valueOf(ErrorCodes.OP_SCHEDULED), errMsg);

                        case DbConstants.OP_CHANGE_PIN:
                            if (opParam == null || opParam.isEmpty() || opParam.length() != CommonConstants.PIN_LEN) {
                                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid new PIN value");
                            }
                            changeCustomerPin(customer, opParam);
                            break;
                    }
                } catch(Exception e) {
                    if(!validException) {
                        mLogger.error("execCustomerOp: Exception while customer operation: "+customer.getPrivate_id(), e);
                        // Rollback - delete customer op added
                        try {
                            BackendOps.deleteCustomerOp(customerOp);
                        } catch(Exception ex) {
                            mLogger.fatal("execCustomerOp: Failed to rollback: customer op deletion failed: "+customer.getPrivate_id());
                            // Rollback also failed
                            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                            throw ex;
                        }
                    }
                    throw e;
                }
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            // return image filename - which if initiated by merchant app - should use to upload card image
            return customerOp.getImgFilename();

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    /*
    public List<MerchantOrders> getMchntOrders(String merchantId, String orderId, String statusCsvStr) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        boolean positiveException = false;

        try {
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "getMchntOrders";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = merchantId+BackendConstants.BACKEND_EDR_SUB_DELIMETER +
                    orderId+BackendConstants.BACKEND_EDR_SUB_DELIMETER +
                    statusCsvStr;

            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            Merchants merchant = null;
            boolean byCCUser = false;
            if(userType==DbConstants.USER_TYPE_MERCHANT) {
                merchant = (Merchants) userObj;
                // check to ensure that merchant is requesting for itself only
                if (!merchant.getAuto_id().equals(merchantId)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),"Invalid merchant id provided: " + merchantId);
                }
            } else if(userType==DbConstants.USER_TYPE_CC || userType==DbConstants.USER_TYPE_CCNT || userType==DbConstants.USER_TYPE_AGENT) {
                // use provided merchant values
                byCCUser = true;
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // not checking for merchant account status

            // fetch merchant orders
            String whereClause = null;
            if(merchantId!=null && !merchantId.isEmpty()) {
                whereClause = "merchantId = '"+merchantId+"'";
            }
            if(orderId!=null && !orderId.isEmpty()) {
                if(whereClause==null) {
                    whereClause = "orderId = '" + orderId + "'";
                } else {
                    whereClause = whereClause + "AND orderId = '" + orderId + "'";
                }
            }
            if(statusCsvStr!=null && !statusCsvStr.isEmpty()) {
                if (whereClause == null) {
                    whereClause = "status IN ("+statusCsvStr+")";
                } else {
                    whereClause = whereClause + "AND status IN ("+statusCsvStr+")";;
                }
            }
            mLogger.debug("where clause: "+whereClause);

            List<MerchantOrders> orders = BackendOps.fetchMchntOrders(whereClause);
            if(orders==null) {
                // not exactly a positive exception - but using it to avoid logging of this as error
                // as it can happen frequently as valid scenario
                positiveException = true;
                throw new BackendlessException(String.valueOf(ErrorCodes.BL_ERROR_NO_DATA_FOUND), "");
            } else {
                mLogger.debug("Fetched Mchnt Orders: "+orders.size());
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return orders;

        } catch(Exception e) {
            BackendUtils.handleException(e,positiveException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }*/

    /*
     * Private helper methods
     */
    /*
    private void changeCustomerCard(Customers customer, String merchantId, CustomerCards newCard, String reason) {

        BackendUtils.checkCardForAllocation(newCard, merchantId, mEdr, mLogger);

        CustomerCards oldCard = customer.getMembership_card();

        // update 'customer' and 'CustomerCard' objects for new card
        newCard.setStatus(DbConstants.CUSTOMER_CARD_STATUS_ACTIVE);
        newCard.setStatus_update_time(new Date());
        newCard.setCustId(customer.getPrivate_id());
        customer.setCardId(newCard.getCard_id());
        customer.setMembership_card(newCard);

        // save updated customer object
        BackendOps.updateCustomer(customer);

        // update old card status
        try {
            if(oldCard!=null) {
                mEdr[BackendConstants.EDR_CUST_CARD_NUM_IDX] = oldCard.getCardNum();

                oldCard.setStatus(DbConstants.CUSTOMER_CARD_STATUS_DISABLED);
                oldCard.setStatus_reason(reason);
                oldCard.setStatus_update_time(new Date());
                BackendOps.saveCustomerCard(oldCard);
            }

        } catch (BackendlessException e) {
            // ignore as not considered as failure for whole 'changeCustomerCard' operation
            // but log as alarm for manual correction
            mLogger.error("Exception while updating old card status: "+e.toString());
            mLogger.error(BackendUtils.stackTraceStr(e));
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX]=BackendConstants.BACKEND_EDR_MANUAL_CHECK;
        }

        // Send message to customer informing the same - ignore sent status
        String smsText = SmsHelper.buildNewCardSMS(customer.getMobile_num(), newCard.getCardNum());
        SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);
    }*/

    private void changeCustomerMobile(BackendlessUser custUser, String newMobile) {
        Customers customer = (Customers) custUser.getProperty("customer");
        String oldMobile = customer.getMobile_num();

        // update mobile number
        customer.setMobile_num(newMobile);

        custUser.setProperty("user_id", newMobile);
        // update status to 'restricted access'
        // not using setCustomerStatus() fx. - to avoid two DB operations
        customer.setAdmin_status(DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY);
        //customer.setStatus_reason("Mobile Number changed in last "+MyGlobalSettings.getCustAccLimitModeHrs()+" hours");
        customer.setStatus_reason("Mobile Number changed recently");
        customer.setStatus_update_time(new Date());

        custUser.setProperty("customer", customer);
        BackendOps.updateUser(custUser);

        // Send message to customer informing the same - ignore sent status
        String smsText = SmsHelper.buildMobileChangeSMS( oldMobile, newMobile );
        SmsHelper.sendSMS(smsText, oldMobile+","+newMobile, mEdr, mLogger, true);
    }

    private void resetCustomerPin(Customers customer, String merchantName) {
        // This is just to make existing PIN un-usable
        // Else new PIn will be generated on Customer Reset PIN timer expiry
        SecurityHelper.generateCustPin(customer, mLogger);
        // update user account for the PIN
        BackendOps.updateCustomer(customer);

        // Send SMS through HTTP
        String smsText = String.format(SmsConstants.SMS_PIN_INIT,
                (merchantName==null?"You":merchantName),
                CommonUtils.getPartialVisibleStr(customer.getMobile_num()),
                MyGlobalSettings.getCustPasswdResetMins().toString());
        SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);
    }

    private void changeCustomerPin(Customers customer, String newPin) {
        // update user account for the PIN
        //customer.setTxn_pin(newPin);
        SecurityHelper.setCustPin(customer, newPin, mLogger);
        BackendOps.updateCustomer(customer);

        // Send SMS through HTTP
        String smsText = SmsHelper.buildPinChangeSMS(customer.getMobile_num());
        SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);
    }

}

    /*private String custPinResetWhereClause(String customerMobileNum) {
        StringBuilder whereClause = new StringBuilder();

        whereClause.append("op_code = '").append(DbConstants.OP_RESET_PIN).append("'");
        whereClause.append(" AND op_status = '").append(DbConstantsBackend.USER_OP_STATUS_PENDING).append("'");
        whereClause.append("AND mobile_num = '").append(customerMobileNum).append("'");

        // created within last 'cool off' mins
        long time = (new Date().getTime()) - (MyGlobalSettings.getCustPasswdResetMins() * 60 * 1000);
        whereClause.append(" AND createTime > ").append(time);
        return whereClause.toString();
    }*/

    /*public Customers getInternalUser(String userId) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getInternalUser";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = userId;

        boolean validException = false;
        try {
            mLogger.debug("In getInternalUser");

            // Send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediatly after
            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, true);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            InternalUser internalUser = null;
            if (userType == DbConstants.USER_TYPE_CC || userType==DbConstants.USER_TYPE_AGENT || userType==DbConstants.USER_TYPE_CNT) {
                internalUser = (Customers) userObj;
                if (!internalUser.getMobile_num().equals(userId)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),
                            "Invalid customer id provided: " + userId);
                }
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return internalUser;
        } catch (Exception e) {
            BackendUtils.handleException(e, validException, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }*/

