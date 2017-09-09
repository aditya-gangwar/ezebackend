package in.ezeshop.events.persistence_service;

import com.backendless.exceptions.BackendlessException;
import com.backendless.persistence.BackendlessDataQuery;
import com.backendless.servercode.RunnerContext;
import com.backendless.servercode.annotation.Asset;
import in.ezeshop.common.constants.ErrorCodes;
import in.ezeshop.constants.BackendConstants;
import in.ezeshop.common.database.CustomerOps;
import in.ezeshop.utilities.BackendUtils;
import in.ezeshop.utilities.MyLogger;

/**
 * CustomerOpsTableEventHandler handles events for all entities. This is accomplished
 * with the @Asset( "CustomerOps" ) annotation.
 * The methods in the class correspond to the events selected in Backendless
 * Console.
 */
@Asset( "CustomerOps" )
public class CustomerOpsTableEventHandler extends com.backendless.servercode.extension.PersistenceExtender<CustomerOps>
{
    private MyLogger mLogger = new MyLogger("events.CustomerOpsTableEventHandler");
    private String[] mEdr = new String[BackendConstants.BACKEND_EDR_MAX_FIELDS];;

    @Override
    public void beforeFirst( RunnerContext context ) throws Exception
    {
        // block for not-authenticated user
        // this event handler does not get called, if find done from servercode
        if(context.getUserToken()==null) {
            //initCommon();
            //mLogger.error("In beforeLast: find attempt by not-authenticated user.");
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "CustomerOps-beforeFirst";
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
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "CustomerOps-beforeFind";
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
            mEdr[BackendConstants.EDR_API_NAME_IDX] = "CustomerOps-beforeLast";
            BackendUtils.writeOpNotAllowedEdr(mLogger, mEdr);
            throw new BackendlessException(String.valueOf(ErrorCodes.OPERATION_NOT_ALLOWED), "");
        }
    }
}
