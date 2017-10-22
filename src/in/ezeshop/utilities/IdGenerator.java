package in.ezeshop.utilities;

import com.backendless.exceptions.BackendlessException;
import in.ezeshop.common.Base35;
import in.ezeshop.common.CommonUtils;
import in.ezeshop.common.constants.CommonConstants;
import in.ezeshop.common.constants.DbConstants;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.MerchantIdBatches;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by adgangwa on 22-10-2017.
 */
public class IdGenerator {

    /*
     * Password & ID generators
     * All User facing IDs should be only numbers (expect the 2 char prefix)
     * All Internal IDs should be Base35
     */

    /*
     * No IDs should be without pre-defined prefix - Except Merchant ID
     */

    // user facing id prefixes - in caps
    private static String TXN_ID_PREFIX = "T";
    private static String CUST_ORDER_ID_PREFIX = "C";
    // internal id prefixes - in small
    private static String CUST_PRIVATE_ID_PREFIX = "i";
    private static String CUST_ADDR_ID_PREFIX = "a";
    private static String AREA_ID_PREFIX = "r";
    private static String CUST_PRESCRIPTION_ID_PREFIX = "p";

    private static Long mCustPrescripIdBreaker;

    /*
     * While use 'synchronized' in all methods - but not sure if it will work across different Backendless API calls from app
     * As getMyEpochSecs() returns the difference - so 9 digit secs will suffice for ~30 years from start time
     *
     * The user visisble ID format is: <1 char prefix> + <upto 14 digits>
     *     <prefix> + <5 random digits> + <upto 9 digit, my_epoch_secs>
     *
     * The internal id format is: <1 char prefix> + <Base35(upto 15 digits) = upto 10 chars>
     *     <prefix> + Base35(<6 random digits> + <upto 9 digit, my_epoch_secs>)
     */


    /*
     * User visible IDs - only numbers
     */

    public synchronized static String generateMerchantId(MerchantIdBatches batch, String countryCode, long regCounter) {
        // 8 digit merchant id format:
        // <1-3 digit country code> + <0-2 digit range id> + <2 digit batch id> + <3 digit s.no.>
        int serialNo = (int) (regCounter % BackendConstants.MERCHANT_ID_MAX_SNO_PER_BATCH);
        return countryCode + batch.getRangeBatchId() + String.format("%03d", serialNo);
    }

    public synchronized static String generateTxnId(String merchantId) {
        // Chances of clash - i.e. merchants with same end 5 digits trying to do txn in same second is very low
        return TXN_ID_PREFIX + merchantId.substring(merchantId.length()-5) + CommonUtils.getMyEpochSecs();

        // Txn Id: <prefix> + <mchnt id as base 35> + <my epoch time in secs as base35>
        //long mchntIdLong = Long.parseUnsignedLong(merchantId);
        //return TXN_ID_PREFIX + Base35.fromBase10(mchntIdLong,0) + Base35.fromBase10(CommonUtils.getMyEpochSecs(),0);

        // Txn Id : <7 chars for curr time in secs as Base35> + <6 char for merchant id as Base26> = total 13 chars
        /*long timeSecs = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long mchntIdLong = Long.parseUnsignedLong(merchantId);
        return Base35.fromBase10(timeSecs, 7) + Base25.fromBase10(mchntIdLong, 6);*/
    }

    public synchronized static String genCustOrderId(String merchantId) {
        // Chances of clash - i.e. merchants with same end 5 digits getting order in same second is very low
        // using last digits of merchantId instead of customerId - as merchants are very less compared to customers
        return CUST_PRIVATE_ID_PREFIX + merchantId.substring(merchantId.length()-5) + CommonUtils.getMyEpochSecs();
    }

    /*
     * Internal IDs - base 35 - to use less chars
     */

    public synchronized static String genCustPrivateId(String merchantId) {
        // Chances of clash - i.e. merchants with same end 6 digits, registering customer in same second is very very low
        long num = Long.parseUnsignedLong(merchantId.substring(merchantId.length()-6) + CommonUtils.getMyEpochSecs());
        return CUST_ORDER_ID_PREFIX + Base35.fromBase10(num,0);

        //Long customerCnt =  BackendOps.fetchCounterValue(DbConstantsBackend.CUSTOMER_ID_COUNTER);
        //return Base35.fromBase10(customerCnt, CommonConstants.CUSTOMER_INTERNAL_ID_LEN);
    }

