package in.ezeshop.utilities;

import com.backendless.exceptions.BackendlessException;
import in.ezeshop.common.constants.CommonConstants;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.common.database.Customers;
import in.ezeshop.common.database.MerchantDevice;
import in.ezeshop.database.AllOtp;
import in.ezeshop.constants.BackendConstants;
import in.myecash.security.SaltedHashService;
import in.myecash.security.SimpleAES;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Created by adgangwa on 23-12-2016.
 */
public class SecurityHelper {

    //private static final String KEYADMIN_LOGINID = "keyadmin";
    //public static final String MEMBERCARD_KEY_COL_NAME = "memberCardKey";
    //public static final String DEVICEID_KEY_COL_NAME = "deviceIdKey";


    /*
     * OTP related fxs.
     */
    public static String generateOtp(AllOtp otp, MyLogger logger) {
        try {
            byte[] salt = SaltedHashService.generateSalt();
            String otpStr = generateOTP();
            byte[] id = SaltedHashService.getEncrypted(otpStr,salt);
            //logger.debug("Salt: "+new String(salt));
            //logger.debug("Encrypted OTP: "+new String(id));

            otp.setNamak(Base64.getEncoder().encodeToString(salt));
            otp.setOtp_value(Base64.getEncoder().encodeToString(id));
            //logger.debug("Salt: "+otp.getNamak());
            //logger.debug("PIN: "+otp.getOtp_value());

            return otpStr;

        } catch (Exception e) {
            logger.error("Exception while hashing OTP: "+otp.getOwnerId()+", "+otp.getOpcode(),e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Hashing of OTP failed: "+otp.getOwnerId()+", "+otp.getOpcode());
        }
    }

    public static boolean verifyOtp(AllOtp otp, String rcvdOtp, MyLogger logger) {
        logger.debug("In verifyOtp");
        try {
            byte[] salt = Base64.getDecoder().decode(otp.getNamak());
            byte[] id = Base64.getDecoder().decode(otp.getOtp_value());
            //logger.debug("Salt: "+new String(salt));
            //logger.debug("Encrypted OTP: "+new String(id));

            return SaltedHashService.authenticate(rcvdOtp, id, salt);

        } catch (Exception e) {
            String msg = "Exception while verifying OTP: "+otp.getOwnerId()+", "+otp.getOpcode();
            logger.error(msg,e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), msg);
        }
    }

