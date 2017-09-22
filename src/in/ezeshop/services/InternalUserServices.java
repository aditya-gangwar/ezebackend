package in.ezeshop.services;

import com.backendless.Backendless;
import com.backendless.exceptions.BackendlessException;
import com.backendless.servercode.IBackendlessService;
import com.backendless.servercode.InvocationContext;
import in.ezeshop.common.CommonUtils;
//import in.ezeshop.common.MyCardForAction;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.constants.DbConstantsBackend;
import in.ezeshop.database.InternalUser;
import in.ezeshop.messaging.SmsConstants;
import in.ezeshop.messaging.SmsHelper;
import in.ezeshop.utilities.BackendOps;
import in.ezeshop.utilities.MyLogger;
import in.ezeshop.utilities.BackendUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import in.ezeshop.common.database.*;
import in.ezeshop.common.constants.*;

/**
 * Created by adgangwa on 12-08-2016.
 */
public class InternalUserServices implements IBackendlessService {

    private MyLogger mLogger = new MyLogger("services.AgentServicesNoLogin");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    /*
     * Public methods: Backend REST APIs
     */
    public String registerMerchant(Merchants merchant)
    {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "registerMerchant";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = merchant.getRegFormNum()+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                merchant.getMobile_num()+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                merchant.getName();

        try {
            //mLogger.debug("In registerMerchant");
            //mLogger.debug("registerMerchant: Before: "+ InvocationContext.asString());
            //mLogger.debug("registerMerchant: Before: "+HeadersManager.getInstance().getHeaders().toString());
            //mLogger.flush();

            // Fetch agent
            InternalUser agent = (InternalUser) BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_AGENT, mEdr, mLogger, false);

            String merchantId = BackendUtils.registerMerchant(merchant, agent.getId(), mLogger, mEdr);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return merchantId;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void disableMerchant(String merchantId, String ticketNum, String reason, String remarks) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "disableMerchant";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = merchantId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                ticketNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                reason;

        try {
            // Fetch customer care user
            InternalUser internalUser = (InternalUser) BackendUtils.fetchCurrentUser(
                    null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            if( userType!=DbConstants.USER_TYPE_CC && userType!=DbConstants.USER_TYPE_ADMIN ) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // Fetch merchant
            Merchants merchant = BackendOps.getMerchant(merchantId, false, false);

            if(merchant.getAdmin_status()!=DbConstants.USER_STATUS_ACTIVE) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Merchant is not Active.");
            }

            // Add merchant op first - then update status
            MerchantOps op = new MerchantOps();
            op.setCreateTime(new Date());
            op.setMerchant_id(merchant.getAuto_id());
            op.setMobile_num(merchant.getMobile_num());
            op.setOp_code(DbConstants.OP_DISABLE_ACC);
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            op.setTicketNum(ticketNum);
            op.setReason(reason);
            op.setRemarks(remarks);
            op.setAgentId(internalUser.getId());
            op.setInitiatedBy( (userType==DbConstants.USER_TYPE_CC)?
                    DbConstantsBackend.USER_OP_INITBY_MCHNT :
                    DbConstantsBackend.USER_OP_INITBY_ADMIN);
            if(userType==DbConstants.USER_TYPE_CC) {
                op.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_CC);
            }
            op = BackendOps.saveMerchantOp(op);

            // Update status
            try {
                BackendUtils.setMerchantStatus(merchant, DbConstants.USER_STATUS_DISABLED, reason,
                        mEdr, mLogger);
                /*merchant.setAdmin_status(DbConstants.USER_STATUS_DISABLED);
                merchant.setStatus_update_time(new Date());
                merchant.setStatus_reason(reason);
                merchant = BackendOps.updateMerchant(merchant);*/
            } catch(Exception e) {
                mLogger.error("disableMerchant: Exception while updating merchant status: "+merchantId);
                // Rollback - delete merchant op added
                try {
                    BackendOps.deleteMerchantOp(op);
                } catch(Exception ex) {
                    mLogger.fatal("disableMerchant: Failed to rollback: merchant op deletion failed: "+merchantId);
                    // Rollback also failed
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    throw ex;
                }
                throw e;
            }

            // send SMS
            String smsText = String.format(SmsConstants.SMS_ACCOUNT_DISABLE, CommonUtils.getHalfVisibleStr(merchantId));
            SmsHelper.sendSMS(smsText, merchant.getMobile_num(), mEdr, mLogger, true);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void disableCustomer(boolean ltdModeCase, String privateId, String ticketNum, String reason, String remarks) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "disableCustomer";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = ltdModeCase+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                privateId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                ticketNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                reason;

        try {
            // Fetch customer care user
            InternalUser internalUser = (InternalUser) BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            if( userType!=DbConstants.USER_TYPE_CC && userType!=DbConstants.USER_TYPE_ADMIN) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // Fetch customer
            Customers customer = BackendOps.getCustomer(privateId, CommonConstants.ID_TYPE_AUTO, false);

            if(customer.getAdmin_status()!=DbConstants.USER_STATUS_ACTIVE) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Customer is not Active.");
            }

            // Add customer op first - then update status
            CustomerOps op = new CustomerOps();
            op.setCreateTime(new Date());
            op.setPrivateId(privateId);
            op.setMobile_num(customer.getMobile_num());
            if(ltdModeCase) {
                op.setOp_code(DbConstants.OP_LIMITED_MODE_ACC);
            } else {
                op.setOp_code(DbConstants.OP_DISABLE_ACC);
            }
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            op.setTicketNum(ticketNum);
            op.setReason(reason);
            op.setRemarks(remarks);
            op.setRequestor_id(internalUser.getId());
            op.setInitiatedBy( (userType==DbConstants.USER_TYPE_CC)?
                    DbConstantsBackend.USER_OP_INITBY_MCHNT :
                    DbConstantsBackend.USER_OP_INITBY_ADMIN);
            if(userType==DbConstants.USER_TYPE_CC) {
                op.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_CC);
            }
            op = BackendOps.saveCustomerOp(op);

