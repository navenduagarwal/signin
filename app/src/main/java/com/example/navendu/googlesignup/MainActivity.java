package com.example.navendu.googlesignup;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.plus.Plus;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int STATE_SIGNED_IN = 0;
    private static final int STATE_SIGN_IN = 1;
    private static final int STATE_IN_PROGRESS = 2;
    private static final int RC_SIGN_IN = 0;
    private static final int DIALOG_PLAY_SERVICES_ERROR = 0;
    private GoogleApiClient mGoogleApiClient;
    private TextView mStatusOutput;
    private SignInButton mSignInButton;
    private Button mSignOutButton;
    private Button mRevokeButton;
    private int mSignInProgress;
    private int mSignInError;
    private PendingIntent mSignInIntent;
    private int MY_PERMISSIONS_REQUEST_GET_ACCOUNTS;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStatusOutput = (TextView) findViewById(R.id.sign_in_status);
        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
        mSignOutButton = (Button) findViewById(R.id.sign_out_button);
        mRevokeButton = (Button) findViewById(R.id.revoke_access_button);

        mSignInButton.setOnClickListener(this);
        mSignOutButton.setOnClickListener(this);
        mRevokeButton.setOnClickListener(this);
        buildGoogleApiClient();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Plus.API, Plus.PlusOptions.builder().build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addScope(new Scope("email"))
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //Reaching onConnected means we consider the user signed in
        Log.i(LOG_TAG, "GoogleApiClient is connected");

        //Update the user interface to reflect that user is signed in
        mSignInButton.setEnabled(false);
        mSignOutButton.setEnabled(true);
        mRevokeButton.setEnabled(true);

        //Indicate that the sign in process is complete
        mSignInProgress = STATE_SIGNED_IN;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.GET_ACCOUNTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.GET_ACCOUNTS},
                    MY_PERMISSIONS_REQUEST_GET_ACCOUNTS);
        }
        //We are signed in !
        //Retrieve some profile information to personalize our app for the user
        try {
            String emailAddress = Plus.AccountApi.getAccountName(mGoogleApiClient);
            mStatusOutput.setText(String.format("Signed In to G+ as %s", emailAddress));
        } catch (Exception ex) {
            String exception = ex.getLocalizedMessage();
            String exceptionString = exception.toString();
            Log.i(LOG_TAG, exceptionString);
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(LOG_TAG, "GoogleApiClient connection has been suspended: " + cause);
        mGoogleApiClient.connect();

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(LOG_TAG, "GoogleApiClient connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
        if (mSignInProgress != STATE_IN_PROGRESS) {
            //We do not have an intent in progress so we should store the
            //error resolution intent for use when the sign in button is clicked
            mSignInIntent = connectionResult.getResolution();
            mSignInError = connectionResult.getErrorCode();
            if (mSignInProgress == STATE_SIGN_IN) {
                //STATE_SIGN_IN indicates the user already clicked the sign in
                //so we should continue processing errors until the user is
                //or they click cancel
                resolveSignInError();
            }
        }
        //In this sample we consider the user signed out whenever they do not
        //a connection to Google Play Services
        onSignedOut();
    }

    @Override
    public void onClick(View view) {
        if (!mGoogleApiClient.isConnecting()) {
            //We only process button clicks when GoogleApiClient is not transacting
            //between connected and not connected
            switch (view.getId()) {
                case R.id.sign_in_button:
                    mStatusOutput.setText("Signing In");
                    resolveSignInError();
                    break;
                case R.id.sign_out_button:
                    //We clear the default account on sign out so that Google
                    //services will not return an onConnected callback without any
                    //interaction
                    Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
                    mGoogleApiClient.disconnect();
                    mGoogleApiClient.connect();
                    mStatusOutput.setText("Signed Out. Please Sign In");
                    break;
                case R.id.revoke_access_button:
                    //After revoke permissions for user with a GoogleApiClient
                    //instance, we must discard it and create a new one
                    Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
                    Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient);
                    buildGoogleApiClient();
                    mGoogleApiClient.connect();
                    mStatusOutput.setText("Permission Revoked. Please Sign In");
                    break;
            }
        }
    }

    private void resolveSignInError() {
        if (mSignInIntent != null) {
            //We have an intent which will allow our user to sign in or
            // resolve an error. For example if the user needs to
            //select an account to sign in with, or if they need to consent
            //to the permissions your app is requesting
            try {
                //Send the pending intent that we stored on the most recent
                //onConnectionFailed callback. This will allow the user to
                //resolve the error currently preventing our connection to
                //Google Play Services
                mSignInProgress = STATE_IN_PROGRESS;
                startIntentSenderForResult(mSignInIntent.getIntentSender(),
                        RC_SIGN_IN, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.i(LOG_TAG, "Sign in intent could not be sent: " + e.getLocalizedMessage());
                //The intent was cancelled before it could be sent.
                //get an updated ConnectionResult
                mSignInProgress = STATE_SIGN_IN;
                mGoogleApiClient.connect();
            }

        } else {
            showDialog(DIALOG_PLAY_SERVICES_ERROR);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RC_SIGN_IN:
                if (resultCode == RESULT_OK) {
                    mSignInProgress = STATE_SIGN_IN;
                } else {
                    mSignInProgress = STATE_SIGNED_IN;
                }
                if (!mGoogleApiClient.isConnecting()) {
                    mGoogleApiClient.connect();
                }
                break;
        }
    }

    private void onSignedOut() {
        mSignInButton.setEnabled(true);
        mSignOutButton.setEnabled(false);
        mRevokeButton.setEnabled(false);
    }
}

