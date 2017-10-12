
package in.ezeshop.services;

import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.IBackendlessService;
import in.ezeshop.common.MyMerchant;
import in.ezeshop.common.database.CustomerOps;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

import java.util.ArrayList;
import java.util.List;

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
    public List<CustAddress> saveCustAddress(CustAddress addr, Boolean setAsDefault) {
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "updateCustAddress";

        boolean validException = false;
        try {
            mLogger.debug("In updateCustAddress");

            // Only customer allowed to update address - internal user also not allowed
            Customers customer = (Customers) BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_CUSTOMER, mEdr, mLogger, false);
            mEdr[BackendConstants.EDR_CUST_ID_IDX] = customer.getPrivate_id();

            if(!customer.getPrivate_id().equals(addr.getCustPrivateId())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            CustAddress addrToSave = null;
            // Check if edit case
            if(addr.getId()!=null && !addr.getId().isEmpty()) {
                mLogger.debug("Cust Address edit case: "+addr.getId());
                // Fetch corresponding custAddress record from DB
                CustAddress addrDb = BackendOps.getAddress(addr.getId());
                // copy all fields
                addrDb.setArea(addr.getArea());
                addrDb.setContactNum(addr.getContactNum());
                addrDb.setText1(addr.getText1());
                addrDb.setToName(addr.getToName());
                addrToSave = addrDb;
            } else {
                mLogger.debug("Cust Address add case");
                // Add case - generate id
                addr.setId(BackendUtils.generateCustAddrId(customer.getPrivate_id()));
                if(addr.getArea().getId()==null || addr.getArea().getId().isEmpty()) {
                    mLogger.debug("New Area case: "+addr.getArea().getAreaName());
                    // New area case
                    addr.getArea().setId(BackendUtils.generateAreaId());
                    addr.getArea().setValidated(false);
                }
                addrToSave = addr;
            }

            addrToSave = BackendOps.saveCustAddress(addrToSave);

            try {
                if(setAsDefault) {
                    customer.setDefaultAddressId(addrToSave.getId());
                    BackendOps.updateCustomer(customer);
                }
            } catch (Exception e) {
                // ignore
                mLogger.error("CustomerServices.updateCustAddress: Failed to set as default address: "+
                        customer.getPrivate_id()+", "+addrToSave.getId(),e);
                mEdr[BackendConstants.EDR_IGNORED_ERROR_IDX] = BackendConstants.IGNORED_ERROR_CUST_ADDR_DEFAULT;
            }

            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return BackendOps.fetchCustAddresses(customer.getPrivate_id());

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
                    // dont want to send complete merchant objects
                    // convert the required info into a CSV string
                    // and send as other_details column of the cashback object
                    for (Cashback cb : data) {
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
                    }
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