    /*
     * Trusted Device related fxs.
     */
    public static void setDeviceId(MerchantDevice device, String deviceId, MyLogger logger) {
        try {
            byte[] salt = SaltedHashService.generateSalt();
            byte[] id = SaltedHashService.getEncrypted(deviceId,salt);

            device.setNamak(Base64.getEncoder().encodeToString(salt));
            device.setDevice_id(Base64.getEncoder().encodeToString(id));

        } catch (Exception e) {
            logger.error("Exception while hashing device id: "+device.getMerchant_id()+", " +
                    device.getManufacturer()+", "+device.getModel()+", "+deviceId,e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Hashing of device Id failed: "+deviceId);
        }
    }

    public static boolean verifyDeviceId(MerchantDevice device, String rcvdId) {
        try {
            byte[] salt = Base64.getDecoder().decode(device.getNamak());
            byte[] id = Base64.getDecoder().decode(device.getDevice_id());
            return SaltedHashService.authenticate(rcvdId, id, salt);

        } catch (Exception e) {
            String msg = "Exception while verifying device id: "+device.getMerchant_id()+", " +
                    device.getManufacturer()+", "+device.getModel()+", "+rcvdId;
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), msg);
        }
    }

    /*
     * Customer PIN related fxs.
     */
    public static String generateCustPin(Customers customer, MyLogger logger) {
        String pin = generateCustomerPIN();
        //logger.debug("Clear text PIN: "+pin);
        setCustPin(customer, pin, logger);
        return pin;
    }

    public static void setCustPin(Customers customer, String pin, MyLogger logger) {
        try {
            byte[] salt = SaltedHashService.generateSalt();
            byte[] pwd = SaltedHashService.getEncrypted(pin,salt);
            //logger.debug("Salt: "+new String(salt));
            //logger.debug("Encrypted PIN: "+new String(pwd));

            customer.setNamak(Base64.getEncoder().encodeToString(salt));
            customer.setTxn_pin(Base64.getEncoder().encodeToString(pwd));
            //logger.debug("Salt: "+customer.getNamak());
            //logger.debug("PIN: "+customer.getTxn_pin());

        } catch (Exception e) {
            logger.error("Exception while hashing customer pin: "+customer.getMobile_num(),e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Hashing of customer PIN failed: "+customer.getMobile_num());
        }
    }

    public static boolean verifyCustPin(Customers customer, String rcvdPin, MyLogger logger) {
        logger.debug("In verifyCustPin");
        try {
            byte[] salt = Base64.getDecoder().decode(customer.getNamak());
            byte[] pin = Base64.getDecoder().decode(customer.getTxn_pin());
            //logger.debug("Salt: "+new String(salt));
            //logger.debug("Encrypted PIN: "+new String(pin));
            return SaltedHashService.authenticate(rcvdPin, pin, salt);

        } catch (Exception e) {
            logger.error("Exception while verifying pin: "+customer.getMobile_num(),e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Customer PIN verification failed: "+customer.getMobile_num());
        }
    }

    /*
     * Member Card Number related fxs.
     */
    /*
    public static String getCardIdFromNum(String cardNum, String key, MyLogger logger) {
        //logger.debug("In getEncryptedCardId: "+cardNum);

        try {
            // Card Number is less than 16 bytes - so using SimpleAES
            // Add fixed prefix - this will help to do some basic validation in the app
            String strEncoded = CommonConstants.MEMBER_CARD_ID_PREFIX + SimpleAES.encrypt(key.toCharArray(), cardNum);
            //logger.debug("Encoded CardNum: " + strEncoded);
            return strEncoded;

        } catch (Exception e) {
            logger.error("Exception while encrypting text: "+cardNum,e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Encryption of text failed: "+cardNum);
        }
    }

    public static String getCardBarcode(String cardNum, MyLogger logger) {
        try {
            // random numeric string
            SecureRandom random = new SecureRandom();
            char[] id = new char[CommonConstants.CUSTOMER_CARDID_LEN];
            for (int i = 0; i < CommonConstants.CUSTOMER_CARDID_LEN; i++) {
                id[i] = BackendConstants.onlyNumbers[random.nextInt(BackendConstants.onlyNumbers.length)];
            }

            return CommonConstants.MEMBER_CARD_ID_PREFIX + new String(id);

        } catch (Exception e) {
            logger.error("Exception while encrypting text: "+e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Generate barcode failed");
        }
    }

    public static String getCardNumFromId(String cardId, String key, MyLogger logger) {
        //logger.debug("In getDecrypted: "+cardId);

        try {
            // check and remove prefix
            String toDecode = null;
            if (cardId.startsWith(CommonConstants.MEMBER_CARD_ID_PREFIX)) {
                toDecode = cardId.substring(CommonConstants.MEMBER_CARD_ID_PREFIX.length());
            } else {
                logger.error("Invalid Member card id prefix: "+cardId);
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Invalid Member card id prefix: "+cardId);
            }

            String strDecoded = SimpleAES.decrypt(key.toCharArray(), toDecode);
            //logger.debug("Decoded CardNum: " + strDecoded);
            return strDecoded;

        } catch (Exception e) {
            logger.error("Exception while decrypting text: "+cardId,e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Encryption of text failed: "+cardId);
        }
    }
    */

    /*
     * Function to get desired key
     */
    /*public static String getKey(String keyName, String keyadminPwd, MyLogger logger) {
        //logger.debug("In getKey");

        // login using 'admin' user
        BackendOps.loginUser(KEYADMIN_LOGINID,keyadminPwd);

        Backendless.Data.mapTableToClass("MycKeys", MycKeys.class);
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause("name = '" + keyName +"'");

        BackendlessCollection<MycKeys> collection = Backendless.Data.of(MycKeys.class).find(dataQuery);
        if( collection.getTotalObjects() != 1) {
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Invalid number of key records: "+keyName+":"+collection.getTotalObjects());
        }

        String key = (collection.getData().get(0)).getKey();

        BackendOps.logoutUser();
        //logger.debug("Exiting getKey: "+key);
        return key;
    }*/

    /*
     * Private helper fxs.
     */
    private static String generateCustomerPIN() {
        // random numeric string
        SecureRandom random = new SecureRandom();
        char[] id = new char[CommonConstants.PIN_LEN];
        for (int i = 0; i < CommonConstants.PIN_LEN; i++) {
            id[i] = BackendConstants.onlyNumbers[random.nextInt(BackendConstants.onlyNumbers.length)];
        }
        return new String(id);
    }

    public static String generateOTP() {
        if(BackendConstants.TESTING_USE_FIXED_OTP) {
            return BackendConstants.TESTING_FIXED_OTP_VALUE;
        }

        // random numeric string
        SecureRandom random = new SecureRandom();
        char[] id = new char[CommonConstants.OTP_LEN];
        for (int i = 0; i < CommonConstants.OTP_LEN; i++) {
            id[i] = BackendConstants.onlyNumbers[random.nextInt(BackendConstants.onlyNumbers.length)];
        }
        return new String(id);
    }

}

    /*
     * Generic encrypt/decrypt fxs.
     */
    /*public static String encryptStr(String str, String key, MyLogger logger) {
        //logger.debug("In encryptStr: "+str);
        try {
            // Using SimpleAES - as where ever its used, the string is less than 16 bytes
            return SimpleAES.encrypt(key.toCharArray(), str);
        } catch (Exception e) {
            logger.error("Exception while encrypting text: "+str,e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Encryption of text failed: "+str);
        }
    }

    public static String decryptStr(String str, String key, MyLogger logger) {
        //logger.debug("In decryptStr: "+str);
        try {
            return SimpleAES.decrypt(key.toCharArray(), str);
        } catch (Exception e) {
            logger.error("Exception while decrypting text: "+str,e);
            throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "Encryption of text failed: "+str);
        }
    }*/


