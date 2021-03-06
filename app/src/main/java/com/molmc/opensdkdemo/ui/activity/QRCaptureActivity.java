package com.molmc.opensdkdemo.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.widget.Toolbar;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.molmc.opensdk.bean.DeviceBean;
import com.molmc.opensdk.utils.Logger;
import com.molmc.opensdkdemo.R;
import com.molmc.opensdkdemo.bean.QrDeviceBean;
import com.molmc.opensdkdemo.support.zxing.camera.CameraManager;
import com.molmc.opensdkdemo.support.zxing.decoding.CaptureActivityHandler;
import com.molmc.opensdkdemo.support.zxing.decoding.InactivityTimer;
import com.molmc.opensdkdemo.support.zxing.view.ViewfinderView;
import com.molmc.opensdkdemo.ui.fragment.ImlinkNetworkFragment;

import java.io.IOException;
import java.util.Vector;
import java.util.regex.Pattern;

import butterknife.Bind;
import butterknife.ButterKnife;

public class QRCaptureActivity extends BaseActivity implements Callback {

	public static void launch(Activity from) {
		Intent intent = new Intent(from, QRCaptureActivity.class);
		from.startActivity(intent);
	}

	public static void launch(Activity from, DeviceBean device, Type type) {
		Intent intent = new Intent(from, QRCaptureActivity.class);
		intent.putExtra("type", type);
		intent.putExtra("device", device);
		from.startActivity(intent);
	}

	@Bind(R.id.preview_view)
	SurfaceView surfaceView;
	@Bind(R.id.viewfinder_view)
	ViewfinderView viewfinderView;
	@Bind(R.id.toolbar)
	Toolbar toolbar;
	@Bind(R.id.txtResult)
	TextView txtResult;

	public enum Type {
		dev_create,
		dev_bind,
		friend,
		group
	}

	private static final float BEEP_VOLUME = 0.10f;
	private static final long VIBRATE_DURATION = 200L;
	/**
	 * When the beep has finished playing, rewind to queue up another one.
	 */
	private final OnCompletionListener beepListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};
	private CaptureActivityHandler handler;
	private boolean hasSurface;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private boolean vibrate;

	private Type type;
	private QrDeviceBean qrBean;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_capture);
		ButterKnife.bind(this);
		setSupportActionBar(toolbar);
		getSupportActionBar().setTitle(R.string.qr_scan_title);
		getSupportActionBar().setDisplayShowHomeEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		//初始化 CameraManager
		CameraManager.init(getApplication());
		if (getIntent() != null) {
			try {
				if (getIntent().getExtras().get("type") != null) {
					type = (Type) getIntent().getExtras().get("type");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;

		playBeep = true;
		AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		CameraManager.get().closeDriver();
	}


	@Override
	public void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (IOException ioe) {
			return;
		} catch (RuntimeException e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
	                           int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;

	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();

	}

	public void handleDecode(Result obj, Bitmap barcode) {
		//handle result
		String resultText = obj.getText();
		Logger.i("result:" + resultText);
		inactivityTimer.onActivity();
		viewfinderView.drawResultBitmap(barcode);
		playBeepSoundAndVibrate();
		Pattern pattern = Pattern.compile("^\\w{24}");
		if (pattern.matcher(resultText).matches()) {
			if (type == Type.dev_bind) {
//				device = (QrDeviceBean) getIntent().getExtras().getSerializable("qrBean");
				Logger.i("launch to bind device fragment");
				showToast("launch to bind device fragment");
//				BindDeviceFragment.launch(this, device, resultText.toLowerCase());
			} else {
				Logger.i("launch to create device fragment");
				showToast("launch to create device fragment");
//				CreateDeviceFragment.launch(this, resultText.toLowerCase());
			}
		}else if (resultText.contains("deviceKey")&&resultText.contains("productId")){
			try{
				qrBean = new Gson().fromJson(resultText, QrDeviceBean.class);
				ImlinkNetworkFragment.launch(this, qrBean);
				this.finish();
			}catch (Exception e){
				e.printStackTrace();
				showToast(R.string.qr_scan_err);
			}
		}else if (resultText.startsWith("http://") || resultText.startsWith("https://")) {
			Intent intent = new Intent();
			intent.setAction("android.intent.action.VIEW");
			Uri content_url = Uri.parse(resultText);
			intent.setData(content_url);
			startActivity(intent);
		} else {
			showToast(R.string.qr_scan_err);
		}
	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it
			// too loud,
			// so we now play on the music stream.
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

			AssetFileDescriptor file = getResources().openRawResourceFd(
					R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(),
						file.getStartOffset(), file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			} catch (IOException e) {
				mediaPlayer = null;
			}
		}
	}

	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

}
