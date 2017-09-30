package in.ezeshop.messaging;

import com.backendless.Backendless;
import com.backendless.messaging.DeliveryOptions;
import com.backendless.messaging.PublishOptions;
import in.ezeshop.utilities.MyLogger;

import static in.ezeshop.utilities.BackendUtils.stackTraceStr;

/**
 * Created by adgangwa on 29-09-2017.
 */
public class PushNotifier {

    public static String PUSH_NOTIFY_TICKET_TEXT = "MyeCash";
    public static String PUSH_NOTIFY_CONTENT_TITLE = "MyeCash";

    public static boolean pushNotification(String msgText, String contentText,
                                           String recipient, String[] edr, MyLogger logger) {

        //long sTime = System.currentTimeMillis();
        if(recipient==null || recipient.isEmpty()) {
            return false;
        }
        logger.debug("Notification: " + contentText);

        try {

            DeliveryOptions deliveryOptions = new DeliveryOptions();
            deliveryOptions.addPushSinglecast( recipient );
            //deliveryOptions.setPushBroadcast( PushBroadcastMask.ANDROID | PushBroadcastMask.IOS );

            PublishOptions publishOptions = new PublishOptions();
            publishOptions.putHeader( "android-ticker-text", PUSH_NOTIFY_TICKET_TEXT );
            publishOptions.putHeader( "android-content-title", PUSH_NOTIFY_CONTENT_TITLE );
            publishOptions.putHeader( "android-content-text", contentText );

            Backendless.Messaging.publish( msgText, publishOptions, deliveryOptions );
            logger.debug("Notification sent: "+recipient);
            //edr[BackendConstants.EDR_SMS_SUBMIT_TIME_IDX] = String.valueOf(System.currentTimeMillis() - sTime);

        } catch (Exception e) {
            logger.error("Failed to push notification ("+contentText+") to "+recipient);
            logger.error("Failed to push notification:"+e.toString());
            logger.error(stackTraceStr(e));
            //edr[BackendConstants.EDR_SMS_STATUS_IDX] = BackendConstants.BACKEND_EDR_SMS_NOK;
            return false;

        }
        //edr[BackendConstants.EDR_SMS_STATUS_IDX] = BackendConstants.BACKEND_EDR_SMS_OK;
        return true;
    }


}
