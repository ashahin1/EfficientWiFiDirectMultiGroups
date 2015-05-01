package esnetlab.apps.android.wifidirect.efficientmultigroups;

/**
 * Created by Ahmed on 4/9/2015.
 */
public class MessageHelper {

    public static final String MESSAGE_TYPE_DATA_SEPARATOR = ">>>";

    public static String getFormattedMessage(MessageType messageType, String message) {
        String formattedMessage;

        formattedMessage = messageType.toString();
        formattedMessage += MESSAGE_TYPE_DATA_SEPARATOR;
        formattedMessage += message;

        return formattedMessage;
    }

    public static MessageTypeData getMessageTypeAndData(String message) {
        MessageTypeData messageTypeData = new MessageTypeData();
        String msgStrings[] = message.split(MESSAGE_TYPE_DATA_SEPARATOR);
        if (msgStrings.length == 2) {
            messageTypeData.messageType = MessageType.valueOf(msgStrings[0]);
            messageTypeData.messageData = msgStrings[1];

            return messageTypeData;
        }
        return null;
    }
}

