package de.qabel.qabelbox.chat;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.qabel.core.config.Contact;
import de.qabel.core.config.Identity;
import de.qabel.core.drop.DropMessage;
import de.qabel.qabelbox.QabelBoxApplication;

/**
 * Created by danny on 17.02.16.
 */
public class ChatServer {

	private static final String TAG = "ChatServer";
	public static final String TAG_MESSAGE = "msg";
	public static final String TAG_URL = "url";
	public static final String TAG_KEY = "key";

	private final ChatMessagesDataBase dataBase;
	private final List<ChatServerCallback> callbacks = new ArrayList<>();

	public ChatServer(Identity currentIdentity) {

		dataBase = new ChatMessagesDataBase(QabelBoxApplication.getInstance(), currentIdentity);
	}


	public void addListener(ChatServerCallback callback) {

		callbacks.add(callback);
	}

	public void removeListener(ChatServerCallback callback) {

		callbacks.remove(callback);
	}

	/**
	 * click on refresh button
	 */

	private Collection<DropMessage> pullDropMessages() {
		long lastRetrieved = dataBase.getLastRetrievedDropMessageTime();
		Log.d(TAG, "last retrieved dropmessage time " + lastRetrieved + " / " + System.currentTimeMillis());
		String identityKey=QabelBoxApplication.getInstance().getService().getActiveIdentity().getEcPublicKey().getReadableKeyIdentifier();
		Collection<DropMessage> result = QabelBoxApplication.getInstance().getService().retrieveDropMessages(QabelBoxApplication.getInstance().getService().getActiveIdentity(), lastRetrieved);

		if (result != null) {
			Log.d(TAG, "new message count: " + result.size());
			//store into db
			for (DropMessage item : result) {
				ChatMessageItem cms = new ChatMessageItem(item);
				cms.receiver=identityKey;
				cms.isNew = true;
				storeIntoDB(cms);
				//@todo replace this with header from server response.
				lastRetrieved = Math.max(item.getCreationDate().getTime(), lastRetrieved);
        			}
		}
		dataBase.setLastRetrivedDropMessagesTime(lastRetrieved);
		Log.d(TAG, "new retrieved dropmessage time " + lastRetrieved);

		sendCallbacksRefreshed();
		return result;
	}

	boolean isSyncing = false;

	public void sync() {
		if (!isSyncing) {
			isSyncing = true;
			new AsyncTask<Void, Void, Collection<DropMessage>>() {
				@Override
				protected Collection<DropMessage> doInBackground(Void... params) {
					isSyncing = true;
					return pullDropMessages();
				}

				@Override
				protected void onPostExecute(Collection<DropMessage> dropMessages) {
					isSyncing = false;
					sendCallbacksRefreshed();
				}
			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

	public void storeIntoDB(ChatMessageItem item) {
		if (item != null) {
			dataBase.put(item);
		}
	}

	/**
	 * send all listener that chatmessage list was refrehsed
	 */
	private void sendCallbacksRefreshed() {

		for (ChatServerCallback callback : callbacks) {
			callback.onRefreshed();
		}
	}

	public DropMessage getTextDropMessage(String message) {

		String payload_type = ChatMessageItem.BOX_MESSAGE;
		JSONObject payloadJson = new JSONObject();
		try {
			payloadJson.put(TAG_MESSAGE, message);
		} catch (JSONException e) {
			Log.e(TAG, "error on create json", e);
		}
		String payload = payloadJson.toString();
		return new DropMessage(QabelBoxApplication.getInstance().getService().getActiveIdentity(), payload, payload_type);
	}

	public DropMessage getShareDropMessage(String message, String url, String key) {

		String payload_type = ChatMessageItem.SHARE_NOTIFICATION;
		JSONObject payloadJson = new JSONObject();
		try {
			payloadJson.put(TAG_MESSAGE, message);
			payloadJson.put(TAG_URL, url);
			payloadJson.put(TAG_KEY, key);
		} catch (JSONException e) {
			Log.e(TAG, "error on create json", e);
		}
		String payload = payloadJson.toString();
		return new DropMessage(QabelBoxApplication.getInstance().getService().getActiveIdentity(), payload, payload_type);
	}


	public int getNewMessageCount(Contact c) {
		return dataBase.getNewMessageCount(c);
	}

	public int setAllMessagesReaded(Contact c) {
		return dataBase.setAllMessagesRead(c);
	}

	public ChatMessageItem[] getAllMessages(Contact c) {
		return dataBase.get(c.getEcPublicKey().getReadableKeyIdentifier());
	}



	public interface ChatServerCallback {

		//droplist refreshed
		void onRefreshed();
	}
}
