package in.ezeshop.utilities;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.BackendlessUser;
import com.backendless.exceptions.BackendlessException;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.logging.Logger;
import com.backendless.persistence.BackendlessDataQuery;
import com.backendless.persistence.QueryOptions;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.MyGlobalSettings;
import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.*;
import in.ezeshop.messaging.SmsHelper;

import java.awt.geom.Area;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by adgangwa on 15-05-2016.
 */
public class BackendOps {

    /*
     * BackendlessUser operations
     */
    public static BackendlessUser registerUser(BackendlessUser user) {
        return Backendless.UserService.register(user);
    }

    public static void assignRole(String userId, String role) {
        Backendless.UserService.assignRole(userId, role);
    }

    public static BackendlessUser loginUser(String userId, String password) {
        return Backendless.UserService.login(userId, password, false);
    }

    public static void logoutUser() {
        try {
            Backendless.UserService.logout();
        } catch (Exception e) {
            // ignore exception
        }
    }

    public static BackendlessUser updateUser(BackendlessUser user) {
        return Backendless.UserService.update( user );
    }

    public static BackendlessUser fetchUser(String userid, int userType, boolean allChilds) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("user_id = '"+userid+"'");

        if(userType != -1) {
            QueryOptions queryOptions = new QueryOptions();
            switch (userType) {
                case DbConstants.USER_TYPE_CUSTOMER:
                    queryOptions.addRelated("customer");
                    //queryOptions.addRelated("customer.membership_card");
                    break;
                case DbConstants.USER_TYPE_MERCHANT:
                    queryOptions.addRelated("merchant");
                    //queryOptions.addRelated("merchant.trusted_devices");
                    if (allChilds) {
                        queryOptions.addRelated("merchant.address");
                    }
                case DbConstants.USER_TYPE_AGENT:
                case DbConstants.USER_TYPE_CC:
                //case DbConstants.USER_TYPE_CCNT:
                    queryOptions.addRelated("internalUser");
            }
            query.setQueryOptions(queryOptions);
        }

