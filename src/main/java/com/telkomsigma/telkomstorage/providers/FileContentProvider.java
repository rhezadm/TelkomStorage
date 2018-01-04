/*
 * ownCloud Android client application
 *
 * @author Bartek Przybylski
 * @author David A. Velasco
 * @author masensio
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2016 ownCloud Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.telkomsigma.telkomstorage.providers;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.telkomsigma.telkomstorage.MainApp;
import com.telkomsigma.telkomstorage.R;
import com.telkomsigma.telkomstorage.datamodel.OCFile;
import com.telkomsigma.telkomstorage.db.ProviderMeta;
import com.owncloud.android.lib.common.accounts.AccountUtils;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.shares.ShareType;
import com.telkomsigma.telkomstorage.utils.FileStorageUtils;
import com.telkomsigma.telkomstorage.utils.MimeType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * The ContentProvider for the ownCloud App.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class FileContentProvider extends ContentProvider {

    private DataBaseHelper mDbHelper;
    private Context mContext;

    private static final int SINGLE_FILE = 1;
    private static final int DIRECTORY = 2;
    private static final int ROOT_DIRECTORY = 3;
    private static final int SHARES = 4;
    private static final int CAPABILITIES = 5;
    private static final int UPLOADS = 6;
    private static final int SYNCED_FOLDERS = 7;
    private static final int EXTERNAL_LINKS = 8;
    private static final int ARBITRARY_DATA = 9;
    private static final int VIRTUAL = 10;
    private static final int FILESYSTEM = 11;

    private static final String TAG = FileContentProvider.class.getSimpleName();

    private UriMatcher mUriMatcher;
    // todo avoid string concatenation and use string formatting instead later.
    private static final String ERROR = "ERROR ";
    private static final String SQL = "SQL";
    private static final String INTEGER = " INTEGER, ";
    private static final String TEXT = " TEXT, ";
    private static final String ALTER_TABLE = "ALTER TABLE ";
    private static final String ADD_COLUMN = " ADD COLUMN ";
    private static final String UPGRADE_VERSION_MSG = "OUT of the ADD in onUpgrade; oldVersion == %d, newVersion == %d";

    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        int count;

        if (isCallerNotAllowed()) {
            return -1;
        }
                
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            count = delete(db, uri, where, whereArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mContext.getContentResolver().notifyChange(uri, null);
        return count;
    }

    private int delete(SQLiteDatabase db, Uri uri, String where, String[] whereArgs) {
        if (isCallerNotAllowed()) {
            return -1;
        }
        
        int count = 0;
        switch (mUriMatcher.match(uri)) {
            case SINGLE_FILE:
                Cursor c = query(db, uri, null, where, whereArgs, null);
                String remoteId = "";
                try {
                    if (c != null && c.moveToFirst()) {
                        remoteId = c.getString(c.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID));
                        //ThumbnailsCacheManager.removeFileFromCache(remoteId);
                    }
                    Log_OC.d(TAG, "Removing FILE " + remoteId);

                    count = db.delete(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta._ID
                                    + "="
                                    + uri.getPathSegments().get(1)
                                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ")" : ""),
                            whereArgs);
                } catch (Exception e) {
                    Log_OC.d(TAG, "DB-Error removing file!", e);
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                break;
            case DIRECTORY:
                // deletion of folder is recursive
            /*
            Uri folderUri = ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_FILE, Long.parseLong(uri.getPathSegments().get(1)));
            Cursor folder = query(db, folderUri, null, null, null, null);
            String folderName = "(unknown)";
            if (folder != null && folder.moveToFirst()) {
                folderName = folder.getString(folder.getColumnIndex(ProviderTableMeta.FILE_PATH));
            }
            */
                Cursor children = query(uri, null, null, null, null);
                if (children != null && children.moveToFirst()) {
                    long childId;
                    boolean isDir;
                    while (!children.isAfterLast()) {
                        childId = children.getLong(children.getColumnIndex(ProviderMeta.ProviderTableMeta._ID));
                        isDir = MimeType.DIRECTORY.equals(children.getString(
                                children.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_CONTENT_TYPE)
                        ));
                        //remotePath = children.getString(children.getColumnIndex(ProviderTableMeta.FILE_PATH));
                        if (isDir) {
                            count += delete(
                                    db,
                                    ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_DIR, childId),
                                    null,
                                    null
                            );
                        } else {
                            count += delete(
                                    db,
                                    ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_FILE, childId),
                                    null,
                                    null
                            );
                        }
                        children.moveToNext();
                    }
                    children.close();
                } /*else {
                Log_OC.d(TAG, "No child to remove in DIRECTORY " + folderName);
            }
            Log_OC.d(TAG, "Removing DIRECTORY " + folderName + " (or maybe not) ");
            */
                count += db.delete(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                        ProviderMeta.ProviderTableMeta._ID
                                + "="
                                + uri.getPathSegments().get(1)
                                + (!TextUtils.isEmpty(where) ? " AND (" + where
                                + ")" : ""), whereArgs);
            /* Just for log
             if (folder != null) {
                folder.close();
            }*/
                break;
            case ROOT_DIRECTORY:
                //Log_OC.d(TAG, "Removing ROOT!");
                count = db.delete(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, where, whereArgs);
                break;
            case SHARES:
                count = db.delete(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME, where, whereArgs);
                break;
            case CAPABILITIES:
                count = db.delete(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME, where, whereArgs);
                break;
            case UPLOADS:
                count = db.delete(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME, where, whereArgs);
                break;
            case SYNCED_FOLDERS:
                count = db.delete(ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME, where, whereArgs);
                break;
            case EXTERNAL_LINKS:
                count = db.delete(ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_TABLE_NAME, where, whereArgs);
                break;
            case ARBITRARY_DATA:
                count = db.delete(ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_TABLE_NAME, where, whereArgs);
                break;
            case VIRTUAL:
                count = db.delete(ProviderMeta.ProviderTableMeta.VIRTUAL_TABLE_NAME, where, whereArgs);
                break;
            case FILESYSTEM:
                count = db.delete(ProviderMeta.ProviderTableMeta.FILESYSTEM_TABLE_NAME, where, whereArgs);
                break;
            default:
                //Log_OC.e(TAG, "Unknown uri " + uri);
                throw new IllegalArgumentException("Unknown uri: " + uri.toString());
        }
        return count;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
                return ProviderMeta.ProviderTableMeta.CONTENT_TYPE;
            case SINGLE_FILE:
                return ProviderMeta.ProviderTableMeta.CONTENT_TYPE_ITEM;
            default:
                throw new IllegalArgumentException("Unknown Uri id."
                        + uri.toString());
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        if (isCallerNotAllowed()) {
            return null;
        }

        Uri newUri;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            newUri = insert(db, uri, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mContext.getContentResolver().notifyChange(newUri, null);
        return newUri;
    }

    private Uri insert(SQLiteDatabase db, Uri uri, ContentValues values) {
        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
            case SINGLE_FILE:
                String remotePath = values.getAsString(ProviderMeta.ProviderTableMeta.FILE_PATH);
                String accountName = values.getAsString(ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER);
                String[] projection = new String[]{
                        ProviderMeta.ProviderTableMeta._ID, ProviderMeta.ProviderTableMeta.FILE_PATH,
                        ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER
                };
                String where = ProviderMeta.ProviderTableMeta.FILE_PATH + "=? AND " +
                        ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?";
                String[] whereArgs = new String[]{remotePath, accountName};
                Cursor doubleCheck = query(db, uri, projection, where, whereArgs, null);
                // ugly patch; serious refactorization is needed to reduce work in
                // FileDataStorageManager and bring it to FileContentProvider
                if (doubleCheck == null || !doubleCheck.moveToFirst()) {
                    if (doubleCheck != null) {
                        doubleCheck.close();
                    }
                    long rowId = db.insert(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, null, values);
                    if (rowId > 0) {
                        return ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_FILE, rowId);
                    } else {
                        throw new SQLException(ERROR + uri);
                    }
                } else {
                    // file is already inserted; race condition, let's avoid a duplicated entry
                    Uri insertedFileUri = ContentUris.withAppendedId(
                            ProviderMeta.ProviderTableMeta.CONTENT_URI_FILE,
                            doubleCheck.getLong(doubleCheck.getColumnIndex(ProviderMeta.ProviderTableMeta._ID))
                    );
                    doubleCheck.close();

                    return insertedFileUri;
                }

            case SHARES:
                Uri insertedShareUri;
                long rowId = db.insert(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME, null, values);
                if (rowId > 0) {
                    insertedShareUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_SHARE, rowId);
                } else {
                    throw new SQLException(ERROR + uri);

                }
                updateFilesTableAccordingToShareInsertion(db, values);
                return insertedShareUri;

            case CAPABILITIES:
                Uri insertedCapUri;
                long id = db.insert(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME, null, values);
                if (id > 0) {
                    insertedCapUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_CAPABILITIES, id);
                } else {
                    throw new SQLException(ERROR + uri);

                }
                return insertedCapUri;

            case UPLOADS:
                Uri insertedUploadUri;
                long uploadId = db.insert(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME, null, values);
                if (uploadId > 0) {
                    insertedUploadUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_UPLOADS, uploadId);
                } else {
                    throw new SQLException(ERROR + uri);

                }
                return insertedUploadUri;

            case SYNCED_FOLDERS:
                Uri insertedSyncedFolderUri;
                long syncedFolderId = db.insert(ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME, null, values);
                if (syncedFolderId > 0) {
                    insertedSyncedFolderUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_SYNCED_FOLDERS, syncedFolderId);
                } else {
                    throw new SQLException("ERROR " + uri);

                }
                return insertedSyncedFolderUri;

            case EXTERNAL_LINKS:
                Uri insertedExternalLinkUri;
                long externalLinkId = db.insert(ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_TABLE_NAME, null, values);
                if (externalLinkId > 0) {
                    insertedExternalLinkUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_EXTERNAL_LINKS, externalLinkId);
                } else {
                    throw new SQLException("ERROR " + uri);

                }
                return insertedExternalLinkUri;

            case ARBITRARY_DATA:
                Uri insertedArbitraryDataUri;
                long arbitraryDataId = db.insert(ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_TABLE_NAME, null, values);
                if (arbitraryDataId > 0) {
                    insertedArbitraryDataUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_ARBITRARY_DATA, arbitraryDataId);
                } else {
                    throw new SQLException("ERROR " + uri);

                }
                return insertedArbitraryDataUri;
            case VIRTUAL:
                Uri insertedVirtualUri;
                long virtualId = db.insert(ProviderMeta.ProviderTableMeta.VIRTUAL_TABLE_NAME, null, values);

                if (virtualId > 0) {
                    insertedVirtualUri = ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_VIRTUAL, virtualId);
                } else {
                    throw new SQLException("ERROR " + uri);
                }

                return insertedVirtualUri;
            case FILESYSTEM:
                Uri insertedFilesystemUri;
                long filesystemId = db.insert(ProviderMeta.ProviderTableMeta.FILESYSTEM_TABLE_NAME, null, values);
                if (filesystemId > 0) {
                    insertedFilesystemUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_FILESYSTEM, filesystemId);
                } else {
                    throw new SQLException("ERROR " + uri);
                }
                return insertedFilesystemUri;
            default:
                throw new IllegalArgumentException("Unknown uri id: " + uri);
        }

    }

    private void updateFilesTableAccordingToShareInsertion(
            SQLiteDatabase db, ContentValues newShare
    ) {
        ContentValues fileValues = new ContentValues();
        int newShareType = newShare.getAsInteger(ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_TYPE);
        if (newShareType == ShareType.PUBLIC_LINK.getValue()) {
            fileValues.put(ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK, 1);
        } else if (
                newShareType == ShareType.USER.getValue() ||
                        newShareType == ShareType.GROUP.getValue() ||
                        newShareType == ShareType.EMAIL.getValue() ||
                        newShareType == ShareType.FEDERATED.getValue()) {
            fileValues.put(ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE, 1);
        }

        String where = ProviderMeta.ProviderTableMeta.FILE_PATH + "=? AND " +
                ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?";
        String[] whereArgs = new String[]{
                newShare.getAsString(ProviderMeta.ProviderTableMeta.OCSHARES_PATH),
                newShare.getAsString(ProviderMeta.ProviderTableMeta.OCSHARES_ACCOUNT_OWNER)
        };
        db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, fileValues, where, whereArgs);
    }


    @Override
    public boolean onCreate() {
        mDbHelper = new DataBaseHelper(getContext());
        mContext = getContext();

        if (mContext == null) {
            return false;
        }

        String authority = mContext.getResources().getString(R.string.authority);
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(authority, null, ROOT_DIRECTORY);
        mUriMatcher.addURI(authority, "file/", SINGLE_FILE);
        mUriMatcher.addURI(authority, "file/#", SINGLE_FILE);
        mUriMatcher.addURI(authority, "dir/", DIRECTORY);
        mUriMatcher.addURI(authority, "dir/#", DIRECTORY);
        mUriMatcher.addURI(authority, "shares/", SHARES);
        mUriMatcher.addURI(authority, "shares/#", SHARES);
        mUriMatcher.addURI(authority, "capabilities/", CAPABILITIES);
        mUriMatcher.addURI(authority, "capabilities/#", CAPABILITIES);
        mUriMatcher.addURI(authority, "uploads/", UPLOADS);
        mUriMatcher.addURI(authority, "uploads/#", UPLOADS);
        mUriMatcher.addURI(authority, "synced_folders", SYNCED_FOLDERS);
        mUriMatcher.addURI(authority, "external_links", EXTERNAL_LINKS);
        mUriMatcher.addURI(authority, "arbitrary_data", ARBITRARY_DATA);
        mUriMatcher.addURI(authority, "virtual", VIRTUAL);
        mUriMatcher.addURI(authority, "filesystem", FILESYSTEM);

        return true;
    }


    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        // skip check for files as they need to be queried to get access via document provider
        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
            case SINGLE_FILE:
            case DIRECTORY:
                break;

            default:
                if (isCallerNotAllowed()) {
                    return null;
                }
        }


        Cursor result;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        db.beginTransaction();
        try {
            result = query(db, uri, projection, selection, selectionArgs, sortOrder);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return result;
    }

    private Cursor query(
            SQLiteDatabase db,
            Uri uri,
            String[] projectionArray,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {

        SQLiteQueryBuilder sqlQuery = new SQLiteQueryBuilder();

        sqlQuery.setTables(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME);

        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
                break;
            case DIRECTORY:
                String folderId = uri.getPathSegments().get(1);
                sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta.FILE_PARENT + "="
                        + folderId);
                break;
            case SINGLE_FILE:
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case SHARES:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case CAPABILITIES:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case UPLOADS:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case SYNCED_FOLDERS:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case EXTERNAL_LINKS:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case ARBITRARY_DATA:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            case VIRTUAL:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.VIRTUAL_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "=" + uri.getPathSegments().get(1));
                }
                break;
            case FILESYSTEM:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.FILESYSTEM_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown uri id: " + uri);
        }

        String order;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (mUriMatcher.match(uri)) {
                case SHARES:
                    order = ProviderMeta.ProviderTableMeta.OCSHARES_DEFAULT_SORT_ORDER;
                    break;
                case CAPABILITIES:
                    order = ProviderMeta.ProviderTableMeta.CAPABILITIES_DEFAULT_SORT_ORDER;
                    break;
                case UPLOADS:
                    order = ProviderMeta.ProviderTableMeta.UPLOADS_DEFAULT_SORT_ORDER;
                    break;
                case SYNCED_FOLDERS:
                    order = ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_LOCAL_PATH;
                    break;
                case EXTERNAL_LINKS:
                    order = ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_NAME;
                    break;
                case ARBITRARY_DATA:
                    order = ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_CLOUD_ID;
                    break;
                case VIRTUAL:
                    order = ProviderMeta.ProviderTableMeta.VIRTUAL_TYPE;
                    break;
                default: // Files
                    order = ProviderMeta.ProviderTableMeta.FILE_DEFAULT_SORT_ORDER;
                    break;
                case FILESYSTEM:
                    order = ProviderMeta.ProviderTableMeta.FILESYSTEM_FILE_LOCAL_PATH;
                    break;
            }
        } else {
            order = sortOrder;
        }

        // DB case_sensitive
        db.execSQL("PRAGMA case_sensitive_like = true");

        // only file list is accessible via content provider, so only this has to be protected with projectionMap
        if (mUriMatcher.match(uri) == ROOT_DIRECTORY && projectionArray != null) {
            HashMap<String, String> projectionMap = new HashMap<>();

            for (String projection : ProviderMeta.ProviderTableMeta.FILE_ALL_COLUMNS) {
                projectionMap.put(projection, projection);
            }

            sqlQuery.setProjectionMap(projectionMap);
        }

        // if both are null, let them pass to query
        if (selectionArgs == null && selection != null) {
            selectionArgs = new String[]{selection};
            selection = "(?)";
        }

        sqlQuery.setStrict(true);
        Cursor c = sqlQuery.query(db, projectionArray, selection, selectionArgs, null, null, order);
        c.setNotificationUri(mContext.getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        if (isCallerNotAllowed()) {
            return -1;
        }

        int count;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            count = update(db, uri, values, selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mContext.getContentResolver().notifyChange(uri, null);
        return count;
    }


    private int update(
            SQLiteDatabase db,
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs
    ) {
        switch (mUriMatcher.match(uri)) {
            case DIRECTORY:
                return 0; //updateFolderSize(db, selectionArgs[0]);
            case SHARES:
                return db.update(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME, values, selection, selectionArgs);
            case CAPABILITIES:
                return db.update(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME, values, selection, selectionArgs);
            case UPLOADS:
                return db.update(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME, values, selection, selectionArgs);
            case SYNCED_FOLDERS:
                return db.update(ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME, values, selection, selectionArgs);
            case ARBITRARY_DATA:
                return db.update(ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_TABLE_NAME, values, selection, selectionArgs);
            case FILESYSTEM:
                return db.update(ProviderMeta.ProviderTableMeta.FILESYSTEM_TABLE_NAME, values, selection, selectionArgs);
            default:
                return db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, values, selection, selectionArgs);
        }
    }

    @NonNull
    @Override
    public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        Log_OC.d("FileContentProvider", "applying batch in provider " + this +
                " (temporary: " + isTemporary() + ")");
        ContentProviderResult[] results = new ContentProviderResult[operations.size()];
        int i = 0;

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();  // it's supposed that transactions can be nested
        try {
            for (ContentProviderOperation operation : operations) {
                results[i] = operation.apply(this, results, i);
                i++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Log_OC.d("FileContentProvider", "applied batch in provider " + this);
        return results;
    }


    class DataBaseHelper extends SQLiteOpenHelper {

        DataBaseHelper(Context context) {
            super(context, ProviderMeta.DB_NAME, null, ProviderMeta.DB_VERSION);

        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // files table
            Log_OC.i(SQL, "Entering in onCreate");
            createFilesTable(db);

            // Create OCShares table
            createOCSharesTable(db);

            // Create capabilities table
            createCapabilitiesTable(db);

            // Create uploads table
            createUploadsTable(db);

            // Create synced folders table
            createSyncedFoldersTable(db);

            // Create external links table
            createExternalLinksTable(db);

            // Create arbitrary data table
            createArbitraryData(db);

            // Create virtual table
            createVirtualTable(db);

            // Create filesystem table
            createFileSystemTable(db);

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log_OC.i(SQL, "Entering in onUpgrade");
            boolean upgraded = false;
            if (oldVersion == 1 && newVersion >= 2) {
                Log_OC.i(SQL, "Entering in the #2 ADD in onUpgrade");
                db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                        ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_KEEP_IN_SYNC + " INTEGER " +
                        " DEFAULT 0");
                upgraded = true;
            }
            if (oldVersion < 3 && newVersion >= 3) {
                Log_OC.i(SQL, "Entering in the #3 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA +
                            " INTEGER " + " DEFAULT 0");

                    // assume there are not local changes pending to upload
                    db.execSQL("UPDATE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " SET " + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA + " = "
                            + System.currentTimeMillis() +
                            " WHERE " + ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 4 && newVersion >= 4) {
                Log_OC.i(SQL, "Entering in the #4 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA +
                            " INTEGER " + " DEFAULT 0");

                    db.execSQL("UPDATE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " SET " + ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA + " = " +
                            ProviderMeta.ProviderTableMeta.FILE_MODIFIED +
                            " WHERE " + ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 5 && newVersion >= 5) {
                Log_OC.i(SQL, "Entering in the #5 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_ETAG + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 6 && newVersion >= 6) {
                Log_OC.i(SQL, "Entering in the #6 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK + " INTEGER " +
                            " DEFAULT 0");

                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_PUBLIC_LINK + " TEXT " +
                            " DEFAULT NULL");

                    // Create table OCShares
                    createOCSharesTable(db);

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 7 && newVersion >= 7) {
                Log_OC.i(SQL, "Entering in the #7 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_PERMISSIONS + " TEXT " +
                            " DEFAULT NULL");

                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 8 && newVersion >= 8) {
                Log_OC.i(SQL, "Entering in the #8 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_UPDATE_THUMBNAIL + " INTEGER " +
                            " DEFAULT 0");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 9 && newVersion >= 9) {
                Log_OC.i(SQL, "Entering in the #9 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_IS_DOWNLOADING + " INTEGER " +
                            " DEFAULT 0");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 10 && newVersion >= 10) {
                Log_OC.i(SQL, "Entering in the #10 ADD in onUpgrade");
                updateAccountName(db);
                upgraded = true;
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 11 && newVersion >= 11) {
                Log_OC.i(SQL, "Entering in the #11 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_ETAG_IN_CONFLICT + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 12 && newVersion >= 12) {
                Log_OC.i(SQL, "Entering in the #12 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE + " INTEGER " +
                            " DEFAULT 0");
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 13 && newVersion >= 13) {
                Log_OC.i(SQL, "Entering in the #13 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    // Create capabilities table
                    createCapabilitiesTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (oldVersion < 14 && newVersion >= 14) {
                Log_OC.i(SQL, "Entering in the #14 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    // drop old instant_upload table
                    db.execSQL("DROP TABLE IF EXISTS " + "instant_upload" + ";");
                    // Create uploads table
                    createUploadsTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (oldVersion < 15 && newVersion >= 15) {
                Log_OC.i(SQL, "Entering in the #15 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    // drop old capabilities table
                    db.execSQL("DROP TABLE IF EXISTS " + "capabilities" + ";");
                    // Create uploads table
                    createCapabilitiesTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (oldVersion < 16 && newVersion >= 16) {
                Log_OC.i(SQL, "Entering in the #16 ADD synced folders table");
                db.beginTransaction();
                try {
                    // Create synced folders table
                    createSyncedFoldersTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 17 && newVersion >= 17) {
                Log_OC.i(SQL, "Entering in the #17 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILE_FAVORITE +
                            " INTEGER " + " DEFAULT 0");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 18 && newVersion >= 18) {
                Log_OC.i(SQL, "Entering in the #18 Adding external link column to capabilities");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_EXTERNAL_LINKS +
                            " INTEGER " + " DEFAULT -1");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 19 && newVersion >= 19) {
                Log_OC.i(SQL, "Entering in the #19 Adding external link column to capabilities");
                db.beginTransaction();
                try {
                    createExternalLinksTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 20 && newVersion >= 20) {
                Log_OC.i(SQL, "Entering in the #20 Adding arbitrary data table");
                db.beginTransaction();
                try {
                    createArbitraryData(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 21 && newVersion >= 21) {
                Log_OC.i(SQL, "Entering in the #21 Adding virtual table");
                db.beginTransaction();
                try {
                    createVirtualTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 22 && newVersion >= 22) {
                Log_OC.i(SQL, "Entering in the #22 Adding user theming to capabilities table");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_NAME + " TEXT ");
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_COLOR + " TEXT ");
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_BACKGROUND_URL + " TEXT ");
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_SLOGAN + " TEXT ");
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 23 && newVersion >= 23) {
                Log_OC.i(SQL, "Entering in the #23 adding type column for synced folders, Create filesystem table");
                db.beginTransaction();
                try {
                    // add type column default being CUSTOM (0)
                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_TYPE)) {
                        Log_OC.i(SQL, "Add type column and default value 0 (CUSTOM) to synced_folders table");
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_TYPE +
                                " INTEGER " + " DEFAULT 0");
                    } else {
                        Log_OC.i(SQL, "Type column of synced_folders table already exists");
                    }

                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.UPLOADS_IS_WIFI_ONLY)) {
                        Log_OC.i(SQL, "Add charging and wifi columns to uploads");
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.UPLOADS_IS_WIFI_ONLY +
                                " INTEGER " + " DEFAULT 0");
                    } else {
                        Log_OC.i(SQL, "Wifi column of uploads table already exists");
                    }

                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.UPLOADS_IS_WHILE_CHARGING_ONLY)) {
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.UPLOADS_IS_WHILE_CHARGING_ONLY +
                                " INTEGER " + " DEFAULT 0");
                    } else {
                        Log_OC.i(SQL, "Charging column of uploads table already exists");
                    }

                    // create Filesystem table
                    Log_OC.i(SQL, "Create filesystem table");
                    createFileSystemTable(db);

                    upgraded = true;
                    db.setTransactionSuccessful();

                } catch (Throwable t) {
                    Log_OC.e(TAG, "ERROR!", t);
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 24 && newVersion >= 24) {
                Log_OC.i(SQL, "Entering in the #24 Re-adding user theming to capabilities table");
                db.beginTransaction();
                try {
                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_NAME)) {
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_NAME + " TEXT ");
                    }

                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_COLOR)) {
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_COLOR + " TEXT ");
                    }

                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_BACKGROUND_URL)) {
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_BACKGROUND_URL + " TEXT ");
                    }

                    if (!checkIfColumnExists(db, ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME,
                            ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_SLOGAN)) {
                        db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                                ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_SLOGAN + " TEXT ");
                    }

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }

            if (oldVersion < 25 && newVersion >= 25) {
                Log_OC.i(SQL, "Entering in the #25 Adding text and element color to capabilities");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_TEXT_COLOR + " TEXT ");

                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_ELEMENT_COLOR + " TEXT ");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (oldVersion < 26 && newVersion >= 26) {
                Log_OC.i(SQL, "Entering in the #26 Adding CRC32 column to filesystem table");
                db.beginTransaction();
                try {
                    db.execSQL(ALTER_TABLE + ProviderMeta.ProviderTableMeta.FILESYSTEM_TABLE_NAME +
                            ADD_COLUMN + ProviderMeta.ProviderTableMeta.FILESYSTEM_CRC32 + " TEXT ");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }


            if (!upgraded) {
                Log_OC.i(SQL, String.format(Locale.ENGLISH, UPGRADE_VERSION_MSG, oldVersion, newVersion));
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 25 && newVersion == 24) {
                // nothing needs to be done as the upgrade was adding columns only if they did not exist
                Log_OC.i(TAG, "Downgrading v" + oldVersion + " to " + newVersion);
            }
        }
    }

    private boolean checkIfColumnExists(SQLiteDatabase database, String table, String column) {
        Cursor cursor = database.rawQuery("SELECT * FROM " + table + " LIMIT 0", null);
        boolean exists = cursor.getColumnIndex(column) != -1;

        cursor.close();

        return exists;
    }

    private void createFilesTable(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.FILE_NAME + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_PATH + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_PARENT + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_CREATION + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_MODIFIED + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_CONTENT_TYPE + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_CONTENT_LENGTH + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_KEEP_IN_SYNC + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_ETAG + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK + INTEGER
                + ProviderMeta.ProviderTableMeta.FILE_PUBLIC_LINK + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_PERMISSIONS + " TEXT null,"
                + ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID + " TEXT null,"
                + ProviderMeta.ProviderTableMeta.FILE_UPDATE_THUMBNAIL + INTEGER //boolean
                + ProviderMeta.ProviderTableMeta.FILE_IS_DOWNLOADING + INTEGER //boolean
                + ProviderMeta.ProviderTableMeta.FILE_FAVORITE + INTEGER // boolean
                + ProviderMeta.ProviderTableMeta.FILE_ETAG_IN_CONFLICT + TEXT
                + ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE + " INTEGER);"
        );
    }

    private void createOCSharesTable(SQLiteDatabase db) {
        // Create OCShares table
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_FILE_SOURCE + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_ITEM_SOURCE + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_TYPE + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH + TEXT
                + ProviderMeta.ProviderTableMeta.OCSHARES_PATH + TEXT
                + ProviderMeta.ProviderTableMeta.OCSHARES_PERMISSIONS + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARED_DATE + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_EXPIRATION_DATE + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_TOKEN + TEXT
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH_DISPLAY_NAME + TEXT
                + ProviderMeta.ProviderTableMeta.OCSHARES_IS_DIRECTORY + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.OCSHARES_USER_ID + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_ID_REMOTE_SHARED + INTEGER
                + ProviderMeta.ProviderTableMeta.OCSHARES_ACCOUNT_OWNER + " TEXT );");
    }

    private void createCapabilitiesTable(SQLiteDatabase db) {
        // Create capabilities table
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MAYOR + INTEGER
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MINOR + INTEGER
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MICRO + INTEGER
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_STRING + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_EDITION + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_CORE_POLLINTERVAL + INTEGER
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_API_ENABLED + INTEGER // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_ENABLED + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED + INTEGER    // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENABLED + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS + INTEGER
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENFORCED + INTEGER // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SEND_MAIL + INTEGER    // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_UPLOAD + INTEGER       // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_USER_SEND_MAIL + INTEGER      // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_RESHARING + INTEGER           // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_OUTGOING + INTEGER     // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_INCOMING + INTEGER     // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_BIGFILECHUNKING + INTEGER   // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_UNDELETE + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_VERSIONING + INTEGER   // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_DROP + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_EXTERNAL_LINKS + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_NAME + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_COLOR + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_TEXT_COLOR + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_ELEMENT_COLOR + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_SLOGAN + TEXT
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SERVER_BACKGROUND_URL + " TEXT );");
    }

    private void createUploadsTable(SQLiteDatabase db) {
        // Create uploads table
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_PATH + TEXT
                + ProviderMeta.ProviderTableMeta.UPLOADS_REMOTE_PATH + TEXT
                + ProviderMeta.ProviderTableMeta.UPLOADS_ACCOUNT_NAME + TEXT
                + ProviderMeta.ProviderTableMeta.UPLOADS_FILE_SIZE + " LONG, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_STATUS + INTEGER               // UploadStatus
                + ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_BEHAVIOUR + INTEGER      // Upload LocalBehaviour
                + ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_TIME + INTEGER
                + ProviderMeta.ProviderTableMeta.UPLOADS_FORCE_OVERWRITE + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.UPLOADS_IS_CREATE_REMOTE_FOLDER + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_END_TIMESTAMP + INTEGER
                + ProviderMeta.ProviderTableMeta.UPLOADS_LAST_RESULT + INTEGER     // Upload LastResult
                + ProviderMeta.ProviderTableMeta.UPLOADS_IS_WHILE_CHARGING_ONLY + INTEGER  // boolean
                + ProviderMeta.ProviderTableMeta.UPLOADS_IS_WIFI_ONLY + INTEGER // boolean
                + ProviderMeta.ProviderTableMeta.UPLOADS_CREATED_BY + " INTEGER );"    // Upload createdBy
        );


        /* before:
        // PRIMARY KEY should always imply NOT NULL. Unfortunately, due to a
        // bug in some early versions, this is not the case in SQLite.
        //db.execSQL("CREATE TABLE " + TABLE_UPLOAD + " (" + " path TEXT PRIMARY KEY NOT NULL UNIQUE,"
        //        + " uploadStatus INTEGER NOT NULL, uploadObject TEXT NOT NULL);");
        // uploadStatus is used to easy filtering, it has precedence over
        // uploadObject.getUploadStatus()
        */
    }

    private void createSyncedFoldersTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.SYNCED_FOLDERS_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "                          // id
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_LOCAL_PATH + " TEXT, "           // local path
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_REMOTE_PATH + " TEXT, "           // remote path
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_WIFI_ONLY + " INTEGER, "          // wifi_only
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_CHARGING_ONLY + " INTEGER, "      // charging only
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_ENABLED + " INTEGER, "            // enabled
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_SUBFOLDER_BY_DATE + " INTEGER, "  // subfolder by date
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_ACCOUNT + "  TEXT, "              // account
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_UPLOAD_ACTION + " INTEGER, "     // upload action
                + ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_TYPE + " INTEGER );"               // type
        );
    }

    private void createExternalLinksTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "          // id
                + ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_ICON_URL + " TEXT, "     // icon url
                + ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_LANGUAGE + " TEXT, "     // language
                + ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_TYPE + " INTEGER, "      // type
                + ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_NAME + " TEXT, "         // name
                + ProviderMeta.ProviderTableMeta.EXTERNAL_LINKS_URL + " TEXT );"          // url
        );
    }

    private void createArbitraryData(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "      // id
                + ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_CLOUD_ID + " TEXT, " // cloud id (account name + FQDN)
                + ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_KEY + " TEXT, "      // key
                + ProviderMeta.ProviderTableMeta.ARBITRARY_DATA_VALUE + " TEXT );"    // value
        );
    }

    private void createVirtualTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.VIRTUAL_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "          // id
                + ProviderMeta.ProviderTableMeta.VIRTUAL_TYPE + " TEXT, "                // type
                + ProviderMeta.ProviderTableMeta.VIRTUAL_OCFILE_ID + " INTEGER )"        // file id
        );
    }

    private void createFileSystemTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ProviderMeta.ProviderTableMeta.FILESYSTEM_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "      // id
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_FILE_LOCAL_PATH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_FILE_IS_FOLDER + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_FILE_FOUND_RECENTLY + " LONG, "
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_FILE_SENT_FOR_UPLOAD + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_SYNCED_FOLDER_ID + " STRING, "
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_CRC32 + " STRING, "
                + ProviderMeta.ProviderTableMeta.FILESYSTEM_FILE_MODIFIED + " LONG );"
        );
    }


    /**
     * Version 10 of database does not modify its scheme. It coincides with the upgrade of the ownCloud account names
     * structure to include in it the path to the server instance. Updating the account names and path to local files
     * in the files table is a must to keep the existing account working and the database clean.
     *
     * @param db Database where table of files is included.
     */
    private void updateAccountName(SQLiteDatabase db) {
        Log_OC.d(SQL, "THREAD:  " + Thread.currentThread().getName());
        AccountManager ama = AccountManager.get(getContext());
        try {
            // get accounts from AccountManager ;  we can't be sure if accounts in it are updated or not although
            // we know the update was previously done in {link @FileActivity#onCreate} because the changes through
            // AccountManager are not synchronous
            Account[] accounts = AccountManager.get(getContext()).getAccountsByType(
                    MainApp.getAccountType());
            String serverUrl;
            String username;
            String oldAccountName;
            String newAccountName;

            for (Account account : accounts) {
                // build both old and new account name
                serverUrl = ama.getUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL);
                username = AccountUtils.getUsernameForAccount(account);
                oldAccountName = AccountUtils.buildAccountNameOld(Uri.parse(serverUrl), username);
                newAccountName = AccountUtils.buildAccountName(Uri.parse(serverUrl), username);

                // update values in database
                db.beginTransaction();
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER, newAccountName);
                    int num = db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                            cv,
                            ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?",
                            new String[]{oldAccountName});

                    Log_OC.d(SQL, "Updated account in database: old name == " + oldAccountName +
                            ", new name == " + newAccountName + " (" + num + " rows updated )");

                    // update path for downloaded files
                    updateDownloadedFiles(db, newAccountName, oldAccountName);

                    db.setTransactionSuccessful();

                } catch (SQLException e) {
                    Log_OC.e(TAG, "SQL Exception upgrading account names or paths in database", e);
                } finally {
                    db.endTransaction();
                }
            }
        } catch (Exception e) {
            Log_OC.e(TAG, "Exception upgrading account names or paths in database", e);
        }
    }


    /**
     * Rename the local ownCloud folder of one account to match the a rename of the account itself. Updates the
     * table of files in database so that the paths to the local files keep being the same.
     *
     * @param db             Database where table of files is included.
     * @param newAccountName New name for the target OC account.
     * @param oldAccountName Old name of the target OC account.
     */
    private void updateDownloadedFiles(SQLiteDatabase db, String newAccountName,
                                       String oldAccountName) {

        String whereClause = ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=? AND " +
                ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL";

        Cursor c = db.query(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                null,
                whereClause,
                new String[]{newAccountName},
                null, null, null);

        try {
            if (c.moveToFirst()) {
                // create storage path
                String oldAccountPath = FileStorageUtils.getSavePath(oldAccountName);
                String newAccountPath = FileStorageUtils.getSavePath(newAccountName);

                // move files
                File oldAccountFolder = new File(oldAccountPath);
                File newAccountFolder = new File(newAccountPath);
                oldAccountFolder.renameTo(newAccountFolder);

                // update database
                do {
                    // Update database
                    String oldPath = c.getString(
                            c.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH));
                    OCFile file = new OCFile(
                            c.getString(c.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_PATH)));
                    String newPath = FileStorageUtils.getDefaultSavePathFor(newAccountName, file);

                    ContentValues cv = new ContentValues();
                    cv.put(ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH, newPath);
                    db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                            cv,
                            ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + "=?",
                            new String[]{oldPath});

                    Log_OC.v(SQL, "Updated path of downloaded file: old file name == " + oldPath +
                            ", new file name == " + newPath);

                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
    }

    private boolean isCallerNotAllowed() {
        String callingPackage;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            callingPackage = getCallingPackage();
        } else {
            callingPackage = mContext.getPackageManager().getNameForUid(Binder.getCallingUid());
        }

        return callingPackage == null || !callingPackage.contains(mContext.getPackageName());
    }
}
