package com.breadwallet.presenter.fragments;/*
 * Copyright (C) 2015 The Android Open Source Project 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License 
 */

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.hardware.fingerprint.FingerprintManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.presenter.BreadWalletApp;
import com.breadwallet.presenter.activities.MainActivity;
import com.breadwallet.presenter.entities.PaymentRequestEntity;
import com.breadwallet.tools.BRConstants;
import com.breadwallet.tools.adapter.MiddleViewAdapter;
import com.breadwallet.tools.animation.FragmentAnimator;
import com.breadwallet.tools.security.FingerprintUiHelper;
import com.breadwallet.tools.security.KeyStoreManager;
import com.breadwallet.wallet.BRWalletManager;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
public class FingerprintDialogFragment extends DialogFragment
        implements FingerprintUiHelper.Callback {

    private FingerprintManager.CryptoObject mCryptoObject;
    private FingerprintUiHelper mFingerprintUiHelper;

    private PaymentRequestEntity request;

    private int mode = -1;

    FingerprintUiHelper.FingerprintUiHelperBuilder mFingerprintUiHelperBuilder;

    public FingerprintDialogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not create a new Fragment when the Activity is re-created such as orientation changes. 
        setRetainInstance(true);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fingerprint_dialog_container, container, false);
        getDialog().setTitle("Fingerprint authorization");
        // If fingerprint authentication is not available, switch immediately to the backup
        // (password) screen.
        FingerprintManager mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Activity.FINGERPRINT_SERVICE);
        mFingerprintUiHelperBuilder = new FingerprintUiHelper.FingerprintUiHelperBuilder(mFingerprintManager);
        mFingerprintUiHelper = mFingerprintUiHelperBuilder.build(
                (ImageView) v.findViewById(R.id.fingerprint_icon),
                (TextView) v.findViewById(R.id.fingerprint_status), this);
        View mFingerprintContent = v.findViewById(R.id.fingerprint_container);

        Button mCancelButton = (Button) v.findViewById(R.id.cancel_button);
        Button mSecondDialogButton = (Button) v.findViewById(R.id.second_dialog_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        mCancelButton.setText(R.string.cancel);
        mSecondDialogButton.setText(R.string.use_password);
        mFingerprintContent.setVisibility(View.VISIBLE);
        mSecondDialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToBackup();
            }
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mFingerprintUiHelper.startListening(mCryptoObject);
    }

    @Override
    public void onPause() {
        super.onPause();
        mFingerprintUiHelper.stopListening();
    }

    /**
     * Switches to backup (password) screen. This either can happen when fingerprint is not
     * available or the user chooses to use the password authentication method by pressing the
     * button. This can also happen when the user had too many fingerprint attempts.
     */
    private void goToBackup() {

        // Fingerprint is not used anymore. Stop listening for it.
        getDialog().cancel();
        PasswordDialogFragment passwordDialogFragment = new PasswordDialogFragment();
        passwordDialogFragment.setMode(mode);
        passwordDialogFragment.setPaymentRequestEntity(request);
        passwordDialogFragment.setVerifyOnlyTrue();
        FragmentManager fm = getActivity().getFragmentManager();
        passwordDialogFragment.show(fm, PasswordDialogFragment.class.getName());
        if (mFingerprintUiHelper != null)
            mFingerprintUiHelper.stopListening();
    }

    @Override
    public void onAuthenticated() {
        // Callback from FingerprintUiHelper. Let the activity know that authentication was 
        // successful.
        getDialog().cancel();

        ((BreadWalletApp) getActivity().getApplicationContext()).setUnlocked(true);
        FragmentSettingsAll.refreshUI(getActivity());
        MiddleViewAdapter.resetMiddleView(getActivity(), null);
        ((BreadWalletApp) getActivity().getApplicationContext()).allowKeyStoreAccessForSeconds();
        getDialog().dismiss();
        if (mode == BRConstants.AUTH_FOR_PHRASE) {
            FragmentAnimator.animateSlideToLeft((MainActivity) getActivity(), new FragmentRecoveryPhrase(), new FragmentSettings());
        } else if (mode == BRConstants.AUTH_FOR_PAY && request != null) {
            BRWalletManager walletManager = BRWalletManager.getInstance(getActivity());
            String seed = KeyStoreManager.getKeyStorePhrase(getActivity(), BRConstants.PAY_REQUEST_CODE);
            if (seed != null && !seed.isEmpty()) {
                boolean success;
                if (request.serializedTx != null) {
                    success = walletManager.publishSerializedTransaction(request.serializedTx, seed);
                } else {
                    success = walletManager.pay(request.addresses[0], (request.amount), seed);
                }
                if (!success) {
                    ((BreadWalletApp) getActivity().getApplication()).showCustomToast(getActivity(),
                            "Failed to send", MainActivity.screenParametersPoint.y / 2, Toast.LENGTH_LONG, 0);
                    return;
                }
            } else {
                return;
            }
            final MediaPlayer mp = MediaPlayer.create(getActivity(), R.raw.coinflip);
            mp.start();
            FragmentAnimator.hideScanResultFragment();
        }
        dismiss();
    }

    @Override
    public void onError() {
        goToBackup();
    }

    public void setPaymentRequestEntity(PaymentRequestEntity requestEntity) {
        request = requestEntity;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }
}