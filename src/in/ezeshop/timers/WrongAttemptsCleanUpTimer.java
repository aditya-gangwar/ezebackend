package in.ezeshop.timers;

//import javax.ws.rs.core.UriBuilder;


/**
 * WrongAttemptsCleanUpTimer is a timer.
 * It is executed according to the schedule defined in Backendless Console. The
 * class becomes a timer by extending the TimerExtender class. The information
 * about the timer, its name, schedule, expiration date/time is configured in
 * the special annotation - BackendlessTimer. The annotation contains a JSON
 * object which describes all properties of the timer.
 */
/*
@BackendlessTimer("{'startDate':1465326000000,'frequency':{'schedule':'daily','repeat':{'every':1}},'timername':'WrongAttemptsCleanUp'}")
public class WrongAttemptsCleanUpTimer extends com.backendless.servercode.extension.TimerExtender
{

    private Logger mLogger;
    private SimpleDateFormat mSdfOnlyDateBackend;
    private Date mToday;

    @Override
    public void execute( String appVersionId ) throws Exception
    {
        initCommon();
        mToday = new Date();
        mSdfOnlyDateBackend = new SimpleDateFormat(CommonConstants.DATE_FORMAT_ONLY_DATE_BACKEND, CommonConstants.DATE_LOCALE);
        mSdfOnlyDateBackend.setTimeZone(TimeZone.getTimeZone(BackendConstants.TIMEZONE));
        // delete all wrong attempts of time older than midnight
        deleteOldWrongAttempts();
    }

    private void initCommon() {
        Backendless.Logging.setLogReportingPolicy(BackendConstants.LOG_POLICY_NUM_MSGS, BackendConstants.LOG_POLICY_FREQ_SECS);
        mLogger = Backendless.Logging.getLogger("com.myecash.services.MerchantServices");
        CommonUtils.initAll();
    }

    private int deleteOldWrongAttempts() {
        mLogger.debug( "In deleteOldWrongAttempts: ");

        // https://api.backendless.com/v1/data/bulk/WrongAttempts?where=created%20%3C%201465237800000
        HttpURLConnection conn = null;
        try {
            String whereClause = URLEncoder.encode(buildWrongAteemptsWhereClause(), "UTF-8").replaceAll("\\+", "%20");
            mLogger.debug( "Delete wrong attempts where clause: "+whereClause);

            UriBuilder builder = UriBuilder
                    .fromPath("https://api.backendless.com/v1/data/bulk")
                    .path("/{wrongAttemptsTable}")
                    .queryParam("where", whereClause);
            URI uri = builder.build("WrongAttempts");
            mLogger.debug( "WrongAttempts delete URL: "+uri.toString());
            URL url = new URL(uri.toString());

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("application-id", BackendConstants.APP_ID);
            conn.setRequestProperty("secret-key", BackendConstants.SECRET_KEY);
            conn.setRequestProperty("Content-Type", "application/json");


            //OutputStream os = conn.getOutputStream();
            //os.write(input.getBytes());
            //os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                mLogger.error("Error HTTP response: "+conn.getResponseCode());
                return -1;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            int recordsUpdated = 0;
            String output;
            while ((output = br.readLine()) != null) {
                mLogger.debug("Output from server: "+output);
                recordsUpdated = Integer.parseInt(output.replaceAll("\\p{C}", "?"));
            }

            conn.disconnect();
            conn=null;
            mLogger.debug("Updated delete status of wrong attempts: "+recordsUpdated);
            return recordsUpdated;

        } catch (Exception e) {
            mLogger.error("Exception in deleteOldWrongAttempts: "+e.toString());
            if(conn!=null) { conn.disconnect(); }
            return -1;
        }
    }

    private String buildWrongAteemptsWhereClause() {
        String today = mSdfOnlyDateBackend.format(mToday);
        // all txns older than today midnight - the timer runs 12:30 AM each day
        return "created < '"+today+"'";
    }
}
*/