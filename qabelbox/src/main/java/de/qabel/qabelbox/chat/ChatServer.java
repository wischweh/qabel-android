package de.qabel.qabelbox.chat;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.qabel.core.config.Contact;
import de.qabel.core.config.Entity;
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

	protected final ChatMessagesDataBase dataBase;
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
		Log.d(TAG, "Pulling DropMessages");
		long lastRetrieved = dataBase.getLastRetrievedDropMessageTime();
		Collection<DropMessage> result = QabelBoxApplication.getInstance().getService().retrieveDropMessages(QabelBoxApplication.getInstance().getService().getActiveIdentity(), lastRetrieved);
		retrieveMessages(result);
		return result;
	}

	protected void retrieveMessages(Collection<DropMessage> retrievedMessages) {
		String identityKey=QabelBoxApplication.getInstance().getService().getActiveIdentity().getEcPublicKey().getReadableKeyIdentifier();
		long lastRetrieved=Long.MIN_VALUE;
		Log.d(TAG, "last retrieved dropmessage time " + lastRetrieved + " / " + System.currentTimeMillis());
		if (retrievedMessages != null) {
			Log.d(TAG, "new message count: " + retrievedMessages.size());
			//store into db
			for (DropMessage item : retrievedMessages) {
				lastRetrieved=storeDropMessage(item, identityKey, lastRetrieved);
			}
		}
		dataBase.setLastRetrivedDropMessagesTime(lastRetrieved);
		Log.d(TAG, "new retrieved dropmessage time " + lastRetrieved);
		notifyCallbacks();
	}

	protected long storeDropMessage(DropMessage item, String recieversIdentityKey, long lastRetrieved) {
		ChatMessageItem cms = new ChatMessageItem(item);
		cms.receiver=recieversIdentityKey;
		cms.isNew = true;
		storeIntoDB(cms);
		//@todo replace this with header from server response.
		return Math.max(item.getCreationDate().getTime(), lastRetrieved);
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
					notifyCallbacks();
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
	private void notifyCallbacks() {

		for (ChatServerCallback callback : callbacks) {
			callback.onRefreshed();
		}
	}

	public DropMessage getTextDropMessage(String message) {
		return getTextDropMessage(QabelBoxApplication.getInstance().getService().getActiveIdentity(),message);
	}

	public DropMessage getTextDropMessage(Entity sender, String message) {
		JSONObject payloadJson = new JSONObject();
		try {
			payloadJson.put(TAG_MESSAGE, message);
		} catch (JSONException e) {
			Log.e(TAG, "error on create json", e);
		}
		String payload = payloadJson.toString();
		return new DropMessage(sender, payload, ChatMessageItem.BOX_MESSAGE);

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
