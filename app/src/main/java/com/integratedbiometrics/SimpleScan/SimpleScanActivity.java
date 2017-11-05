/* *************************************************************************************************
 * SimpleScanActivity.java
 *
 * DESCRIPTION:
 *     SimpleScan demo app for IBScanUltimate
 *     http://www.integratedbiometrics.com
 *
 * NOTES:
 *     Copyright (c) Integrated Biometrics, 2012-2013
 *     
 * HISTORY:
 *     2013/03/01  First version.
 *     2013/03/06  Added automatic population of capture type spinner based on initialized scanner.
 *                 A refresh from INITIALIZED now checks whether the initialized scanner is present;
 *                 if not, the scanner is closed and the list refreshed.
 *     2013/03/22  Added NFIQ calculation following completion of image capture.
 *     2013/04/06  Made minor changes, including allowing user to supply file name of e-mail image.
 *     2013/10/18  Add support for new finger quality values that indicate presence of finger in 
 *                 invalid area along scanner edge.
 *                 Implement new extended result method for IBScanDeviceListener.
 *     2013/06/19  Updated for IBScanUltimate v1.7.3.
 *     2015/04/07  Updated for IBScanUltimate v1.8.4.
 *     2015/12/11  Updated for IBScanUltimate v1.9.0.
 *     2016/01/21  Updated for IBScanUltimate v1.9.2.
 *     2016/09/22  Updated for IBScanUltimate v1.9.4.
************************************************************************************************ */

package com.integratedbiometrics.ibsimplescan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.integratedbiometrics.ibscanultimate.IBScan;
import com.integratedbiometrics.ibscanultimate.IBScan.DeviceDesc;
import com.integratedbiometrics.ibscanultimate.IBScan.SdkVersion;
import com.integratedbiometrics.ibscanultimate.IBScanDevice;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.BeepPattern;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.FingerCountState;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.FingerQualityState;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.ImageData;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.ImageResolution;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.ImageType;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.PlatenState;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.PropertyId;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.RollingData;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.RollingState;
import com.integratedbiometrics.ibscanultimate.IBScanDevice.SegmentPosition;
import com.integratedbiometrics.ibscanultimate.IBScanDeviceListener;
import com.integratedbiometrics.ibscanultimate.IBScanException;
import com.integratedbiometrics.ibscanultimate.IBScanListener;

/**
 * Main activity for SimpleScan application.  Capture on a single connected scanner can be started
 * and stopped.  After an acquisition is complete, long-clicking on the small preview window will
 * allow the image to be e-mailed or show a larger view of the image.
 */
public class SimpleScanActivity extends Activity implements IBScanListener, IBScanDeviceListener
{
	/* *********************************************************************************************
	 * PRIVATE CLASSES
	 ******************************************************************************************** */

	/*
	 * Enum representing the application state.  The application will move between states based on 
	 * callbacks from the IBScan library.  There are two methods associated with each state.  The 
	 * "transition" method is call by the app to move to a new state and sends a message to a 
	 * handler.  The "handleTransition" method is called by that handler to effect the transition.
	 */
	private static enum AppState
	{
		NO_SCANNER_ATTACHED,
		SCANNER_ATTACHED,
		REFRESH,
		INITIALIZING,
		INITIALIZED,
		CLOSING,
		STARTING_CAPTURE,
		CAPTURING,
		STOPPING_CAPTURE,
		IMAGE_CAPTURED,
		COMMUNICATION_BREAK;
	}
	
	/*
	 * This class wraps the data saved by the app for configuration changes.
	 */
	private class AppData
	{
		/* The state of the application. */
		public AppState           state                      = AppState.NO_SCANNER_ATTACHED;
		
		/* The type of capture currently selected. */
		public int                captureType                = CAPTURE_TYPE_INVALID;
		
		/* The current contents of the status TextView. */
		public String             status                     = STATUS_DEFAULT;
		
		/* The current contents of the frame time TextView. */
		public String             frameTime                  = FRAME_TIME_DEFAULT;
		
		/* The current image displayed in the image preview ImageView. */
		public Bitmap             imageBitmap                = null;
		
		/* The current background colors of the finger quality TextViews. */
		public int[]              fingerQualityColors        = new int[]
				{FINGER_QUALITY_NOT_PRESENT_COLOR, FINGER_QUALITY_NOT_PRESENT_COLOR, 
				 FINGER_QUALITY_NOT_PRESENT_COLOR, FINGER_QUALITY_NOT_PRESENT_COLOR};
				 
		/* Indicates when the finger encounters the scanner edge. */
		public boolean            fingerMarkerTop            = false;
		public boolean            fingerMarkerLeft           = false;
		public boolean            fingerMarkerRight          = false;
		
		/* Indicates whether the image preview ImageView can be long-clicked. */
		public boolean            imagePreviewImageClickable = false;
		
		/* The current contents of the device description TextView. */
		public String             description                = NO_DEVICE_DESCRIPTION_STRING;
		
		/* The current background color of the device description TextView. */
		public int                descriptionColor           = NO_DEVICE_DESCRIPTION_COLOR;
		
		/* The current device count displayed in the device count TextView. */
		public int                deviceCount                = 0;
		
		/* the product name. */
		public String             deviceName                = STATUS_DEFAULT;
	}
	
	/* *********************************************************************************************
	 * PRIVATE CONSTANTS
	 ******************************************************************************************** */

	/* The tag used for Android log messages from this app. */
	private static final String TAG                               = "Simple Scan";
	
	/* The default value of the status TextView. */
	private static final String STATUS_DEFAULT                   = "";
	
	/* The default value of the frame time TextView. */
	private static final String FRAME_TIME_DEFAULT               = "n/a";

	/* The default file name for images and templates for e-mail. */
	private static final String FILE_NAME_DEFAULT                = "output";

	/* The value of AppData.captureType when the capture type has never been set. */
	private static final int    CAPTURE_TYPE_INVALID             = -1;
	
	/* The number of finger qualities set in the preview image. */
	private static final int    FINGER_QUALITIES_COUNT           = 4;
	
	/* The description in the device description TextView when no device is attached. */
	private static final String NO_DEVICE_DESCRIPTION_STRING     = "(no scanner)";
	
	/* The background color of the device description TextView when no device is attached. */
	private static final int    NO_DEVICE_DESCRIPTION_COLOR      = Color.RED;
	
	/* The background color of the device description TextView when a device is attached. */
	private static final int    DEVICE_DESCRIPTION_COLOR         = Color.GRAY;
	
	/* The delay between resubmissions of messages when trying to stop capture. */
	private static final int    STOPPING_CAPTURE_DELAY_MILLIS    = 250;
	
	/* The device index which will be initialized. */
	private static final int    INITIALIZING_DEVICE_INDEX        = 0;
	
	/* The background color of the preview image ImageView. */
	private static final int    PREVIEW_IMAGE_BACKGROUND         = Color.LTGRAY;
	
	/* The background color of a finger quality TextView when the finger is not present. */
	private static final int    FINGER_QUALITY_NOT_PRESENT_COLOR = Color.LTGRAY;

	/* The background color of a finger quality TextView when the finger is good quality. */
	private static final int    FINGER_QUALITY_GOOD_COLOR        = Color.GREEN;

	/* The background color of a finger quality TextView when the finger is fair quality. */
	private static final int    FINGER_QUALITY_FAIR_COLOR        = Color.YELLOW;

	/* The background color of a finger quality TextView when the finger is poor quality. */
	private static final int    FINGER_QUALITY_POOR_COLOR        = Color.RED;
	
	int 						setLeds;
	
	int							OnlyLEFTFOUR					=0 ;//LEFT
	
	int							OnlyRIGHTFOUR					=0 ;//RIGHT
	
	boolean 					devicekojak					=false;
	/* *********************************************************************************************
	 * PRIVATE FIELDS (UI COMPONENTS)
	 ******************************************************************************************** */

	private TextView   m_txtDeviceCount;
	private TextView   m_txtStatus;                   
	private TextView   m_txtDesciption;                    
	private TextView   m_txtFrameTime;            
	private ImageView  m_imagePreviewImage;                    
	private TextView[] m_txtFingerQuality = new TextView[FINGER_QUALITIES_COUNT];
	private TextView   m_txtSDKVersion;
	private Spinner    m_spinnerCaptureType;
	private Button     m_startCaptureBtn;
	private Button     m_stopCaptureBtn;
	private Button     m_openScannerBtn;
	private Button     m_closeScannerBtn;
	private Button     m_refreshBtn;
	private Dialog     m_enlargedDialog;
	private Bitmap	   m_BitmapImage;
	private Bitmap	   m_BitmapKojakRollImage;
	
	/* *********************************************************************************************
	 * PRIVATE FIELDS
	 ******************************************************************************************** */

	/* 
	 * A handle to the single instance of the IBScan class that will be the primary interface to
	 * the library, for operations like getting the number of scanners (getDeviceCount()) and 
	 * opening scanners (openDeviceAsync()). 
	 */
	private IBScan       m_ibScan;

	/* 
	 * A handle to the open IBScanDevice (if any) that will be the interface for getting data from
	 * the open scanner, including capturing the image (beginCaptureImage(), cancelCaptureImage()),
	 * and the type of image being captured.
	 */
	private IBScanDevice m_ibScanDevice;
	private ImageType    m_imageType;
	