    public synchronized static String generateCustAddrId(String custPrivId) {
        // as custPrivId is already in Base35 - so using last 5 digits instead of 6
        return CUST_ADDR_ID_PREFIX + custPrivId.substring(custPrivId.length()-5) + Base35.fromBase10(CommonUtils.getMyEpochSecs(),0);

        // Id : <6 chars for customer private id> + <6 char for own epoch time in 10 secs block> = total 12 chars
        //long myTimeSecs = Math.round(CommonUtils.getMyEpochSecs() / 10);
        //return custPrivId + Base35.fromBase10(myTimeSecs, 6);
    }

    public synchronized static String generateAreaId() {
        Random random = new Random();
        return AREA_ID_PREFIX + Base35.fromBase10(random.nextLong(), 0);
    }

    // firstPrescripInSet: Indicates that if this is first prescription in the order set or not.
    // So, if a customer uploads more than 1 prescription (say 4), then
    // for first prescrip: firstPrescripInSet=true, for other: firstPrescripInSet=false

    public synchronized static String generatePrescripId(String custPrivId, boolean firstPrescripInSet) {

        // In case of race condition between 2 different API call threads executing this function
        // Either the 'synchronized' should work - in which case modifying mCustPrescripIdBreakeris safe
        // Else, if 'synchronized' is not working, means 2 threads are in totally different space - hence modifying mCustPrescripIdBreakeris should still be safe
        if(firstPrescripInSet || mCustPrescripIdBreaker==null) {
            mCustPrescripIdBreaker = CommonUtils.getMyEpochSecs();
        } else {
            mCustPrescripIdBreaker++;
        }

        // as custPrivId is already in Base35 - so using last 5 digits instead of 6
        return CUST_PRESCRIPTION_ID_PREFIX + custPrivId.substring(custPrivId.length()-5) + Base35.fromBase10(mCustPrescripIdBreaker,0);
    }

    public synchronized static String generateLogId() {
        // random alphanumeric string
        Random random = new Random();
        char[] id = new char[BackendConstants.LOG_ID_LEN];
        for (int i = 0; i < BackendConstants.LOG_ID_LEN; i++) {
            id[i] = BackendConstants.pwdChars[random.nextInt(BackendConstants.pwdChars.length)];
        }
        return new String(id);
    }

    /*public static String generateMchntOrderId() {
        mSdfTimeDay.setTimeZone(TimeZone.getTimeZone(CommonConstants.TIMEZONE));
        String day = mSdfTimeDay.format(new Date());
        Long orderCnt =  BackendOps.fetchCounterValue(DbConstantsBackend.ORDER_ID_COUNTER);

        // Order Id : <MO>+<4 chars for curr day in ddMMyy format> + <daily counter>
        return CommonConstants.MCHNT_ORDER_ID_PREFIX + Base35.fromBase10(Long.parseLong(day), 4) + orderCnt.toString();
    }*/


    /*
     * Get User ID type - depending upon length and prefix
     */
    public static int getMerchantIdType(String id) {
        switch (id.length()) {
            case CommonConstants.MOBILE_NUM_LENGTH:
                return CommonConstants.ID_TYPE_MOBILE;
            case CommonConstants.MERCHANT_ID_LEN:
                return CommonConstants.ID_TYPE_AUTO;
            default:
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid Merchant ID: "+id);
        }
    }

    public static int getCustomerIdType(String id) {
        if(id.startsWith(CUST_PRIVATE_ID_PREFIX)) {
            return CommonConstants.ID_TYPE_AUTO;

        } else if(id.length()==CommonConstants.MOBILE_NUM_LENGTH) {
            return CommonConstants.ID_TYPE_MOBILE;
        } else {
            throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invalid Customer ID: "+id);
        }
    }

    /*
     * Get User type - depending upon the length and prefix of ID
     */
    public static int getUserType(String userId) {

        if(userId.startsWith(CUST_PRIVATE_ID_PREFIX)) {
            return DbConstants.USER_TYPE_CUSTOMER;

        } else {
            switch(userId.length()) {
                case CommonConstants.MERCHANT_ID_LEN:
                    return DbConstants.USER_TYPE_MERCHANT;
                case CommonConstants.INTERNAL_USER_ID_LEN:
                    if(userId.startsWith(CommonConstants.PREFIX_AGENT_ID)) {
                        return DbConstants.USER_TYPE_AGENT;
                    } else if(userId.startsWith(CommonConstants.PREFIX_CC_ID)) {
                        return DbConstants.USER_TYPE_CC;
                    } else {
                        throw new BackendlessException(String.valueOf(ErrorCodes.USER_WRONG_ID_PASSWD),"Invalid user type for id: "+userId);
                    }
                case CommonConstants.MOBILE_NUM_LENGTH:
                    return DbConstants.USER_TYPE_CUSTOMER;
                default:
                    throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_USER),"Invalid user type for id: "+userId);
            }
        }
    }



}
