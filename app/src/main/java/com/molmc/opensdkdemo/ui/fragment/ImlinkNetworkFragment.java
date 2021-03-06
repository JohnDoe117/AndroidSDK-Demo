package com.molmc.opensdkdemo.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.molmc.opensdk.bean.DeviceBeanReq;
import com.molmc.opensdk.http.TaskException;
import com.molmc.opensdk.imlink.ImlinkConfig;
import com.molmc.opensdk.imlink.ImlinkListener;
import com.molmc.opensdk.imlink.WifiUtils;
import com.molmc.opensdk.utils.Logger;
import com.molmc.opensdkdemo.R;
import com.molmc.opensdkdemo.bean.FragmentArgs;
import com.molmc.opensdkdemo.bean.QrDeviceBean;
import com.molmc.opensdkdemo.ui.activity.BaseActivity;
import com.molmc.opensdkdemo.ui.activity.FragmentCommonActivity;
import com.skyfishjy.library.RippleBackground;

import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * features: 配置网络
 * Author：  hhe on 16-8-1 18:21
 * Email：   hhe@molmc.com
 */

public class ImlinkNetworkFragment extends BaseFragment implements ImlinkListener {

	public static ImlinkNetworkFragment newInstance() {
		ImlinkNetworkFragment fragment = new ImlinkNetworkFragment();
		return fragment;
	}

	public static void launch(Activity from, QrDeviceBean qrBean) {
		FragmentArgs args = new FragmentArgs();
		args.add("qrBean", qrBean);
		FragmentCommonActivity.launch(from, ImlinkNetworkFragment.class, args);
	}

	@Bind(R.id.configWifiName)
	TextView configWifiName;
	@Bind(R.id.deviceName)
	EditText deviceName;
	@Bind(R.id.configWifiPassword)
	EditText configWifiPassword;
	@Bind(R.id.startConfig)
	TextView startConfig;
	@Bind(R.id.rippleButton)
	RippleBackground rippleButton;


	//wifi
	private WifiUtils mWifiUtils;
	//手机当前连接的wifi ssid
	private String currentConnectWifi;

	//正在配置标志位
	private boolean isStarting = false;

	private String wifiSSid;
	private String wifiPassword;
	private ImlinkConfig imlinkConfig;
	private DeviceBeanReq deviceReq;
	private QrDeviceBean qrBean;


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_imlink, container, false);
		if (getArguments()!=null){
			qrBean = (QrDeviceBean) getArguments().get("qrBean");
		}
		initView();
		ButterKnife.bind(this, view);
		mWifiUtils = new WifiUtils(getActivity());
		configWifiPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					startConfig.performClick();
				}
				return false;
			}
		});
		return view;
	}

	private void initView(){
		BaseActivity baseActivity = (BaseActivity) getActivity();
		baseActivity.getSupportActionBar().setTitle(R.string.device_wifi_config);
		deviceReq = new DeviceBeanReq();
	}

	@Override
	public void onResume() {
		super.onResume();
		mWifiUtils.getConnectedInfo();
		currentConnectWifi = mWifiUtils.getConnectedSSID().replaceAll("\"", "");
		configWifiName.setText(currentConnectWifi);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		ButterKnife.unbind(this);
	}

	/**
	 * 开始配置设备
	 *
	 */
	private void startConfig() {
		if (!isStarting) {
			String devName = deviceName.getText().toString();
			if (TextUtils.isEmpty(devName)){
				showToast(R.string.err_devname_empty);
				return;
			}
			deviceReq.setName(devName);

			SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format));
			String date = sdf.format(new Date(System.currentTimeMillis()));
			deviceReq.setDescription(String.format(getString(R.string.device_description), devName, date));
			Logger.i(deviceReq.getDescription());

			wifiSSid = configWifiName.getText().toString();
			wifiPassword = configWifiPassword.getText().toString();
			if (TextUtils.isEmpty(wifiSSid)) {
				showToast(R.string.err_ssid_empty);
				return;
			}
			if (TextUtils.isEmpty(wifiPassword)) {
				wifiPassword = "";
			}
			isStarting = true;
			configWifiPassword.setEnabled(false);
			rippleButton.startRippleAnimation();
			startConfig.setText(R.string.config_config_running);
			imlinkConfig = new ImlinkConfig(getActivity());
			imlinkConfig.setImlinkListener(this);
			imlinkConfig.startConfig(wifiSSid, mWifiUtils.getConnectedBSSID(), wifiPassword, deviceReq);
		} else {
			isStarting = false;
			configWifiName.setEnabled(true);
			configWifiPassword.setEnabled(true);
			startConfig.setText(R.string.config_start);
			rippleButton.stopRippleAnimation();
			if (imlinkConfig != null) {
				imlinkConfig.interruptConfig();
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (imlinkConfig != null) {
			imlinkConfig.interruptConfig();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		isStarting = false;
		rippleButton.stopRippleAnimation();
		configWifiPassword.setEnabled(true);
		if (imlinkConfig != null) {
			imlinkConfig.interruptConfig();
		}
		startConfig.setText(R.string.config_start);
	}

	@OnClick(R.id.startConfig)
	public void onClick() {
		startConfig();
	}

	@Override
	public void onSuccess() {
		showToast(R.string.suc_device_config);
		rippleButton.stopRippleAnimation();
		configWifiPassword.setEnabled(true);
		isStarting = false;
		startConfig.setText(R.string.config_start);
		imlinkConfig.interruptConfig();
		getActivity().finish();
	}

	@Override
	public void onProgress(int progress, String msg) {

	}

	@Override
	public void onFailed(TaskException exception) {
		showToast(exception.getCode() +" : "+ exception.getMessage());
		rippleButton.stopRippleAnimation();
		configWifiPassword.setEnabled(true);
		isStarting = false;
		startConfig.setText(R.string.config_start);
		imlinkConfig.interruptConfig();
	}
}
