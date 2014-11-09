package eu.siacs.conversations.entities;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.xml.Element;
import android.content.ContentValues;
import android.database.Cursor;

public class Contact implements ListItem {
	public static final String TABLENAME = "contacts";

	public static final String SYSTEMNAME = "systemname";
	public static final String SERVERNAME = "servername";
	public static final String JID = "jid";
	public static final String OPTIONS = "options";
	public static final String SYSTEMACCOUNT = "systemaccount";
	public static final String PHOTOURI = "photouri";
	public static final String KEYS = "pgpkey";
	public static final String ACCOUNT = "accountUuid";
	public static final String AVATAR = "avatar";
    public static final String LAST_PRESENCE = "last_presence";
    public static final String LAST_TIME = "last_time";

	protected String accountUuid;
	protected String systemName;
	protected String serverName;
	protected String presenceName;
	protected String jid;
	protected int subscription = 0;
	protected String systemAccount;
	protected String photoUri;
	protected String avatar;
	protected JSONObject keys = new JSONObject();
	protected Presences presences = new Presences();

	protected Account account;

	protected boolean inRoster = true;

	public Lastseen lastseen = new Lastseen();

	public Contact(final String account, final String systemName, final String serverName,
		final String jid, final int subscription, final String photoUri,
		final String systemAccount, final String keys, final String avatar,
		final Lastseen lastseen) {
		this(account, systemName, serverName, jid, subscription, photoUri, systemAccount, keys,
				avatar);
		this.lastseen = lastseen;
	}

	public Contact(final String account, final String systemName, final String serverName,
		final String jid, final int subscription, final String photoUri,
		final String systemAccount, final String keys, final String avatar) {
		this.accountUuid = account;
		this.systemName = systemName;
		this.serverName = serverName;
		this.jid = jid;
		this.subscription = subscription;
		this.photoUri = photoUri;
		this.systemAccount = systemAccount;
		try {
			this.keys = (keys == null ? new JSONObject("") : new JSONObject(keys));
		} catch (JSONException e) {
			this.keys = new JSONObject();
		}
		this.avatar = avatar;
	}

	public Contact(final String jid) {
		this.jid = jid;
	}

	public String getDisplayName() {
		if (this.systemName != null) {
			return this.systemName;
		} else if (this.serverName != null) {
			return this.serverName;
		} else if (this.presenceName != null) {
			return this.presenceName;
		} else {
			return this.jid.split("@")[0];
		}
	}

	public String getProfilePhoto() {
		return this.photoUri;
	}

	public String getJid() {
		return this.jid.toLowerCase(Locale.getDefault());
	}

	public boolean match(String needle) {
		return needle == null
				|| jid.contains(needle.toLowerCase())
				|| getDisplayName().toLowerCase()
						.contains(needle.toLowerCase());
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(ACCOUNT, accountUuid);
		values.put(SYSTEMNAME, systemName);
		values.put(SERVERNAME, serverName);
		values.put(JID, jid);
		values.put(OPTIONS, subscription);
		values.put(SYSTEMACCOUNT, systemAccount);
		values.put(PHOTOURI, photoUri);
		values.put(KEYS, keys.toString());
		values.put(AVATAR, avatar);
		values.put(LAST_PRESENCE, lastseen.presence);
		values.put(LAST_TIME, lastseen.time);
		return values;
	}

	public static Contact fromCursor(final Cursor cursor) {
		final Lastseen lastseen = new Lastseen(
				cursor.getString(cursor.getColumnIndex(LAST_PRESENCE)),
				cursor.getLong(cursor.getColumnIndex(LAST_TIME)));
		return new Contact(cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				cursor.getString(cursor.getColumnIndex(SYSTEMNAME)),
				cursor.getString(cursor.getColumnIndex(SERVERNAME)),
				cursor.getString(cursor.getColumnIndex(JID)),
				cursor.getInt(cursor.getColumnIndex(OPTIONS)),
				cursor.getString(cursor.getColumnIndex(PHOTOURI)),
				cursor.getString(cursor.getColumnIndex(SYSTEMACCOUNT)),
				cursor.getString(cursor.getColumnIndex(KEYS)),
				cursor.getString(cursor.getColumnIndex(AVATAR)),
				lastseen);
	}

	public int getSubscription() {
		return this.subscription;
	}

	public void setSystemAccount(String account) {
		this.systemAccount = account;
	}

	public void setAccount(Account account) {
		this.account = account;
		this.accountUuid = account.getUuid();
	}

	public Account getAccount() {
		return this.account;
	}

	public Presences getPresences() {
		return this.presences;
	}

	public void updatePresence(String resource, int status) {
		this.presences.updatePresence(resource, status);
	}

	public void removePresence(String resource) {
		this.presences.removePresence(resource);
	}

	public void clearPresences() {
		this.presences.clearPresences();
		this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
	}

	public int getMostAvailableStatus() {
		return this.presences.getMostAvailableStatus();
	}

	public void setPresences(Presences pres) {
		this.presences = pres;
	}