            // Update status
            try {
                if(ltdModeCase) {
                    BackendUtils.setCustomerStatus(customer, DbConstants.USER_STATUS_LIMITED_CREDIT_ONLY, reason, mEdr, mLogger);
                } else {
                    BackendUtils.setCustomerStatus(customer, DbConstants.USER_STATUS_DISABLED, reason, mEdr, mLogger);
                }
            } catch(Exception e) {
                mLogger.error("disableMerchant: Exception while updating merchant status: "+privateId);
                // Rollback - delete merchant op added
                try {
                    BackendOps.deleteCustomerOp(op);
                } catch(Exception ex) {
                    mLogger.fatal("disableMerchant: Failed to rollback: merchant op deletion failed: "+privateId);
                    // Rollback also failed
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    throw ex;
                }
                throw e;
            }

            // send SMS
            String smsText = null;
            if(ltdModeCase) {
                smsText = String.format(SmsConstants.SMS_ACCOUNT_LIMITED_MODE, CommonUtils.getHalfVisibleMobileNum(customer.getMobile_num()));
            } else {
                smsText = String.format(SmsConstants.SMS_ACCOUNT_DISABLE, CommonUtils.getHalfVisibleMobileNum(customer.getMobile_num()));
            }
            SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public void clearDummyMchntData() {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "clearDummyMchntData";
        //mEdr[BackendConstants.EDR_API_PARAMS_IDX] = cardId;

        boolean validException = false;
        try {
            Object userObj = BackendUtils.fetchCurrentUser(DbConstants.USER_TYPE_AGENT, mEdr, mLogger, true);
            String agentId = mEdr[BackendConstants.EDR_INTERNAL_USER_ID_IDX];

            // Find the dummy merchant first
            Merchants merchant = BackendOps.getMerchant("agentId = '"+agentId+"' AND auto_id like '"+BackendConstants.DUMMY_MCHNT_COUNTRY_CODE+"%'");

            // Hardcoded table name check - to avoid accidental deletion
            if(!merchant.getCashback_table().equals("Cashback99") ||
                    !merchant.getTxn_table().equals("Transaction99")) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
            }

            BackendUtils.printCtxtInfo(mLogger);

            String userToken = InvocationContext.getUserToken();
            String whereClause = "merchant_id = '"+merchant.getAuto_id()+"'";

            // delete all cashback records
            Backendless.Data.mapTableToClass(merchant.getCashback_table(), Cashback.class);
            int recDel = BackendOps.doBulkRequest(merchant.getCashback_table(), whereClause, "DELETE", null, userToken, mLogger);
            mLogger.debug("Cashback records deleted: "+recDel+", "+whereClause);

            // delete all txns
            Backendless.Data.mapTableToClass(merchant.getTxn_table(), Transaction.class);
            recDel = BackendOps.doBulkRequest(merchant.getTxn_table(), whereClause, "DELETE", null, userToken, mLogger);
            mLogger.debug("Txn records deleted: "+recDel+", "+whereClause);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch (Exception e) {
            BackendUtils.handleException(e, false, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }


    /*
     * Private helper methods
     */
    /*
    private void setCbAndTransTables(Merchants merchant, long regCounter) {
        // decide on the cashback table using round robin
        int pool_size = BackendConstants.CASHBACK_TABLE_POOL_SIZE;
        int pool_start = BackendConstants.CASHBACK_TABLE_POOL_START;

        // use last 4 numeric digits for round-robin
        int table_suffix = pool_start + ((int)(regCounter % pool_size));

        String cbTableName = DbConstantsBackend.CASHBACK_TABLE_NAME + String.valueOf(table_suffix);
        merchant.setCashback_table(cbTableName);
        mLogger.debug("Generated cashback table name:" + cbTableName);

        // use the same prefix for cashback and transaction tables
        // as there is 1-to-1 mapping in the table schema - transaction0 maps to cashback0 only
        String transTableName = DbConstantsBackend.TRANSACTION_TABLE_NAME + String.valueOf(table_suffix);
        merchant.setTxn_table(transTableName);
        mLogger.debug("Generated transaction table name:" + transTableName);
    }*/

//    private void rollbackRegister(BackendlessUser user) {
}

    /*public List<MyCardForAction> execActionForCards(String codes, String action, String allotToUserId, String orderId, boolean getCardNumsOnly) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "execActionForCards";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = action+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                allotToUserId+BackendConstants.BACKEND_EDR_SUB_DELIMETER
                +orderId;

        try {
            // Fetch user
            InternalUser internalUser = (InternalUser) BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            // check for allowed roles and their actions
            switch (userType) {
                case DbConstants.USER_TYPE_CCNT:
                    // all card actions allowed to this user type
                    break;
                case DbConstants.USER_TYPE_AGENT:
                    if( !action.equals(CommonConstants.CARDS_ALLOT_TO_MCHNT) ) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Action not allowed to this user type");
                    }
                    break;
                default:
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // Integrity checks
            Merchants merchant = null;
            MerchantOrders effectOrder = null;
            int allotCardCnt = 0;
            if( action.equals(CommonConstants.CARDS_ALLOT_TO_MCHNT)||action.equals(CommonConstants.CARDS_RETURN_BY_MCHNT) ) {
                if(orderId==null || orderId.isEmpty() || allotToUserId==null || allotToUserId.isEmpty()) {
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Order ID is missing");
                }
                // fetch order
                List<MerchantOrders> orders = BackendOps.fetchMchntOrders("orderId = '"+orderId+"'");
                if(orders==null || orders.size()!=1) {
                    throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Order with given ID not found");
                }
                effectOrder = orders.get(0);
                allotCardCnt = effectOrder.getAllotedCardCnt();

                // Find merchant whom to allot
                merchant = BackendOps.getMerchant(allotToUserId, false, false);
                if(action.equals(CommonConstants.CARDS_ALLOT_TO_MCHNT)) {
                    BackendUtils.checkMerchantStatus(merchant, mEdr, mLogger);
                    // match merchant id
                    if(!allotToUserId.equals(effectOrder.getMerchantId())) {
                        mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                        throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR),
                                "Merchant ID given and in order don't match");
                    }
                }

                // Agent can allocate cards to only first order
                if(!effectOrder.getIsFirstOrder() &&
                        userType==DbConstants.USER_TYPE_AGENT) {
                    // Agent can allocate cards to only first order
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR),
                            "Request from Agent for non-first order");
                }
            }

            List<MyCardForAction> actionResults = null;

            // Loop on all given codes
            // If not able to assign any particular card from given list - return the status accordingly
            String[] csvFields = codes.split(CommonConstants.CSV_DELIMETER, -1);
            for (String code : csvFields) {
                if(code==null || code.isEmpty()) {
                    continue;
                }
                if(actionResults==null) {
                    actionResults = new ArrayList<>(csvFields.length);
                }

                MyCardForAction cardForAction = new MyCardForAction();
                cardForAction.setScannedCode(code);
                try {
                    // find card row against this code
                    CustomerCards card = BackendOps.getCustomerCard(code,true);
                    int curStatus = card.getStatus();
                    cardForAction.setCardNum(card.getCardNum());

                    if(!getCardNumsOnly) {
                        // update card row - as per requested action
                        boolean updateCard = false;
                        switch (action) {
                            case CommonConstants.CARDS_UPLOAD_TO_POOL:
                                if (curStatus == DbConstants.CUSTOMER_CARD_STATUS_FOR_PRINT) {
                                    card.setStatus(DbConstants.CUSTOMER_CARD_STATUS_NEW);
                                    card.setCcntId(internalUser.getId());
                                    updateCard = true;
                                } else {
                                    cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_WRONG_STATUS);
                                }
                                break;

                            case CommonConstants.CARDS_ALLOT_TO_MCHNT:
                                if (curStatus == DbConstants.CUSTOMER_CARD_STATUS_NEW) {

                                   if(allotCardCnt < effectOrder.getItemQty()) {
                                       // Update Card values
                                       if (userType == DbConstants.USER_TYPE_CCNT) {
                                           card.setCcntId(internalUser.getId());
                                       } else {
                                           card.setAgentId(internalUser.getId());
                                       }
                                       card.setStatus(DbConstants.CUSTOMER_CARD_STATUS_WITH_MERCHANT);
                                       card.setMchntId(merchant.getAuto_id());
                                       card.setOrderId(orderId);
                                       updateCard = true;
                                   } else {
                                       cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_ORDER_FULL);
                                   }
                                } else {
                                    cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_WRONG_STATUS);
                                }
                                break;

                            case CommonConstants.CARDS_RETURN_BY_MCHNT:
                                if (curStatus == DbConstants.CUSTOMER_CARD_STATUS_WITH_MERCHANT) {
                                    if(!card.getOrderId().equals(orderId)) {
                                        cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_WRONG_ORDER);
                                    } else if(allotCardCnt > 0) {
                                        card.setStatus(DbConstants.CUSTOMER_CARD_STATUS_NEW);
                                        card.setCcntId(internalUser.getId());
                                        card.setOrderId("");
                                        card.setMchntId("");
                                        updateCard = true;
                                    } else {
                                        cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_ORDER_EMPTY);
                                    }
                                } else {
                                    cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_WRONG_STATUS);
                                }
                                break;
                        }

                        if (updateCard) {
                            card.setStatus_update_time(new Date());
                            card.setStatus_reason("");
                            BackendOps.saveCustomerCard(card);
                            // set return status for this card - only after save ok
                            cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_OK);
                            if(action.equals(CommonConstants.CARDS_ALLOT_TO_MCHNT)) {
                                allotCardCnt++;
                            } else if(action.equals(CommonConstants.CARDS_RETURN_BY_MCHNT)) {
                                allotCardCnt--;
                            }
                        }
                    }

                } catch(Exception e) {
                    mLogger.error("execActionForCards: Exception",e);
                    // ignore error - only set action result accordingly
                    if( e instanceof BackendlessException ) {
                        int beCode = Integer.parseInt( ((BackendlessException) e).getCode() );
                        switch (beCode) {
                            case ErrorCodes.NO_SUCH_CARD:
                                cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_NSC);
                                break;
                            case ErrorCodes.NO_SUCH_USER:
                                cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_NSA);
                                break;
                            case ErrorCodes.USER_ACC_DISABLED:
                            case ErrorCodes.USER_ACC_LOCKED:
                                cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_WRONG_ALLOT_STATUS);
                                break;
                            default:
                                mLogger.error("execActionForCards: BE exception",e);
                                cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_ERROR);
                        }
                    } else {
                        cardForAction.setActionStatus(MyCardForAction.ACTION_STATUS_ERROR);
                    }
                }
                // Add to result
                actionResults.add(cardForAction);
            }

            // all cards processed - update allotted card count in the order
            if( action.equals(CommonConstants.CARDS_ALLOT_TO_MCHNT)||action.equals(CommonConstants.CARDS_RETURN_BY_MCHNT) ) {
                // cross verify allotted count once
                int cnt = BackendOps.getAllottedCardCnt(effectOrder.getOrderId());
                if(cnt!=allotCardCnt) {
                    // raise alarm for manual correction
                    // even though ignoring exception
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    BackendlessException e = new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR),
                            "Allotted card count does not match: "+allotCardCnt+","+cnt);
                    mLogger.error(e.getMessage(),e);
                }

                effectOrder.setAllotedCardCnt(allotCardCnt);
                // change order status, if required
                if(allotCardCnt == effectOrder.getItemQty() &&
                        action.equals(CommonConstants.CARDS_ALLOT_TO_MCHNT) &&
                        effectOrder.getStatus().equals(DbConstants.MCHNT_ORDER_STATUS.New.name())) {
                    // all cards allocated to order - change status
                    effectOrder.setStatus(DbConstants.MCHNT_ORDER_STATUS.InProcess.name());
                    effectOrder.setStatusChangeTime(new Date());
                    effectOrder.setStatusChangeUser(internalUser.getId());
                }
                try {
                    BackendOps.saveMchntOrder(effectOrder);
                } catch (Exception e) {
                    // ignore exception
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    mLogger.error("Failed to save mchnt order",e);
                }
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return actionResults;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public List<CustomerCards> getAllottedCards(String orderId) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getAllottedCards";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = orderId;

        try {
            // Fetch user
            InternalUser internalUser = (InternalUser) BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            // check for allowed roles and their actions
            switch (userType) {
                case DbConstants.USER_TYPE_AGENT:
                case DbConstants.USER_TYPE_CCNT:
                    break;
                default:
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // fetch order
            List<MerchantOrders> orders = BackendOps.fetchMchntOrders("orderId = '"+orderId+"'");
            if(orders==null || orders.size()!=1){
                throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), "Order Not found: "+orderId);
            }

            List<CustomerCards> cards = BackendOps.getAllottedCards(orderId);
            if(cards==null || (cards.size()!=orders.get(0).getAllotedCardCnt())) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                throw new BackendlessException(String.valueOf(ErrorCodes.GENERAL_ERROR),
                        "Allotted card count does not match: "+orders.get(0).getAllotedCardCnt()+","+cards.size());
            }

            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return cards;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public MerchantOrders changeOrderStatus(MerchantOrders updatedOrder) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "changeOrderStatus";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = updatedOrder.getOrderId()+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                updatedOrder.getStatus();

        try {
            // Fetch user
            InternalUser internalUser = (InternalUser) BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            // check for allowed roles and their actions
            switch (userType) {
                case DbConstants.USER_TYPE_CCNT:
                    break;
                default:
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                    throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // fetch order
            List<MerchantOrders> orders = BackendOps.fetchMchntOrders("orderId = '" + updatedOrder.getOrderId() + "'");
            if (orders == null || orders.size() != 1) {
                throw new BackendlessException(String.valueOf(ErrorCodes.NO_DATA_FOUND), "Order Not found: " + updatedOrder.getOrderId());
            }
            MerchantOrders orderInDb = orders.get(0);

            DbConstants.MCHNT_ORDER_STATUS newStatus = DbConstants.MCHNT_ORDER_STATUS.valueOf(updatedOrder.getStatus());
            switch (newStatus) {
                case Shipped:
                    if(updatedOrder.getInvoiceId().isEmpty() || updatedOrder.getInvoiceUrl().isEmpty()) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invoice details missing");
                    }
                    // check that agent id is valid
                    try {
                        BackendOps.getInternalUser(updatedOrder.getAgentId());
                    } catch (Exception e) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Wrong Agent ID");
                    }
                    orderInDb.setAgentId(updatedOrder.getAgentId());
                    orderInDb.setInvoiceId(updatedOrder.getInvoiceId());
                    orderInDb.setInvoiceUrl(updatedOrder.getInvoiceUrl());
                    break;

                case Completed:
                    if(orderInDb.getAllotedCardCnt().intValue()!=orderInDb.getItemQty().intValue()) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.MCHNT_ORDER_ALLOT_CARDS), "Free up the allocated cards first ");
                    }
                    if(orderInDb.getStatus().equals(DbConstants.MCHNT_ORDER_STATUS.Shipped.name())) {
                        if(updatedOrder.getActualPayMode().isEmpty() || updatedOrder.getPaymentRef().isEmpty()) {
                            throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invoice details missing");
                        }
                        // check that agent id is valid
                        try {
                            BackendOps.getInternalUser(updatedOrder.getAgentId());
                        } catch (Exception e) {
                            throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Wrong Agent ID");
                        }
                        orderInDb.setAgentId(updatedOrder.getAgentId());
                        orderInDb.setActualPayMode(updatedOrder.getActualPayMode());
                        orderInDb.setPaymentRef(updatedOrder.getPaymentRef());
                    }
                    break;

                case PaymentVerifyPending:
                    if(updatedOrder.getActualPayMode().isEmpty() || updatedOrder.getPaymentRef().isEmpty()) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Invoice details missing");
                    }
                    // check that agent id is valid
                    try {
                        BackendOps.getInternalUser(updatedOrder.getAgentId());
                    } catch (Exception e) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Wrong Agent ID");
                    }
                    orderInDb.setAgentId(updatedOrder.getAgentId());
                    orderInDb.setActualPayMode(updatedOrder.getActualPayMode());
                    orderInDb.setPaymentRef(updatedOrder.getPaymentRef());
                    break;

                case Rejected:
                case PaymentFailed:
                    if(orderInDb.getAllotedCardCnt()>0) {
                        throw new BackendlessException(String.valueOf(ErrorCodes.MCHNT_ORDER_FREE_CARDS), "Free up the allocated cards first ");
                    }
                    orderInDb.setInvoiceUrl("");
                    orderInDb.setComments(updatedOrder.getComments());
                    break;
            }

            //String txt = orderInDb.getComments() + updatedOrder.getComments();
            orderInDb.setComments(updatedOrder.getComments());
            orderInDb.setStatus(updatedOrder.getStatus());
            orderInDb = BackendOps.saveMchntOrder(orderInDb);

            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return orderInDb;

        } catch (Exception e) {
            BackendUtils.handleException(e, false, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }*/

    /*
    public void disableCustCard(String privateId, String cardNum, String ticketNum, String reason, String remarks) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "disableCustCard";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = cardNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                privateId+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                ticketNum+BackendConstants.BACKEND_EDR_SUB_DELIMETER+
                reason;

        try {
            // Fetch customer care user
            InternalUser internalUser = (InternalUser) BackendUtils.fetchCurrentUser(null, mEdr, mLogger, false);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            if( userType!=DbConstants.USER_TYPE_CC && userType!=DbConstants.USER_TYPE_ADMIN) {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // Fetch customer
            Customers customer = BackendOps.getCustomer(privateId, CommonConstants.ID_TYPE_AUTO, true);
            CustomerCards card = customer.getMembership_card();
            if(card==null) {
                String errorMsg = "No customer card set for user: "+customer.getMobile_num();
                throw new BackendlessException(String.valueOf(ErrorCodes.NO_SUCH_CARD), errorMsg);
            }

            if(!card.getCardNum().equals(cardNum)) {
                throw new BackendlessException(String.valueOf(ErrorCodes.WRONG_INPUT_DATA), "Wrong Card Number");
            }
            if(card.getStatus()!=DbConstants.CUSTOMER_CARD_STATUS_ACTIVE) {
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Customer Card is not Active");
            }

            // Add customer op first - then update status
            CustomerOps op = new CustomerOps();
            op.setCreateTime(new Date());
            op.setPrivateId(privateId);
            op.setMobile_num(customer.getMobile_num());
            op.setOp_code(DbConstants.OP_DISABLE_CARD);
            op.setOp_status(DbConstantsBackend.USER_OP_STATUS_COMPLETE);
            op.setTicketNum(ticketNum);
            op.setReason(reason);
            op.setRemarks(remarks);
            op.setRequestor_id(internalUser.getId());
            op.setInitiatedBy( (userType==DbConstants.USER_TYPE_CC)?
                    DbConstantsBackend.USER_OP_INITBY_MCHNT :
                    DbConstantsBackend.USER_OP_INITBY_ADMIN);
            if(userType==DbConstants.USER_TYPE_CC) {
                op.setInitiatedVia(DbConstantsBackend.USER_OP_INITVIA_CC);
            }
            String extra = "Card Num: "+card.getCardNum();
            op.setExtra_op_params(extra);
            op = BackendOps.saveCustomerOp(op);

            // Update Card Object
            try {
                card.setStatus(DbConstants.CUSTOMER_CARD_STATUS_DISABLED);
                card.setStatus_update_time(new Date());
                card.setStatus_reason(reason);
                BackendOps.saveCustomerCard(card);

            } catch(Exception e) {
                mLogger.error("disableCustCard: Exception while updating card status: "+privateId);
                // Rollback - delete merchant op added
                try {
                    BackendOps.deleteCustomerOp(op);
                } catch(Exception ex) {
                    mLogger.fatal("disableCustCard: Failed to rollback: merchant op deletion failed: "+privateId);
                    // Rollback also failed
                    mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_MANUAL_CHECK;
                    throw ex;
                }
                throw e;
            }

            // send SMS
            String smsText = String.format(SmsConstants.SMS_DISABLE_CARD, customer.getName(),
                    CommonUtils.getPartialVisibleStr(card.getCardNum()));
            SmsHelper.sendSMS(smsText, customer.getMobile_num(), mEdr, mLogger, true);

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;

        } catch(Exception e) {
            BackendUtils.handleException(e,false,mLogger,mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime,mLogger,mEdr);
        }
    }

    public CustomerCards getMemberCard(String cardId) {
        BackendUtils.initAll();
        long startTime = System.currentTimeMillis();
        mEdr[BackendConstants.EDR_START_TIME_IDX] = String.valueOf(startTime);
        mEdr[BackendConstants.EDR_API_NAME_IDX] = "getMemberCard";
        mEdr[BackendConstants.EDR_API_PARAMS_IDX] = cardId;

        try {
            // Send userType param as null to avoid checking within fetchCurrentUser fx.
            // But check immediatly after
            Object userObj = BackendUtils.fetchCurrentUser(null, mEdr, mLogger, true);
            int userType = Integer.parseInt(mEdr[BackendConstants.EDR_USER_TYPE_IDX]);

            CustomerCards memberCard = null;
            if (userType == DbConstants.USER_TYPE_CC || userType == DbConstants.USER_TYPE_CCNT || userType == DbConstants.USER_TYPE_AGENT) {
                memberCard = BackendOps.getCustomerCard(cardId, !BackendUtils.isCardNum(cardId));
                mEdr[BackendConstants.EDR_CUST_CARD_NUM_IDX] = memberCard.getCardNum();
            } else {
                mEdr[BackendConstants.EDR_SPECIAL_FLAG_IDX] = BackendConstants.BACKEND_EDR_SECURITY_BREACH;
                throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "Operation not allowed to this user");
            }

            // no exception - means function execution success
            mEdr[BackendConstants.EDR_RESULT_IDX] = BackendConstants.BACKEND_EDR_RESULT_OK;
            return memberCard;

        } catch (Exception e) {
            BackendUtils.handleException(e, false, mLogger, mEdr);
            throw e;
        } finally {
            BackendUtils.finalHandling(startTime, mLogger, mEdr);
        }
    }
    */

