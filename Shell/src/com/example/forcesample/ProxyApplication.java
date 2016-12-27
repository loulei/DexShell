package com.example.forcesample;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Build;
import android.util.ArrayMap;
import dalvik.system.DexClassLoader;

public class ProxyApplication extends Application {

	private String apkFilename;
	private String odexPath;
	private String libPath;
	
	private AssetManager mAssetManager;
	private Resources mResources;
	private Theme mTheme;
	
	@SuppressLint("NewApi")
	@Override
	protected void attachBaseContext(Context base) {
		// TODO Auto-generated method stub
		super.attachBaseContext(base);
		try {
			File odex = getDir("payload_odex", 0);
			File libs = getDir("payload_lib", 0);
			odexPath = odex.getAbsolutePath();
			libPath = libs.getAbsolutePath();
			apkFilename = odex.getAbsolutePath() + "/payload.apk";
			File dexFile = new File(apkFilename);
			if(!dexFile.exists()){
				dexFile.createNewFile();
				byte[] dexData = readDexFileFromApk();
				splitPayloadFromDex(dexData);
			}
			
			Class<?> activityThreadClazz = getClassLoader().loadClass("android.app.ActivityThread");
			Method currentActivityThreadMd = activityThreadClazz.getDeclaredMethod("currentActivityThread");
			Object currentActivityThread = currentActivityThreadMd.invoke(null);
			String packageName = getPackageName();
			Field field = activityThreadClazz.getDeclaredField("mPackages");
			field.setAccessible(true);
			Object loadedApk = null;
			int code = Build.VERSION.SDK_INT;
			if(code <= 19){
				HashMap<String, WeakReference<?>> mPackages = (HashMap<String, WeakReference<?>>) field.get(currentActivityThread);
				WeakReference<?> wr = mPackages.get(packageName);
				loadedApk = wr.get();
			}else{
				ArrayMap<String, WeakReference<?>> mPackages = (ArrayMap<String, WeakReference<?>>) field.get(currentActivityThread);
				WeakReference<?> wr = mPackages.get(packageName);
				loadedApk = wr.get();
			}
			Class<?> loadedApkClazz = getClassLoader().loadClass("android.app.LoadedApk");
			Field classloaderField = loadedApkClazz.getDeclaredField("mClassLoader");
			classloaderField.setAccessible(true);
			System.out.println("getClassLoader:"+getClassLoader().hashCode() + " class:"+getClassLoader().getClass().getName());
			System.out.println("LoadedApk.mClassLoader:"+classloaderField.get(loadedApk).hashCode() + " class:"+classloaderField.get(loadedApk).getClass().getName());
//			12-27 10:25:22.187: I/System.out(25608): getClassLoader:139861031 class:dalvik.system.PathClassLoader
//			12-27 10:25:22.187: I/System.out(25608): LoadedApk.mClassLoader:139861031 class:dalvik.system.PathClassLoader
			
//			DexClassLoader dexClassLoader = new DexClassLoader(apkFilename, odexPath, libPath, (ClassLoader) classloaderField.get(loadedApk));
			DexClassLoader dexClassLoader = new DexClassLoader(apkFilename, odexPath, libPath, getClassLoader());
			classloaderField.set(loadedApk, dexClassLoader);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
//		loadResources(apkFilename);
	}
	
	private byte[] readDexFileFromApk() throws Exception{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ZipInputStream inputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(getApplicationInfo().sourceDir)));
		while(true){
			ZipEntry zipEntry = inputStream.getNextEntry();
			if(zipEntry == null){
				inputStream.close();
				break;
			}
			if(zipEntry.getName().equals("classes.dex")){
				byte[] buffer = new byte[1024];
				while(true){
					int len = inputStream.read(buffer);
					if(len == -1){
						break;
					}
					bos.write(buffer, 0, len);
				}
			}
			inputStream.closeEntry();
		}
		inputStream.close();
		return bos.toByteArray();
	}
	
	public static int byteToInt(byte[] b) {
		int s = 0;
		int s0 = b[0] & 0xff;// 最低位
		int s1 = b[1] & 0xff;
		int s2 = b[2] & 0xff;
		int s3 = b[3] & 0xff;
		s3 <<= 24;
		s2 <<= 16;
		s1 <<= 8;
		s = s0 | s1 | s2 | s3;
		return s;
	}
	
	private void splitPayloadFromDex(byte[] apkData) throws Exception{
		int apklen = apkData.length;
		byte[] dexlen = new byte[4];
		System.arraycopy(apkData, apklen-4, dexlen, 0, 4);
		int readInt = byteToInt(dexlen);
		System.out.println("len:"+readInt);
		byte[] newDex = new byte[readInt];
		System.arraycopy(apkData, apklen-4-readInt, newDex, 0, readInt);
		newDex = decrypt(newDex);
		File file = new File(apkFilename);
		
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(newDex);
		fos.close();
		
		ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
		while(true){
			ZipEntry zipEntry = zipInputStream.getNextEntry();
			if(zipEntry == null){
				zipInputStream.close();
				break;
			}
			String name = zipEntry.getName();
			if(name.startsWith("lib/") && name.endsWith(".so")){
				File storeFile = new File(libPath + File.separator + name.substring(name.lastIndexOf(File.separator)));
				storeFile.createNewFile();
				FileOutputStream os = new FileOutputStream(storeFile);
				byte[] buffer = new byte[1024];
				while(true){
					int len = zipInputStream.read(buffer);
					if(len == -1){
						break;
					}
					os.write(buffer, 0, len);
				}
				os.flush();
				os.close();
			}
			zipInputStream.closeEntry();
		}
		zipInputStream.close();
	}
	
	private byte[] decrypt(byte[] srcData){
		for(int i=0; i<srcData.length; i++){
			srcData[i] = (byte) (0xFF ^ srcData[i]);
		}
		return srcData;
	}
	
	private void loadResources(String dexpath){
		try {
			AssetManager assetManager = AssetManager.class.newInstance();
			Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
			addAssetPath.invoke(assetManager, dexpath);
			mAssetManager = assetManager;
			
			Resources resources = super.getResources();
			mResources = new Resources(mAssetManager, resources.getDisplayMetrics(), resources.getConfiguration());
			mTheme = mResources.newTheme();
			mTheme.setTo(super.getTheme());
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public AssetManager getAssets() {
		// TODO Auto-generated method stub
		return mAssetManager == null ? super.getAssets() : mAssetManager;
	}
	
	@Override
	public Resources getResources() {
		// TODO Auto-generated method stub
		return mResources == null ? super.getResources() : mResources;
	}
	
	@Override
	public Theme getTheme() {
		// TODO Auto-generated method stub
		return mTheme == null ? super.getTheme() : mTheme;
	}
}