	public void setPhotoUri(String uri) {
		this.photoUri = uri;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public void setSystemName(String systemName) {
		this.systemName = systemName;
	}

	public void setPresenceName(String presenceName) {
		this.presenceName = presenceName;
	}

	public String getSystemAccount() {
		return systemAccount;
	}

	public Set<String> getOtrFingerprints() {
		Set<String> set = new HashSet<String>();
		try {
			if (this.keys.has("otr_fingerprints")) {
				JSONArray fingerprints = this.keys
						.getJSONArray("otr_fingerprints");
				for (int i = 0; i < fingerprints.length(); ++i) {
					set.add(fingerprints.getString(i));
				}
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return set;
	}

	public void addOtrFingerprint(String print) {
		try {
			JSONArray fingerprints;
			if (!this.keys.has("otr_fingerprints")) {
				fingerprints = new JSONArray();

			} else {
				fingerprints = this.keys.getJSONArray("otr_fingerprints");
			}
			fingerprints.put(print);
			this.keys.put("otr_fingerprints", fingerprints);
		} catch (JSONException e) {

		}
	}

	public void setPgpKeyId(long keyId) {
		try {
			this.keys.put("pgp_keyid", keyId);
		} catch (JSONException e) {

		}
	}

	public long getPgpKeyId() {
		if (this.keys.has("pgp_keyid")) {
			try {
				return this.keys.getLong("pgp_keyid");
			} catch (JSONException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}

	public void setOption(int option) {
		this.subscription |= 1 << option;
	}

	public void resetOption(int option) {
		this.subscription &= ~(1 << option);
	}

	public boolean getOption(int option) {
		return ((this.subscription & (1 << option)) != 0);
	}

	public boolean showInRoster() {
		return (this.getOption(Contact.Options.IN_ROSTER) && (!this
				.getOption(Contact.Options.DIRTY_DELETE)))
				|| (this.getOption(Contact.Options.DIRTY_PUSH));
	}

	public void parseSubscriptionFromElement(Element item) {
		String ask = item.getAttribute("ask");
		String subscription = item.getAttribute("subscription");

		if (subscription != null) {
			if (subscription.equals("to")) {
				this.resetOption(Contact.Options.FROM);
				this.setOption(Contact.Options.TO);
			} else if (subscription.equals("from")) {
				this.resetOption(Contact.Options.TO);
				this.setOption(Contact.Options.FROM);
				this.resetOption(Contact.Options.PREEMPTIVE_GRANT);
			} else if (subscription.equals("both")) {
				this.setOption(Contact.Options.TO);
				this.setOption(Contact.Options.FROM);
				this.resetOption(Contact.Options.PREEMPTIVE_GRANT);
			} else if (subscription.equals("none")) {
				this.resetOption(Contact.Options.FROM);
				this.resetOption(Contact.Options.TO);
			}
		}

		// do NOT override asking if pending push request
		if (!this.getOption(Contact.Options.DIRTY_PUSH)) {
			if ((ask != null) && (ask.equals("subscribe"))) {
				this.setOption(Contact.Options.ASKING);
			} else {
				this.resetOption(Contact.Options.ASKING);
			}
		}
	}

	public Element asElement() {
		Element item = new Element("item");
		item.setAttribute("jid", this.jid);
		if (this.serverName != null) {
			item.setAttribute("name", this.serverName);
		}
		return item;
	}

	public class Options {
		public static final int TO = 0;
		public static final int FROM = 1;
		public static final int ASKING = 2;
		public static final int PREEMPTIVE_GRANT = 3;
		public static final int IN_ROSTER = 4;
		public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
		public static final int DIRTY_PUSH = 6;
		public static final int DIRTY_DELETE = 7;
	}

	public static class Lastseen {
        public long time;
        public String presence;

        public Lastseen() {
            time = 0;
            presence = null;
        }
        public Lastseen(final String presence, final long time) {
            this.time = time;
            this.presence = presence;
        }
	}

	@Override
	public int compareTo(ListItem another) {
		return this.getDisplayName().compareToIgnoreCase(
				another.getDisplayName());
	}

	public String getServer() {
		String[] split = getJid().split("@");
		if (split.length >= 2) {
			return split[1];
		} else {
			return null;
		}
	}

	public boolean setAvatar(String filename) {
		if (this.avatar != null && this.avatar.equals(filename)) {
			return false;
		} else {
			this.avatar = filename;
			return true;
		}
	}

	public String getAvatar() {
		return this.avatar;
	}

	public boolean deleteOtrFingerprint(String fingerprint) {
		boolean success = false;
		try {
			if (this.keys.has("otr_fingerprints")) {
				JSONArray newPrints = new JSONArray();
				JSONArray oldPrints = this.keys
						.getJSONArray("otr_fingerprints");
				for (int i = 0; i < oldPrints.length(); ++i) {
					if (!oldPrints.getString(i).equals(fingerprint)) {
						newPrints.put(oldPrints.getString(i));
					} else {
						success = true;
					}
				}
				this.keys.put("otr_fingerprints", newPrints);
			}
			return success;
		} catch (JSONException e) {
			return false;
		}
	}

	public boolean trusted() {
		return getOption(Options.FROM) && getOption(Options.TO);
	}
}
