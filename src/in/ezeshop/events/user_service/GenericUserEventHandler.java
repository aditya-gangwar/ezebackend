package in.ezeshop.events.user_service;

import com.backendless.HeadersManager;
import com.backendless.exceptions.BackendlessException;
import com.backendless.persistence.BackendlessDataQuery;
import com.backendless.servercode.ExecutionResult;
import com.backendless.servercode.RunnerContext;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.InternalUser;
import in.ezeshop.database.InternalUserDevice;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import java.util.*;
import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

/**
* GenericUserEventHandler handles the User Service events.
* The event handlers are the individual methods implemented in the class.
* The "before" and "after" prefix determines if the handler is executed before
* or after the default handling logic provided by Backendless.
* The part after the prefix identifies the actual event.
* For example, the "beforeLogin" method is the "Login" event handler and will
* be called before Backendless applies the default login logic. The event
* handling pipeline looks like this:

* Client Request ---> Before Handler ---> Default Logic ---> After Handler --->
* Return Response
*/

public class GenericUserEventHandler extends com.backendless.servercode.extension.UserExtender {
    private MyLogger mLogger = new MyLogger("events.GenericUserEventHandler");
    ;
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];

    @Override
    public void afterLogin(RunnerContext context, String login, String password, ExecutionResult<HashMap> result) throws Exception {
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "afterLogin";
        mEdr[BackendConstants.EDR_USER_ID_IDX] = login;
        //initCommon();
        boolean validException = false;

        try {
            mLogger.debug("In GenericUserEventHandler: afterLogin: " + login);
            //mLogger.debug("Before: "+HeadersManager.getInstance().getHeaders().toString());
            //mLogger.debug(context.toString());
            //List<String> roles = Backendless.UserService.getUserRoles();
            //mLogger.debug("Roles: "+roles.toString());

            if (result.getException() == null) {
                // Login is successful
                // add user token, so as correct roles are assumed
                if (context.getUserToken() == null) {
                    mLogger.debug("In afterLogin: RunnerContext: " + context.toString());
                    //TODO: user token is coming null - bug with standlone backendless. Open the below check when fixed.
                    //throw new BackendlessException(BackendResponseCodes.NOT_LOGGED_IN, "User not logged in: " + login);
                } else {
                    HeadersManager.getInstance().addHeader(HeadersManager.HeadersEnum.USER_TOKEN_KEY, context.getUserToken());
                }

                String userId = (String) result.getResult().get("user_id");
                Integer userType = (Integer) result.getResult().get("user_type");
                mEdr[BackendConstants.EDR_USER_TYPE_IDX] = userType.toString();

                if (userType == DbConstants.USER_TYPE_MERCHANT) {
                    // fetch merchant object
                    Merchants merchant = BackendOps.getMerchant(userId, true, true);
                    mLogger.setProperties(merchant.getAuto_id(), DbConstants.USER_TYPE_MERCHANT, merchant.getDebugLogs());
                    mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();

                    // check admin status
                    BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);

                    // ** REMOVED TRUSTED DEVICE FUNCTIONALITY TO MAKE THINGS SIMPLER ** //

                    // Get deviceId - if valid
                    //String deviceId = getDeviceId(merchant.getTempDevId());

                    // Check if device is in trusted list
                    /*List<MerchantDevice> trustedDevices = merchant.getTrusted_devices();
                    if (!BackendUtils.isTrustedDevice(deviceId, trustedDevices)) {
                        // Device not in trusted list

                        String[] csvFields = merchant.getTempDevId().split(CommonConstants.CSV_DELIMETER);
                        String rcvdOtp = (csvFields.length == 6) ? csvFields[5] : null;

                        if (rcvdOtp == null || rcvdOtp.isEmpty()) {
                            // First run of un-trusted device - generate OTP

                            // Check for max devices allowed per user
                            int deviceCnt = (merchant.getTrusted_devices() != null) ? merchant.getTrusted_devices().size() : 0;
                            if (deviceCnt >= CommonConstants.MAX_DEVICES_PER_MERCHANT) {
                                validException = true;
                                throw new BackendlessException(String.valueOf(ErrorCodes.TRUSTED_DEVICE_LIMIT_RCHD), "Trusted device limit reached");
                            }
                            // Generate OTP
                            AllOtp newOtp = new AllOtp();
                            newOtp.setUser_id(userId);
                            newOtp.setMobile_num(merchant.getMobile_num());
                            newOtp.setOpcode(DbConstants.OP_LOGIN);
                            BackendOps.generateOtp(newOtp, mEdr, mLogger);

                            // OTP generated successfully - return exception to indicate so
                            validException = true;
                            throw new BackendlessException(String.valueOf(ErrorCodes.OTP_GENERATED), "");

                        } else {
                            // OTP available - validate the same
                            if (!BackendOps.validateOtp(userId, DbConstants.OP_LOGIN, rcvdOtp, mEdr, mLogger)) {
                                mLogger.error("Wrong OTP value: " + rcvdOtp);
                                validException = true;
                                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_OTP), "");
                            }

                            // OTP is valid - add this device to trusted list
                            // Trusted device may be null - create new if so
                            if (trustedDevices == null) {
                                trustedDevices = new ArrayList<>();
                            }

                            // New device - add as trusted device
                            MerchantDevice device = new MerchantDevice();
                            device.setMerchant_id(merchant.getAuto_id());
                            device.setManufacturer(csvFields[1]);
                            device.setModel(csvFields[2]);
                            device.setOs_type("Android");
                            device.setOs_version(csvFields[3]);
                            //device.setDevice_id(deviceId);
                            SecurityHelper.setDeviceId(device, deviceId, mLogger);

                            trustedDevices.add(device);

                            // Update merchant
                            merchant.setTrusted_devices(trustedDevices);
                            // when Ufirst login, the device will also be new
                            // so it is not required to update this outside this if block
                            if (!merchant.getFirst_login_ok()) {
                                merchant.setFirst_login_ok(true);
                                //merchant.setAdmin_remarks("Last state was new registered");
                            }
                            merchant.setTempDevId(null);

                            try {
                                merchant = BackendOps.updateMerchant(merchant);
                            } catch (BackendlessException e) {
                                if (e.getCode().equals(ErrorCodes.BL_ERROR_DUPLICATE_ENTRY)) {
                                    // there's unique index on device id
                                    throw new BackendlessException(String.valueOf(ErrorCodes.DEVICE_ALREADY_REGISTERED),
                                            deviceId + " is already registered");
                                }
                                throw e;
                            }
                        }
                    }

                    // all fine - update result with Merchant object
                    // remove sensitive info
                    merchant.setTempDevId("");
                    List<MerchantDevice> devices = merchant.getTrusted_devices();
                    for (MerchantDevice device : devices) {
                        //device.setDevice_id("");
                        device.setNamak("");
                    }*/

                    if (!merchant.getFirst_login_ok()) {
                        merchant.setFirst_login_ok(true);
                        //merchant.setAdmin_remarks("Last state was new registered");
                        merchant = BackendOps.updateMerchant(merchant);
                    }

                    result.getResult().put("merchant", merchant);

                } else if (userType == DbConstants.USER_TYPE_CUSTOMER) {
                    // fetch customer object
                    Customers customer = BackendOps.getCustomer(userId, CommonConstants.ID_TYPE_MOBILE, true);
                    mLogger.setProperties(customer.getMobile_num(), DbConstants.USER_TYPE_CUSTOMER, customer.getDebugLogs());
                    mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();

                    // check admin status
                    BackendUtils.checkCustomerStatus(customer, mEdr, mLogger);

                    if (!customer.getFirst_login_ok()) {
                        customer.setFirst_login_ok(true);
                        customer = BackendOps.updateCustomer(customer);
                    }

                    // all fine - add customer object to result
                    // remove common sensitive info from the object
                    customer.setTxn_pin("");
                    customer.setNamak("");
                    // 'addresses' is not actually stored in DB
                    // this field only acts as transport - so as customer dont have to do another query for it
                    customer.setAddresses(BackendOps.fetchCustAddresses(customer.getPrivate_id()));
                    result.getResult().put("customer", customer);

                } else if (userType == DbConstants.USER_TYPE_AGENT ||
                        userType == DbConstants.USER_TYPE_CC
                        //|| userType == DbConstants.USER_TYPE_CCNT
                        ) {
                    InternalUser internalUser = BackendOps.getInternalUser(userId);
                    mLogger.setProperties(internalUser.getId(), userType, internalUser.getDebugLogs());
                    mEdr[BackendConstants.EDR_INTERNAL_USER_ID_IDX] = internalUser.getId();
                    // check admin status
                    BackendUtils.checkInternalUserStatus(internalUser);

                    // fetch device data
                    InternalUserDevice deviceData = BackendOps.fetchInternalUserDevice(userId);
                    if (deviceData == null || deviceData.getTempId() == null || deviceData.getTempId().isEmpty()) {
                        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                        mLogger.fatal("In afterLogin for internal user: Temp instance id not available: " + userId);
                        throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "SubCode1");
                    }
                    // If first login after register - store the provided 'instanceId' as trusted
                    if (!internalUser.getFirst_login_ok()) {
                        mLogger.debug("First login case for agent user: " + userId);
                        if (deviceData.getInstanceId() == null || deviceData.getInstanceId().isEmpty()) {
                            deviceData.setInstanceId(deviceData.getTempId());
                            internalUser.setFirst_login_ok(true);
                            internalUser.setAdmin_remarks("Last state was new registered");
                            BackendOps.updateInternalUser(internalUser);
                        } else {
                            // invalid state
                            mLogger.fatal("In afterLogin for agent: Invalid state: " + userId);
                            throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "SubCode2");
                        }
                    } else {
                        // compare instanceIds
                        if (!deviceData.getInstanceId().equals(deviceData.getTempId())) {
                            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                            throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "Agent Device Id not matching");
                        }
                    }
                    // update device data
                    deviceData.setTempId(null);
                    BackendOps.saveInternalUserDevice(deviceData);
                }

            } else {
                // Login failed for some reason
                Integer userType = BackendUtils.getUserType(login);
                mEdr[BackendConstants.EDR_USER_TYPE_IDX] = userType.toString();

                // login failed - increase count if failed due to wrong password
                //if(result.getException().getCode() == Integer.parseInt(BackendResponseCodes.BL_ERROR_INVALID_ID_PASSWD)) {
                //if(result.getException().getExceptionClass().endsWith("UserLoginException")) {
                // crude way to check for failure due to wrong password - but above ones were not working properly
                if (result.getException().getExceptionMessage().contains("password")) {
                    int cnt = 0;
                    mLogger.debug("Login failed for user: " + login + " due to wrong id/passwd. "+result.getException().getExceptionMessage());
                    switch (userType) {
                        case DbConstants.USER_TYPE_MERCHANT:
                            // fetch merchant
                            Merchants merchant = BackendOps.getMerchant(login, true, false);
                            mEdr[BackendConstants.EDR_MCHNT_ID_IDX] = merchant.getAuto_id();

                            // ** REMOVED TRUSTED DEVICE FUNCTIONALITY TO MAKE THINGS SIMPLER ** //
                            // Check for trusted device
                            // this, so as someone else cannot get account locked for other merchant account
                            //String deviceId = getDeviceId(merchant.getTempDevId());
                            //List<MerchantDevice> trustedDevices = merchant.getTrusted_devices();
                            //if (BackendUtils.isTrustedDevice(deviceId, trustedDevices)) {
                                BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);
                                cnt = BackendUtils.handleWrongAttempt(login, merchant, DbConstants.USER_TYPE_MERCHANT,
                                        DbConstantsBackend.WRONG_PARAM_TYPE_PASSWD, DbConstants.OP_LOGIN, mEdr, mLogger);
                                if (!merchant.getFirst_login_ok()) {
                                    // first login not done yet
                                    mLogger.debug("First login pending");
                                    validException = true;
                                    throw new BackendlessException(String.valueOf(ErrorCodes.FIRST_LOGIN_PENDING), "");
                                }
                            //} else {
                              //  throw new BackendlessException(String.valueOf(ErrorCodes.WRNG_PSWD_NOT_TRUSTED_DEV), "");
                            //}

                            break;

                        case DbConstants.USER_TYPE_CUSTOMER:
                            // fetch customer
                            //BackendUtils.printCtxtInfo(mLogger, context);
                            //mLogger.debug("Before customer object: "+login);
                            Customers customer = BackendOps.getCustomer(login, CommonConstants.ID_TYPE_MOBILE, false);
                            //mLogger.debug("Got customer object");
                            mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();

                            BackendUtils.checkCustomerStatus(customer, mEdr, mLogger);
                            cnt = BackendUtils.handleWrongAttempt(login, customer, DbConstants.USER_TYPE_CUSTOMER,
                                    DbConstantsBackend.WRONG_PARAM_TYPE_PASSWD, DbConstants.OP_LOGIN, mEdr, mLogger);
                            if (!customer.getFirst_login_ok()) {
                                // first login not done yet
                                mLogger.debug("First login pending");
                                validException = true;
                                throw new BackendlessException(String.valueOf(ErrorCodes.FIRST_LOGIN_PENDING), "");
                            }
                            break;

                        case DbConstants.USER_TYPE_CC:
                        //case DbConstants.USER_TYPE_CCNT:
                        case DbConstants.USER_TYPE_AGENT:
                            // fetch agent
                            InternalUser internalUser = BackendOps.getInternalUser(login);
                            mEdr[BackendConstants.EDR_INTERNAL_USER_ID_IDX] = internalUser.getId();
                            cnt = BackendUtils.handleWrongAttempt(login, internalUser, userType,
                                    DbConstantsBackend.WRONG_PARAM_TYPE_PASSWD, DbConstants.OP_LOGIN, mEdr, mLogger);
                            break;
                    }

                    validException = true;
                    throw new BackendlessException(String.valueOf(ErrorCodes.VERIFICATION_FAILED_PASSWD), String.valueOf(cnt));

                } else {
                    mLogger.debug("Login failed for user: " + login + ": " + result.getException().toString());
                }
                // login failed - set the exception code to the same
                //mEdr[BackendConstants.EDR_EXP_CODE_IDX] = BackendResponseCodes.BL_ERROR_INVALID_ID_PASSWD;
                mEdr[BackendConstants.EDR_EXP_CODE_IDX] = String.valueOf(result.getException().getCode());
                mEdr[BackendConstants.EDR_EXP_MSG_IDX] = result.getException().getExceptionMessage();
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch (Exception e) {
            BackendUtils.handleException(e, validException, mLogger, mEdr);
            if (e instanceof BackendlessException) {
                throw BackendUtils.getNewException((BackendlessException) e);
            }
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }

    private String getDeviceId(String deviceInfo) {
        // Check if 'device id' not set
        // This is set in setDeviceId() backend API
        if (deviceInfo == null || deviceInfo.isEmpty()) {
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "");
        }

        // deviceInfo format: <device id>,<manufacturer>,<model>,<os version>,<time>,<otp>
        String[] csvFields = deviceInfo.split(CommonConstants.CSV_DELIMETER);
        String deviceId = csvFields[0];
        long entryTime = Long.parseLong(csvFields[4]);
        //String rcvdOtp = (csvFields.length == 6) ? csvFields[5] : null;

        // 'deviceInfo' is valid only if from last DEVICE_INFO_VALID_SECS
        // This logic helps us to avoid resetting 'tempDevId' to NULL on each login call
        long timeDiff = System.currentTimeMillis() - entryTime;
        if (timeDiff > (BackendConstants.DEVICE_INFO_VALID_SECS * 1000) &&
                BackendConstants.TESTING_SKIP_DEVICEID_CHECK) {
            // deviceInfo is old than 5 mins = 300 secs
            // most probably from last login call - setDeviceInfo not called before this login
            // can indicate sabotage
            mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
            throw new BackendlessException(String.valueOf(ErrorCodes.NOT_TRUSTED_DEVICE), "Device data is old");
        }

        return deviceId;
    }

    @Override
    public void beforeRestorePassword( RunnerContext context, String email ) throws Exception
    {
        MyLogger mLogger = new MyLogger("events.beforeRestorePassword");
        String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;
        // update not allowed from app - return exception
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "beforeRestorePassword";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = email;
        BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
    }

    @Override
    public void beforeRegister( RunnerContext context, HashMap userValue ) throws Exception
    {
        MyLogger mLogger = new MyLogger("events.beforeRegister");
        String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;
        // update not allowed from app - return exception
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "beforeRegister";
        BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
    }

    @Override
    public void beforeFind( RunnerContext context, BackendlessDataQuery query ) throws Exception
    {
        MyLogger mLogger = new MyLogger("events.beforeFind");
        String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;
        // update not allowed from app - return exception
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "users-beforeFind";
        BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
    }

    @Override
    public void beforeUpdate( RunnerContext context, HashMap userValue ) throws Exception
    {
        MyLogger mLogger = new MyLogger("events.beforeUpdate");
        String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;
        // update not allowed from app - return exception
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "users-beforeUpdate";
        BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
    }
}