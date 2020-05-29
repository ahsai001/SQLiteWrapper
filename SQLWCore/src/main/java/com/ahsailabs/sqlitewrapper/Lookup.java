package com.ahsailabs.sqlitewrapper;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.Gson;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by ahmad s on 2019-06-30.
 */
public class Lookup {
    private static final int KEY_SIZE = 256;
    // requires Spongycastle crypto libraries
    // private static final String AES_KEY_ALG = "AES/GCM/NoPadding";
    // private static final String AES_KEY_ALG = "AES/CBC/PKCS5Padding";
    private static final String AES_KEY_ALG = "AES";
    private static final String PRIMARY_PBE_KEY_ALG = "PBKDF2WithHmacSHA1";
    private static final String BACKUP_PBE_KEY_ALG = "PBEWithMD5AndDES";
    private static final int ITERATIONS = 2000;
    // change to SC if using Spongycastle crypto libraries
    private static final String PROVIDER = "BC";
    private static byte[] sKey;

    private static boolean isSecureEnabled = false;
    private static SQLiteWrapper sqLiteWrapper;

    public static void init(Context context){
        init(context, false);
    }

    public static void init(Context context, boolean isSecureEnabled){
        if(sqLiteWrapper == null) {
            sqLiteWrapper = SQLiteWrapper.getLookupDatabase(context);
        }

        // Initialize encryption/decryption key
        Lookup.isSecureEnabled = false;
        if(isSecureEnabled) {
            try {
                final String key = generateAesKeyName(context);
                String value = get(key, null);
                if (value == null) {
                    value = generateAesKeyValue();
                    Lookup.set(key, value);
                }
                sKey = decode(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Lookup.isSecureEnabled = isSecureEnabled;
    }



    private static String encode(byte[] input) {
        return Base64.encodeToString(input, Base64.NO_PADDING | Base64.NO_WRAP);
    }
    private static byte[] decode(String input) {
        return Base64.decode(input, Base64.NO_PADDING | Base64.NO_WRAP);
    }
    private static String generateAesKeyName(Context context)
            throws InvalidKeySpecException, NoSuchAlgorithmException,
            NoSuchProviderException {
        final char[] password = context.getPackageName().toCharArray();
        final byte[] salt = getDeviceSerialNumber(context).getBytes();
        SecretKey key;
        try {
            // TODO: what if there's an OS upgrade and now supports the primary
            // PBE
            key = generatePBEKey(password, salt,
                    PRIMARY_PBE_KEY_ALG, ITERATIONS, KEY_SIZE);
        } catch (NoSuchAlgorithmException e) {
            // older devices may not support the have the implementation try
            // with a weaker
            // algorthm
            key = generatePBEKey(password, salt,
                    BACKUP_PBE_KEY_ALG, ITERATIONS, KEY_SIZE);
        }
        return encode(key.getEncoded());
    }
    /**
     * Derive a secure key based on the passphraseOrPin
     *
     * @param passphraseOrPin
     * @param salt
     * @param algorthm
     * - which PBE algorthm to use. some <4.0 devices don;t support
     * the prefered PBKDF2WithHmacSHA1
     * @param iterations
     * - Number of PBKDF2 hardening rounds to use. Larger values
     * increase computation time (a good thing), defaults to 1000 if
     * not set.
     * @param keyLength
     * @return Derived Secretkey
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchProviderException
     */
    private static SecretKey generatePBEKey(char[] passphraseOrPin,
                                            byte[] salt, String algorthm, int iterations, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException,
            NoSuchProviderException {
        if (iterations == 0) {
            iterations = 1000;
        }
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(
                algorthm, PROVIDER);
        KeySpec keySpec = new PBEKeySpec(passphraseOrPin, salt, iterations,
                keyLength);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        return secretKey;
    }
    /**
     * Gets the hardware serial number of this device.
     *
     * @return serial number or Settings.Secure.ANDROID_ID if not available.
     */
    private static String getDeviceSerialNumber(Context context) {
        // We're using the Reflection API because Build.SERIAL is only available
        // since API Level 9 (Gingerbread, Android 2.3).
        try {
            String deviceSerial = (String) Build.class.getField("SERIAL").get(
                    null);
            if (TextUtils.isEmpty(deviceSerial)) {
                deviceSerial = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ANDROID_ID);
            }
            return deviceSerial;
        } catch (Exception ignored) {
            // default to Android_ID
            return Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        }
    }
    private static String generateAesKeyValue() throws NoSuchAlgorithmException {
        // Do *not* seed secureRandom! Automatically seeded from system entropy
        final SecureRandom random = new SecureRandom();
        // Use the largest AES key length which is supported by the OS
        final KeyGenerator generator = KeyGenerator.getInstance("AES");
        try {
            generator.init(KEY_SIZE, random);
        } catch (Exception e) {
            try {
                generator.init(192, random);
            } catch (Exception e1) {
                generator.init(128, random);
            }
        }
        return encode(generator.generateKey().getEncoded());
    }
    private static String encrypt(String cleartext) {
        if (cleartext == null || cleartext.length() == 0) {
            return cleartext;
        }
        try {
            final Cipher cipher = Cipher.getInstance(AES_KEY_ALG, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(
                    sKey, AES_KEY_ALG));
            return encode(cipher.doFinal(cleartext
                    .getBytes("UTF-8")));
        } catch (Exception e) {
            return null;
        }
    }
    private static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.length() == 0) {
            return ciphertext;
        }
        try {
            final Cipher cipher = Cipher.getInstance(AES_KEY_ALG, PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(
                    sKey, AES_KEY_ALG));
            return new String(cipher.doFinal(decode(ciphertext)), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static void checkCondition(){
        if(sqLiteWrapper == null){
            throw new IllegalStateException("you need to run init method first, you can put it inside oncreate of Application");
        }
    }


    private static void checkSecureCondition(){
        if(!isSecureEnabled){
            throw new IllegalStateException("you need to run init method first with parameter isSecuredEnabled true, you can put it inside oncreate of Application");
        }
    }



    //string
    public static String get(String key, String defaultValue){
        return get(key, defaultValue, false);
    }

    public static String getS(String key, String defaultValue){
        return get(key, defaultValue, true);
    }

    public static void set(String key, String value){
        set(key,value, false);
    }

    public static void setS(String key, String value){
        set(key,value, true);
    }

    private static String get(String key, String defaultValue, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        return lookup==null?defaultValue:(isSecureEnabled ?decrypt(lookup.getString()):lookup.getString());
    }


    private static void set(String key, String value, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            lookup.setString(isSecureEnabled ?encrypt(value):value);
            lookup.update();
        } else {
            lookup = new SQLiteWrapper.TLookup();
            lookup.setKey(key);
            lookup.setString(isSecureEnabled ?encrypt(value):value);
            lookup.save();
        }
    }


    //boolean
    public static boolean get(String key, boolean defaultValue){
        return get(key, defaultValue, false);
    }

    public static boolean getS(String key, boolean defaultValue){
        return get(key, defaultValue, true);
    }

    public static void set(String key, boolean value){
        set(key,value, false);
    }

    public static void setS(String key, boolean value){
        set(key,value, true);
    }

    private static boolean get(String key, boolean defaultValue, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        return lookup==null?defaultValue:(isSecureEnabled ?Boolean.parseBoolean(decrypt(lookup.getString())):lookup.getBoolean());
    }

    private static void set(String key, boolean value, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            if(isSecureEnabled){
                lookup.setString(encrypt(Boolean.toString(value)));
            } else {
                lookup.setBoolean(value);
            }
            lookup.update();
        } else {
            lookup = new SQLiteWrapper.TLookup();
            lookup.setKey(key);
            if(isSecureEnabled){
                lookup.setString(encrypt(Boolean.toString(value)));
            } else {
                lookup.setBoolean(value);
            }
            lookup.save();
        }
    }


    //int
    public static int get(String key, int defaultValue){
        return get(key, defaultValue, false);
    }

    public static int getS(String key, int defaultValue){
        return get(key, defaultValue, true);
    }

    public static void set(String key, int value){
        set(key,value, false);
    }

    public static void setS(String key, int value){
        set(key,value, true);
    }

    private static int get(String key, int defaultValue, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        return lookup==null?defaultValue:(isSecureEnabled ?Integer.parseInt(decrypt(lookup.getString())):lookup.getInt());
    }

    private static void set(String key, int value, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            if(isSecureEnabled){
                lookup.setString(encrypt(Integer.toString(value)));
            } else {
                lookup.setInt(value);
            }
            lookup.update();
        } else {
            lookup = new SQLiteWrapper.TLookup();
            lookup.setKey(key);
            if(isSecureEnabled){
                lookup.setString(encrypt(Integer.toString(value)));
            } else {
                lookup.setInt(value);
            }
            lookup.save();
        }
    }


    //long
    public static long get(String key, long defaultValue){
        return get(key, defaultValue, false);
    }

    public static long getS(String key, long defaultValue){
        return get(key, defaultValue, true);
    }

    public static void set(String key, long value){
        set(key,value, false);
    }

    public static void setS(String key, long value){
        set(key,value, true);
    }

    private static long get(String key, long defaultValue, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        return lookup==null?defaultValue:(isSecureEnabled ?Long.parseLong(decrypt(lookup.getString())):lookup.getLong());
    }

    private static void set(String key, long value, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            if(isSecureEnabled){
                lookup.setString(encrypt(Long.toString(value)));
            } else {
                lookup.setLong(value);
            }
            lookup.update();
        } else {
            lookup = new SQLiteWrapper.TLookup();
            lookup.setKey(key);
            if(isSecureEnabled){
                lookup.setString(encrypt(Long.toString(value)));
            } else {
                lookup.setLong(value);
            }
            lookup.save();
        }
    }


