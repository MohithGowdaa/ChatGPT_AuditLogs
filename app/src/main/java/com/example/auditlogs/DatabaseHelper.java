package com.example.auditlogs;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "AuditLogs.db";
    private static final String ENCRYPTION_KEY = "your_encryption_key_here";
    private final String TAG = DatabaseHelper.class.getSimpleName();

    public DatabaseHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        SQLiteDatabase.loadLibs(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE AuditPrompt (SequenceNumber INTEGER PRIMARY KEY AUTOINCREMENT, DateTime TEXT NOT NULL, Prompt TEXT NOT NULL CHECK(length(Prompt) <= 1024))");
        db.execSQL("CREATE TABLE Responses (SequenceNumber INTEGER PRIMARY KEY AUTOINCREMENT, DateTime TEXT NOT NULL, Response TEXT NOT NULL CHECK(length(Response) <= 4096))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    @Nullable
    private String encrypt(String input) {
        try {
            SecretKey secretKey = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Encryption error", e);
            return null;
        }
    }

    @Nullable
    private String decrypt(String input) {
        try {
            SecretKey secretKey = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.decode(input, Base64.DEFAULT));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption error", e);
            return null;
        }
    }

    public void insertAuditPrompt(String dateTime, @NonNull String prompt) {
        SQLiteDatabase db = this.getWritableDatabase(ENCRYPTION_KEY);
        ContentValues values = new ContentValues();
        values.put("DateTime", dateTime);
        String encryptedPrompt = encrypt(prompt.substring(0, Math.min(prompt.length(), 1024)));
        if (encryptedPrompt != null) {
            values.put("Prompt", encryptedPrompt);
            db.insert("AuditPrompt", null, values);
        }
    }

    public void insertResponse(String dateTime, @NonNull String response) {
        SQLiteDatabase db = this.getWritableDatabase(ENCRYPTION_KEY);
        ContentValues values = new ContentValues();
        values.put("DateTime", dateTime);
        String encryptedResponse = encrypt(response.substring(0, Math.min(response.length(), 4096)));
        if (encryptedResponse != null) {
            values.put("Response", encryptedResponse);
            db.insert("Responses", null, values);
        }
    }

    public List<String> getAllAuditPrompts() {
        List<String> prompts = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase(ENCRYPTION_KEY);
        Cursor cursor = db.rawQuery("SELECT * FROM AuditPrompt", null);
        while (cursor.moveToNext()) {
            String dateTime = cursor.getString(cursor.getColumnIndex("DateTime"));
            String prompt = decrypt(cursor.getString(cursor.getColumnIndex("Prompt")));
            String readableDateTime = convertDateStringFormat(dateTime);
            if (prompt != null) {
                prompts.add(readableDateTime + ": " + prompt);
            }
        }
        cursor.close();
        return prompts;
    }

    public List<String> getAllResponses() {
        List<String> responses = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase(ENCRYPTION_KEY);
        Cursor cursor = db.rawQuery("SELECT * FROM Responses", null);
        while (cursor.moveToNext()) {
            String dateTime = cursor.getString(cursor.getColumnIndex("DateTime"));
            String response = decrypt(cursor.getString(cursor.getColumnIndex("Response")));
            String readableDateTime = convertDateStringFormat(dateTime);
            if (response != null) {
                responses.add(readableDateTime + ": " + response);
            }
        }
        cursor.close();
        return responses;
    }

    private String convertDateStringFormat(String input) {
        SimpleDateFormat desiredFormatter = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        try {
            long timestamp = Long.parseLong(input);
            Date date = new Date(timestamp);
            return desiredFormatter.format(date);
        } catch (NumberFormatException e) {
            SimpleDateFormat currentFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            try {
                Date date = currentFormatter.parse(input);
                if (date != null) {
                    return desiredFormatter.format(date);
                }
            } catch (ParseException parseException) {
                Log.e(TAG, "Date format conversion error for input: " + input, parseException);
            }
        }
        return input;
    }
}