        BackendlessCollection<BackendlessUser> user = Backendless.Data.of( BackendlessUser.class ).find(query);
        if( user.getTotalObjects() == 0) {
            String errorMsg = "No user found: "+userid;
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        } else {
            return user.getData().get(0);
        }
    }

    public static BackendlessUser fetchUserByObjectId(String objectId, boolean allMchntChilds) {
        if(objectId==null || objectId.isEmpty()) {
            // this usually happens, if same user logs in from anoter device
            // and then tries some request from first device
            // Multiple login is off in backendless
            throw new BackendlessException(String.valueOf(ErrorCodes.SESSION_TIMEOUT), "Logged is user object id is null");
        }

        ArrayList<String> relationProps = new ArrayList<>();
        // add all childs
        relationProps.add("merchant");
        relationProps.add("customer");
        // membership card data is almost always required
        //relationProps.add("customer.membership_card");
        relationProps.add("internalUser");

        if(allMchntChilds) {
            //relationProps.add("merchant.trusted_devices");
            relationProps.add("merchant.address");
        }

        return Backendless.Data.of(BackendlessUser.class).findById(objectId, relationProps);
    }

    /*
     * Merchant operations
     */
    public static void loadMerchant(BackendlessUser user) {
        ArrayList<String> relationProps = new ArrayList<>();
        relationProps.add("merchant");
        Backendless.Data.of( BackendlessUser.class ).loadRelations(user, relationProps);
    }

    public static Merchants getMerchant(String userId, boolean trustedDevicesChild, boolean addressChild) {
        BackendlessDataQuery query = new BackendlessDataQuery();

        if(IdGenerator.getMerchantIdType(userId)== CommonConstants.ID_TYPE_AUTO) {
            query.setWhereClause("auto_id = '"+userId+"'");
        } else {
            query.setWhereClause("mobile_num = '"+userId+"'");
        }

        if(addressChild) {
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.addRelated("address");
            query.setQueryOptions(queryOptions);
        }
        /*if(addressChild || trustedDevicesChild) {
            QueryOptions queryOptions = new QueryOptions();
            if(addressChild) {
                queryOptions.addRelated("address");
            }
            if(trustedDevicesChild) {
                queryOptions.addRelated("trusted_devices");
            }
            query.setQueryOptions(queryOptions);
        }*/

        BackendlessCollection<Merchants> user = Backendless.Data.of( Merchants.class ).find(query);
        if( user.getTotalObjects() == 0) {
            String errorMsg = "No Merchant found: "+userId+", "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        } else {
            Merchants mchnt = user.getData().get(0);
            if(addressChild) {
                // fetch and set area
                mchnt.getAddress().setAreaNIDB(getArea(mchnt.getAddress().getAreaId()));
            }
            return mchnt;
        }
    }

    public static Map<String, Merchants> fetchMerchants(List<String> autoIds, boolean addressChild, MyLogger logger) {
        BackendlessDataQuery query = new BackendlessDataQuery();

        // build where clause
        StringBuffer whereClause = null;
        for (String id : autoIds) {
            if(id!=null) {
                if (whereClause == null) {
                    whereClause = new StringBuffer("auto_id = '"+id+"'");
                } else {
                    whereClause.append(" OR auto_id = '").append(id).append("'");
                }
            }
        }
        query.setWhereClause(whereClause.toString());

        if(addressChild) {
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.addRelated("address");
            query.setQueryOptions(queryOptions);
        }

        BackendlessCollection<Merchants> users = Backendless.Data.of( Merchants.class ).find(query);
        if( users.getTotalObjects() == 0) {
            String errorMsg = "No Merchant found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        }

        Map<String, Merchants> objects = new HashMap<>(users.getTotalObjects());
        while (users.getCurrentPage().size() > 0)
        {
            Iterator<Merchants> iterator = users.getCurrentPage().iterator();
            while( iterator.hasNext() )
            {
                Merchants mchnt = iterator.next();
                objects.put(mchnt.getAuto_id(), mchnt);
            }
            users = users.nextPage();
        }

        // fetch area objects, and attach to 'address' object in merchant
        if(addressChild) {
            List<String> areaIds = new ArrayList<>(objects.size());
            for (Merchants m : objects.values()) {
                areaIds.add(m.getAddress().getAreaId());
            }
            HashMap<String, Areas> areas = fetchAreas(areaIds, logger);
            for (Merchants mchnt : objects.values()) {
                mchnt.getAddress().setAreaNIDB(areas.get(mchnt.getAddress().getAreaId()));
            }
        }

        return objects;
    }

    public static Merchants getMerchant(String whereClause) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        BackendlessCollection<Merchants> user = Backendless.Data.of( Merchants.class ).find(query);
        if( user.getTotalObjects() == 0) {
            String errorMsg = "No Merchant found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        } else {
            return user.getData().get(0);
        }
    }

    public static int getMerchantCnt(String whereClause) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        BackendlessCollection<Merchants> users = Backendless.Data.of( Merchants.class ).find(query);
        return users.getTotalObjects();
    }

    public static Merchants saveMerchant(Merchants merchant) {
        // this field is not stored in DB and
        // is only used to transfer area object between app and backend
        if(merchant.getAddress()!=null) {
            merchant.getAddress().setAreaNIDB(null);
        }
        return Backendless.Persistence.save(merchant);
    }

    /*
     * Customer operations
     */
    public static void loadCustomer(BackendlessUser user) {
        ArrayList<String> relationProps = new ArrayList<>();
        relationProps.add("customer");
        Backendless.Data.of( BackendlessUser.class ).loadRelations(user, relationProps);
    }

    public static Customers getCustomer(String custId, int idType, boolean fetchCard) {
        Backendless.Data.mapTableToClass("Customers", Customers.class);

        BackendlessDataQuery query = new BackendlessDataQuery();
        switch(idType) {
            case CommonConstants.ID_TYPE_MOBILE:
                query.setWhereClause("mobile_num = '"+custId+"'");
                break;
            /*case CommonConstants.ID_TYPE_CARD:
                query.setWhereClause("cardId = '"+custId+"'");
                break;*/
            case CommonConstants.ID_TYPE_AUTO:
                query.setWhereClause("private_id = '"+custId+"'");
                break;
        }

        /*if(fetchCard) {
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.addRelated("membership_card");
            query.setQueryOptions(queryOptions);
        }*/

        BackendlessCollection<Customers> user = Backendless.Data.of( Customers.class ).find(query);
        if( user.getTotalObjects() == 0) {
            // No customer found is not an error
            //return null;
            String errorMsg = "No Customer found: "+custId+". whereclause: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        } else {
            /*if(fetchCard && user.getData().get(0).getMembership_card()==null) {
                String errorMsg = "No customer card set for user: "+custId;
                throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_CARD), errorMsg);
            }*/
            return user.getData().get(0);
        }
    }

    public static Customers saveCustomer(Customers customer) {
        Backendless.Data.mapTableToClass("Customers", Customers.class);
        // this field is not stored in DB and
        // is only used to transfer area object between app and backend
        customer.setAddressesNIDB(null);
        return Backendless.Persistence.save(customer);
    }

    /*
     * Customer card operations
     */
    /*public static CustomerCards getCustomerCard(String key, boolean isID) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        if(isID) {
            dataQuery.setWhereClause("card_id = '" + key + "'");
        } else {
            dataQuery.setWhereClause("cardNum = '" + key + "'");
        }

        BackendlessCollection<CustomerCards> collection = Backendless.Data.of(CustomerCards.class).find(dataQuery);
        if( collection.getTotalObjects() == 0) {
            String errorMsg = "No membership card found: "+key;
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_CARD), errorMsg);
        } else {
            return collection.getData().get(0);
        }
    }

    public static CustomerCards saveCustomerCard(CustomerCards card) {
        return Backendless.Persistence.save( card );
    }

    public static int getCardCnt(String whereClause) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        BackendlessCollection<CustomerCards> users = Backendless.Data.of( CustomerCards.class ).find(query);
        return users.getTotalObjects();
    }*/

    /*
     * Customer card new operations
     */
    /*public static CustomerCardsNew getCustomerCardNew(String key, boolean isID) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        if(isID) {
            dataQuery.setWhereClause("card_id = '" + key + "'");
        } else {
            dataQuery.setWhereClause("cardNum = '" + key + "'");
        }

        BackendlessCollection<CustomerCardsNew> collection = Backendless.Data.of(CustomerCardsNew.class).find(dataQuery);
        if( collection.getTotalObjects() == 0) {
            String errorMsg = "No membership card found: "+key;
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_CARD), errorMsg);
        } else {
            return collection.getData().get(0);
        }
    }

    public static CustomerCardsNew saveCustomerCardNew(CustomerCardsNew card) {
        return Backendless.Persistence.save( card );
    }

    public static int getCardCntNew(String whereClause) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        BackendlessCollection<CustomerCardsNew> users = Backendless.Data.of( CustomerCardsNew.class ).find(query);
        return users.getTotalObjects();
    }*/

    /*
     * Cashback operations
     */
    public static ArrayList<Cashback> fetchCashback(String whereClause, String cashbackTable,
                                                    boolean customerData) {

        Backendless.Data.mapTableToClass(cashbackTable, Cashback.class);

        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);
        dataQuery.setWhereClause(whereClause);

        if(customerData) {
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.addRelated("customer");
            //queryOptions.addRelated("customer.membership_card");
            dataQuery.setQueryOptions(queryOptions);
        }
        /*if(mchntData) {
            queryOptions.addRelated("merchant");
            //queryOptions.addRelated("merchant.buss_category");
            queryOptions.addRelated("merchant.address");
        }*/

        BackendlessCollection<Cashback> collection = Backendless.Data.of(Cashback.class).find(dataQuery);

        int cnt = collection.getTotalObjects();
        if(cnt > 0) {
            ArrayList<Cashback> objects = new ArrayList<>();
            while (collection.getCurrentPage().size() > 0)
            {
                objects.addAll(collection.getData());
                /*Iterator<Cashback> iterator = collection.getCurrentPage().iterator();
                while( iterator.hasNext() )
                {
                    objects.add(iterator.next());
                }*/
                collection = collection.nextPage();
            }
            return objects;
        } else {
            // no object found is not an error
            return null;
        }
    }

    public static Cashback saveCashback(Cashback cb, String tableName) {
        Backendless.Data.mapTableToClass(tableName, Cashback.class);
        return Backendless.Persistence.save( cb );
    }

    /*
     * OTP operations
     */
    public static void generateOtp(AllOtp otp, String mchntName, String[] edr, MyLogger logger) {
        // check if any OTP object already available for this user_id
        // If yes, first delete the same.
        // Create new OTP object
        // Send SMS with OTP
        try {
            AllOtp newOtp = null;
            AllOtp oldOtp = fetchOtp(otp.getUser_id(), otp.getOpcode());
            if (oldOtp != null) {
                // delete oldOtp
                try {
                    deleteOtp(oldOtp);
                } catch(Exception e) {
                    edr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_OTP_DELETE_FAILED;
                    logger.error("Exception in generateOtp: Delete Otp: "+e.toString(),e);
                }
            }
            // create new OTP object
            //otp.setOtp_value(BackendUtils.generateOTP());
            String otpStr = SecurityHelper.generateOtp(otp, logger);
            newOtp = Backendless.Persistence.save(otp);

            // Send SMS through HTTP
            String smsText = SmsHelper.buildOtpSMS(newOtp.getUser_id(), otpStr, newOtp.getOpcode(), mchntName);
            // dont retry and raise exception immediately
            if (!SmsHelper.sendSMS(smsText, newOtp.getMobile_num(), edr, logger, false)){
                throw new BackendlessException(String.valueOf(ErrorCodes.SEND_SMS_FAILED), "Failed to send OTP SMS");
            }
        } catch (Exception e) {
            String errorMsg = "Exception in generateOtp: "+e.toString();
            throw new BackendlessException(String.valueOf(ErrorCodes.OTP_GENERATE_FAILED), errorMsg);
        }
    }

    public static boolean validateOtp(String userId, String opcode, String rcvdOtp, String[] edr, MyLogger logger) {
        logger.debug("In validateOtp");
        AllOtp otp = null;
        try {
            otp = BackendOps.fetchOtp(userId, opcode);

        } catch(BackendlessException e) {
            if(e.getCode().equals(ErrorCodes.BL_ERROR_NO_DATA_FOUND)) {
                //throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_OTP), "");
                logger.debug("No OTP row available");
                return false;
            }
            throw e;
        }

        if(otp==null) {
            logger.debug("OTP object is null: "+userId+","+opcode);
            return false;
        }

        //Date otpTime = otp.getUpdated()==null?otp.getCreated():otp.getUpdated();
        Date currTime = new Date();

        if ( ((currTime.getTime() - otp.getCreated().getTime()) < (MyGlobalSettings.getOtpValidMins()*60*1000)) &&
                SecurityHelper.verifyOtp(otp, rcvdOtp, logger) ) {
            // active otp available and matched
            // delete as can be used only once
            try {
                deleteOtp(otp);
            } catch(Exception e) {
                // ignore - delete will be tried again, when OTP is generated by same user for same opcode again
                edr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_OTP_DELETE_FAILED;
                logger.error("Exception in generateOtp: Delete Otp: "+e.toString(),e);
            }
        } else {
            //throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_OTP), "");
            logger.debug("OTP not matching: "+currTime.getTime()+", "+otp.getCreated().getTime());
            return false;
        }
        return true;
    }

    private static AllOtp fetchOtp(String userId, String opcode) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause("user_id = '" + userId + "' AND opcode = '"+opcode+"'");

        QueryOptions options = new QueryOptions();
        // let the latest on top
        options.addSortByOption("created DESC");
        dataQuery.setQueryOptions(options);

        BackendlessCollection<AllOtp> collection = Backendless.Data.of(AllOtp.class).find(dataQuery);
        if( collection.getTotalObjects() > 0) {
            // return the latest one, in case of multiple records
            // may happen if delete failed for similar record
            return collection.getData().get(0);
        } else {
            return null;
        }
    }

    private static void deleteOtp(AllOtp otp) {
        Backendless.Persistence.of( AllOtp.class ).remove( otp );
    }

    /*
     * Counters operations
     */
    /*
    public static Double fetchCounterValue(String name) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause("name = '" + name + "'");

        BackendlessCollection<Counters> collection = Backendless.Data.of(Counters.class).find(dataQuery);
        if( collection.getTotalObjects() > 0) {
            Counters counter = collection.getData().get(0);

            // increment counter - very important to do
            counter.setValue(counter.getValue()+1);
            counter = Backendless.Persistence.save( counter );
            return counter.getValue();
        } else {
            String errorMsg = "In fetchCounter: No data found" + name;
            BackendlessFault fault = new BackendlessFault(BackendResponseCodes.BL_ERROR_NO_DATA_FOUND,errorMsg);
            throw new BackendlessException(fault);
        }
    }*/

    public static Long fetchCounterValue(String name) {
        return Backendless.Counters.incrementAndGet(name);
    }
    public static Long decrementCounterValue(String name) {
        return Backendless.Counters.decrementAndGet(name);
    }

    /*
     * Trusted Devices operations
     */
    public static MerchantDevice fetchDevice(String deviceId) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause("device_id = '" + deviceId + "'");

        BackendlessCollection<MerchantDevice> collection = Backendless.Data.of(MerchantDevice.class).find(dataQuery);
        if( collection.getTotalObjects() > 0) {
            return collection.getData().get(0);
        } else {
            String errorMsg = "In fetchDevice: No data found" + deviceId;
            BackendlessFault fault = new BackendlessFault(ErrorCodes.BL_ERROR_NO_DATA_FOUND,errorMsg);
            throw new BackendlessException(fault);
        }
    }

    public static void deleteMchntDevice(MerchantDevice device) {
        Backendless.Persistence.of( MerchantDevice.class ).remove( device );
    }

    /*
     * Merchant operations ops
     */
    public static ArrayList<MerchantOps> findActiveMchntPwdResetReqs(String merchantId) {

        //create where clause
        StringBuilder whereClause = new StringBuilder();

        whereClause.append("op_code = '").append(DbConstants.OP_RESET_PASSWD).append("'");
        whereClause.append(" AND op_status = '").append(DbConstantsBackend.USER_OP_STATUS_PENDING).append("'");
        whereClause.append("AND merchant_id = '").append(merchantId).append("'");

        // created within last 'cool off mins'
        // Taking twice (*2) time - as it may happen that white 'reset mins' duration is passed
        // but passwdResetTime have not exceuted yet - as it executes every 10/15 mins or so
        long time = (new Date().getTime()) - (2*MyGlobalSettings.getMchntPasswdResetMins() * CommonConstants.MILLISECS_IN_MINUTE);
        whereClause.append(" AND createTime > ").append(time);

        return fetchMerchantOps(whereClause.toString());
    }

    public static ArrayList<MerchantOps> fetchMerchantOps(String whereClause) {
        // fetch cashback objects from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause(whereClause);
        dataQuery.setPageSize( CommonConstants.DB_QUERY_PAGE_SIZE);

        QueryOptions options = new QueryOptions();
        options.addSortByOption("createTime DESC");
        dataQuery.setQueryOptions(options);

        BackendlessCollection<MerchantOps> collection = Backendless.Data.of(MerchantOps.class).find(dataQuery);

        int cnt = collection.getTotalObjects();
        if(cnt > 0) {
            ArrayList<MerchantOps> objects = new ArrayList<>();
            while (collection.getCurrentPage().size() > 0)
            {
                objects.addAll(collection.getData());
                /*int size  = collection.getCurrentPage().size();
                Iterator<MerchantOps> iterator = collection.getCurrentPage().iterator();
                while( iterator.hasNext() )
                {
                    objects.add(iterator.next());
                }*/
                collection = collection.nextPage();
            }
            return objects;
        } else {
            // no object is not an error
            return null;
        }
    }

    public static MerchantOps saveMerchantOp(MerchantOps op) {
        return Backendless.Persistence.save( op );
    }

    public static void deleteMerchantOp(MerchantOps op) {
        Backendless.Persistence.of( MerchantOps.class ).remove( op );
    }

    /*
     * Customer operations ops
     */
    public static ArrayList<CustomerOps> findActiveCustPinResetReqs(String custPvtId) {

        //create where clause
        StringBuilder whereClause = new StringBuilder();

        whereClause.append("op_code = '").append(DbConstants.OP_RESET_PIN).append("'");
        whereClause.append(" AND op_status = '").append(DbConstantsBackend.USER_OP_STATUS_PENDING).append("'");
        whereClause.append("AND privateId = '").append(custPvtId).append("'");

        // created within last 'cool off' mins
        long time = (new Date().getTime()) - (MyGlobalSettings.getCustPasswdResetMins() * CommonConstants.MILLISECS_IN_MINUTE);
        whereClause.append(" AND createTime > ").append(time);

        return fetchCustomerOps(whereClause.toString());
    }

    public static ArrayList<CustomerOps> fetchCustomerOps(String whereClause) {
        // fetch objects from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause(whereClause);
        dataQuery.setPageSize( CommonConstants.DB_QUERY_PAGE_SIZE);

        QueryOptions options = new QueryOptions();
        options.addSortByOption("createTime DESC");
        dataQuery.setQueryOptions(options);

        BackendlessCollection<CustomerOps> collection = Backendless.Data.of(CustomerOps.class).find(dataQuery);

        int cnt = collection.getTotalObjects();
        if(cnt > 0) {
            ArrayList<CustomerOps> objects = new ArrayList<>();
            while (collection.getCurrentPage().size() > 0)
            {
                //int size  = collection.getCurrentPage().size();
                objects.addAll(collection.getData());
                /*Iterator<CustomerOps> iterator = collection.getCurrentPage().iterator();
                while( iterator.hasNext() )
                {
                    objects.add(iterator.next());
                }*/
                collection = collection.nextPage();
            }
            return objects;
        } else {
            // no object is not an error
            return null;
        }
    }

    public static CustomerOps saveCustomerOp(CustomerOps op) {
        return Backendless.Persistence.save( op );
    }

    public static void deleteCustomerOp(CustomerOps op) {
        Backendless.Persistence.of( CustomerOps.class ).remove( op );
    }


    /*
     * WrongAttempts operations
     */
    // returns 'null' if not found and new created
    /*
    public static WrongAttempts fetchOrCreateWrongAttempt(String userId, String type, int userType) {
        WrongAttempts attempt = null;
        try {
            attempt = fetchWrongAttempts(userId, type);
        } catch(BackendlessException e) {
            if(!e.getCode().equals(BackendResponseCodes.BL_ERROR_NO_DATA_FOUND)) {
                throw e;
            }
        }
        if(attempt==null) {
            // create row
            WrongAttempts newAttempt = new WrongAttempts();
            newAttempt.setUser_id(userId);
            newAttempt.setParam_type(type);
            newAttempt.setAttempt_cnt(0);
            newAttempt.setUser_type(userType);
            return saveWrongAttempt(newAttempt);
        }
        return attempt;
    }*/

    public static int getWrongAttemptCnt(String userId, String type) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();

        long now = (new Date()).getTime();
        long expiryTime = now - (MyGlobalSettings.getWrongAttemptResetMins()*CommonConstants.MILLISECS_IN_MINUTE);

        //DateUtil todayMidnight = new DateUtil(BackendConstants.TIMEZONE);
        //todayMidnight.toMidnight();

        dataQuery.setWhereClause("user_id = '" + userId +
                "' AND param_type = '" + type +
                //"' AND created < '" + todayMidnight.getTime().getTime() + "'");
                "' AND created > " + expiryTime);

        BackendlessCollection<WrongAttempts> collection = Backendless.Data.of(WrongAttempts.class).find(dataQuery);
        return collection.getTotalObjects();
    }

    /*public static WrongAttempts fetchWrongAttempts(String userId, String type) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause("user_id = '" + userId + "'" + "AND attempt_type = '" + type + "'");

        BackendlessCollection<WrongAttempts> collection = Backendless.Data.of(WrongAttempts.class).find(dataQuery);
        if( collection.getTotalObjects() > 0) {
            return collection.getData().get(0);
        } else {
            return null;
        }
    }*/

    public static WrongAttempts saveWrongAttempt(WrongAttempts attempt) {
        return Backendless.Persistence.save( attempt );
    }

    /*
     * MerchantStats operations
     */
    public static MerchantStats fetchMerchantStats(String merchantId) {
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);
        dataQuery.setWhereClause("merchant_id = '" + merchantId + "'");

        BackendlessCollection<MerchantStats> collection = Backendless.Data.of(MerchantStats.class).find(dataQuery);
        if( collection.getTotalObjects() > 0) {
            return collection.getData().get(0);
        } else {
            return null;
        }
    }

    public static MerchantStats saveMerchantStats(MerchantStats stats) {
        return Backendless.Persistence.save( stats );
    }

    /*
     * Transaction operations
     */
    public static List<Transaction> fetchTransactions(String whereClause, String tableName) {
        Backendless.Data.mapTableToClass(tableName, Transaction.class);
        //Backendless.Data.mapTableToClass(cbTableName, Cashback.class);

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        // sorted by create time
        //QueryOptions queryOptions = new QueryOptions("create_time");
        //dataQuery.setQueryOptions(queryOptions);
        dataQuery.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);
        dataQuery.setWhereClause(whereClause);

        BackendlessCollection<Transaction> collection = Backendless.Data.of(Transaction.class).find(dataQuery);

        int size = collection.getTotalObjects();
        if(size <= 0) {
            // no matching txns in not an error
            return null;
        }

        List<Transaction> transactions = collection.getData();
        while(collection.getCurrentPage().size() > 0) {
            collection = collection.nextPage();
            transactions.addAll(collection.getData());
        }
        return transactions;
    }

    public static Transaction fetchTxn(String txnId, String tableName, String cbTableName) {
        Backendless.Data.mapTableToClass(tableName, Transaction.class);
        Backendless.Data.mapTableToClass(cbTableName, Cashback.class);

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("cashback");

        dataQuery.setQueryOptions(queryOptions);
        dataQuery.setWhereClause("trans_id = '"+txnId+"'");

        BackendlessCollection<Transaction> collection = Backendless.Data.of(Transaction.class).find(dataQuery);

        if( collection.getTotalObjects() == 0) {
            // no data found
            String errorMsg = "No Txn found: "+txnId;
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), errorMsg);
        } else {
            return collection.getData().get(0);
        }
    }

    public static Transaction updateTxn(Transaction txn, String tableName, String cbTableName) {
        Backendless.Data.mapTableToClass(tableName, Transaction.class);
        Backendless.Data.mapTableToClass(cbTableName, Cashback.class);
        return Backendless.Persistence.save(txn);
    }

    /*
     * InternalUser operations
     */
    public static InternalUser getInternalUser(String userId) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("id = '"+userId+"'");

        BackendlessCollection<InternalUser> user = Backendless.Data.of( InternalUser.class ).find(query);
        if( user.getTotalObjects() == 0) {
            // no data found
            String errorMsg = "No internal user found: "+userId;
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        } else {
            return user.getData().get(0);
        }
    }

    public static void loadInternalUser(BackendlessUser user) {
        ArrayList<String> relationProps = new ArrayList<>();
        relationProps.add("internalUser");

        Backendless.Data.of( BackendlessUser.class ).loadRelations(user, relationProps);
    }

    public static InternalUser updateInternalUser(InternalUser user) {
        return Backendless.Persistence.save(user);
    }

    /*
     * Merchant Id ops
     */
    public static MerchantIdBatches firstMerchantIdBatchByBatchId(String tableName, String whereClause, boolean highest) {
        Backendless.Data.mapTableToClass(tableName, MerchantIdBatches.class);

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        QueryOptions options = new QueryOptions();
        if(highest) {
            options.addSortByOption("batchId DESC");
        } else {
            options.addSortByOption("batchId ASC");
        }
        dataQuery.setQueryOptions(options);
        dataQuery.setPageSize(1);
        dataQuery.setWhereClause(whereClause);

        BackendlessCollection<MerchantIdBatches> collection = Backendless.Data.of(MerchantIdBatches.class).find(dataQuery);
        if(collection.getTotalObjects() > 0) {
            return collection.getData().get(0);
        } else {
            return null;
        }
    }

    public static MerchantIdBatches saveMerchantIdBatch(String tableName, MerchantIdBatches batch) {
        Backendless.Data.mapTableToClass(tableName, MerchantIdBatches.class);
        return Backendless.Persistence.save(batch);
    }

    public static MerchantIdBatches fetchMerchantIdBatch(String tableName, String whereClause) {
        Backendless.Data.mapTableToClass(tableName, MerchantIdBatches.class);

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause(whereClause);

        BackendlessCollection<MerchantIdBatches> collection = Backendless.Data.of(MerchantIdBatches.class).find(dataQuery);
        int size = collection.getTotalObjects();
        if(size == 0) {
            return null;
        } else if(size == 1) {
            return collection.getData().get(0);
        }
        throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "More than 1 open merchant id batches: "+size+","+tableName);
    }

    /*
     * Card Id ops
     */
    /*public static CardIdBatches firstCardIdBatchByBatchId(String tableName, String whereClause, boolean highest) {
        Backendless.Data.mapTableToClass(tableName, CardIdBatches.class);

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        QueryOptions options = new QueryOptions();
        if(highest) {
            options.addSortByOption("batchId DESC");
        } else {
            options.addSortByOption("batchId ASC");
        }
        dataQuery.setQueryOptions(options);
        dataQuery.setPageSize(1);
        dataQuery.setWhereClause(whereClause);

        BackendlessCollection<CardIdBatches> collection = Backendless.Data.of(CardIdBatches.class).find(dataQuery);
        if(collection.getTotalObjects() > 0) {
            return collection.getData().get(0);
        } else {
            return null;
        }
    }

    public static CardIdBatches saveCardIdBatch(String tableName, CardIdBatches batch) {
        Backendless.Data.mapTableToClass(tableName, CardIdBatches.class);
        return Backendless.Persistence.save(batch);
    }

    public static List<CardIdBatches> fetchOpenCardIdBatches(String tableName) {
        Backendless.Data.mapTableToClass(tableName, CardIdBatches.class);

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);
        dataQuery.setWhereClause("status = '"+ DbConstantsBackend.BATCH_STATUS_OPEN+"'");

        BackendlessCollection<CardIdBatches> collection = Backendless.Data.of(CardIdBatches.class).find(dataQuery);

        int size = collection.getTotalObjects();
        if(size <= 0) {
            // no open batches
            return null;
        }

        List<CardIdBatches> batches = collection.getData();
        while(collection.getCurrentPage().size() > 0) {
            collection = collection.nextPage();
            batches.addAll(collection.getData());
        }
        return batches;
    }*/

    /*public static CardIdBatches fetchHighestOpenCardIdBatch(String tableName) {
        Backendless.Data.mapTableToClass(tableName, CardIdBatches.class);

        String whereClause = "status = '"+ DbConstantsBackend.BATCH_STATUS_OPEN+"'";

        // fetch txns object from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        QueryOptions options = new QueryOptions();
        options.addSortByOption("batchId DESC");
        dataQuery.setQueryOptions(options);
        dataQuery.setPageSize(1);
        dataQuery.setWhereClause(whereClause);

        BackendlessCollection<CardIdBatches> collection = Backendless.Data.of(CardIdBatches.class).find(dataQuery);
        int size = collection.getTotalObjects();
        if(size == 0) {
            return null;
        } else if(size == 1) {
            return collection.getData().get(0);
        }
        throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "More than 1 open Card id batches: "+size+","+tableName);
    }*/


    public static InternalUserDevice fetchInternalUserDevice(String userId) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("userId = '"+userId+"'");

        BackendlessCollection<InternalUserDevice> user = Backendless.Data.of( InternalUserDevice.class ).find(query);
        if( user.getTotalObjects() == 0) {
            return null;
        } else {
            return user.getData().get(0);
        }
    }

    public static InternalUserDevice saveInternalUserDevice(InternalUserDevice device) {
        return Backendless.Persistence.save(device);
    }

    public static Cities fetchCity(String cityName) {
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("city = '"+cityName+"'");

        BackendlessCollection<Cities> city = Backendless.Data.of( Cities.class ).find(query);
        if( city.getTotalObjects() == 0) {
            // no data found
            String errorMsg = "No city found: "+cityName;
            throw new BackendlessException(String.valueOf(ErrorCodes.BL_ERROR_NO_DATA_FOUND), errorMsg);
        } else {
            return city.getData().get(0);
        }
    }

    public static void describeTable(String tableName) {
        Backendless.Persistence.describe(tableName);
    }

    public static String renameFile(String oldPathName, String newName) {
        return Backendless.Files.renameFile( oldPathName, newName );
    }

    /*
     * Failed SMS ops
     */
    public static ArrayList<FailedSms> findRecentFailedSms() {

        // created within last 'cool off mins'
        long time = (new Date().getTime()) - (GlobalSettingConstants.FAILED_SMS_RETRY_MINS * 60 * 1000);
        String whereClause = "created > "+String.valueOf(time)+" AND status = '"+DbConstantsBackend.FAILED_SMS_STATUS_PENDING+"'";

        return fetchFailedSms(whereClause);
    }

    public static ArrayList<FailedSms> fetchFailedSms(String whereClause) {
        Backendless.Data.mapTableToClass("FailedSms", FailedSms.class);
        
        // fetch cashback objects from DB
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause(whereClause);
        dataQuery.setPageSize( CommonConstants.DB_QUERY_PAGE_SIZE);

        QueryOptions options = new QueryOptions();
        options.addSortByOption("created ASC");
        dataQuery.setQueryOptions(options);

        BackendlessCollection<FailedSms> collection = Backendless.Data.of(FailedSms.class).find(dataQuery);

        int cnt = collection.getTotalObjects();
        if(cnt > 0) {
            ArrayList<FailedSms> objects = new ArrayList<>();
            while (collection.getCurrentPage().size() > 0)
            {
                objects.addAll(collection.getData());
                collection = collection.nextPage();
            }
            return objects;
        } else {
            // no object is not an error
            return null;
        }
    }

    public static FailedSms addFailedSms(String text, String recipients) {
        FailedSms data = new FailedSms();
        data.setRecipients(recipients);
        data.setText(text);
        data.setStatus(DbConstantsBackend.FAILED_SMS_STATUS_PENDING);

        //TODO: encode SMS text

        Backendless.Data.mapTableToClass("FailedSms", FailedSms.class);
        return Backendless.Persistence.save( data );
    }

    public static FailedSms saveFailedSms(FailedSms sms) {
        Backendless.Data.mapTableToClass("FailedSms", FailedSms.class);
        return Backendless.Persistence.save( sms );
    }

    /*
     * Global Setting Ops
     */
    public static int getGlobalSettingCnt() {
        BackendlessCollection<GlobalSettings> data = Backendless.Data.of( GlobalSettings.class ).find();
        return data.getTotalObjects();
    }

    public static void saveGlobalSetting(GlobalSettings data) {
        Backendless.Data.mapTableToClass("GlobalSettings", GlobalSettings.class);
        Backendless.Persistence.save( data );
    }

    /*
     * Merchant Order fxs
     */
    /*public static List<MerchantOrders> fetchMchntOrders(String whereClause) {
        //Backendless.Data.mapTableToClass("MerchantOrders", MerchantOrders.class);
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);
        query.setWhereClause(whereClause);

        Backendless.Data.mapTableToClass("MerchantOrders", MerchantOrders.class);
        BackendlessCollection<MerchantOrders> collection = Backendless.Data.of( MerchantOrders.class ).find(query);
        int cnt = collection.getTotalObjects();
        if( cnt == 0) {
            // No matching merchant order is not an error
            return null;
        }

        List<MerchantOrders> objects = new ArrayList<MerchantOrders>();
        while (collection.getCurrentPage().size() > 0)
        {
            objects.addAll(collection.getData());
            collection = collection.nextPage();
        }
        return objects;
    }

    public static int getMchntOrderCnt(String whereClause) {
        Backendless.Data.mapTableToClass("MerchantOrders", MerchantOrders.class);
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        BackendlessCollection<MerchantOrders> users = Backendless.Data.of( MerchantOrders.class ).find(query);
        return users.getTotalObjects();
    }

    public static MerchantOrders saveMchntOrder(MerchantOrders order) {
        return Backendless.Persistence.save( order );
    }

    public static void deleteMchntOrder(MerchantOrders order) {
        Backendless.Persistence.of( MerchantOrders.class ).remove( order );
    }

    public static int getAllottedCardCnt(String orderId) {
        String whereClause = "orderId = '"+orderId+"'";
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        BackendlessCollection<CustomerCards> users = Backendless.Data.of( CustomerCards.class ).find(query);
        return users.getTotalObjects();
    }

    public static List<CustomerCards> getAllottedCards(String orderId) {
        String whereClause = "orderId = '"+orderId+"'";
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause(whereClause);

        return Backendless.Data.of( CustomerCards.class ).find(query).getData();
    }*/

    /*
     * Customer Address functions
     */
    public static List<CustAddress> fetchCustAddresses(String privateId, MyLogger logger) {
        Backendless.Data.mapTableToClass("CustAddress", CustAddress.class);
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("custPrivateId = '"+privateId+"'");

        /*QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("area");
        queryOptions.addRelated("area.city");
        query.setQueryOptions(queryOptions);*/

        BackendlessCollection<CustAddress> collection = Backendless.Data.of( CustAddress.class ).find(query);
        int cnt = collection.getTotalObjects();
        if( cnt == 0) {
            return null;
        } else {
            logger.debug("Fetched Cust Addresses: "+cnt);
        }

        List<CustAddress> objects = new ArrayList<>(cnt);
        while (collection.getCurrentPage().size() > 0)
        {
            objects.addAll(collection.getData());
            collection = collection.nextPage();
        }

        // fetch area objects, and attach to 'custAddress' objects
        List<String> areaIds = new ArrayList<>(CommonConstants.MAX_ADDRESS_PER_CUSTOMER);
        for (CustAddress addr: objects) {
            areaIds.add(addr.getAreaId());
        }
        HashMap<String, Areas> areas = fetchAreas(areaIds, logger);
        for (CustAddress addr: objects) {
            addr.setAreaNIDB(areas.get(addr.getAreaId()));
        }

        return objects;
    }

    public static HashMap<String, CustAddress> fetchCustAddresses(List<String> idList, MyLogger logger) {
        Backendless.Data.mapTableToClass("CustAddress", CustAddress.class);

        // build where clause
        String whereClause = null;
        for (String id : idList) {
            if (whereClause == null) {
                whereClause = "id = '" + id + "'";
            } else {
                whereClause = whereClause + " OR id = '" + id + "'";
            }
        }

        BackendlessDataQuery query = new BackendlessDataQuery();
        if(whereClause!=null) {
            logger.debug("fetchCustAddresses: whereClause: "+whereClause);
            query.setWhereClause(whereClause);
        }

        BackendlessCollection<CustAddress> collection = Backendless.Data.of( CustAddress.class ).find(query);
        int cnt = collection.getTotalObjects();
        if( cnt == 0) {
            String errorMsg = "No Addresses found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), errorMsg);
        }

        HashMap<String, CustAddress> objects = new HashMap<>();
        while (collection.getCurrentPage().size() > 0)
        {
            Iterator<CustAddress> iterator = collection.getCurrentPage().iterator();
            while (iterator.hasNext()) {
                CustAddress item = iterator.next();
                objects.put(item.getId(), item);
            }
            collection = collection.nextPage();
        }

        // fetch area objects, and attach to 'custAddress' objects
        List<String> areaIds = new ArrayList<>(CommonConstants.MAX_ADDRESS_PER_CUSTOMER);
        for (CustAddress addr: objects.values()) {
            areaIds.add(addr.getAreaId());
        }
        HashMap<String, Areas> areas = fetchAreas(areaIds, logger);
        for (CustAddress addr: objects.values()) {
            addr.setAreaNIDB(areas.get(addr.getAreaId()));
        }

        return objects;
    }

    public static CustAddress getCustAddress(String id) {
        if(id==null || id.isEmpty()) {
            return null;
        }

        Backendless.Data.mapTableToClass("CustAddress", CustAddress.class);
        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("id = '"+id+"'");

        /*QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("area");
        queryOptions.addRelated("area.city");
        query.setQueryOptions(queryOptions);*/

        BackendlessCollection<CustAddress> addr = Backendless.Data.of( CustAddress.class ).find(query);
        if( addr.getTotalObjects() == 0) {
            String errorMsg = "No Cust Address found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), errorMsg);
        } else {
            return addr.getData().get(0);
        }
    }

    public static CustAddress saveCustAddress(CustAddress addr) {
        Backendless.Data.mapTableToClass("CustAddress", CustAddress.class);
        // this field is not stored in DB and
        // is only used to transfer area object between app and backend
        addr.setAreaNIDB(null);
        return Backendless.Persistence.save(addr);
    }

    /*
     * Area functions
     */
    public static Areas getArea(String id) {
        Backendless.Data.mapTableToClass("Areas", Areas.class);

        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("id = '" + id + "'");

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("city");
        query.setQueryOptions(queryOptions);

        BackendlessCollection<Areas> collection = Backendless.Data.of( Areas.class ).find(query);
        int cnt = collection.getTotalObjects();
        if( cnt == 0) {
            String errorMsg = "No Areas found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), errorMsg);
        } else {
            return collection.getData().get(0);
        }
    }

    public static HashMap<String, Areas> fetchAreas(List<String> idList, MyLogger logger) {
        Backendless.Data.mapTableToClass("Areas", Areas.class);

        // build where clause
        String whereClause = null;
        for (String id : idList) {
            if (whereClause == null) {
                whereClause = "id = '" + id + "'";
            } else {
                whereClause = whereClause + " OR id = '" + id + "'";
            }
        }

        BackendlessDataQuery query = new BackendlessDataQuery();
        if(whereClause!=null) {
            logger.debug("fetchAreas: whereClause: "+whereClause);
            query.setWhereClause(whereClause);
        }

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("city");
        query.setQueryOptions(queryOptions);

        BackendlessCollection<Areas> collection = Backendless.Data.of( Areas.class ).find(query);
        int cnt = collection.getTotalObjects();
        if( cnt == 0) {
            String errorMsg = "No Areas found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), errorMsg);
        }

        HashMap<String, Areas> objects = new HashMap<>();
        while (collection.getCurrentPage().size() > 0)
        {
            Iterator<Areas> iterator = collection.getCurrentPage().iterator();
            while (iterator.hasNext()) {
                Areas item = iterator.next();
                objects.put(item.getId(), item);
            }
            collection = collection.nextPage();
        }
        return objects;
    }

    public static Areas saveArea(Areas area) {
        Backendless.Data.mapTableToClass("Areas", Areas.class);
        return Backendless.Persistence.save(area);
    }


    /*
     * Merchant Delivery Area functions
     */
    public static List<String> fetchMchntsForDlvry(String areaId) {
        Backendless.Data.mapTableToClass("MchntDlvryAreas", MchntDlvryAreas.class);

        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setWhereClause("areaId = '"+areaId+"'");
        query.setPageSize( CommonConstants.DB_QUERY_PAGE_SIZE);

        BackendlessCollection<MchntDlvryAreas> collection = Backendless.Data.of( MchntDlvryAreas.class ).find(query);
        int cnt = collection.getTotalObjects();
        if( cnt == 0) {
            String errorMsg = "No Merchants found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), errorMsg);
        }

        ArrayList<String> objects = new ArrayList<>();
        while (collection.getCurrentPage().size() > 0)
        {
            Iterator<MchntDlvryAreas> iterator = collection.getCurrentPage().iterator();
            while (iterator.hasNext()) {
                MchntDlvryAreas item = iterator.next();
                objects.add(item.getMerchantId());
            }
            collection = collection.nextPage();
        }
        return objects;
    }


    /*
     * Prescription functions
     */
    public static Prescriptions savePrescription(Prescriptions prescrip) {
        Backendless.Data.mapTableToClass("Prescriptions", Prescriptions.class);
        return Backendless.Persistence.save(prescrip);
    }

    /*
     * Customer Order functions
     */

    // This does not fetch any child objects
    public static CustomerOrder getCustomerOrder(String orderId, MyLogger logger) {
        Backendless.Data.mapTableToClass("CustomerOrder", CustomerOrder.class);
        Backendless.Data.mapTableToClass("Prescriptions", Prescriptions.class);

        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);

        // build where clause
        String whereClause = "id = '" + orderId + "'";
        query.setWhereClause(whereClause);

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("prescrips");
        query.setQueryOptions(queryOptions);

        BackendlessCollection<CustomerOrder> orders = Backendless.Data.of( CustomerOrder.class ).find(query);
        if( orders.getTotalObjects() == 0) {
            String errorMsg = "No Orders found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        }

        return orders.getData().get(0);

        /*CustomerOrder order = orders.getData().get(0);
        order.setAddressNIDB(getCustAddress(order.getAddressId()));

        List<CustomerOrder> objects = new ArrayList<>(orders.getTotalObjects());
        while (orders.getCurrentPage().size() > 0)
        {
            objects.addAll(orders.getData());
            orders = orders.nextPage();
        }

        // fetch 'address' objects
        List<String> addressIds = new ArrayList<>(objects.size());
        for (CustomerOrder item: objects) {
            addressIds.add(item.getAddressId());
        }
        HashMap<String, CustAddress> addresses = fetchCustAddresses(addressIds, logger);
        for (CustomerOrder order: objects) {
            order.setAddressNIDB(addresses.get(order.getAddressId()));
        }

        return objects;*/

    }

    public static CustomerOrder saveCustOrder(CustomerOrder order) {
        Backendless.Data.mapTableToClass("CustomerOrder", CustomerOrder.class);
        return Backendless.Persistence.save(order);
    }

    public static List<CustomerOrder> fetchPendingOrders(String mchntId, MyLogger logger) {
        Backendless.Data.mapTableToClass("CustomerOrder", CustomerOrder.class);
        Backendless.Data.mapTableToClass("Prescriptions", Prescriptions.class);

        BackendlessDataQuery query = new BackendlessDataQuery();
        query.setPageSize(CommonConstants.DB_QUERY_PAGE_SIZE);

        // build where clause
        String whereClause = "merchantId = '" + mchntId + "' AND " + DbConstants.CUSTOMER_ORDER_STATUS.getPendingWhereClause();
        logger.debug("In fetchPendingOrders: "+whereClause);
        query.setWhereClause(whereClause);

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addRelated("prescrips");
        query.setQueryOptions(queryOptions);

        BackendlessCollection<CustomerOrder> orders = Backendless.Data.of( CustomerOrder.class ).find(query);
        if( orders.getTotalObjects() == 0) {
            String errorMsg = "No Orders found: "+query.getWhereClause();
            throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER), errorMsg);
        }

        List<CustomerOrder> objects = new ArrayList<>(orders.getTotalObjects());
        while (orders.getCurrentPage().size() > 0)
        {
            objects.addAll(orders.getData());
            orders = orders.nextPage();
        }

        // fetch 'address' objects
        List<String> addressIds = new ArrayList<>(objects.size());
        for (CustomerOrder item: objects) {
            addressIds.add(item.getAddressId());
        }
        HashMap<String, CustAddress> addresses = fetchCustAddresses(addressIds, logger);
        for (CustomerOrder order: objects) {
            order.setAddressNIDB(addresses.get(order.getAddressId()));
        }

        return objects;

    }


    /*
     * Bulk Operations via REST
     */
    public static int doBulkRequest(String txnTableName, String whereClause, String method, String updateStr,
                                     String userToken, MyLogger logger) {
        /*
        * curl
        * -H application-id:09667F8B-98A7-E6B9-FFEB-B2B6EE831A00
        * -H secret-key:95971CBD-BADD-C61D-FF32-559664AE4F00
        * -H Content-Type:application/json
        * -X DELETE
        * -v https://api.backendless.com/v1/data/bulk/<tablename>?where=<whereClause>
        */
        try {
            String whereClauseEnc = URLEncoder.encode(whereClause, "UTF-8");

            // Building URL without URI
            StringBuilder sb = new StringBuilder(BackendConstants.BULK_API_URL);
            sb.append(txnTableName);
            sb.append("?where=");
            sb.append(whereClauseEnc);

            URL url = new URL(sb.toString());
            logger.debug("Bulk URL: "+url.toString());

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            //for( String key : HeadersManager.getInstance().getHeaders().keySet() )
            //    conn.addRequestProperty( key, HeadersManager.getInstance().getHeaders().get( key ) );

            conn.setRequestProperty("application-id", BackendConstants.APPLICATION_ID);
            conn.setRequestProperty("user-token", userToken);
            conn.setRequestProperty("secret-key", BackendConstants.REST_SECRET_KEY);
            conn.setRequestProperty("application-type", "REST");
            //logger.debug(userToken+", "+BackendConstants.REST_SECRET_KEY);

            if(updateStr!=null && !updateStr.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                OutputStream os = conn.getOutputStream();
                os.write(updateStr.getBytes());
                os.flush();
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logger.error("Error HTTP response: "+conn.getResponseCode());
                return -1;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            int records = 0;
            String output;
            while ((output = br.readLine()) != null) {
                logger.debug("Output from server: "+output);
                records = Integer.parseInt(output.replaceAll("\\p{C}", "?"));
            }

            conn.disconnect();
            logger.debug("Bulk Op rows: "+records);
            return records;

        } catch (Exception e) {
            logger.error("Bulk Op failed: "+e.toString());
            return -1;
        }
    }
}
