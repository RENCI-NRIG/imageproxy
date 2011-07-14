package orca.imageproxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Util {
	
	public static String getFileHash(String filename) throws NoSuchAlgorithmException, IOException {
        byte[] b = createFileHash(filename, "SHA-1");
        return asHex(b);
    }
	
	public static byte[] createFileHash(String filename, String method) throws NoSuchAlgorithmException, IOException {
        InputStream fis =  new FileInputStream(filename);
        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance(method);
        int numRead = 0;
        while (numRead != -1) {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        }
        fis.close();
        return complete.digest();
    }
    
    public static String asHex(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result +=
            Integer.toString(( b[i] & 0xff ) + 0x100, 16).substring(1);
        }
        return result;
    }
}