    //float
    public static float get(String key, float defaultValue){
        return get(key, defaultValue, false);
    }

    public static float getS(String key, float defaultValue){
        return get(key, defaultValue, true);
    }

    public static void set(String key, float value){
        set(key,value, false);
    }

    public static void setS(String key, float value){
        set(key,value, true);
    }

    private static float get(String key, float defaultValue, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        return lookup==null?defaultValue:(isSecureEnabled ?Float.parseFloat(decrypt(lookup.getString())):lookup.getFloat());
    }

    private static void set(String key, float value, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            if(isSecureEnabled){
                lookup.setString(encrypt(Float.toString(value)));
            } else {
                lookup.setFloat(value);
            }
            lookup.update();
        } else {
            lookup = new SQLiteWrapper.TLookup();
            lookup.setKey(key);
            if(isSecureEnabled){
                lookup.setString(encrypt(Float.toString(value)));
            } else {
                lookup.setFloat(value);
            }
            lookup.save();
        }
    }


    //double
    public static double get(String key, double defaultValue){
        return get(key, defaultValue, false);
    }

    public static double getS(String key, double defaultValue){
        return get(key, defaultValue, true);
    }

    public static void set(String key, double value){
        set(key,value, false);
    }

    public static void setS(String key, double value){
        set(key,value, true);
    }

    private static double get(String key, double defaultValue, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        return lookup==null?defaultValue:(isSecureEnabled ?Double.parseDouble(decrypt(lookup.getString())):lookup.getDouble());
    }

    private static void set(String key, double value, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }

        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            if(isSecureEnabled){
                lookup.setString(encrypt(Double.toString(value)));
            } else {
                lookup.setDouble(value);
            }
            lookup.update();
        } else {
            lookup = new SQLiteWrapper.TLookup();
            lookup.setKey(key);
            if(isSecureEnabled){
                lookup.setString(encrypt(Double.toString(value)));
            } else {
                lookup.setDouble(value);
            }
            lookup.save();
        }
    }



    public static void remove(String key){
        remove(key, false);
    }

    public static void removeS(String key){
        remove(key, true);
    }

    private static void remove(String key, boolean isSecureEnabled){
        checkCondition();
        if(isSecureEnabled){
            checkSecureCondition();
            key = encrypt(key);
        }
        SQLiteWrapper.TLookup lookup = sqLiteWrapper.findFirstWithCriteria(null, SQLiteWrapper.TLookup.class,
                "key=?", new String[]{key});
        if(lookup != null){
            lookup.delete();
        }
    }

    public static void dump(Object src){
        dump("", src);
    }

    public static void dump(String key, Object src){
        Gson gson = new Gson();
        Lookup.setS(key+"_dumped_"+src.getClass().getSimpleName(), gson.toJson(src));
    }

    public static <T> T getDumped(Class<T> type){
        return getDumped("", type);
    }

    public static <T> T getDumped(String key, Class<T> type){
        Gson gson = new Gson();
        return gson.fromJson(Lookup.getS(key+"_dumped_"+type.getSimpleName(), null), type);
    }



}
