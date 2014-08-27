package com.sckftr.android.securephoto.processor;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.sckftr.android.securephoto.AppConst;
import com.sckftr.android.securephoto.helper.UserHelper;
import com.sckftr.android.utils.IO;
import com.sckftr.android.utils.Procedure;
import com.sckftr.android.utils.Storage;
import com.sckftr.android.utils.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import androidkeystore.android.security.KeyStoreManager;

public class Cryptograph {

    private static final String TAG = Cryptograph.class.getSimpleName();

    // TODO add user hash
    public static boolean encrypt(Context ctx, Uri source, String key) {

        if (ctx == null || source == null)
            throw new IllegalArgumentException(TAG + ": Encryption is impossible. Bad source!!");

        if (Strings.isEmpty(key))
            throw new IllegalArgumentException(TAG + ": Encryption is impossible: Illegal key!!");

        key += UserHelper.getUserHash();

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
//            is = ctx.getContentResolver().openInputStream(source);

            fis = new FileInputStream(source.getPath());

            byte[] buffer = new byte[fis.available()];

            fis.read(buffer);

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKeySpec(key));

            Uri secureUri = Storage.Images.getPrivateUri(source);//todo storage images

            File file = new File(secureUri.getPath());

            fos = new FileOutputStream(file);

            fos.write(cipher.doFinal(buffer));

            Log.d("Encode", "SecurePath = " + secureUri.getPath());

        } catch (IOException e) {
            AppConst.Log.e(TAG, "Encrypt", e);
            return false;
        } catch (IllegalBlockSizeException e) {
            AppConst.Log.e(TAG, "Encrypt: ", e);
            return false;
        } catch (InvalidKeyException e) {
            AppConst.Log.e(TAG, "Encrypt: ", e);
            return false;
        } catch (BadPaddingException e) {
            AppConst.Log.e(TAG, "Encrypt: ", e);
            return false;
        } catch (NoSuchAlgorithmException e) {
            AppConst.Log.e(TAG, "Encrypt: ", e);
            return false;
        } catch (NoSuchPaddingException e) {
            AppConst.Log.e(TAG, "Encrypt: ", e);
            return false;
        } catch (NoSuchProviderException e) {
            AppConst.Log.e(TAG, "Encrypt: ", e);
            return false;
        } finally {
            IO.close(fis);
            IO.close(fos);
        }

        return true;
    }

    // TODO add user hash
    public static byte[] decrypt(byte[] encodedBytes, String key) {

        if (encodedBytes == null || encodedBytes.length <= 0)
            throw new IllegalArgumentException(TAG + ": Decryption is impossible: Illegal source");

        if (Strings.isEmpty(key))
            throw new IllegalArgumentException(TAG + ": Decryption is impossible: Illegal key");

        key += UserHelper.getUserHash();

        try {

            Cipher cipher = Cipher.getInstance("AES");

            cipher.init(Cipher.DECRYPT_MODE, getSecretKeySpec(key));

            return cipher.doFinal(encodedBytes);

        } catch (IllegalBlockSizeException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        } catch (InvalidKeyException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        } catch (BadPaddingException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        } catch (NoSuchAlgorithmException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        } catch (NoSuchPaddingException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        } catch (NoSuchProviderException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        } catch (UnsupportedEncodingException e) {
            AppConst.Log.e(TAG, "Decryption: ", e);
        }

        return null;
    }

    private static SecretKeySpec getSecretKeySpec(String key) throws NoSuchProviderException, NoSuchAlgorithmException, UnsupportedEncodingException {

        byte[] keyStart = key.getBytes("UTF-8");

        KeyGenerator generator = KeyGenerator.getInstance("AES");

        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "Crypto");

        sr.setSeed(keyStart);

        generator.init(128, sr);

        SecretKey skey = generator.generateKey();

        return new SecretKeySpec(skey.getEncoded(), "AES");

    }

    public void storeKey(final String alias, final String key) {
        KeyStoreManager.put(alias, key, new Procedure<String>() {
            @Override
            public void apply(String result) {
                if (result != null && result.equals(KeyStoreManager.ERROR_LOCKED)) {

                    AppConst.API.get().putPreference(alias, key);

                }
            }
        });

    }

    public void getKey(final String alias, final Procedure<String> resultCallback) {

        KeyStoreManager.get(alias, new Procedure<String>() {
            @Override
            public void apply(String result) {

                if (result != null && result.equals(KeyStoreManager.ERROR_LOCKED)) {

                    resultCallback.apply(AppConst.API.get().getPreferenceString(alias, null));
                    return;

                }

                resultCallback.apply(result);

            }
        });
    }

    public void deleteKey(String alias) {

        KeyStoreManager.deleteEntries();

        AppConst.API.get().putPreference(alias, null);
    }

}