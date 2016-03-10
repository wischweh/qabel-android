package de.qabel.qabelbox.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import de.qabel.core.config.Contact;
import de.qabel.core.config.Identity;

/**
 * class to store chat messages in database
 */
public class ChatMessagesDataBase extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "ChatMessages.db";
	private static final String TAG = "ChatMessagesDataBase";

	//table for store last load
	private static final String TABLE_NAME_LOAD = "load";
	private static final String COL_LOAD_TIMESTAMP = "timestamp";

	private static final String TABLE_MESSAGE_NAME = "messages";
	private static final String COL_MESSAGE_ID = "id";
	private static final String COL_MESSAGE_ISNEW = "isnew";
	private static final String COL_MESSAGE_TIMESTAMP = "timestamp";
	private static final String COL_MESSAGE_SENDER = "sender";
	private static final String COL_MESSAGE_RECEIVER = "receiver";
	private static final String COL_MESSAGE_ACKNOWLEDGE_ID = "ackid";
	private static final String COL_MESSAGE_PAYLOAD_TYPE = "payload_type";
	private static final String COL_MESSAGE_PAYLOAD = "payload";

	private static final String CREATE_TABLE =
			"CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGE_NAME + " (" +
					COL_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
					COL_MESSAGE_SENDER + " TEXT," +
					COL_MESSAGE_RECEIVER + " TEXT," +
					COL_MESSAGE_ACKNOWLEDGE_ID + " TEXT," +
					COL_MESSAGE_TIMESTAMP + " LONG NOT NULL," +
					COL_MESSAGE_PAYLOAD_TYPE + " TEXT NOT NULL," +
					COL_MESSAGE_ISNEW + " INTEGER DEFAULT 1," +
					COL_MESSAGE_PAYLOAD + " TEXT);";

	private static final String CREATE_TABLE_LOAD =
			"CREATE TABLE IF NOT EXISTS " + TABLE_NAME_LOAD + " (" +

					COL_LOAD_TIMESTAMP + " LONG NOT NULL);";
	private final String fullDBName;



	public ChatMessagesDataBase(Context context, Identity activeIdentity) {

		super(context, DATABASE_NAME + activeIdentity.getEcPublicKey().getReadableKeyIdentifier(), null, DATABASE_VERSION);
		fullDBName = DATABASE_NAME + activeIdentity.getEcPublicKey().getReadableKeyIdentifier();
		Log.d(TAG, "fulldbname: " + fullDBName);

	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase) {
		Log.v(TAG, CREATE_TABLE);
		sqLiteDatabase.execSQL(CREATE_TABLE);
		sqLiteDatabase.execSQL(CREATE_TABLE_LOAD);
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int from, int to) {
		sqLiteDatabase.execSQL("DROP TABLE " + TABLE_MESSAGE_NAME + ";");
		sqLiteDatabase.execSQL("DROP TABLE " + TABLE_NAME_LOAD + ";");
		onCreate(sqLiteDatabase);
	}


	public void put(ChatMessageItem item) {
		Log.i(TAG, "Put into db: " + item.toString() + " " + item.getSenderKey() + " " + item.getReceiverKey());
		ContentValues values = new ContentValues();
		values.put(COL_MESSAGE_SENDER, item.getSenderKey());
		values.put(COL_MESSAGE_RECEIVER, item.getReceiverKey());
		values.put(COL_MESSAGE_TIMESTAMP, item.time_stamp);
		values.put(COL_MESSAGE_PAYLOAD_TYPE, item.drop_payload_type);
		values.put(COL_MESSAGE_PAYLOAD, item.drop_payload == null ? "" : item.drop_payload);
		//values.put(COL_MESSAGE_ISNEW, item.isNew ? 1 : 0);

		long existingID=getID(item.getSenderKey(),item.getReceiverKey(), item.getTime() + "", item.drop_payload);
		if (existingID <= -1) {
			long id = getWritableDatabase().insert(TABLE_MESSAGE_NAME, null, values);
			if (id == -1) {
				Log.e(TAG, "Failed put into db: " + item.toString());
			} else {
				Log.v(TAG, "db entry putted " + item.drop_payload);
			}
		} else {
			int affectedRows=getWritableDatabase().update(TABLE_MESSAGE_NAME,  values, COL_MESSAGE_ID+" = ?",new String[]{String.valueOf(existingID)});
			Log.w(TAG, "updated " + affectedRows + " rows");
		}
	}


	private int getID(String sender, String receiver,String timestamp, String payload) {
		if (sender == null) {
			Log.e(TAG, "sender can't be null");
			return -1;
		}
		if (receiver == null) {
			Log.e(TAG, "sender can't be null");
			return -1;
		}
		Cursor c = null;
		try {
			c = getReadableDatabase().query(TABLE_MESSAGE_NAME,
					new String[]{COL_MESSAGE_ID},
					COL_MESSAGE_SENDER + "=? and " + COL_MESSAGE_RECEIVER + "=? and " + COL_MESSAGE_TIMESTAMP + "=? and " + COL_MESSAGE_PAYLOAD + "=?",
					new String[]{sender, receiver, timestamp, payload}, null, null, null, null);
			if (c.moveToFirst()) //if the row exist then return the id
			{
				int id = c.getInt(c.getColumnIndex(COL_MESSAGE_ID));
				Log.d(TAG, "id: " + id);
				return id;
			} else {
				Log.d(TAG, "new item");
				return -1;
			}
		} finally {
			if (c!=null) {
				c.close();
			}
		}
	}

	public ChatMessageItem[] get(String key) {

		SQLiteDatabase database = getReadableDatabase();

		Cursor cursor = null;
		try {
			cursor = database.query(TABLE_MESSAGE_NAME, getAllColumnsList(),
					//selection
					COL_MESSAGE_SENDER + "=? OR " + COL_MESSAGE_RECEIVER + "=?",
					//selection args
					new String[]{key, key},
					null, null, null);

			return createResultList(cursor);
		} finally {
			if (cursor!=null) {
				cursor.close();
			}
		}
	}

	@NonNull
	private String[] getAllColumnsList() {

		return new String[]
				{
						//colums
						COL_MESSAGE_ID,
						COL_MESSAGE_ISNEW,
						COL_LOAD_TIMESTAMP,
						COL_MESSAGE_SENDER,
						COL_MESSAGE_RECEIVER,
						COL_MESSAGE_ACKNOWLEDGE_ID,
						COL_MESSAGE_PAYLOAD_TYPE,
						COL_MESSAGE_PAYLOAD};
	}

	public ChatMessageItem[] getAll() {

		SQLiteDatabase database = getReadableDatabase();

		Cursor cursor = database.query(TABLE_MESSAGE_NAME, getAllColumnsList(),
				//selection
				null, null,
				null, null, null);

		return createResultList(cursor);
	}

	@Nullable
	private ChatMessageItem[] createResultList(Cursor cursor) {

		cursor.moveToFirst();
			Log.d(TAG, "messages in db " + cursor.getCount());
			int count = cursor.getCount();
			Log.v(TAG, "database result count");
			ChatMessageItem[] items = new ChatMessageItem[count];
			int i = 0;

			while (!cursor.isAfterLast()) {
				items[i] = new ChatMessageItem(
						cursor.getInt(0),
						cursor.getShort(1)>0,
						cursor.getLong(2),
						cursor.getString(3),
						cursor.getString(4),
						cursor.getString(5),
						cursor.getString(6),
						cursor.getString(7));


				i++;
				cursor.moveToNext();
			}
			cursor.close();
			return items;

	}

	public int getNewMessageCount(Contact c) {
		SQLiteDatabase database = getReadableDatabase();
		return (int) DatabaseUtils.queryNumEntries(database, TABLE_MESSAGE_NAME,
				COL_MESSAGE_SENDER + "=? AND " + COL_MESSAGE_ISNEW + "=?", new String[]{c.getEcPublicKey().getReadableKeyIdentifier(), "1"});

	}

	public int setAllMessagesRead(Contact c) {
		SQLiteDatabase database = getReadableDatabase();

		ContentValues cv = new ContentValues();
		cv.put(COL_MESSAGE_ISNEW, 0);
		return database.update(TABLE_MESSAGE_NAME, cv, COL_MESSAGE_SENDER + "='" + c.getEcPublicKey().getReadableKeyIdentifier() + "'", null);
	}

	public long getLastRetrievedDropMessageTime() {
		SQLiteDatabase database = getReadableDatabase();
		Cursor cursor = database.query(TABLE_NAME_LOAD, new String[]{COL_LOAD_TIMESTAMP}, null, null, null, null, null);
		try {
			if (cursor.moveToFirst()) {
				return cursor.getLong(cursor.getColumnIndex(COL_LOAD_TIMESTAMP));
			} else {
				return 0;
			}
		} finally {
			if (cursor!=null) {
				cursor.close();
			}
		}

	}

	public void setLastRetrivedDropMessagesTime(long time) {
		SQLiteDatabase database = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(COL_LOAD_TIMESTAMP, time);
		database.beginTransaction();
		int deletedRows=database.delete(TABLE_NAME_LOAD,"1",null);
		long newId=database.insert(TABLE_NAME_LOAD, null, values);
		database.setTransactionSuccessful();
		database.endTransaction();
	}

	public String getDBName() {
		return fullDBName;
	}
}
