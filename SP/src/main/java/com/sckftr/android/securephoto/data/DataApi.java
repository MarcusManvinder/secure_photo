package com.sckftr.android.securephoto.data;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;

import com.sckftr.android.app.ServiceConst;
import com.sckftr.android.securephoto.AppConst;
import com.sckftr.android.securephoto.Application;
import com.sckftr.android.securephoto.R;
import com.sckftr.android.securephoto.contract.Contracts;
import com.sckftr.android.securephoto.db.Cryptonite;
import com.sckftr.android.securephoto.db.DbModel;
import com.sckftr.android.securephoto.db.Image;
import com.sckftr.android.securephoto.helper.UserHelper;
import com.sckftr.android.securephoto.processor.Cryptograph;
import com.sckftr.android.utils.ContractUtils;
import com.sckftr.android.utils.CursorUtils;
import com.sckftr.android.utils.Procedure;
import com.sckftr.android.utils.Storage;
import com.sckftr.android.utils.Strings;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DataApi implements AppConst {

    private static final String TAG = DataApi.class.getSimpleName();

    private static DataApi instance;

    private enum CommandName {
        UNLOCK, LOCK, DELETE, RELOCK
    }

    public static DataApi instance() {

        if (instance == null) instance = new DataApi();

        return instance;
    }

    private DataApi() {
    }

    private void unlockFiles(Context context, List<Cryptonite> files) {

        List<Image> toDbDelete = new ArrayList<Image>(files.size());

        try {

            for (Cryptonite file : files) {

                File securedFile = new File(file.getFileUri().toString());

                byte[] buffer = FileUtils.readFileToByteArray(securedFile);

                FileUtils.forceDelete(securedFile);

                File publicFile = Storage.Images.getPublicFile(file.getFileUri());

                FileUtils.writeByteArrayToFile(publicFile, Cryptograph.decrypt(buffer, file.getKey()));

                Storage.scanFile(context, Uri.fromFile(publicFile));

                toDbDelete.add((Image) file);
            }
        } catch (IOException e) {

            Log.e(TAG, "unlockFile: ", e);

        }

        API.db().delete((ArrayList<? extends DbModel>) toDbDelete);
    }

    private void lockFiles(Context context, List<Cryptonite> files) {

        List<Image> toDbInsert = new ArrayList<Image>(files.size());

        for (Cryptonite file : files) {

            Uri uri = file.getFileUri();

            String key = file.getKey();

            if (file instanceof Image) {

                try {

                    ((Image) file).appendImageMeta();

                } catch (IOException e) {

                    Log.e(TAG, "lockFiles: ", e);

                }

                if (Cryptograph.encrypt(context, uri, key)) {

                    Storage.deleteFileIfPublic(uri);

                    toDbInsert.add((Image) file);

                }
            }
        }

        API.db().insert((ArrayList<? extends DbModel>) toDbInsert);
    }

    private void relockFiles(Context context) {

        if (UserHelper.isPhotosRestoring()) return;

        String hash = UserHelper.getOldUserHash();

        Cursor c = API.db().query(ContractUtils.getUri(Contracts.ImageContract.class), null);

        int max = c == null ? -1 : c.getCount();

        if (max <= 0 || Strings.isEmpty(hash)) return;

        NotificationManager manager = null;
        NotificationCompat.Builder builder = null;

        try {

            UserHelper.setPhotosRestoring(true);

            manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            builder = new NotificationCompat.Builder(context)
                    .setContentTitle("Restoring " + max + " photos")
                    .setContentText(context.getString(R.string.restoring_in_progress))
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setProgress(max, 0, false);

            manager.notify(1, builder.build());

            ArrayList<Cryptonite> items = new ArrayList<Cryptonite>(max);

            for (int i = 0; i < max; i++) if (c.moveToPosition(i)) items.add(new Image(c));

            CursorUtils.close(c);

            Cryptonite file;

            for (int i = 0; i < items.size(); i++) {

                builder.setProgress(max, i, false);
                manager.notify(1, builder.build());

                file = items.get(i);

                File f = new File(file.getFileUri().toString());

                byte[] buffer = Cryptograph.decrypt(FileUtils.readFileToByteArray(f), file.getKey(), hash);

                Cryptograph.encrypt(context, file.getFileUri(), buffer, file.getKey());
            }

        } catch (IOException e) {
            Log.e(TAG, "relockFiles: ", e);
        } finally {

            UserHelper.setPhotosRestoring(false);

            context.getContentResolver().notifyChange(ContractUtils.getUri(Contracts.ImageContract.class), null);

            if (builder != null && manager != null) {
                builder.setContentText(context.getString(R.string.restoring_completed))
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setProgress(0, 0, false);

                manager.notify(1, builder.build());
            }
        }
    }

    public void deleteFiles(List<? extends Cryptonite> files) {

        if (files == null || files.isEmpty()) return;

        ArrayList<DbModel> dbList = new ArrayList<DbModel>(files.size());

        for (Cryptonite image : files) {

            Storage.deleteFileSync(image.getFileUri());

            if (image instanceof DbModel) dbList.add((DbModel) image);

        }

        API.db().delete(dbList);
    }

    public CursorLoader getEncryptedImagesCursorLoader(Context context) {
        return new CursorLoader(context,
                ContractUtils.getUri(Contracts.ImageContract.class),
                null,
                null,
                null,
                Contracts.ImageContract._ID + " DESC");
    }

    public CursorLoader getGalleryImagesCursorLoader(Context context) {
        return new CursorLoader(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DATA, BaseColumns._ID},
                null,
                null,
                MediaStore.Images.Media._ID + " DESC");
    }

    public static class DataAsyncEnforcerService extends IntentService implements ServiceConst {

        public DataAsyncEnforcerService() {
            super("DataAsyncEnforcerService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {

            final CommandName commandName = (CommandName) intent.getSerializableExtra(PARAM_IN_COMMAND_NAME);
            final ResultReceiver receiver = intent.getParcelableExtra(PARAM_IN_CALLBACK);
            final ArrayList<Cryptonite> files = intent.getParcelableArrayListExtra(PARAM_IN_DATA);

            Bundle resultingBundle = null;

            switch (commandName) {
                case UNLOCK: {

                    API.data().unlockFiles(getBaseContext(), files);

                    break;
                }
                case LOCK: {

                    API.data().lockFiles(getBaseContext(), files);

                    resultingBundle = createSingleEntryBundle("ok");

                    break;
                }
                case DELETE:

                    API.data().deleteFiles(files);

                case RELOCK:

                    API.data().relockFiles(getBaseContext());

                    resultingBundle = createSingleEntryBundle("ok");
                default:
                    break;
            }

            //send results if we have receiver
            if (receiver != null) receiver.send(0, resultingBundle);
        }

        private Bundle createSingleEntryBundle(Serializable value) {

            Bundle resultingBundle = new Bundle(1);

            resultingBundle.putSerializable(PARAM_OUT_MSG, value);

            return resultingBundle;
        }

        private Bundle createSingleEntryBundle(Parcelable value) {

            Bundle resultingBundle = new Bundle(1);

            resultingBundle.putParcelable(PARAM_OUT_MSG, value);

            return resultingBundle;
        }

    }

    public void uncryptonize(ArrayList<? extends Cryptonite> cryptonite, Procedure<? extends Object> callback) {

        final Intent intent = createBaseIntentForAsyncEnforcer(CommandName.UNLOCK, createResultReceiver(callback));

        intent.putParcelableArrayListExtra(DataAsyncEnforcerService.PARAM_IN_DATA, cryptonite);

        dispatchServiceCall(intent);
    }

    public void cryptonize(ArrayList<? extends Cryptonite> cryptonite, Procedure<? extends Object> callback) {

        final Intent intent = createBaseIntentForAsyncEnforcer(CommandName.LOCK, createResultReceiver(callback));

        intent.putParcelableArrayListExtra(DataAsyncEnforcerService.PARAM_IN_DATA, cryptonite);

        dispatchServiceCall(intent);
    }

    public void recryptonize(Procedure<? extends Object> callback) {

        final Intent intent = createBaseIntentForAsyncEnforcer(CommandName.RELOCK, createResultReceiver(callback));

        dispatchServiceCall(intent);
    }

    public void delete(ArrayList<? extends Cryptonite> cryptonite, Procedure<Integer> callback) {

        final Intent intent = createBaseIntentForAsyncEnforcer(CommandName.DELETE, createResultReceiver(callback));

        intent.putParcelableArrayListExtra(DataAsyncEnforcerService.PARAM_IN_DATA, cryptonite);

        dispatchServiceCall(intent);
    }

    private <T extends Serializable> Intent createBaseIntentForAsyncEnforcer(final CommandName commandName, final ResultReceiver resultReceiver) {

        Intent msgIntent = new Intent(Application.get(), DataAsyncEnforcerService.class);

        msgIntent.putExtra(DataAsyncEnforcerService.PARAM_IN_COMMAND_NAME, commandName);

        if (resultReceiver != null) {
            msgIntent.putExtra(DataAsyncEnforcerService.PARAM_IN_CALLBACK, resultReceiver);
        }

        return msgIntent;
    }

    private void dispatchServiceCall(final Intent intent) {
        Application.get().startService(intent);
    }


    private <T> ResultReceiver createResultReceiver(final Procedure<T> callback) {
        if (callback == null) return null;

        return new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle msg) {

                Object result;
                if (isOutputParcelable(callback)) {

                    result = msg.getParcelable(DataAsyncEnforcerService.PARAM_OUT_MSG);

                } else {

                    result = msg.getSerializable(DataAsyncEnforcerService.PARAM_OUT_MSG);

                }

                callback.apply((T) result);
            }
        };
    }

    private <T> boolean isOutputParcelable(final Procedure<T> callback) {

        Type[] types = callback.getClass().getGenericInterfaces();

        if (types.length == 0 || !(types[0] instanceof ParameterizedType)) {

            return false;

        }

        Type[] argumentsTypes = ((ParameterizedType) types[0]).getActualTypeArguments();

        return !(argumentsTypes.length == 0 || !(argumentsTypes[0] instanceof Class)) && Parcelable.class.isAssignableFrom((Class<?>) argumentsTypes[0]);
    }
}
