
package in.ezeshop.services;

import com.backendless.BackendlessCollection;
import com.backendless.exceptions.BackendlessException;
import com.backendless.files.FileInfo;
import com.backendless.servercode.IBackendlessService;
import in.ezeshop.common.database.CustomerOps;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.InternalUser;
import in.ezeshop.messaging.PushNotifier;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.IdGenerator;
import in.ezeshop.utilities.MyLogger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;


/**
 * Created by adgangwa on 25-09-2016.
 */
public class CustomerServices implements IBackendlessService {

    private MyLogger mLogger = new MyLogger("services.CustomerServices");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    /*
     * Public methods: Backend REST APIs
     * Customer operations
     */
    public CustomerOrder createCustomerOrder(String mchntId, String addressId, String comments, java.util.List<String> prescripUrls) {
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "createCustomerOrder";

        boolean validException = false;
        try {
            mLogger.debug("In createCustomerOrder: "+mchntId);

            // Only customer allowed to create order - internal user also not allowed
            Customers customer = (Customers) BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_CUSTOMER, mEdr, mLogger, false);
            String custId = customer.getPrivate_id();
            mEdr[BackendConstants.EDR_CUST_ID_IDX] = custId;

            // Validations - Ideally none should fail, as already checked in app
            // 1) Valid Address should exist
            // 2) Valid Merchant should exist
            // 3) Either 'prescription' or 'comments' should be provided
            CustAddress addr = BackendOps.getAddress(addressId);
            if(addr==null) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),"Invalid Delivery Address: " + addressId);
            }
            Merchants mchnt = BackendOps.getMerchant(mchntId,false,false);
            if(mchnt==null) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),"Invalid Merchant: " + mchntId);
            }
            if( prescripUrls.size()<1 && (comments==null || comments.isEmpty()) ) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),"Neither prescription nor comments provided");
            }

            // Create prescription objects
            List<Prescriptions> prescrips = null;
            boolean firstPrescrip = true;
            if(prescripUrls.size()>0) {
                prescrips = new ArrayList<>(prescripUrls.size());
                for (String pUrl :
                        prescripUrls) {
                    Prescriptions p = new Prescriptions();
                    p.setCustomerId(custId);
                    p.setUrl(pUrl);
                    p.setId(IdGenerator.generatePrescripId(custId, firstPrescrip));
                    p = BackendOps.savePrescription(p);
                    prescrips.add(p);
                    firstPrescrip = false;
                }
            } else {
                mLogger.debug("No prescriptions in this order");
            }

            // Create order object and save
            CustomerOrder order = new CustomerOrder();
            order.setCustPrivId(custId);
            order.setMerchantId(mchntId);
            order.setAddressId(addressId);
            order.setCreateTime(new Date());
            order.setCustComments(comments);
            order.setPrescrips(prescrips);
            order.setId(IdGenerator.genCustOrderId(mchntId));
            // order state
            order.setCurrStatus(DbConstants.CUSTOMER_ORDER_STATUS.New.toString());
            order.setPrevStatus("");
            order.setStatusChgByUserType(DbConstants.USER_TYPE_CUSTOMER);
            order.setStatusChgReason("");
            // save in DB
            order = BackendOps.saveCustOrder(order);

            // Send in app notification to merchant
            String msg = String.format(CommonConstants.MY_LOCALE, SmsConstants.MSG_CUST_ORDER_NOTIF_TO_MCHNT, customer.getName());
            PushNotifier.pushNotification(msg,msg,mchnt.getMsgDevId(),mEdr,mLogger);

            // Set NIDB fields - after saving in DB
            BackendUtils.remSensitiveData(mchnt);
            order.setMerchantNIDB(mchnt);
            order.setAddressNIDB(addr);

            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return order;

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public List<Merchants> mchntsByDeliveryArea(String areadId) {
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "mchntsByDeliveryArea";

        boolean validException = false;
        try {
            mLogger.debug("In mchntsByDeliveryArea: "+areadId);

            // Fetch customer - send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediately after
            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            //boolean callByCC = false;
            if(userType==DbConstants.USER_TYPE_CUSTOMER) {
                Customers customer = (Customers) userObj;
                mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();

            } else if(userType==DbConstants.USER_TYPE_CC) {
                InternalUser user = (InternalUser) userObj;
                mEdr[BackendConstants.EDR_INTERNAL_USER_ID_IDX] = user.getId();

            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // Fetch merchant ids delivering in given area
            List<String> mchntIds= BackendOps.fetchMchntsForDlvry(areadId);
            List<Merchants> mchnts = new ArrayList<>(BackendOps.fetchMerchants(mchntIds, true, mLogger).values());
            // remove sensitive data from all objects
            for (Merchants mchnt : mchnts) {
                BackendUtils.remSensitiveData(mchnt);
            }

            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return mchnts;

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public List<CustAddress> saveCustAddress(CustAddress addr, Boolean setAsDefault) {
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "updateCustAddress";

        boolean validException = false;
        try {
            Areas rcvdArea = addr.getAreaNIDB();
            mLogger.debug("In saveCustAddress: "+rcvdArea.getValidated()+", "+setAsDefault);

            // Only customer allowed to update address - internal user also not allowed
            Customers customer = (Customers) BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_CUSTOMER, mEdr, mLogger, false);
            mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();

            if(!customer.getPrivate_id().equals(addr.getCustPrivateId())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // First check if area is to be saved
            if(rcvdArea.getId()==null || rcvdArea.getId().isEmpty()) {
                mLogger.debug("New Area case: "+rcvdArea.getAreaName());
                // New area - add to DB first
                rcvdArea.setId(IdGenerator.generateAreaId());
                rcvdArea.setValidated(false);
                rcvdArea = BackendOps.saveArea(rcvdArea);
            }

            // Save address after saving area
            CustAddress addrToSave = null;

            if(addr.getId()!=null && !addr.getId().isEmpty()) {
                mLogger.debug("Cust Address edit case: "+addr.getId());

                // Fetch corresponding custAddress record from DB
                CustAddress addrDb = BackendOps.getAddress(addr.getId());
                // copy all fields that can be edited
                addrDb.setContactNum(addr.getContactNum());
                addrDb.setText1(addr.getText1());
                addrDb.setToName(addr.getToName());
                addrToSave = addrDb;
            } else {
                mLogger.debug("Cust Address add case");
                // Add case - generate id
                addr.setId(IdGenerator.generateCustAddrId(customer.getPrivate_id()));
                addrToSave = addr;
            }

            // area id to be updated whether edit or add case
            addrToSave.setAreaId(rcvdArea.getId());
            addrToSave = BackendOps.saveCustAddress(addrToSave);

            try {
                if(setAsDefault) {
                    customer.setDefaultAddressId(addrToSave.getId());
                    BackendOps.saveCustomer(customer);
                }
            } catch (Exception e) {
                // ignore
                mLogger.error("CustomerServices.updateCustAddress: Failed to set as default address: "+
                        customer.getPrivate_id()+", "+addrToSave.getId(),e);
                mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_CUST_ADDR_DEFAULT;
            }

            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return BackendOps.fetchCustAddresses(customer.getPrivate_id(), mLogger);

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public List<Cashback> getCashbacks(String custPrivateId, long updatedSince) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getCashbacks";

        boolean validException = false;
        try {
            mLogger.debug("In getCashbacks");

            // Fetch merchant - send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediately after
            Object userObj = BackendUtils.fetchCurrentUser(
                    null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            //boolean callByCC = false;
            Customers customer = null;
            if(userType==DbConstants.USER_TYPE_CUSTOMER) {
                customer = (Customers) userObj;
                custPrivateId = customer.getPrivate_id();
                mEdr[BackendConstants.EDR_CUST_ID_IDX] = custPrivateId;

            } else if(userType==DbConstants.USER_TYPE_CC) {
                // fetch customer
                try {
                    customer = BackendOps.getCustomer(custPrivateId, CommonConstants.ID_TYPE_AUTO, false);
                } catch(BackendlessException e) {
                    if(e.getCode().equals(String.valueOf(ErrorCodes.NO_SUCH_USER))) {
                        // CC agent may enter wrong customer id by mistake
                        validException = true;
                    }
                    throw e;
                }
                //callByCC = true;
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // not checking for customer account status

            List<Cashback> cbs= null;
            String[] csvFields = customer.getCashback_table().split(CommonConstants.CSV_DELIMETER);

            // fetch cashback records from each table
            ArrayList<Cashback> data = null;
            for(int i=0; i<csvFields.length; i++) {

                // fetch all CB records for this customer in this table
                String whereClause = "cust_private_id = '" + custPrivateId + "'";

                if(updatedSince>0) {
                    whereClause = whereClause + " AND updated > "+updatedSince;
                }
                mLogger.debug("whereClause: "+whereClause);

                data = BackendOps.fetchCashback(whereClause,csvFields[i], false);
                if (data != null) {
                    if(cbs==null) {
                        cbs= new ArrayList<>();
                    }

                    List<String> mchntIds = new ArrayList<>(cbs.size());
                    for (Cashback cb : data) {
                        mchntIds.add(cb.getMerchant_id());
                    }
                    Map<String, Merchants> mchnts = BackendOps.fetchMerchants(mchntIds,true,mLogger);
                    for (Cashback cb : data) {
                        cb.setMerchantNIDB(mchnts.get(cb.getMerchant_id()));
                        cbs.add(cb);
                    }

                    // dont want to send complete merchant objects
                    // convert the required info into a CSV string
                    // and send as other_details column of the cashback object
                    /*for (Cashback cb : data) {
                        try {
                            Merchants merchant = BackendOps.getMerchant(cb.getMerchant_id(), false, true);
                            cb.setOther_details(MyMerchant.toCsvString(merchant));
                            cbs.add(cb);

                        } catch (Exception e) {
                            // I shouldn't be here
                            // ignore and try for next merchant - but log as alarm
                            mLogger.error("CustomerServices.getCashbacks: Exception while fetching Merchant: "+
                                    cb.getMerchant_id()+", "+custPrivateId,e);
                            mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_CB_WITH_NO_MCHNT;
                        }
                    }*/
                }
            }

            if( cbs==null || cbs.size()==0 ) {
                if( data!=null && data.size()>0) {
                    // cashback rows available, but still some issue
                    throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR), "CB rows available, but failed to transform.");
                }
                /*if(updatedSince==0) {
                    // should have atleast single record created during registration
                    mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_CUST_WITH_NO_CB_RECORD;
                } else {*/
                    validException = true;
                //}
                throw new BackendlessException(String.valueOf(ErrorCodes.BL_ERROR_NO_DATA_FOUND), "");
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return cbs;

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public List<Transaction> getTransactions(String custPrivateId, String whereClause) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getTransactions";

        boolean validException = false;
        try {
            mLogger.debug("In getTransactions");

            // Fetch customer - send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediately after
            Object userObj = BackendUtils.fetchCurrentUser(
                    null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            //boolean callByCC = false;
            Customers customer = null;
            if(userType==DbConstants.USER_TYPE_CUSTOMER) {
                customer = (Customers) userObj;
                custPrivateId = customer.getPrivate_id();
                mEdr[BackendConstants.EDR_CUST_ID_IDX] = custPrivateId;

            } else if(userType==DbConstants.USER_TYPE_CC) {
                // fetch customer
                try {
                    customer = BackendOps.getCustomer(custPrivateId, CommonConstants.ID_TYPE_AUTO, false);
                } catch(BackendlessException e) {
                    if(e.getCode().equals(String.valueOf(ErrorCodes.NO_SUCH_USER))) {
                        // CC agent may enter wrong customer id by mistake
                        validException = true;
                    }
                    throw e;
                }
                //callByCC = true;
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // not checking for customer account status

            List<Transaction> txns= null;
            String[] csvFields = customer.getTxn_tables().split(CommonConstants.CSV_DELIMETER);

            // fetch cashback records from each table
            for(int i=0; i<csvFields.length; i++) {

                // fetch txns for this customer in this table
                List<Transaction> data = BackendOps.fetchTransactions(whereClause,csvFields[i]);
                if (data != null) {
                    if(txns==null) {
                        txns= new ArrayList<>();
                    }
                    // add all fetched records from this table to final set
                    txns.addAll(data);
                }
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return txns;

        } catch(Exception e) {
            BackendUtils.handleException(e,validException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public List<CustomerOps> getCustomerOps(String internalId) {

        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        boolean positiveException = false;

        try {
            mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "getCustomerOps";
            mEdr[BackendConstants.EDR_API_PARAMS_IDX] = internalId;

            // send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediately after
            Object userObj = BackendUtils.fetchCurrentUser(
                    null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            Customers customer = null;
            boolean byCCUser = false;
            if(userType==DbConstants.USER_TYPE_CUSTOMER) {
                customer = (Customers) userObj;
                // check to ensure that merchant is request CB for itself only
                if (!customer.getPrivate_id().equals(internalId)) {
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA),"Invalid customer id provided: " + internalId);
                }
            } else if(userType==DbConstants.USER_TYPE_CC) {
                // use provided merchant values
                byCCUser = true;
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // not checking for merchant account status

            // fetch merchant ops
            String whereClause = "privateId = '"+internalId+"'";
            if(!byCCUser) {
                // return only 'completed' ops to merchant
                whereClause = whereClause+" AND op_status = '"+ DbConstantsBackend.USER_OP_STATUS_COMPLETE +"'";
            }
            mLogger.debug("where clause: "+whereClause);

            List<CustomerOps> ops = BackendOps.fetchCustomerOps(whereClause);
            if(ops==null) {
                // not exactly a positive exception - but using it to avoid logging of this as error
                // as it can happen frequently as valid scenario
                positiveException = true;
                throw new BackendlessException(String.valueOf(ErrorCodes.BL_ERROR_NO_DATA_FOUND), "");
            }

            if(!byCCUser) {
                // remove sensitive fields - from in-memory objects
                for (CustomerOps op: ops) {
                    op.setTicketNum("");
                    op.setReason("");
                    //op.setOtp("");
                    op.setOp_status("");
                    op.setRemarks("");
                }
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return ops;

        } catch(Exception e) {
            BackendUtils.handleException(e,positiveException,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

}
