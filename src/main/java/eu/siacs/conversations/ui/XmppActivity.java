package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.utils.ExceptionHelper;

public abstract class XmppActivity extends Activity {

	protected static final int REQUEST_ANNOUNCE_PGP = 0x0101;
	protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x0102;

	public XmppConnectionService xmppConnectionService;
	public boolean xmppConnectionServiceBound = false;

	protected int mPrimaryTextColor;
	protected int mSecondaryTextColor;
	protected int mSecondaryBackgroundColor;
	protected int mColorRed;
	protected int mColorOrange;
	protected int mColorGreen;
	protected int mPrimaryColor;

	protected boolean mUseSubject = true;

	private DisplayMetrics metrics;

	protected interface OnValueEdited {
		public void onValueEdited(String value);
	}

	public interface OnPresenceSelected {
		public void onPresenceSelected();
	}

	protected ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			XmppConnectionBinder binder = (XmppConnectionBinder) service;
			xmppConnectionService = binder.getService();
			xmppConnectionServiceBound = true;
			onBackendConnected();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			xmppConnectionServiceBound = false;
		}
	};

	@Override
	protected void onStart() {
		super.onStart();
		if (!xmppConnectionServiceBound) {
			connectToBackend();
		}
	}

	public void connectToBackend() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction("ui");
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (xmppConnectionServiceBound) {
			unbindService(mConnection);
			xmppConnectionServiceBound = false;
		}
	}

	protected void hideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		View focus = getCurrentFocus();

		if (focus != null) {

			inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	public boolean hasPgp() {
		return xmppConnectionService.getPgpEngine() != null;
	}

	public void showInstallPgpDialog() {
		Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.openkeychain_required));
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(getText(R.string.openkeychain_required_long));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setNeutralButton(getString(R.string.restart),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (xmppConnectionServiceBound) {
							unbindService(mConnection);
							xmppConnectionServiceBound = false;
						}
						stopService(new Intent(XmppActivity.this,
								XmppConnectionService.class));
						finish();
					}
				});
		builder.setPositiveButton(getString(R.string.install),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Uri uri = Uri
								.parse("market://details?id=org.sufficientlysecure.keychain");
						Intent marketIntent = new Intent(Intent.ACTION_VIEW,
								uri);
						PackageManager manager = getApplicationContext()
								.getPackageManager();
						List<ResolveInfo> infos = manager
								.queryIntentActivities(marketIntent, 0);
						if (infos.size() > 0) {
							startActivity(marketIntent);
						} else {
							uri = Uri.parse("http://www.openkeychain.org/");
							Intent browserIntent = new Intent(
									Intent.ACTION_VIEW, uri);
							startActivity(browserIntent);
						}
						finish();
					}
				});
		builder.create().show();
	}

	abstract void onBackendConnected();

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.action_accounts:
				startActivity(new Intent(this, ManageAccountActivity.class));
				break;
			case android.R.id.home:
				finish();
				break;
			case R.id.action_show_qr_code:
				showQrCode();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		metrics = getResources().getDisplayMetrics();
		ExceptionHelper.init(getApplicationContext());
		mPrimaryTextColor = getResources().getColor(R.color.primarytext);
		mSecondaryTextColor = getResources().getColor(R.color.secondarytext);
		mColorRed = getResources().getColor(R.color.red);
		mColorOrange = getResources().getColor(R.color.orange);
		mColorGreen = getResources().getColor(R.color.green);
		mPrimaryColor = getResources().getColor(R.color.primary);
		mSecondaryBackgroundColor = getResources().getColor(
				R.color.secondarybackground);
		if (getPreferences().getBoolean("use_larger_font", false)) {
			setTheme(R.style.ConversationsTheme_LargerText);
		}
		mUseSubject = getPreferences().getBoolean("use_subject", true);
	}

	protected SharedPreferences getPreferences() {
		return PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
	}

	public boolean useSubjectToIdentifyConference() {
		return mUseSubject;
	}

	public void switchToConversation(Conversation conversation) {
		switchToConversation(conversation, null, false);
	}

	public void switchToConversation(Conversation conversation, String text,
									 boolean newTask) {
		Intent viewConversationIntent = new Intent(this,
				ConversationActivity.class);
		viewConversationIntent.setAction(Intent.ACTION_VIEW);
		viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
				conversation.getUuid());
		if (text != null) {
			viewConversationIntent.putExtra(ConversationActivity.TEXT, text);
		}
		viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);
		if (newTask) {
			viewConversationIntent.setFlags(viewConversationIntent.getFlags()
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		} else {
			viewConversationIntent.setFlags(viewConversationIntent.getFlags()
					| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}
		startActivity(viewConversationIntent);
		finish();
	}

	public void switchToContactDetails(Contact contact) {
		Intent intent = new Intent(this, ContactDetailsActivity.class);
		intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
		intent.putExtra("account", contact.getAccount().getJid());
		intent.putExtra("contact", contact.getJid());
		startActivity(intent);
	}

	public void switchToAccount(Account account) {
		Intent intent = new Intent(this, EditAccountActivity.class);
		intent.putExtra("jid", account.getJid());
		startActivity(intent);
	}

	protected void inviteToConversation(Conversation conversation) {
		Intent intent = new Intent(getApplicationContext(),
				ChooseContactActivity.class);
		intent.putExtra("conversation", conversation.getUuid());
		startActivityForResult(intent, REQUEST_INVITE_TO_CONVERSATION);
	}

	protected void announcePgp(Account account, final Conversation conversation) {
		xmppConnectionService.getPgpEngine().generateSignature(account,
				"online", new UiCallback<Account>() {

					@Override
					public void userInputRequried(PendingIntent pi,
												  Account account) {
						try {
							startIntentSenderForResult(pi.getIntentSender(),
									REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
						} catch (SendIntentException e) {
						}
					}

					@Override
					public void success(Account account) {
						xmppConnectionService.databaseBackend
								.updateAccount(account);
						xmppConnectionService.sendPresencePacket(account,
								xmppConnectionService.getPresenceGenerator()
										.sendPresence(account));
						if (conversation != null) {
							conversation
									.setNextEncryption(Message.ENCRYPTION_PGP);
							xmppConnectionService.databaseBackend
									.updateConversation(conversation);
						}
					}

					@Override
					public void error(int error, Account account) {
						displayErrorDialog(error);
					}
				});
	}

	protected void displayErrorDialog(final int errorCode) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						XmppActivity.this);
				builder.setIconAttribute(android.R.attr.alertDialogIcon);
				builder.setTitle(getString(R.string.error));
				builder.setMessage(errorCode);
				builder.setNeutralButton(R.string.accept, null);
				builder.create().show();
			}
		});

	}

	protected void showAddToRosterDialog(final Conversation conversation) {
		String jid = conversation.getContactJid();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(jid);
		builder.setMessage(getString(R.string.not_in_roster));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.add_contact),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						String jid = conversation.getContactJid();
						Account account = conversation.getAccount();
						Contact contact = account.getRoster().getContact(jid);
						xmppConnectionService.createContact(contact);
						switchToContactDetails(contact);
					}
				});
		builder.create().show();
	}

	private void showAskForPresenceDialog(final Contact contact) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(contact.getJid());
		builder.setMessage(R.string.request_presence_updates);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.request_now,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (xmppConnectionServiceBound) {
							xmppConnectionService.sendPresencePacket(contact
									.getAccount(), xmppConnectionService
									.getPresenceGenerator()
									.requestPresenceUpdatesFrom(contact));
						}
					}
				});
		builder.create().show();
	}

	private void warnMutalPresenceSubscription(final Conversation conversation,
											   final OnPresenceSelected listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(conversation.getContact().getJid());
		builder.setMessage(R.string.without_mutual_presence_updates);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.ignore, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				conversation.setNextPresence(null);
				if (listener != null) {
					listener.onPresenceSelected();
				}
			}
		});
		builder.create().show();
	}

	protected void quickEdit(String previousValue, OnValueEdited callback) {
		quickEdit(previousValue, callback, false);
	}

	protected void quickPasswordEdit(String previousValue,
									 OnValueEdited callback) {
		quickEdit(previousValue, callback, true);
	}

	@SuppressLint("InflateParams")
	private void quickEdit(final String previousValue,
						   final OnValueEdited callback, boolean password) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = getLayoutInflater().inflate(R.layout.quickedit, null);
		final EditText editor = (EditText) view.findViewById(R.id.editor);
		OnClickListener mClickListener = new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				String value = editor.getText().toString();
				if (!previousValue.equals(value) && value.trim().length() > 0) {
					callback.onValueEdited(value);
				}
			}
		};
		if (password) {
			editor.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
			editor.setHint(R.string.password);
			builder.setPositiveButton(R.string.accept, mClickListener);
		} else {
			builder.setPositiveButton(R.string.edit, mClickListener);
		}
		editor.requestFocus();
		editor.setText(previousValue);
		builder.setView(view);
		builder.setNegativeButton(R.string.cancel, null);
		builder.create().show();
	}

	public void selectPresence(final Conversation conversation,
							   final OnPresenceSelected listener) {
		Contact contact = conversation.getContact();
		if (!contact.showInRoster()) {
			showAddToRosterDialog(conversation);
		} else {
			Presences presences = contact.getPresences();
			if (presences.size() == 0) {
				if (!contact.getOption(Contact.Options.TO)
						&& !contact.getOption(Contact.Options.ASKING)
						&& contact.getAccount().getStatus() == Account.STATUS_ONLINE) {
					showAskForPresenceDialog(contact);
				} else if (!contact.getOption(Contact.Options.TO)
						|| !contact.getOption(Contact.Options.FROM)) {
					warnMutalPresenceSubscription(conversation, listener);
				} else {
					conversation.setNextPresence(null);
					listener.onPresenceSelected();
				}
			} else if (presences.size() == 1) {
				String presence = presences.asStringArray()[0];
				conversation.setNextPresence(presence);
				listener.onPresenceSelected();
			} else {
				final StringBuilder presence = new StringBuilder();
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.choose_presence));
				final String[] presencesArray = presences.asStringArray();
				int preselectedPresence = 0;
				for (int i = 0; i < presencesArray.length; ++i) {
					if (presencesArray[i].equals(contact.lastseen.presence)) {
						preselectedPresence = i;
						break;
					}
				}
				presence.append(presencesArray[preselectedPresence]);
				builder.setSingleChoiceItems(presencesArray,
						preselectedPresence,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								presence.delete(0, presence.length());
								presence.append(presencesArray[which]);
							}
						});
				builder.setNegativeButton(R.string.cancel, null);
				builder.setPositiveButton(R.string.ok, new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						conversation.setNextPresence(presence.toString());
						listener.onPresenceSelected();
					}
				});
				builder.create().show();
			}
		}
	}

	protected void onActivityResult(int requestCode, int resultCode,
									final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_INVITE_TO_CONVERSATION
				&& resultCode == RESULT_OK) {
			String contactJid = data.getStringExtra("contact");
			String conversationUuid = data.getStringExtra("conversation");
			Conversation conversation = xmppConnectionService
					.findConversationByUuid(conversationUuid);
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				xmppConnectionService.invite(conversation, contactJid);
			}
			Log.d(Config.LOGTAG, "inviting " + contactJid + " to "
					+ conversation.getName());
		}
	}

	public int getSecondaryTextColor() {
		return this.mSecondaryTextColor;
	}

	public int getPrimaryTextColor() {
		return this.mPrimaryTextColor;
	}

	public int getWarningTextColor() {
		return this.mColorRed;
	}

	public int getPrimaryColor() {
		return this.mPrimaryColor;
	}

	public int getSecondaryBackgroundColor() {
		return this.mSecondaryBackgroundColor;
	}

	public int getPixel(int dp) {
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		return ((int) (dp * metrics.density));
	}

	public boolean copyTextToClipboard(String text, int labelResId) {
		ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		String label = getResources().getString(labelResId);
		if (mClipBoardManager != null) {
			ClipData mClipData = ClipData.newPlainText(label, text);
			mClipBoardManager.setPrimaryClip(mClipData);
			return true;
		}
		return false;
	}

	protected void registerNdefPushMessageCallback() {
			NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
			if (nfcAdapter != null && nfcAdapter.isEnabled()) {
				nfcAdapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
					@Override
					public NdefMessage createNdefMessage(NfcEvent nfcEvent) {
						NdefMessage msg = new NdefMessage(new NdefRecord[]{
								NdefRecord.createUri(getShareableUri()),
								NdefRecord.createApplicationRecord("eu.siacs.conversations")
						});
						return msg;
					}
				}, this);
			}
	}

	protected void unregisterNdefPushMessageCallback() {
		NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter != null && nfcAdapter.isEnabled()) {
			nfcAdapter.setNdefPushMessageCallback(null,this);
		}
	}

	protected String getShareableUri() {
		return null;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (this.getShareableUri()!=null) {
			this.registerNdefPushMessageCallback();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		this.unregisterNdefPushMessageCallback();
	}

	protected void showQrCode() {
		String uri = getShareableUri();
		if (uri!=null) {
			Point size = new Point();
			getWindowManager().getDefaultDisplay().getSize(size);
			final int width = (size.x < size.y ? size.x : size.y);
			Bitmap bitmap = createQrCodeBitmap(uri, width);
			ImageView view = new ImageView(this);
			view.setImageBitmap(bitmap);
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setView(view);
			builder.create().show();
		}
	}

	protected Bitmap createQrCodeBitmap(String input, int size) {
		try {
			final QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();
			final Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
			final BitMatrix result = QR_CODE_WRITER.encode(input, BarcodeFormat.QR_CODE, size, size, hints);
			final int width = result.getWidth();
			final int height = result.getHeight();
			final int[] pixels = new int[width * height];
			for (int y = 0; y < height; y++) {
				final int offset = y * width;
				for (int x = 0; x < width; x++) {
					pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
				}
			}
			final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
			return bitmap;
		} catch (final WriterException e) {
			return null;
		}
	}

	public AvatarService avatarService() {
		return xmppConnectionService.getAvatarService();
	}

	class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private Message message = null;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		@Override
		protected Bitmap doInBackground(Message... params) {
			message = params[0];
			try {
				return xmppConnectionService.getFileBackend().getThumbnail(
						message, (int) (metrics.density * 288), false);
			} catch (FileNotFoundException e) {
				return null;
			}
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (imageViewReference != null && bitmap != null) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}

	public void loadBitmap(Message message, ImageView imageView) {
		Bitmap bm;
		try {
			bm = xmppConnectionService.getFileBackend().getThumbnail(message,
					(int) (metrics.density * 288), true);
		} catch (FileNotFoundException e) {
			bm = null;
		}
		if (bm != null) {
			imageView.setImageBitmap(bm);
			imageView.setBackgroundColor(0x00000000);
		} else {
			if (cancelPotentialWork(message, imageView)) {
				imageView.setBackgroundColor(0xff333333);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(
						getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(message);
				} catch (RejectedExecutionException e) {
					return;
				}
			}
		}
	}

	public static boolean cancelPotentialWork(Message message,
											  ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Message oldMessage = bitmapWorkerTask.message;
			if (oldMessage == null || message != oldMessage) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap,
							 BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(
					bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}
}
