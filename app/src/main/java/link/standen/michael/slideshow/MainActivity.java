package link.standen.michael.slideshow;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;

import link.standen.michael.slideshow.adapter.FileItemArrayAdapter;
import link.standen.michael.slideshow.model.FileItem;
import link.standen.michael.slideshow.model.FileItemViewHolder;
import link.standen.michael.slideshow.util.FileItemHelper;

/**
 * Slideshow main activity.
 */
public class MainActivity extends BaseActivity {

	private static final String TAG = MainActivity.class.getName();

	private String rootLocation;

	private static final String LIST_STATE = "listState";
	private Parcelable listState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Check if the user has granted the WRITE_SETTINGS permission
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (Settings.System.canWrite(this)) {
				// Set the screen brightness to maximum (255)
				setScreenBrightness(255);
			} else {
				// Request permission if it's not granted
				startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
			}
		}
		setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		// Get path
		rootLocation = getRootLocation();

		// Path is external absolute directory
		if (currentPath == null) {
			currentPath = getWhatsAppFolder(this);
		}
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (preferences.getBoolean("remember_location", false)){
			// Override using remembered location
			currentPath = preferences.getString("remembered_location", currentPath);
			if (preferences.getBoolean("auto_start", false)){
				// Start the slideshow automatically
				startSlideshowAt(currentPath, preferences.getString("remembered_image", null), true);
				return;
			}
		}
		if (getIntent().hasExtra("path")){
			// Override using passed value
			currentPath = getIntent().getStringExtra("path");
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

		showChangeLog(false);

		// Permission check
		if (isStoragePermissionGranted()){
			updateListView();
		}
		// else wait for permission handler to continue
	}

	@Override
	protected void onResume(){
		super.onResume();
		// Update the root location in case preferences changed
		rootLocation = getRootLocation();
		if (!currentPath.contains(rootLocation)){
			// Changed from root to non-root preference while in an upper directory. Reset
			currentPath = rootLocation;
			updateListView();
		}
		// Restore the list view scroll location
		if (listState != null) {
			((ListView) findViewById(android.R.id.list)).onRestoreInstanceState(listState);
		}
		listState = null;
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState){
		super.onRestoreInstanceState(savedInstanceState);
		listState = savedInstanceState.getParcelable(LIST_STATE);
	}

	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		listState = ((ListView) findViewById(android.R.id.list)).onSaveInstanceState();
		outState.putParcelable(LIST_STATE, listState);
	}

	private void updateListView(){
		fileList = new FileItemHelper(this).getFileList(currentPath);
		FileItemHelper fileItemHelper = new FileItemHelper(this);

		if (currentPath.equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
			// Put a star on special folders
			String[] specialPaths = new String[]{
					Environment.DIRECTORY_DCIM,
					Environment.DIRECTORY_PICTURES
			};
			for (String path : specialPaths) {
				FileItem specialItem = fileItemHelper.createFileItem(
						Environment.getExternalStoragePublicDirectory(path));
				int index = fileList.indexOf(specialItem);
				if (index != -1) {
					fileList.get(index).setIsSpecial();
				}
			}
		}

		// Set title
		this.setTitle(currentPath.replace(rootLocation, "") + File.separatorChar);
		if (!new File(currentPath).canRead()){
			this.setTitle(String.format("%s %s",
					getTitle(),
					getResources().getString(R.string.inaccessible)));

			// Add Go Home item
			fileList.clear();
			fileList.add(fileItemHelper.createGoHomeFileItem());
		} else if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("play_from_here", false)) {
			fileList.add(0, fileItemHelper.createPlayFileItem());
		}

		ListView listView = findViewById(android.R.id.list);
		listView.setAdapter(new FileItemArrayAdapter(this, fileList));
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				FileItem fileItem = ((FileItemViewHolder) view.getTag()).getFileItem();
				if (fileItem.getIsDirectory()) {
					currentPath = fileItem.getPath();
					updateListView();
				} else if (fileItem.getIsSpecial() || new FileItemHelper(MainActivity.this).isImage(fileItem)){
					// Only open images
					startSlideshowAt(currentPath, fileItem.getPath(), false);
				}
			}
		});
	}

	// Method to change screen brightness
	private void setScreenBrightness(int brightness) {
		// Ensure the brightness value is within the range of 0 to 255
		if (brightness < 0) brightness = 0;
		if (brightness > 255) brightness = 255;

		ContentResolver contentResolver = getContentResolver();
		Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
	}
	/**
	 * Begin a slideshow at the given point
	 * @param folderPath The folder location
	 * @param filePath The file path
	 */
	private void startSlideshowAt(String folderPath, String filePath, boolean autoStart){
		Log.i(TAG, String.format("Calling slideshow at %s %s", folderPath, filePath));
		Intent intent = new Intent(MainActivity.this, ImageActivity.class);
		intent.putExtra("currentPath", folderPath);
		intent.putExtra("imagePath", filePath);
		intent.putExtra("autoStart", autoStart);
		this.startActivity(intent);
	}
	/**
	 * Get the root location, considering the preferences.
	 * @return The root location
	 * @see #getRootLocation()
	 */
	private static String getWhatsAppFolder(Context context) {
		// Lista de posibles rutas
		File[] possiblePaths = {
				new File(Environment.getExternalStorageDirectory(), "WhatsApp"), // Almacenamiento interno
				new File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp") // Android 10+
		};

		// En Android 11+, buscar en directorios públicos
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			possiblePaths = new File[]{
					new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "WhatsApp"),
					new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WhatsApp"),
					new File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp")
			};
		}

		// Buscar en almacenamiento externo (tarjeta SD)
		File[] externalDirs = context.getExternalFilesDirs(null);
		for (File dir : externalDirs) {
			if (dir != null) {
				File sdWhatsApp = new File(dir.getParentFile().getParentFile().getParentFile(), "WhatsApp");
				if (sdWhatsApp.exists()) {
					return sdWhatsApp.getAbsolutePath();
				}
			}
		}

		// Verificar rutas en almacenamiento interno
		for (File path : possiblePaths) {
			if (path.exists()) {
				return path.getAbsolutePath();
			}
		}

		return null; // No se encontró la carpeta de WhatsApp
	}

	/**
	 * Goes up a directory, unless at the top, then exits
	 */
	@Override
	public void onBackPressed(){
		if (currentPath.equals(rootLocation)) {
			super.onBackPressed();
		} else {
			currentPath = currentPath.substring(0, currentPath.lastIndexOf(File.separatorChar));
			updateListView();
		}
	}

	/**
	 * Permissions checker
	 */
	private boolean isStoragePermissionGranted() {
		if (Build.VERSION.SDK_INT >= 23) {
			if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
				Log.v(TAG,"Permission is granted");
				return true;
			} else {
				Log.v(TAG,"Permission is revoked");
				requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
				return false;
			}
		} else {
			// Permission is automatically granted on sdk<23 upon installation
			Log.v(TAG,"Permission is granted");
			return true;
		}
	}

	/**
	 * Permissions handler
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (permissions.length > 0) {
			Log.v(TAG, "Permission: " + permissions[0] + " was " + grantResults[0]);
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				updateListView();
			}
		}
	}

}