	/*
	 * An object that will play a sound when the image capture has completed.
	 */
	private PlaySound    m_beeper = new PlaySound();
	
	/* 
	 * Information retained to show view.
	 */
	private ImageData    m_lastImage;
	
	/*
	 * Information retained for orientation changes.
	 */
	private AppData      m_savedData = new AppData();

	/* *********************************************************************************************
	 * INHERITED INTERFACE (Activity OVERRIDES)
	 ******************************************************************************************** */

    /*
     * Called when the activity is started.
     */
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		   
		this.m_ibScan = IBScan.getInstance(this.getApplicationContext());
  		this.m_ibScan.setScanListener(this);
	    
		Resources r = Resources.getSystem();
		Configuration config = r.getConfiguration();
		onConfigurationChanged(config);
		
		resetButtonsForState(AppState.NO_SCANNER_ATTACHED);
		transitionToRefresh();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) 
	{
		super.onConfigurationChanged(newConfig);

		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) 
		{
			setContentView(R.layout.ib_scan_port);
		} 
		else
		{
			setContentView(R.layout.ib_scan_land);
		}
		
		/* Initialize UI fields for new orientation. */
		initUIFields(); 
		
		/* Populate UI with data from old orientation. */
		populateUI();  
	}
	
	/*
	 * Release driver resources.
	 */
	@Override
	protected void onDestroy() 
	{
		super.onDestroy();
	}

	@Override
	public void onBackPressed() 
	{
		exitApp(this);
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() 
	{
		/* 
		 * For the moment, we do not restore the current operation after a configuration change; 
		 * just close any open device.
		 */
		this.m_scanHandler.removeCallbacksAndMessages(null);
        if (this.m_ibScanDevice != null)
        {
        	/* Try to cancel any active capture. */
        	try
        	{
        		boolean isActive = this.m_ibScanDevice.isCaptureActive();
        		if (isActive)
        		{
        			this.m_ibScanDevice.cancelCaptureImage();
        		}
        	}
        	catch (IBScanException ibse)
        	{
        		Log.e(TAG, "error canceling capture " + ibse.getType().toString());        		
        	}
        	/* Try to close any open device. */
        	try
        	{
        		this.m_ibScanDevice.close();
        	}
        	catch (IBScanException ibse)
        	{
        		Log.e(TAG, "error closing device " + ibse.getType().toString());
        	}
        	this.m_ibScanDevice = null;
        }
        
        return (null);
	}
	
	/* *********************************************************************************************
	 * PRIVATE METHODS
	 ******************************************************************************************** */
 
	/*
	 * Initialize UI fields for new orientation.
	 */
	private void initUIFields()
	{
		this.m_txtDeviceCount      = (TextView) findViewById(R.id.device_count);
		this.m_txtStatus           = (TextView) findViewById(R.id.status);
		this.m_txtDesciption       = (TextView) findViewById(R.id.description);

		/* Hard-coded for four finger qualities. */
		this.m_txtFingerQuality[0] = (TextView) findViewById(R.id.scan_states_color1);
		this.m_txtFingerQuality[1] = (TextView) findViewById(R.id.scan_states_color2);
		this.m_txtFingerQuality[2] = (TextView) findViewById(R.id.scan_states_color3);
		this.m_txtFingerQuality[3] = (TextView) findViewById(R.id.scan_states_color4);

		this.m_txtFrameTime        = (TextView) findViewById(R.id.frame_time);

		this.m_txtSDKVersion       = (TextView) findViewById(R.id.version);

		this.m_imagePreviewImage   = (ImageView) findViewById(R.id.preview_image);
		this.m_imagePreviewImage.setOnLongClickListener(this.m_imagePreviewImageLongClickListener);
		this.m_imagePreviewImage.setBackgroundColor(PREVIEW_IMAGE_BACKGROUND);
		
		this.m_stopCaptureBtn      = (Button) findViewById(R.id.stop_capture_btn);
		this.m_stopCaptureBtn.setOnClickListener(this.m_stopCaptureBtnClickListener);

		this.m_startCaptureBtn     = (Button) findViewById(R.id.start_capture_btn);
		this.m_startCaptureBtn.setOnClickListener(this.m_startCaptureBtnClickListener);
		
		this.m_openScannerBtn      = (Button) findViewById(R.id.open_scanner_btn);
		this.m_openScannerBtn.setOnClickListener(this.m_openScannerBtnClickListener);
		
		this.m_closeScannerBtn     = (Button) findViewById(R.id.close_scanner_btn);
		this.m_closeScannerBtn.setOnClickListener(this.m_closeScannerBtnClickListener);
		
		this.m_refreshBtn          = (Button) findViewById(R.id.refresh_btn);
		this.m_refreshBtn.setOnClickListener(this.m_refreshBtnClickListener);
		
		this.m_spinnerCaptureType  = (Spinner) findViewById(R.id.capture_type);
		final ArrayAdapter<CharSequence> adapterCapture = new ArrayAdapter<CharSequence>(this, 
				android.R.layout.simple_spinner_item, new CharSequence[] { });
		adapterCapture.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		this.m_spinnerCaptureType.setAdapter(adapterCapture);
		this.m_spinnerCaptureType.setOnItemSelectedListener(this.m_captureTypeItemSelectedListener);
	}
		
	/*
	 * Populate UI with data from old orientation.
	 */
	private void populateUI()
	{
		resetButtonsForState(this.m_savedData.state);

		setSDKVersionInfo();
		setDeviceCount(this.m_savedData.deviceCount);
		setDescription(this.m_savedData.description, this.m_savedData.descriptionColor);

		if (this.m_savedData.status != null)
		{
			this.m_txtStatus.setText(this.m_savedData.status);
		}
		if (this.m_savedData.frameTime != null)
		{
			this.m_txtFrameTime.setText(this.m_savedData.frameTime);
		}
		if (this.m_savedData.imageBitmap != null) 
		{
			this.m_imagePreviewImage.setImageBitmap(this.m_savedData.imageBitmap);
		}
		
		for (int i = 0; i < FINGER_QUALITIES_COUNT; i++)
		{
			this.m_txtFingerQuality[i].setBackgroundColor(this.m_savedData.fingerQualityColors[i]);
		}

		if (this.m_savedData.captureType != CAPTURE_TYPE_INVALID)
		{
			this.m_spinnerCaptureType.setSelection(this.m_savedData.captureType);
		}
		
		if(m_BitmapImage != null)
		{
			m_BitmapImage.isRecycled();
		}
		
		if(m_BitmapKojakRollImage != null)
		{
			m_BitmapKojakRollImage.isRecycled();
		}
		this.m_imagePreviewImage.setLongClickable(this.m_savedData.imagePreviewImageClickable);
	}	

	/*
	 * Show Toast message on UI thread.
	 */
	private void showToastOnUiThread(final String message, final int duration)
	{
		runOnUiThread(new Runnable() 
		{ 
			@Override
			public void run()
			{
				Toast toast = Toast.makeText(getApplicationContext(), message, duration);
				toast.show();				
			}
		});		
	}
 
	/*
	 * Set SDK version in SDK version text field.
	 */
	private void setSDKVersionInfo() 
	{
		String txtValue;

		try
		{
			SdkVersion sdkVersion;
			
			sdkVersion = this.m_ibScan.getSdkVersion();			
			txtValue   = "SDK version: " + sdkVersion.file;
		}
		catch (IBScanException ibse)
		{
			txtValue = "(failure)"; 
		}
		
		/* Make sure this occurs on the UI thread. */
		final String txtValueTemp = txtValue;
		runOnUiThread(new Runnable() 
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_txtSDKVersion.setText(txtValueTemp);				
			}
		});
	}

	/*
	 * Set description of header in finger print image box.
	 */
	private void setDescription(final String description, final int color) 
	{
		this.m_savedData.description = description;
		this.m_savedData.descriptionColor = color;
		
		/* Make sure this occurs on the UI thread. */
		runOnUiThread(new Runnable() 
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_txtDesciption.setText(description);
				SimpleScanActivity.this.m_txtDesciption.setBackgroundColor(color);
			}
		});
	}

	/*
	 * Set device count in device count text box.
	 */
	private void setDeviceCount(final int deviceCount) 
	{
		this.m_savedData.deviceCount = deviceCount;
		
		/* Make sure this occurs on the UI thread. */
		runOnUiThread(new Runnable() 
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_txtDeviceCount.setText("" + deviceCount);
			}
		});
	}

	/*
	 * Set status in status field.  Save value for orientation change.
	 */
	private void setStatus(final String s)
	{
		this.m_savedData.status = s;
		
		/* Make sure this occurs on the UI thread. */
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_txtStatus.setText(s);				
			}
		});
	}	
	
	/*
	 * Set frame time in frame time field.  Save value for orientation change.
	 */
	private void setFrameTime(final String s)
	{
		this.m_savedData.frameTime = s;
		
		/* Make sure this occurs on the UI thread. */
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_txtFrameTime.setText(s);		
			}
		});
	}

	/*
	 * Set capture types.
	 */
	private void setCaptureTypes(final String[] captureTypes, final int selectIndex)
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(SimpleScanActivity.this, 
						android.R.layout.simple_spinner_item, captureTypes);
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				SimpleScanActivity.this.m_spinnerCaptureType.setAdapter(adapter);
				SimpleScanActivity.this.m_spinnerCaptureType.setSelection(selectIndex);
			}
		});
	}
	
	/*
	 * Show enlarged image in popup window.
	 */		
	private void showEnlargedImage()
	{
		/* 
		 * Sanity check.  Make sure the image exists.
		 */
		if (this.m_lastImage == null)
		{
			showToastOnUiThread("No last image information", Toast.LENGTH_SHORT);
			return;
		}
		
		this.m_enlargedDialog = new Dialog(this, R.style.Enlarged);
		this.m_enlargedDialog.setContentView(R.layout.enlarged);
		this.m_enlargedDialog.setCancelable(false);
		
		final Bitmap    bitmap        = this.m_lastImage.toBitmap();
		final ImageView enlargedView = (ImageView) this.m_enlargedDialog.findViewById(R.id.enlarged_image);
		enlargedView.setImageBitmap(bitmap);
		enlargedView.setOnClickListener(this.m_enlargedImageClickListener);
		
		this.m_enlargedDialog.show();
	}
	
	/*
	 * Compress the image and attach it to an e-mail using an installed e-mail client. 
	 */
	private void sendImageInEmail(final ImageData imageData, final String fileName) 
	{
		File    file    = new File(Environment.getExternalStorageDirectory().getPath() + "/" + fileName);
		boolean created = false;
		try 
		{
			file.createNewFile();
			
			final FileOutputStream ostream = new FileOutputStream(file);
			final Bitmap           bitmap  = imageData.toBitmap();
			bitmap.compress(CompressFormat.PNG, 100, ostream);
			ostream.close();
			created = true;
		} 
		catch (IOException ioe) 
		{
			Toast.makeText(getApplicationContext(), "Could not create image for e-mail", Toast.LENGTH_LONG).show();
		}

		/* If file was created, send the e-mail. */
		if (created)
		{
			attachAndSendEmail(Uri.fromFile(file), "Fingerprint Image", fileName);
		}
	}

	/*
	 * Attach file to e-mail and send.
	 */
	private void attachAndSendEmail(final Uri uri, final String subject, final String message)
	{
	 	final Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("message/rfc822");
		i.putExtra(Intent.EXTRA_EMAIL,   new String[] { "" });
		i.putExtra(Intent.EXTRA_SUBJECT, subject);
		i.putExtra(Intent.EXTRA_STREAM,  uri);
		i.putExtra(Intent.EXTRA_TEXT,    message);

		try 
		{
			startActivity(Intent.createChooser(i, "Send mail..."));
		} 
		catch (ActivityNotFoundException anfe) 
		{
			showToastOnUiThread("There are no e-mail clients installed", Toast.LENGTH_LONG);
		}
	}
	
	/*
	 * Prompt to send e-mail with image.
	 */
	private void promptForEmail(final ImageData imageData)
	{
		/* The dialog must be shown from the UI thread. */
		runOnUiThread(new Runnable() 
		{ 
			@Override
			public void run()
			{
		    		final LayoutInflater      inflater     = SimpleScanActivity.this.getLayoutInflater();
		    		final View                fileNameView = inflater.inflate(R.layout.file_name_dialog, null);
		    		final AlertDialog.Builder builder      = new AlertDialog.Builder(SimpleScanActivity.this)
		    			.setView(fileNameView)
					.setTitle("Enter file name")
					.setPositiveButton("OK", new DialogInterface.OnClickListener() 
		        	     {
						@Override
						public void onClick(final DialogInterface dialog, final int which) 
						{
							final EditText text     = (EditText) fileNameView.findViewById(R.id.file_name);
							final String   fileName = text.getText().toString();
										
							/* E-mail image in background thread. */
							Thread threadEmail = new Thread() 
							{
								@Override
								public void run()
								{
									sendImageInEmail(imageData, fileName);
								}
							};
							threadEmail.start();
						}
					})
					.setNegativeButton("Cancel", null);
		    			EditText text = (EditText) fileNameView.findViewById(R.id.file_name);
		    			text.setText(FILE_NAME_DEFAULT + "." + "png");
		  
					builder.create().show();
			}
		});
	}
	
	/*
	 * Exit application.
	 */
	private static void exitApp(Activity ac) 
	{
		ac.moveTaskToBack(true);
		ac.finish();
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	/* *********************************************************************************************
	 * PRIVATE METHODS (SCANNING STATE MACHINE)
	 ******************************************************************************************** */
	
	/*
	 * A handler to process state transition messages.
	 */
	private Handler m_scanHandler = new Handler(new Handler.Callback() 
	{		
		@Override
		public boolean handleMessage(final Message msg) 
		{
			final AppState nextState = AppState.values()[msg.what];
			
			switch (nextState)
			{
				case NO_SCANNER_ATTACHED:
					handleTransitionToNoScannerAttached();
					break;		
					
				case SCANNER_ATTACHED:
				{
					String deviceDesc  = (String)msg.obj;
					int    deviceCount = msg.arg1;
					handleTransitionToScannerAttached(deviceDesc, deviceCount);
					break;	
				}
				
				case REFRESH:
					handleTransitionToRefresh();
					break;
					
				case INITIALIZING:
				{
					final int deviceIndex = msg.arg1;
					handleTransitionToInitializing(deviceIndex);
					break;	
				}
				
				case INITIALIZED:
				{
					IBScanDevice scanDevice = (IBScanDevice)msg.obj;
					handleTransitionToInitialized(scanDevice);
					break;
				}
				
				case CLOSING:
					handleTransitionToClosing();
					break;	
				
				case STARTING_CAPTURE:
					handleTransitionToStartingCapture();
					break;	
				
				case CAPTURING:
					handleTransitionToCapturing();
					break;	
					
				case STOPPING_CAPTURE:
					handleTransitionToStoppingCapture();
					break;
					
				case IMAGE_CAPTURED:
				{
					final Object[] objects = (Object[])msg.obj;
					final ImageData   imageData       = (ImageData)objects[0];
					final ImageType   imageType       = (ImageType)objects[1];
					final ImageData[] splitImageArray = (ImageData[])objects[2];
					handleTransitionToImageCaptured(imageData, imageType, splitImageArray);
					break;
				}
				
				case COMMUNICATION_BREAK:
					handleTransitionToCommunicationBreak();
					break;
			}
			
			return (false);
		}
	});
			
	/*
	 * Transition to no ### state.
	 */
	private void transitionToNoScannerAttached()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.NO_SCANNER_ATTACHED.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToScannerAttached(final String deviceDesc, final int deviceCount)
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.SCANNER_ATTACHED.ordinal(), deviceCount, 0, deviceDesc);
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToRefresh()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.REFRESH.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToInitializing(final int deviceIndex)
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.INITIALIZING.ordinal(), deviceIndex, 0);
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToInitialized(final IBScanDevice device)
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.INITIALIZED.ordinal(), device);
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToClosing()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.CLOSING.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToStartingCapture()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.STARTING_CAPTURE.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToCapturing()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.CAPTURING.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToStoppingCapture()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.STOPPING_CAPTURE.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToStoppingCaptureWithDelay(final int delayMillis)
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.STOPPING_CAPTURE.ordinal());
		this.m_scanHandler.sendMessageDelayed(msg, delayMillis);
	}
	private void transitionToImageCaptured(final ImageData image, final ImageType imageType, 
			final ImageData[] splitImageArray)
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.IMAGE_CAPTURED.ordinal(), 0, 0, 
				new Object[] {image, imageType, splitImageArray} );
		this.m_scanHandler.sendMessage(msg);
	}
	private void transitionToCommunicationBreak()
	{
		final Message msg = this.m_scanHandler.obtainMessage(AppState.COMMUNICATION_BREAK.ordinal());
		this.m_scanHandler.sendMessage(msg);
	}
		
	/* 
	 * Handle transition to no scanner attached state. 
	 */
	private void handleTransitionToNoScannerAttached()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case REFRESH:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to NO_SCANNER_ATTACHED from " + this.m_savedData.state.toString());
				return;
		}

		/* Move to this state. */
		this.m_savedData.state = AppState.NO_SCANNER_ATTACHED;			

		/* Setup UI for state. */
		resetButtonsForState(AppState.NO_SCANNER_ATTACHED);
		setStatus("no scanners");
		setFrameTime(FRAME_TIME_DEFAULT);
		setDeviceCount(0);
		setDescription(NO_DEVICE_DESCRIPTION_STRING, NO_DEVICE_DESCRIPTION_COLOR);

		/*
		 * We will stay in this state until a scanner is attached or the user presses the "Refresh".
		 */
	}
	
	/*
	 * Handle transition to scanner attached state.
	 */
	private void handleTransitionToScannerAttached(final String deviceDesc, final int deviceCount)
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case REFRESH:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to SCANNER_ATTACHED from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.SCANNER_ATTACHED;
		
		/* Setup UI for state. */
		resetButtonsForState(AppState.SCANNER_ATTACHED);
		setStatus("uninitialized");
		setFrameTime(FRAME_TIME_DEFAULT);
		setDeviceCount(deviceCount);
		setDescription(deviceDesc, DEVICE_DESCRIPTION_COLOR);
		
		/*
		 * We will stay in this state until the scanner is detached, the user presses the "Refresh"
		 * button, or the user presses the "Start" button.
		 */
	}
	
	/*
	 * Handle transition to refresh state.
	 */
	private void handleTransitionToRefresh()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case NO_SCANNER_ATTACHED:
			case SCANNER_ATTACHED:
			case CLOSING:
				break;
			case INITIALIZED:
				/*
				 * If the initialized device has been disconnected, transition to closing, then 
				 * refresh.
				 */
				if (this.m_ibScanDevice != null)
				{
					try
					{
						/* Just a test call. */
						this.m_ibScanDevice.isCaptureActive();
					}
					catch (IBScanException ibse)
					{
						transitionToClosing();						
					}
				}
				return;
			case INITIALIZING:
			case STARTING_CAPTURE:
			case CAPTURING:
			case STOPPING_CAPTURE:
			case IMAGE_CAPTURED:
			case COMMUNICATION_BREAK:
				/* 
				 * These transitions is ignored to preserve UI state.  The CLOSING state will transition to 
				 * REFRESH.
				 */
				return;
			case REFRESH:
				/*
				 * This transition can occur when multiple events (button presses, device count changed 
				 * callbacks occur).  We assume the last execution will transition to the correct state.
				 */
				return;
			default:
				Log.e(TAG, "Received unexpected transition to REFRESH from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.REFRESH;
		
		/* Setup UI for state. */
		resetButtonsForState(AppState.REFRESH);
		setStatus("refreshing");
		setFrameTime(FRAME_TIME_DEFAULT);
		setCaptureTypes(new String[0], 0);

		/*
		 * Make sure there are no USB devices attached that are IB scanners for which permission has
		 * not been granted.  For any that are found, request permission; we should receive a 
		 * callback when permission is granted or denied and then when IBScan recognizes that new
		 * devices are connected, which will result in another refresh.
		 */
		final UsbManager                 manager        = (UsbManager)this.getApplicationContext().getSystemService(Context.USB_SERVICE);
		final HashMap<String, UsbDevice> deviceList     = manager.getDeviceList();
		final Iterator<UsbDevice>        deviceIterator = deviceList.values().iterator();
		while (deviceIterator.hasNext())
		{
		    final UsbDevice device       = deviceIterator.next();
		    final boolean   isScanDevice = IBScan.isScanDevice(device);		    
		    if (isScanDevice)
		    {
		    	final boolean hasPermission = manager.hasPermission(device);
		    	if (!hasPermission)
		    	{
		    		this.m_ibScan.requestPermission(device.getDeviceId());
		    	}
		    }
		}
		
		/* 
		 * Determine the next state according to device count.  A state transition always occurs,
		 * either to NO_SCANNER_ATTACHED or SCANNER_ATTACHED.
		 */
		try
		{
			final int deviceCount = this.m_ibScan.getDeviceCount();
			if (deviceCount > 0)
			{
				try
				{
					final DeviceDesc deviceDesc = this.m_ibScan.getDeviceDescription(INITIALIZING_DEVICE_INDEX);
					transitionToScannerAttached(deviceDesc.productName + " - " + deviceDesc.serialNumber, deviceCount);
				}
				catch (IBScanException ibse)
				{
					Log.e(TAG, "Received exception getting device description " + ibse.getType().toString());
					transitionToNoScannerAttached();					
				}
			}
			else
			{
				transitionToNoScannerAttached();
			}
		}
		catch (IBScanException ibse)
		{
			Log.e(TAG, "Received exception getting device count " + ibse.getType().toString());
			transitionToNoScannerAttached();
		}
	}
	
	/*
	 * Handle transition to initializing state.
	 */
	private void handleTransitionToInitializing(final int deviceIndex)
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case SCANNER_ATTACHED:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to INITIALIZING from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.INITIALIZING;

		/* Setup UI for state. */
		resetButtonsForState(AppState.INITIALIZING);
		setStatus("initializing");
		setFrameTime(FRAME_TIME_DEFAULT);
		
		/* Clear saved information from last scan and prevent long clicks on the image viewer. */
		this.m_imagePreviewImage.setLongClickable(false);
		this.m_savedData.imagePreviewImageClickable = false;
		this.m_lastImage = null;
			
		/* Start device initialization. */
		try
		{
			/* While the device is being opened, callbacks for initialization progress will be 
			 * received.  When the device is open, another callback will be received and capture can
			 * begin.  We will stay in this state until capture begins.
			 */
			this.m_ibScan.openDeviceAsync(deviceIndex);			
		}
		catch (IBScanException ibse)
		{
			/* Device initialization failed.  Go to closing. */
			showToastOnUiThread("Could not initialize device with exception " + ibse.getType().toString(), Toast.LENGTH_SHORT);
			transitionToClosing();
		}
	}
	
	/*
	 * Handle transition to starting capture state.
	 */
	private void handleTransitionToInitialized(final IBScanDevice device)
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case INITIALIZING:
			case STARTING_CAPTURE:
			case STOPPING_CAPTURE:
			case IMAGE_CAPTURED:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to INITIALIZED from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.INITIALIZED;
		
		/* Setup the UI for state. */
		resetButtonsForState(AppState.INITIALIZED);
		setStatus("initialized");
		setFrameTime(FRAME_TIME_DEFAULT);
		
		if(devicekojak)
		{			
			try {
				OnlyRIGHTFOUR =0;
				OnlyLEFTFOUR =0;
				SimpleScanActivity.this.m_ibScanDevice.setLEDs(IBScanDevice.LED_NONE);
			} catch (IBScanException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
				
		
		/* If the device is null, we have already passed through this state. */
		if (device != null)
		{
			/* Enable power save mode. */
			try
			{
				//setProperty
				device.setProperty(PropertyId.ENABLE_POWER_SAVE_MODE, "TRUE");
				
				this.m_savedData.deviceName = device.getProperty(PropertyId.PRODUCT_ID);
				final String deviceName = this.m_savedData.deviceName;
				
				//getProperty device Width,Height
				String imageW = device.getProperty(PropertyId.IMAGE_WIDTH);
				String imageH =device.getProperty(PropertyId.IMAGE_HEIGHT);
				int	imageWidth = Integer.parseInt(imageW);
				int	imageHeight = Integer.parseInt(imageH);
				this.m_BitmapImage = SimpleScanActivity.this.toDrawBitmap(imageWidth, imageHeight);
				
				if(deviceName.equals("KOJAK") || deviceName.equals("FIVE-0"))
				{
					//Kojak_Five-0
					String kojakRollimageW = device.getProperty(PropertyId.ROLLED_IMAGE_WIDTH);
					String kojakRollimageH =device.getProperty(PropertyId.ROLLED_IMAGE_HEIGHT);
				int	imageKojakRollWidth = Integer.parseInt(kojakRollimageW);
				int	imageKojakRollHeight = Integer.parseInt(kojakRollimageH);
				//draw bitmap.
				this.m_BitmapKojakRollImage = SimpleScanActivity.this.toDrawBitmap(imageKojakRollWidth, imageKojakRollHeight);
				
				}
				
			
			}
			catch (IBScanException ibse)
			{
				/* 
				 * We could not enable power save mode. This is was non-essential, so we continue on and 
				 * see whether we can start capture. 
				 */
				Log.e(TAG, "Could not begin enable power save mode " + ibse.getType().toString());			
			}
			
			/* Get list of acceptable capture types. */
			Vector<String> typeVector = new Vector<String>();
			for (ImageType imageType : ImageType.values())
			{
				try
				{
					boolean available = device.isCaptureAvailable(imageType, ImageResolution.RESOLUTION_500);
					if (available)
					{
						typeVector.add(imageType.toDescription());
						devicekojak =false;
					}
					
					final String deviceName = this.m_savedData.deviceName;
					if(typeVector.size()>4 && deviceName.equals("KOJAK"))
					{
						typeVector.remove(3);
						typeVector.add("Left Four-finger flat fingerprint");
						typeVector.add("Right Four-finger flat fingerprint");
						devicekojak =true;
					}
				
				}
				catch (IBScanException ibse)
				{
					Log.e(TAG, "Could not check capture availability " + ibse.getType().toString());					
				}
			}
			String[] typeArray = new String[0];
			typeArray = typeVector.toArray(typeArray);
			if (typeVector.size() > 1)
			{
				setCaptureTypes(typeArray, 1);
			}
			else
			{
				setCaptureTypes(typeArray, 0);
			}

			/* Save device. */
			this.m_ibScanDevice = device;
		}
		
		/*
		 * Stay in this state waiting to perform scans. 
		 */
	}

	/*
	 * Handle transition to closing state.
	 */
	private void handleTransitionToClosing()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case INITIALIZING:
			case INITIALIZED:
			case COMMUNICATION_BREAK:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to CLOSING from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.CLOSING;
		
		/* Setup the UI for state. */
		resetButtonsForState(AppState.CLOSING);
		setStatus("closing");
		setFrameTime(FRAME_TIME_DEFAULT);
		
		/* Close & null device. */
		if (this.m_ibScanDevice != null)
		{
			try
			{
				this.m_ibScanDevice.close();
			}
			catch (IBScanException ibse)
			{
				Log.e(TAG, "Could not close device " + ibse.getType().toString());				
			}
			this.m_ibScanDevice = null;
		}
		
		/*
		 * Refresh the list of devices. 
		 */
		transitionToRefresh();
	}

	/*
	 * Handle transition to starting capture state.
	 */
	private void handleTransitionToStartingCapture()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case INITIALIZED:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to STARTING_CAPTURE from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.STARTING_CAPTURE;
		
		/* Setup the UI for state. */
		resetButtonsForState(AppState.STARTING_CAPTURE);
		setStatus("starting");
		setFrameTime(FRAME_TIME_DEFAULT);
		
		try 
		{
			ImageType imageType = ImageType.TYPE_NONE;
			
			for (ImageType imageTypeTemp : ImageType.values())
			{
				if (((CharSequence)this.m_spinnerCaptureType.getSelectedItem()).equals(imageTypeTemp.toDescription()))
				{
					imageType = imageTypeTemp;
					break;
				}else
				{
					if(this.m_spinnerCaptureType.getSelectedItem().equals("Left Four-finger flat fingerprint"))
					{
						OnlyLEFTFOUR =1;
						break;
					}
					if(this.m_spinnerCaptureType.getSelectedItem().equals("Right Four-finger flat fingerprint"))
					{
						OnlyRIGHTFOUR =1;
						break;
					}
				}
			}
			
			
			//Bitmap reSize
			/* 
			 * Begin capturing an image.  While the image is being captured, we will receive
			 * preview images through callbacks.  At the end of the capture, we will recieve a
			 * final image. 
			 */
			if(devicekojak)
			{
				if(imageType == ImageType.ROLL_SINGLE_FINGER)
				{
					SimpleScanActivity.this.m_ibScanDevice.setLEDs(IBScanDevice.IBSU_LED_F_PROGRESS_ROLL);
				}				
			}
			
			if(OnlyLEFTFOUR ==1)
			{
				//OnlyLEFTFOUR =1;
				//0:Green , 1:Red ,2:Yellow
				//1:LEFT ,2: RIGHT
				this.PlayLed(1, false, 1);
				SimpleScanActivity.this.m_ibScanDevice.setLEDs(setLeds);
				imageType = ImageType.FLAT_FOUR_FINGERS;
			}
			if(OnlyRIGHTFOUR ==1)
			{
				//OnlyRIGHTFOUR =1;
				//0:Green , 1:Red ,2:Yellow
				//1:LEFT ,2: RIGHT
				this.PlayLed(1, false, 2);
				SimpleScanActivity.this.m_ibScanDevice.setLEDs(setLeds);

				imageType = ImageType.FLAT_FOUR_FINGERS;
			}
			
			this.m_ibScanDevice.beginCaptureImage(imageType, ImageResolution.RESOLUTION_500, 
					IBScanDevice.OPTION_AUTO_CAPTURE | IBScanDevice.OPTION_AUTO_CONTRAST);

			/* Save this device and image type for later use. */
			this.m_imageType = imageType;
			transitionToCapturing();
		}
		catch(IBScanException ibse)
		{
			/* We could not begin capturing.  Go to back to initialized. */
			showToastOnUiThread("Could not begin capturing with error " + ibse.getType().toString(), Toast.LENGTH_SHORT);
			transitionToInitialized(null);
		}
	}
	
	/*
	 * Handle transition to capturing state.
	 */
	private void handleTransitionToCapturing()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case STARTING_CAPTURE:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to CAPTURING from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.CAPTURING;
		
		/* Setup UI for state. */
		resetButtonsForState(AppState.CAPTURING);
		setStatus("capturing");
		setFrameTime(FRAME_TIME_DEFAULT);
		
		/* 
		 * We will start receiving callbacks for preview images and finger count and quality 
		 * changes. 
		 */
		this.m_ibScanDevice.setScanDeviceListener(this);		
		showToastOnUiThread("Now capturing...put a finger on the sensor", Toast.LENGTH_SHORT);
		
		/*
		 * We stay in this state until a good-quality image with the correct number of fingers is 
		 * obtained, an error occurs (such as a communication break), or the user presses the "Stop"
		 * button.
		 */
	}

	/*
	 * Handle transition to stopping capture state.
	 */
	private void handleTransitionToStoppingCapture()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case CAPTURING:
			case STOPPING_CAPTURE:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to STOPPING_CAPTURE from " + this.m_savedData.state.toString());
				return;
		}

		/* Move to this state. */
		this.m_savedData.state = AppState.STOPPING_CAPTURE;		
		
		/* Setup UI for state. */
		resetButtonsForState(AppState.STOPPING_CAPTURE);
		setStatus("stopping");
		setFrameTime(FRAME_TIME_DEFAULT);

		/* Cancel capture if necessary. */
		boolean done = false;		
		try
		{
			final boolean active = this.m_ibScanDevice.isCaptureActive();

			if (!active)
			{
				/* Capture has already stopped.  Let's transition to the refresh state. */
				showToastOnUiThread("Capture stopped", Toast.LENGTH_SHORT);
				done = true;
			} 
			else
			{
				try
				{
					/* Cancel capturing the image. */
					this.m_ibScanDevice.cancelCaptureImage();									
				}
				catch (IBScanException ibse)
				{
					showToastOnUiThread("Could not cancel capturing with error " + ibse.getType().toString(), Toast.LENGTH_SHORT);	
					done = true;
				}
			}			
		}
		catch (IBScanException ibse)
		{
			/* An error occurred.  Let's try to refresh. */
			showToastOnUiThread("Could not query capture active state " + ibse.getType().toString(), Toast.LENGTH_SHORT);
			done = true;
		}
		
		/*
		 * On error or capture not active, transition to initialized.
		 */
		if (done)
		{
			transitionToInitialized(null);
		}
		/* 
		 *  We must wait for this to complete, so we will resubmit this transition with a delay.
		 */
		else
		{
			transitionToStoppingCaptureWithDelay(STOPPING_CAPTURE_DELAY_MILLIS);
		}
	}
	
	/* 
	 * Handle transition to image captured state.
	 */
	private void handleTransitionToImageCaptured(final ImageData image, 
			final ImageType imageType, final ImageData[] splitImageArray)
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case CAPTURING:
				break;
			default:
				showToastOnUiThread("Received unexpected transition to STOPPING_CAPTURE from " + this.m_savedData.state.toString(), Toast.LENGTH_SHORT);
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.IMAGE_CAPTURED;		
		
		/* Setup UI for state. */
		resetButtonsForState(AppState.IMAGE_CAPTURED);
		setStatus("captured");
		setFrameTime(FRAME_TIME_DEFAULT);

		/* 
		 * Save information in case we later show the enlarged image and allow long clicks on the
		 * image view to show that view. 
		 */
		this.m_lastImage = image;
		this.m_savedData.imagePreviewImageClickable = true;
		this.m_imagePreviewImage.setLongClickable(true);
			
		try {
			if(OnlyLEFTFOUR ==1)
			{
				//0:Green , 1:Red ,2:Yellow
				//1:LEFT ,2: RIGHT
				this.PlayLed(0, false, 1);
				SimpleScanActivity.this.m_ibScanDevice.setLEDs(setLeds);
				try {
				Thread.sleep(100);
				} 
				catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				}

				
			}
			if(OnlyRIGHTFOUR ==1)
			{
				
				//0:Green , 1:Red ,2:Yellow
				//1:LEFT ,2: RIGHT
				this.PlayLed(0, false, 2);
				SimpleScanActivity.this.m_ibScanDevice.setLEDs(setLeds);
				try {
				Thread.sleep(100);
				} 
				catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				}

			}
		} catch (IBScanException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		/* Calculate NFIQ score on background thread. */
		Thread t = new Thread() 
		{
			@Override
			public void run()
			{
				try
				{
					int nfiqScore = SimpleScanActivity.this.m_ibScanDevice.calculateNfiqScore(image);
					showToastOnUiThread("NFIQ score for print is " + nfiqScore, Toast.LENGTH_SHORT);
				}
				catch (IBScanException ibse)
				{
					showToastOnUiThread("Error calculating NBIQ score " + ibse.getType().toString(), Toast.LENGTH_SHORT);
				}
/*				
				try
				{
					String filename_wsq = Environment.getExternalStorageDirectory().getPath() + "/" + "test.wsq";
					int nRc = SimpleScanActivity.this.m_ibScanDevice.wsqEncodeToFile(filename_wsq, image.buffer,image.width , 
										image.height, image.pitch, image.bitsPerPixel, 500, 0.75, "");
					showToastOnUiThread("Saved WSQ image", Toast.LENGTH_SHORT);
				}
				catch (IBScanException ibse)
				{
					showToastOnUiThread("Error save WSQ " + ibse.getType().toString(), Toast.LENGTH_SHORT);
				}
*/
			}
		};
		t.start();
			
		/* Move back to initialized state. */
		transitionToInitialized(null);
	}
		
	/*
	 * Handle transition to communication break.
	 */
	private void handleTransitionToCommunicationBreak()
	{
		/* Sanity check state. */
		switch (this.m_savedData.state)
		{
			case CAPTURING:
			case STOPPING_CAPTURE:
			case INITIALIZED:
				break;
			default:
				Log.e(TAG, "Received unexpected transition to COMMUNICATION_BREAK from " + this.m_savedData.state.toString());
				return;
		}
		
		/* Move to this state. */
		this.m_savedData.state = AppState.COMMUNICATION_BREAK;
		
		/* Setup UI for this state. */
		resetButtonsForState(AppState.COMMUNICATION_BREAK);
		setStatus("comm break");
		setFrameTime(FRAME_TIME_DEFAULT);
		
		/* Transition to closing, then to refresh. */
		transitionToClosing();
	}

	/*
	 * Reset the stop, start, and refresh buttons for the state.
	 */
	private void resetButtonsForState(final AppState state)
	{
		                              /* NO_SCAN, SCANNER, REFRESH, INITIALIZING, INITIALIZED, CLOSING, STARTING, CAPTURING, STOPPING, CAPTURED, BREAK */
		final boolean[] stopStates    = {false,   false,   false,   false,        false,       false,   false,    true,      false,    false,    false};
		final boolean[] startStates   = {false,   false,   false,   false,        true,        false,   false,    false,     false,    false,    false};
		final boolean[] refreshStates = {true,    true,    false,   false,        false,       false,   false,    false,     false,    false,    false};
		final boolean[] captureStates = {false,   false,   false,   false,        true,        false,   false,    false,     false,    false,    false};
		final boolean[] openStates    = {false,   true,    false,   false,        false,       false,   false,    false,     false,    false,    false};
		final boolean[] closeStates   = {false,   false,   false,   false,        true,        false,   false,    false,     false,    false,    false};
		
		final boolean stopButtonClickable     = stopStates[state.ordinal()];
		final boolean startButtonClickable    = startStates[state.ordinal()];
		final boolean refreshButtonClickable  = refreshStates[state.ordinal()];
		final boolean captureSpinnerClickable = captureStates[state.ordinal()];
		final boolean openButtonClickable     = openStates[state.ordinal()];
		final boolean closeButtonClickable    = closeStates[state.ordinal()];
		
		/* Make sure the update occurs from the UI thread. */
		runOnUiThread(new Runnable() 
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_stopCaptureBtn.setEnabled(stopButtonClickable);
				SimpleScanActivity.this.m_stopCaptureBtn.setClickable(stopButtonClickable);
				
				SimpleScanActivity.this.m_startCaptureBtn.setEnabled(startButtonClickable);
				SimpleScanActivity.this.m_startCaptureBtn.setClickable(startButtonClickable);

				SimpleScanActivity.this.m_refreshBtn.setEnabled(refreshButtonClickable);
				SimpleScanActivity.this.m_refreshBtn.setClickable(refreshButtonClickable);		
				
				SimpleScanActivity.this.m_spinnerCaptureType.setEnabled(captureSpinnerClickable);
				SimpleScanActivity.this.m_spinnerCaptureType.setClickable(captureSpinnerClickable);
				
				SimpleScanActivity.this.m_openScannerBtn.setEnabled(openButtonClickable);
				SimpleScanActivity.this.m_openScannerBtn.setClickable(openButtonClickable);

				SimpleScanActivity.this.m_closeScannerBtn.setEnabled(closeButtonClickable);
				SimpleScanActivity.this.m_closeScannerBtn.setClickable(closeButtonClickable);				
			}
		});
	}

	/* *********************************************************************************************
	 * EVENT HANDLERS
	 ******************************************************************************************** */
	
	/* 
	 * Handle click on "Start capture" button.
	 */
	private OnClickListener m_startCaptureBtnClickListener = new OnClickListener() 
	{
		@Override
		public void onClick(final View v) 
		{
			/* Sanity check.  Make sure we are in a proper state. */
			switch (SimpleScanActivity.this.m_savedData.state)
			{
				case INITIALIZED:
					break;	
				default:
					Log.e(TAG, "Received unexpected start button event in state " + SimpleScanActivity.this.m_savedData.state.toString());
					return;
			}		
	
			/* Transition to capturing state. */
			transitionToStartingCapture();
		}
	};
	
	/*
	 * Handle click on "Stop capture" button. 
	 */	
	private OnClickListener m_stopCaptureBtnClickListener = new OnClickListener() 
	{
		@Override
		public void onClick(final View v) 
		{
			/* Sanity check.  Make sure we are in a proper state. */
			switch (SimpleScanActivity.this.m_savedData.state)
			{
				case CAPTURING:
					break;	
				default:
					Log.e(TAG, "Received unexpected stop button event in state " + SimpleScanActivity.this.m_savedData.state.toString());
					return;
			}		
	
			/* Transition to stopping capture state. */
			transitionToStoppingCapture();
		}
	};
	
	/*
	 * Handle click on "Open" button. 
	 */	
	private OnClickListener m_openScannerBtnClickListener = new OnClickListener() 
	{
		@Override
		public void onClick(final View v) 
		{
			/* Sanity check.  Make sure we are in a proper state. */
			switch (SimpleScanActivity.this.m_savedData.state)
			{
				case SCANNER_ATTACHED:
					break;	
				default:
					Log.e(TAG, "Received unexpected open button event in state " + SimpleScanActivity.this.m_savedData.state.toString());
					return;
			}		
			
			devicekojak =false;
	
			/* Transition to initializing state. */
			transitionToInitializing(INITIALIZING_DEVICE_INDEX);
		}
	};
	
	/*
	 * Handle click on "Close" button. 
	 */	
	private OnClickListener m_closeScannerBtnClickListener = new OnClickListener() 
	{
		@Override
		public void onClick(final View v) 
		{
			/* Sanity check.  Make sure we are in a proper state. */
			switch (SimpleScanActivity.this.m_savedData.state)
			{
				case INITIALIZED:
					break;	
				default:
					Log.e(TAG, "Received unexpected close button event in state " + SimpleScanActivity.this.m_savedData.state.toString());
					return;
			}		
	
			/* Transition to closing state. */
			transitionToClosing();
		}
	};
	
	/*
	 * Handle long clicks on the image view.
	 */
	private OnLongClickListener m_imagePreviewImageLongClickListener = new OnLongClickListener()
	{
		/*
		 * When the image view is long-clicked, show a popup menu.
		 */
		@Override
		public boolean onLongClick(final View v) 
		{
			final PopupMenu popup = new PopupMenu(SimpleScanActivity.this, SimpleScanActivity.this.m_txtDesciption);
		    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() 
		    {
		    	/*
		    	 * Handle click on a menu item.
		    	 */
				@Override
				public boolean onMenuItemClick(final MenuItem item) 
				{
			        switch (item.getItemId()) 
			        {
			            case R.id.email_image:
			            	promptForEmail(SimpleScanActivity.this.m_lastImage);
			                return (true);
			            case R.id.enlarge:
			            	showEnlargedImage();
			            	return (true);
			            default:
			            	return (false);
			        }
				}
		    	
		    });
		    
		    final MenuInflater inflater = popup.getMenuInflater();
		    inflater.inflate(R.menu.scanimage_menu, popup.getMenu());
		    popup.show();
		    
			return (true);
		}		
	};

	/*
	 * Handle click on the "Refresh" button
	 */
	private OnClickListener m_refreshBtnClickListener = new OnClickListener() 
	{
		@Override
		public void onClick(final View v) 
		{
			/* Sanity check.  Make sure we are in a proper state. */
			switch (SimpleScanActivity.this.m_savedData.state)
			{
				case NO_SCANNER_ATTACHED:
				case SCANNER_ATTACHED:
					break;		
				default:
					Log.e(TAG, "Received unexpected refresh button event in state " + SimpleScanActivity.this.m_savedData.state.toString());
					return;
			}
			
			/* Transition to refresh state. */
			transitionToRefresh();		
		}
	};

	/*
	 * Handle click on the spinner that determine the scan type.
	 */
	private OnItemSelectedListener m_captureTypeItemSelectedListener = new OnItemSelectedListener()
	{
		@Override
		public void onItemSelected(final AdapterView<?> parent, final View view, final int pos,
				final long id)		
		{
			/* Save capture type for screen orientation change. */
			SimpleScanActivity.this.m_savedData.captureType = pos;
		}
		
		@Override
		public void onNothingSelected(final AdapterView<?> parent)
		{
			SimpleScanActivity.this.m_savedData.captureType = CAPTURE_TYPE_INVALID;
		}
	};
	
	/*
	 * Hide the enlarged dialog, if it exists.
	 */
	private OnClickListener m_enlargedImageClickListener = new OnClickListener() 
	{
		@Override
		public void onClick(final View v) 
		{
			if (SimpleScanActivity.this.m_enlargedDialog != null)
			{
				SimpleScanActivity.this.m_enlargedDialog.cancel();
				SimpleScanActivity.this.m_enlargedDialog = null;
			}
		}	
	};
	
	/* *********************************************************************************************
	 * IBScanListener METHODS
	 ******************************************************************************************** */
	
	@Override
	public void scanDeviceAttached(final int deviceId) 
	{
		showToastOnUiThread("Device " + deviceId + " attached", Toast.LENGTH_SHORT);
		
		/* 
		 * Check whether we have permission to access this device.  Request permission so it will
		 * appear as an IB scanner. 
		 */
		final boolean hasPermission = SimpleScanActivity.this.m_ibScan.hasPermission(deviceId);
		if (!hasPermission)
		{ 
			SimpleScanActivity.this.m_ibScan.requestPermission(deviceId);
		}
	}

	@Override
	public void scanDeviceDetached(final int deviceId) 
	{
		/*
		 * A device has been detached.  We should also receive a scanDeviceCountChanged() callback,
		 * whereupon we can refresh the display.  If our device has detached while scanning, we 
		 * should receive a deviceCommunicationBreak() callback as well.
		 */
		showToastOnUiThread("Device " + deviceId + " detached", Toast.LENGTH_SHORT);
	}

	@Override
	public void scanDevicePermissionGranted(final int deviceId, final boolean granted) 
	{
		if (granted)
		{
			/*
			 * This device should appear as an IB scanner.  We can wait for the scanDeviceCountChanged()
			 * callback to refresh the display.
			 */
			showToastOnUiThread("Permission granted to device " + deviceId, Toast.LENGTH_SHORT);
		}
		else
		{
			showToastOnUiThread("Permission denied to device " + deviceId, Toast.LENGTH_SHORT);
		}
	}	

	@Override
	public void scanDeviceCountChanged(final int deviceCount)
	{
		final String verb   = (deviceCount == 1) ? "is" : "are";
		final String plural = (deviceCount == 1) ? ""   : "s";
		showToastOnUiThread("There " + verb + " now " + deviceCount + " accessible device" + plural, Toast.LENGTH_SHORT);

		/*
		 * The number of recognized accessible scanners has changed.  If there are not zero scanners
		 * and we were not already in the SCANNER_ATTACHED state, let's go there.
		 */
		transitionToRefresh();
	}
	
	@Override
	public void scanDeviceInitProgress(final int deviceIndex, final int progressValue) 
	{
		setStatus("init " + progressValue + "%");
	}

	@Override
	public void scanDeviceOpenComplete(final int deviceIndex, final IBScanDevice device, 
			final IBScanException exception) 
	{
		if (device != null)
		{
			/*
			 * The device has now finished initializing.  We can start capturing an image.
			 */
			showToastOnUiThread("Device " + deviceIndex + " is now initialized", Toast.LENGTH_SHORT);			
			transitionToInitialized(device);
		}
		else
		{
			/*
			 * Initialization failed.  Let's report the error, clean up, and refresh.
			 */
			String error = (exception == null) ? "(unknown)" : exception.getType().toString();
			showToastOnUiThread("Device " + deviceIndex + " could not be initialized with error " + error, Toast.LENGTH_SHORT);			
			transitionToClosing();
		}
	}
	
	/* *********************************************************************************************
	 * IBScanDeviceListener METHODS
	 ******************************************************************************************** */

	@Override
	public void deviceCommunicationBroken(final IBScanDevice device) 
	{
		/*
		 * A communication break occurred with a scanner during capture.  Let's cleanup after the 
		 * break and then refresh.
		 */
		showToastOnUiThread("Communication break with device", Toast.LENGTH_SHORT);	
		transitionToCommunicationBreak();
	}

	@Override
	public void deviceFingerCountChanged(final IBScanDevice device, final FingerCountState fingerState) 
	{
		
		if(OnlyLEFTFOUR ==1)
		{
			
			try {
				if(fingerState == FingerCountState.NON_FINGER)
				{
					//SimpleScanActivity.this.m_ibScanDevice.setLEDs(IBScanDevice.LED_NONE);

				}else
				{
					this.PlayLed(2, true, 1);
					SimpleScanActivity.this.m_ibScanDevice.setLEDs(setLeds);
					

				}
			} catch (IBScanException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			/* TODO: UPDATE DESCRIPTION OF FINGER COUNT */
		}else if(OnlyRIGHTFOUR ==1)
		{
			
			try {
				if(fingerState == FingerCountState.NON_FINGER)
				{
					//SimpleScanActivity.this.m_ibScanDevice.setLEDs(IBScanDevice.LED_NONE);

				}else
				{
					this.PlayLed(2, true, 2);
					SimpleScanActivity.this.m_ibScanDevice.setLEDs(setLeds);

				}
			} catch (IBScanException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		/* TODO: UPDATE DESCRIPTION OF FINGER COUNT */
		}
		else
		{			
		switch (fingerState)
		{
			default:
			case FINGER_COUNT_OK:
				setStatus("capturing");
				break;
			case TOO_MANY_FINGERS:
				setStatus("too many fingers");
				break;
			case TOO_FEW_FINGERS:
				setStatus("too few fingers");
				break;
			case NON_FINGER:
				setStatus("non-finger");
				break;
		}
	}
	}

	@Override
	public void deviceAcquisitionBegun(final IBScanDevice device, final ImageType imageType) 
	{
		if (imageType.equals(ImageType.ROLL_SINGLE_FINGER))
		{
			showToastOnUiThread("Beginning acquisition...roll finger left", Toast.LENGTH_SHORT);
		}
	}

	@Override
	public void deviceAcquisitionCompleted(final IBScanDevice device, final ImageType imageType) 
	{
		if (imageType.equals(ImageType.ROLL_SINGLE_FINGER))
		{
			showToastOnUiThread("Completed acquisition...roll finger right", Toast.LENGTH_SHORT);
		}else
		{
			SimpleScanActivity.this.m_beeper.playSound();
		}
	}

	@Override
	public void deviceFingerQualityChanged(final IBScanDevice device, final FingerQualityState[] fingerQualities) 
	{
		/* Make sure this occurs on the UI thread. */
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				SimpleScanActivity.this.m_savedData.fingerMarkerTop = false;
				SimpleScanActivity.this.m_savedData.fingerMarkerLeft = false;
				SimpleScanActivity.this.m_savedData.fingerMarkerRight = false;
				
				/* Determine colors for each finger in finger qualities array. */
				for (int i = 0; i < fingerQualities.length; i++)
				{
					int color;
					
					switch(fingerQualities[i])
					{
						case INVALID_AREA_TOP:
							SimpleScanActivity.this.m_savedData.fingerMarkerTop = true;
							color = FINGER_QUALITY_POOR_COLOR;
							break;							
						case INVALID_AREA_LEFT:
							SimpleScanActivity.this.m_savedData.fingerMarkerLeft = true;
							color = FINGER_QUALITY_POOR_COLOR;
							break;
						case INVALID_AREA_RIGHT:
							SimpleScanActivity.this.m_savedData.fingerMarkerRight = true;
							color = FINGER_QUALITY_POOR_COLOR;
							break;
							
						default:
						case FINGER_NOT_PRESENT:
							color = FINGER_QUALITY_NOT_PRESENT_COLOR;
							break;
						case GOOD:
							color = FINGER_QUALITY_GOOD_COLOR;
							break;
						case FAIR:
							color = FINGER_QUALITY_FAIR_COLOR;
							break;
						case POOR:
							color = FINGER_QUALITY_POOR_COLOR;
							break;
					}
					/* Sanity check.  Make sure marker for this finger exists. */
					if (i < SimpleScanActivity.this.m_txtFingerQuality.length)
					{
						SimpleScanActivity.this.m_savedData.fingerQualityColors[i] = color;
						SimpleScanActivity.this.m_txtFingerQuality[i].setBackgroundColor(color);
					}
				}
				/* If marker exists for more fingers, color then "not present". */
				for (int i = fingerQualities.length; i < SimpleScanActivity.this.m_txtFingerQuality.length; i++)
				{
					SimpleScanActivity.this.m_savedData.fingerQualityColors[i] = FINGER_QUALITY_NOT_PRESENT_COLOR;
					SimpleScanActivity.this.m_txtFingerQuality[i].setBackgroundColor(FINGER_QUALITY_NOT_PRESENT_COLOR);
				}
			}
		});
	}

	@Override
	public void deviceImagePreviewAvailable(final IBScanDevice device, final ImageData image) 
	{

		try
		{
			/*
			 * Preserve aspect ratio of image while resizing.
			 */
//			final String deviceName = SimpleScanActivity.this.m_ibScanDevice.getProperty(PropertyId.PRODUCT_ID);
			final String deviceName = this.m_savedData.deviceName;
			int dstWidth      = this.m_imagePreviewImage.getWidth();
			int dstHeight     = this.m_imagePreviewImage.getHeight();
			int dstHeightTemp = (dstWidth * image.height) / image.width;
			if (dstHeightTemp > dstHeight)
			{
				dstWidth = (dstHeight * image.width) / image.height;
			}
			else
			{
				dstHeight = dstHeightTemp;
			}
			
			/*
			 * Get scaled image, perhaps with rolling lines displayed.
			 */
			//final Bitmap bitmapScaled;
			
			if (this.m_imageType.equals(ImageType.ROLL_SINGLE_FINGER))
			{
				RollingData rollingData;
				try
				{
					rollingData = this.m_ibScanDevice.getRollingInfo();
				}
				catch (IBScanException ibse)
				{
					rollingData = null;
					Log.e("Simple Scan", "failure getting rolling line " + ibse.getType().toString());
				}
				if (rollingData != null)
				{
					int rollingLineWidth = 4;
					if(deviceName.equals("KOJAK") || deviceName.equals("FIVE-0"))
					{						
						SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapKojakRollImage);
						SimpleScanActivity.this.drawBitmapRollingLine(m_BitmapKojakRollImage,dstWidth, dstHeight, image.width,image.height,rollingData.rollingState, rollingData.rollingLineX, rollingLineWidth);
					}else
					{
						SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapImage);
						SimpleScanActivity.this.drawBitmapRollingLine(m_BitmapImage,dstWidth, dstHeight, image.width,image.height,rollingData.rollingState, rollingData.rollingLineX, rollingLineWidth);
					}
				} 
				else
				{
					if(deviceName.equals("KOJAK")|| deviceName.equals("FIVE-0"))
					{						
						SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapKojakRollImage);
					}else
					{
						SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapImage);
					}
				}
			}
			else
			{
				SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapImage);
			
			}

			if (m_BitmapImage != null || m_BitmapKojakRollImage != null)
			{
				/* Make sure this occurs on UI thread. */
				runOnUiThread(new Runnable() 
				{
					@Override
					public void run()
					{
						SimpleScanActivity.this.setFrameTime(String.format("%1$.3f", image.frameTime));
						
						if (SimpleScanActivity.this.m_imageType.equals(ImageType.ROLL_SINGLE_FINGER) && (deviceName.equals("KOJAK") || deviceName.equals("FIVE-0")) )
						{
							
							SimpleScanActivity.this.m_savedData.imageBitmap = m_BitmapKojakRollImage;
							SimpleScanActivity.this.m_imagePreviewImage.setImageBitmap(m_BitmapKojakRollImage);
						}else
						{
							
							SimpleScanActivity.this.m_savedData.imageBitmap = m_BitmapImage;
							SimpleScanActivity.this.m_imagePreviewImage.setImageBitmap(m_BitmapImage);
						}
					}
				});
			}
		}
	    catch(IllegalArgumentException ae)
	    {
			Log.e("Simple Scan", "failure gettin Exception line ");
		}catch (IBScanException e) {
			Log.e("Simple Scan",e.getMessage());
		}
	}

	@Override
	public void deviceImageResultAvailable(final IBScanDevice device, final ImageData image, 
			final ImageType imageType, final ImageData[] splitImageArray) 
	{
		/* TODO: ALTERNATIVELY, USE RESULTS IN THIS FUNCTION */
	}

	@Override
    public void deviceImageResultExtendedAvailable(IBScanDevice device, IBScanException imageStatus,
    		final ImageData image, final ImageType imageType, final int detectedFingerCount, 
    		final ImageData[] segmentImageArray, final SegmentPosition[] segmentPositionArray)
    {
		/*
		 * Preserve aspect ratio of image while resizing.
		 */
		final String deviceName = this.m_savedData.deviceName;
		int dstWidth      = this.m_imagePreviewImage.getWidth();
		int dstHeight     = this.m_imagePreviewImage.getHeight();
		int dstHeightTemp = (dstWidth * image.height) / image.width;
		if (dstHeightTemp > dstHeight)
		{
			dstWidth = (dstHeight * image.width) / image.height;
		}
		else
		{
			dstHeight = dstHeightTemp;
		}
		
		/*
		 * Display image result.
		 */
		try
		{
			if (this.m_imageType.equals(ImageType.ROLL_SINGLE_FINGER))
			{
				if(deviceName.equals("KOJAK")|| deviceName.equals("FIVE-0"))
				{						
					SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapKojakRollImage);
				}
				else
				{
					SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapImage);
				}
			}
			else
			{
				SimpleScanActivity.this.m_ibScanDevice.createBmpEx(image.buffer, m_BitmapImage);
		
			}

			if (m_BitmapImage != null || m_BitmapKojakRollImage != null)
		{
			/* Make sure this occurs on UI thread. */
			runOnUiThread(new Runnable() 
			{
				@Override
				public void run()
				{
						if (SimpleScanActivity.this.m_imageType.equals(ImageType.ROLL_SINGLE_FINGER) && (deviceName.equals("KOJAK") || deviceName.equals("FIVE-0")) )
					{
						SimpleScanActivity.this.m_beeper.playSound();
							SimpleScanActivity.this.m_savedData.imageBitmap = m_BitmapKojakRollImage;
							SimpleScanActivity.this.m_imagePreviewImage.setImageBitmap(m_BitmapKojakRollImage);
						}
						else
						{
						
							SimpleScanActivity.this.m_savedData.imageBitmap = m_BitmapImage;
							SimpleScanActivity.this.m_imagePreviewImage.setImageBitmap(m_BitmapImage);
					}
					SimpleScanActivity.this.setFrameTime(String.format("%1$.3f", image.frameTime));
				}
			});
			}
		}
	    catch(IllegalArgumentException ae)
	    {
			Log.e("Simple Scan", "failure gettin Exception line ");
		}catch (IBScanException e) {
			Log.e("Simple Scan",e.getMessage());
		}

		/*
		 * Finish out the image acquisition and retain result so that the user can view a larger 
		 * version of the image later.
		 */
		if (imageStatus != null)
		{
			/* 
			 * If an image status is returned, then there was an error during image acquisition.
			 */
			showToastOnUiThread("Image capture ended with error: " + imageStatus.getType().toString(), Toast.LENGTH_SHORT);			
		}
		else
		{
			showToastOnUiThread("Image result available", Toast.LENGTH_SHORT);
		}
		transitionToImageCaptured(image, imageType, segmentImageArray);    	
	
    }
	
	@Override
	public void devicePlatenStateChanged(final IBScanDevice device, final PlatenState platenState) 
	{
		/* TODO: REPORT EVENT */
	}

	@Override
	public void deviceWarningReceived(final IBScanDevice device, final IBScanException warning) 
	{
		showToastOnUiThread("Warning received " + warning.getType().toString(), Toast.LENGTH_SHORT);
	}
	@Override
	public void devicePressedKeyButtons(IBScanDevice device,int pressedKeyButtons)
    {
		showToastOnUiThread("PressedKeyButtons ", Toast.LENGTH_SHORT);
	}
	
	public void drawBitmapRollingLine(Bitmap bitmapScaled, int dstWidth, int dstHeight, int imageW,int imageH,
			RollingState rollingState, int rollingLineX, int rollingLineWidth)
	{
		if ((rollingState.equals(RollingState.TAKE_ACQUISITION) || rollingState.equals(RollingState.COMPLETE_ACQUISITION)) && (rollingLineX >= 0))
		{
			int targetLineX     = (rollingLineX * imageW) / imageW;
			int targetLineColor = (rollingState.equals(RollingState.TAKE_ACQUISITION)) ? Color.RED : Color.GREEN;
			
			for (int y = 0; y < imageH; y++) 
			{
				for (int x = targetLineX - (rollingLineWidth / 2); x < targetLineX - (rollingLineWidth / 2) + rollingLineWidth; x++) 
				{
					if ((x >= 0) && (x < imageW))
					{
						bitmapScaled.setPixel(x, y, targetLineColor);
					}
				}
			}
		}
	}
	public Bitmap toDrawBitmap(int width,int height)
    {
		final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		if (bitmap != null)
		{
        	final byte[] imageBuffer = new byte[width * height * 4];
        	/* 
        	 * The image in the buffer is flipped vertically from what the Bitmap class expects; 
        	 * we will flip it to compensate while moving it into the buffer. 
        	 */
    		for (int y = 0; y < height; y++) 
    		{
    			for (int x = 0; x < width; x++) 
    			{
    				imageBuffer[(y * width + x) * 4] = 
    						imageBuffer[(y * width + x) * 4 + 1] = 
    								imageBuffer[(y * width + x) * 4 + 2] = 
    										(byte) 128;
    				imageBuffer[(y * width + x) * 4 + 3] = (byte)255;
    			}
    		}        	
    		bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(imageBuffer));
		}
		return (bitmap);
    }	
	
	public void PlayLed(int ledColor, boolean bBlink ,int imageTypeOnlyFOur) 
	{
		setLeds =0;
		
		if(bBlink)
		{
			if(ledColor == 0)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_BLINK_GREEN;
			}else if(ledColor == 1)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_BLINK_RED;
			}else if(ledColor == 2)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_BLINK_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_BLINK_RED;
			}
		}
		if(ledColor == 0)
		{
			//GREEN
			if(imageTypeOnlyFOur ==1)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_PROGRESS_LEFT_HAND;
				//LEFT_FOUR
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_INDEX_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_MIDDLE_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_RING_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_LITTLE_GREEN;
				
			}
			if(imageTypeOnlyFOur ==2)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_PROGRESS_RIGHT_HAND;
				//Right_FOUR
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_INDEX_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_MIDDLE_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_RING_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_LITTLE_GREEN;
				
			}
		}else if(ledColor == 1)
		{
			//RED
			if(imageTypeOnlyFOur ==1)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_PROGRESS_LEFT_HAND;
				//LEFT_FOUR
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_INDEX_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_MIDDLE_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_RING_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_LITTLE_RED;
				
			}
			if(imageTypeOnlyFOur ==2)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_PROGRESS_RIGHT_HAND;
				//Right_FOUR
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_INDEX_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_MIDDLE_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_RING_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_LITTLE_RED;
				
			}
		}else if(ledColor == 2)
		{
			//RED
			if(imageTypeOnlyFOur ==1)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_PROGRESS_LEFT_HAND;
				//LEFT_FOUR
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_INDEX_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_MIDDLE_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_RING_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_LITTLE_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_INDEX_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_MIDDLE_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_RING_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_LEFT_LITTLE_RED;
				
			}
			if(imageTypeOnlyFOur ==2)
			{
				setLeds |=IBScanDevice.IBSU_LED_F_PROGRESS_RIGHT_HAND;
				//Right_FOUR
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_INDEX_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_MIDDLE_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_RING_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_LITTLE_GREEN;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_INDEX_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_MIDDLE_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_RING_RED;
				setLeds |=IBScanDevice.IBSU_LED_F_RIGHT_LITTLE_RED;
				
			}
		}
	}
}
