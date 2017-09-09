package in.ezeshop.timers;

/**
 * TxnArchive0Timer is a timer.
 * It is executed according to the schedule defined in Backendless Console. The
 * class becomes a timer by extending the TimerExtender class. The information
 * about the timer, its name, schedule, expiration date/time is configured in
 * the special annotation - BackendlessTimer. The annotation contains a JSON
 * object which describes all properties of the timer.
 */

/*
@BackendlessTimer("{'startDate':1463167800000,'frequency':{'schedule':'daily','repeat':{'every':1}},'timername':'TxnArchive0'}")
public class TxnArchive0Timer extends com.backendless.servercode.extension.TimerExtender
{
    @Override
    public void execute( String appVersionId ) throws Exception
    {
        String MERCHANT_ID_SUFFIX = "0";
        Backendless.Logging.setLogReportingPolicy(BackendConstants.LOG_POLICY_NUM_MSGS, BackendConstants.LOG_POLICY_FREQ_SECS);
        Logger logger = Backendless.Logging.getLogger("com.myecash.timers.TxnArchiver0");

        try {
            logger.debug("Before: "+ HeadersManager.getInstance().getHeaders().toString());

            TxnArchiver archiver = new TxnArchiver(MERCHANT_ID_SUFFIX);
            archiver.execute(logger);
        } catch(Exception e) {
            logger.error("Exception in TxnArchive0Timer: "+e.toString());
            //Backendless.Logging.flush();
            if(e instanceof BackendlessException) {
                throw CommonUtils.getNewException((BackendlessException) e);
            }
            throw e;
        }
    }
}*/