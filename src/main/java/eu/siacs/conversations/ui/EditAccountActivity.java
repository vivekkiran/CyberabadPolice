package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Hashtable;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.Validator;
import eu.siacs.conversations.xmpp.XmppConnection.Features;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class EditAccountActivity extends XmppActivity {

	private AutoCompleteTextView mAccountJid;
	private EditText mPassword;
	private EditText mPasswordConfirm;
	private CheckBox mRegisterNew;
	private Button mCancelButton;
	private Button mSaveButton;

	private LinearLayout mStats;
	private TextView mServerInfoSm;
	private TextView mServerInfoCarbons;
	private TextView mServerInfoPep;
	private TextView mSessionEst;
	private TextView mOtrFingerprint;
	private ImageView mAvatar;
	private RelativeLayout mOtrFingerprintBox;
	private ImageButton mOtrFingerprintToClipboardButton;

	private String jidToEdit;
	private Account mAccount;

	private boolean mFetchingAvatar = false;

	private OnClickListener mSaveButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (mAccount != null
					&& mAccount.getStatus() == Account.STATUS_DISABLED) {
				mAccount.setOption(Account.OPTION_DISABLED, false);
				xmppConnectionService.updateAccount(mAccount);
				return;
			}
			if (!Validator.isValidJid(mAccountJid.getText().toString())) {
				mAccountJid.setError(getString(R.string.invalid_jid));
				mAccountJid.requestFocus();
				return;
			}
			boolean registerNewAccount = mRegisterNew.isChecked();
			String[] jidParts = mAccountJid.getText().toString().split("@");
			String username = jidParts[0];
			String server;
			if (jidParts.length >= 2) {
				server = jidParts[1];
			} else {
				server = "";
			}
			String password = mPassword.getText().toString();
			String passwordConfirm = mPasswordConfirm.getText().toString();
			if (registerNewAccount) {
				if (!password.equals(passwordConfirm)) {
					mPasswordConfirm
							.setError(getString(R.string.passwords_do_not_match));
					mPasswordConfirm.requestFocus();
					return;
				}
			}
			if (mAccount != null) {
				mAccount.setPassword(password);
				mAccount.setUsername(username);
				mAccount.setServer(server);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				xmppConnectionService.updateAccount(mAccount);
			} else {
				if (xmppConnectionService.findAccountByJid(mAccountJid
						.getText().toString()) != null) {
					mAccountJid
							.setError(getString(R.string.account_already_exists));
					mAccountJid.requestFocus();
					return;
				}
				mAccount = new Account(username, server, password);
				mAccount.setOption(Account.OPTION_USETLS, true);
				mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				xmppConnectionService.createAccount(mAccount);
			}
			if (jidToEdit != null) {
				finish();
			} else {
				updateSaveButton();
				updateAccountInformation();
			}

		}
	};
	private OnClickListener mCancelButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			finish();
		}
	};
	private OnAccountUpdate mOnAccountUpdateListener = new OnAccountUpdate() {

		@Override
		public void onAccountUpdate() {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (mAccount != null
							&& mAccount.getStatus() != Account.STATUS_ONLINE
							&& mFetchingAvatar) {
						startActivity(new Intent(getApplicationContext(),
								ManageAccountActivity.class));
						finish();
					} else if (jidToEdit == null && mAccount != null
							&& mAccount.getStatus() == Account.STATUS_ONLINE) {
						if (!mFetchingAvatar) {
							mFetchingAvatar = true;
							xmppConnectionService.checkForAvatar(mAccount,
									mAvatarFetchCallback);
						}
					} else {
						updateSaveButton();
					}
					if (mAccount != null) {
						updateAccountInformation();
					}
				}
			});
		}
	};
	private UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {

		@Override
		public void userInputRequried(PendingIntent pi, Avatar avatar) {
			finishInitialSetup(avatar);
		}

		@Override
		public void success(Avatar avatar) {
			finishInitialSetup(avatar);
		}

		@Override
		public void error(int errorCode, Avatar avatar) {
			finishInitialSetup(avatar);
		}
	};
	private KnownHostsAdapter mKnownHostsAdapter;
	private TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
								  int count) {
			updateSaveButton();
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
									  int after) {

		}

		@Override
		public void afterTextChanged(Editable s) {

		}
	};
	private OnClickListener mAvatarClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			if (mAccount!=null) {
				Intent intent = new Intent(getApplicationContext(),
						PublishProfilePictureActivity.class);
				intent.putExtra("account", mAccount.getJid());
				startActivity(intent);
			}
		}
	};

	protected void finishInitialSetup(final Avatar avatar) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				Intent intent;
				if (avatar != null) {
					intent = new Intent(getApplicationContext(),
							StartConversationActivity.class);
				} else {
					intent = new Intent(getApplicationContext(),
							PublishProfilePictureActivity.class);
					intent.putExtra("account", mAccount.getJid());
					intent.putExtra("setup", true);
				}
				startActivity(intent);
				finish();
			}
		});
	}

	protected boolean inputDataDiffersFromAccount() {
		if (mAccount == null) {
			return true;
		} else {
			return (!mAccount.getJid().equals(mAccountJid.getText().toString()))
					|| (!mAccount.getPassword().equals(
					mPassword.getText().toString()) || mAccount
					.isOptionSet(Account.OPTION_REGISTER) != mRegisterNew
					.isChecked());
		}
	}

	protected void updateSaveButton() {
		if (mAccount != null
				&& mAccount.getStatus() == Account.STATUS_CONNECTING) {
			this.mSaveButton.setEnabled(false);
			this.mSaveButton.setTextColor(getSecondaryTextColor());
			this.mSaveButton.setText(R.string.account_status_connecting);
		} else if (mAccount != null
				&& mAccount.getStatus() == Account.STATUS_DISABLED) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			this.mSaveButton.setText(R.string.enable);
		} else {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			if (jidToEdit != null) {
				if (mAccount != null
						&& mAccount.getStatus() == Account.STATUS_ONLINE) {
					this.mSaveButton.setText(R.string.save);
					if (!accountInfoEdited()) {
						this.mSaveButton.setEnabled(false);
						this.mSaveButton.setTextColor(getSecondaryTextColor());
					}
				} else {
					this.mSaveButton.setText(R.string.connect);
				}
			} else {
				this.mSaveButton.setText(R.string.next);
			}
		}
	}

	protected boolean accountInfoEdited() {
		return (!this.mAccount.getJid().equals(
				this.mAccountJid.getText().toString()))
				|| (!this.mAccount.getPassword().equals(
				this.mPassword.getText().toString()));
	}

	@Override
	protected String getShareableUri() {
		if (mAccount!=null) {
			return "xmpp:"+mAccount.getJid();
		} else {
			return "";
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_account);
		this.mAccountJid = (AutoCompleteTextView) findViewById(R.id.account_jid);
		this.mAccountJid.addTextChangedListener(this.mTextWatcher);
		this.mPassword = (EditText) findViewById(R.id.account_password);
		this.mPassword.addTextChangedListener(this.mTextWatcher);
		this.mPasswordConfirm = (EditText) findViewById(R.id.account_password_confirm);
		this.mAvatar = (ImageView) findViewById(R.id.avater);
		this.mAvatar.setOnClickListener(this.mAvatarClickListener);
		this.mRegisterNew = (CheckBox) findViewById(R.id.account_register_new);
		this.mStats = (LinearLayout) findViewById(R.id.stats);
		this.mSessionEst = (TextView) findViewById(R.id.session_est);
		this.mServerInfoCarbons = (TextView) findViewById(R.id.server_info_carbons);
		this.mServerInfoSm = (TextView) findViewById(R.id.server_info_sm);
		this.mServerInfoPep = (TextView) findViewById(R.id.server_info_pep);
		this.mOtrFingerprint = (TextView) findViewById(R.id.otr_fingerprint);
		this.mOtrFingerprintBox = (RelativeLayout) findViewById(R.id.otr_fingerprint_box);
		this.mOtrFingerprintToClipboardButton = (ImageButton) findViewById(R.id.action_copy_to_clipboard);
		this.mSaveButton = (Button) findViewById(R.id.save_button);
		this.mCancelButton = (Button) findViewById(R.id.cancel_button);
		this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
		this.mCancelButton.setOnClickListener(this.mCancelButtonClickListener);
		this.mRegisterNew
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
												 boolean isChecked) {
						if (isChecked) {
							mPasswordConfirm.setVisibility(View.VISIBLE);
						} else {
							mPasswordConfirm.setVisibility(View.GONE);
						}
						updateSaveButton();
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.editaccount, menu);
		MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
		if (mAccount == null) {
			showQrCode.setVisible(false);
		}
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getIntent() != null) {
			this.jidToEdit = getIntent().getStringExtra("jid");
			if (this.jidToEdit != null) {
				this.mRegisterNew.setVisibility(View.GONE);
				getActionBar().setTitle(getString(R.string.account_details));
			} else {
				this.mAvatar.setVisibility(View.GONE);
				getActionBar().setTitle(R.string.action_add_account);
			}
		}
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnAccountListChangedListener();
		}
		super.onStop();
	}

	@Override
	protected void onBackendConnected() {
		this.mKnownHostsAdapter = new KnownHostsAdapter(this,
				android.R.layout.simple_list_item_1,
				xmppConnectionService.getKnownHosts());
		this.xmppConnectionService
				.setOnAccountListChangedListener(this.mOnAccountUpdateListener);
		if (this.jidToEdit != null) {
			this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
			updateAccountInformation();
		} else if (this.xmppConnectionService.getAccounts().size() == 0) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setDisplayShowHomeEnabled(false);
			this.mCancelButton.setEnabled(false);
			this.mCancelButton.setTextColor(getSecondaryTextColor());
		}
		this.mAccountJid.setAdapter(this.mKnownHostsAdapter);
		updateSaveButton();
	}

	private void updateAccountInformation() {
		this.mAccountJid.setText(this.mAccount.getJid());
		this.mPassword.setText(this.mAccount.getPassword());
		if (this.jidToEdit != null) {
			this.mAvatar.setVisibility(View.VISIBLE);
			this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(72)));
		}
		if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
			this.mRegisterNew.setVisibility(View.VISIBLE);
			this.mRegisterNew.setChecked(true);
			this.mPasswordConfirm.setText(this.mAccount.getPassword());
		} else {
			this.mRegisterNew.setVisibility(View.GONE);
			this.mRegisterNew.setChecked(false);
		}
		if (this.mAccount.getStatus() == Account.STATUS_ONLINE
				&& !this.mFetchingAvatar) {
			this.mStats.setVisibility(View.VISIBLE);
			this.mSessionEst.setText(UIHelper.readableTimeDifference(
					getApplicationContext(), this.mAccount.getXmppConnection()
							.getLastSessionEstablished()));
			Features features = this.mAccount.getXmppConnection().getFeatures();
			if (features.carbons()) {
				this.mServerInfoCarbons.setText(R.string.server_info_available);
			} else {
				this.mServerInfoCarbons
						.setText(R.string.server_info_unavailable);
			}
			if (features.sm()) {
				this.mServerInfoSm.setText(R.string.server_info_available);
			} else {
				this.mServerInfoSm.setText(R.string.server_info_unavailable);
			}
			if (features.pubsub()) {
				this.mServerInfoPep.setText(R.string.server_info_available);
			} else {
				this.mServerInfoPep.setText(R.string.server_info_unavailable);
			}
			final String fingerprint = this.mAccount
					.getOtrFingerprint(xmppConnectionService);
			if (fingerprint != null) {
				this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
				this.mOtrFingerprint.setText(fingerprint);
				this.mOtrFingerprintToClipboardButton
						.setVisibility(View.VISIBLE);
				this.mOtrFingerprintToClipboardButton
						.setOnClickListener(new View.OnClickListener() {

							@Override
							public void onClick(View v) {

								if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
									Toast.makeText(
											EditAccountActivity.this,
											R.string.toast_message_otr_fingerprint,
											Toast.LENGTH_SHORT).show();
								}
							}
						});
			} else {
				this.mOtrFingerprintBox.setVisibility(View.GONE);
			}
		} else {
			if (this.mAccount.errorStatus()) {
				this.mAccountJid.setError(getString(this.mAccount
						.getReadableStatusId()));
				this.mAccountJid.requestFocus();
			}
			this.mStats.setVisibility(View.GONE);
		}
	}
}
