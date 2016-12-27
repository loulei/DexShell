package com.example.shell;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Force {

	public static void main(String[] args) throws Exception {
		File payloadSrcFile = new File("file/ForceSample.apk");
		System.out.println("apk size : " + payloadSrcFile.length());
		File unShellDexFile = new File("file/classes.dex");
		byte[] payloadArray = encrypt(readFileBytes(payloadSrcFile));
		byte[] unShellDexArray = readFileBytes(unShellDexFile);
		int payloadLen = payloadArray.length;
		int unshellDexLen = unShellDexArray.length;
		int totalLen = payloadLen + unshellDexLen + 4;
		System.out.println("total size : " + totalLen);
		byte[] newDex = new byte[totalLen];
		System.arraycopy(unShellDexArray, 0, newDex, 0, unshellDexLen);
		System.arraycopy(payloadArray, 0, newDex, unshellDexLen, payloadLen);
		System.arraycopy(Utils.intToByte(payloadLen), 0, newDex, totalLen-4, 4);
		fixFileSizeHeader(newDex);
		fixSha1Header(newDex);
		fixCheckSumHeader(newDex);
		
		String str = "file/force.dex";
		File file = new File(str);
		if(!file.exists()){
			file.createNewFile();
		}
		
		FileOutputStream fos = new FileOutputStream(str);
		fos.write(newDex);
		fos.flush();
		fos.close();
		if(unShellDexFile.delete()){
			file.renameTo(unShellDexFile);
		}
		System.out.println("force complete");
	}
	
	private static byte[] encrypt(byte[] srcData){
		for(int i=0; i<srcData.length; i++){
			srcData[i] = (byte) (0xFF ^ srcData[i]);
		}
		return srcData;
	}
	
	private static byte[] readFileBytes(File file) throws Exception{
		byte[] buffer = new byte[1024];
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		FileInputStream fis = new FileInputStream(file);
		int len = 0;
		while((len=fis.read(buffer)) != -1){
			bos.write(buffer, 0, len);
		}
		bos.flush();
		byte[] data = bos.toByteArray();
		bos.close();
		fis.close();
		return data;
	}
	
	private static void fixFileSizeHeader(byte[] dexBytes){
		byte[] newfs = Utils.intToByte(dexBytes.length);
		System.arraycopy(newfs, 0, dexBytes, 32, 4);
	}
	
	private static void fixSha1Header(byte[] dexBytes){
		byte[] signatureBytes = Utils.doCheckSha1(dexBytes, 32);
		System.arraycopy(signatureBytes, 0, dexBytes, 12, 20);
	}
	
	private static void fixCheckSumHeader(byte[] dexBytes){
		byte[] checkSumBytes = Utils.doCheckSumAlder32(dexBytes, 12);
		System.arraycopy(checkSumBytes, 0, dexBytes, 8, 4);
	}
}




































