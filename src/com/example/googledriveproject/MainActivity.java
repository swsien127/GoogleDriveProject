package com.example.googledriveproject;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;


public class MainActivity extends Activity {
    static final int 				REQUEST_ACCOUNT_PICKER = 1;
    static final int 				REQUEST_AUTHORIZATION = 2;
    static final int				REQUEST_DOWNLOAD_FILE = 3;
    static final int 				RESULT_STORE_FILE = 4;
    private static Uri 				mFileUri;
    private static Drive 			mService;
    private GoogleAccountCredential mCredential;
    private Context 				mContext;
    private List<File> 				mResultList;
    private ListView 				mListView;
    private String[] 				mFileArray;
    private String 					mDLVal;
    private ArrayAdapter 			mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    
        // setup for credentials for connecting to the Google Drive account
        mCredential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
        
        // start activity that prompts the user for their google drive account
        startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
        
        mContext = getApplicationContext();
    	
        setContentView(R.layout.activity_main);
        mListView = (ListView) findViewById(R.id.listView1);
    	
    	OnItemClickListener mMessageClickedHandler = new OnItemClickListener() {
    	    public void onItemClick(AdapterView parent, View v, int position, long id) {
    	    	downloadItemFromList(position);
    	    }
    	};
    
    	mListView.setOnItemClickListener(mMessageClickedHandler); 
    	
    	final Button button = (Button) findViewById(R.id.button1);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	final Intent galleryIntent = new Intent(Intent.ACTION_PICK);
                galleryIntent.setType("*/*");
                startActivityForResult(galleryIntent, RESULT_STORE_FILE);
            }
        });
        
        final Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	getDriveContents();
            }
        });
    }

    private void getDriveContents() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                mResultList = new ArrayList<File>();
                //com.google.api.services.drive.Drive.Files f1 = mService.files();
                Files f1 = mService.files();
                //com.google.api.services.drive.Drive.Files.List request = null;
                Files.List request = null;
		
                do {
                    try { 
                        request = f1.list();
				
                        // get only zip files from drive 
                        request.setQ("trashed=false and mimeType = 'application/zip'");
                        //com.google.api.services.drive.model.FileList fileList = request.execute();
                        FileList fileList = request.execute();
					
                        mResultList.addAll(fileList.getItems());
                        request.setPageToken(fileList.getNextPageToken());
                    } catch (UserRecoverableAuthIOException e) {
                        startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (request != null) {
                            request.setPageToken(null);
                        }
                    }
                } while (request.getPageToken() !=null && request.getPageToken().length() > 0);
			
                populateListView();
            }
        });
        t.start();
    }

    private void downloadItemFromList(int position) {
        mDLVal = (String) mListView.getItemAtPosition(position);
        showToast("You just pressed: " + mDLVal);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for(File tmp : mResultList) {
                    if (tmp.getTitle().equalsIgnoreCase(mDLVal)) {
                        if (tmp.getDownloadUrl() != null && tmp.getDownloadUrl().length() > 0) {
                            Log.i("GoogleDriveProject", "running downloadItemFromList");
                            try { // for httpresponse
                                com.google.api.client.http.HttpResponse resp = 
                                        mService.getRequestFactory()
                                        .buildGetRequest(new GenericUrl(tmp.getDownloadUrl()))
                                        .execute();
                                
                                // gets the zip file's contents
                                InputStream inputStream = resp.getContent();
                                try {
                                    Log.i("GoogleDriveProject", "beginning storage of file");
    
                                    ZipInputStream zis = new ZipInputStream(inputStream);
                                    // the laos directory
                                    java.io.File targetDir = new java.io.File(Environment.getExternalStorageDirectory(), 
                                            getString(R.string.local_storage_folder));
                                    System.out.println("the target directory is " + targetDir.getAbsolutePath());
                                    
                                    int count = 0;
                                    ZipEntry ze;
                                    while ((ze = zis.getNextEntry()) != null) {
                                        count++;
                                        Log.d("DEBUG", "Extracting: " + ze.getName() + "...");
                                        // Extracted file will be saved with same file name that's in the zip drive
                                        String fileName = ze.getName();
                                        System.out.println("file " + count + "'s name is " + fileName);
                                        java.io.File targetFile = new java.io.File(targetDir, fileName);
                                        System.out.println("file " + count + "'s path is " + targetFile.getAbsolutePath());
                                        
                                        showToast("Downloading: " + targetFile.getName() + " to " + targetFile.getPath());
                                        System.out.println("Downloading: " + targetFile.getName() + " to " + targetFile.getPath());
                                        new java.io.File(targetFile.getParent()).mkdirs();
                                        if (ze.isDirectory()) {
                                            System.out.println("entry is directory");
                                            targetFile.mkdirs();
                                        } else {
                                            System.out.println("entry is file");
                                            // reads/writes each file 
                                            FileOutputStream fos = new FileOutputStream(targetFile);
                                            try {
                                                copyContents(zis, fos);
                                                System.out.println("Just wrote file: " + targetFile.getName());
                                            } finally {
                                                fos.close();
                                            }
                                        }  // end if
                                        
                                        // finish the current zip entry
                                        zis.closeEntry();
                                    }  // end while
                                    System.out.println("zipentry count = " + count);
                                    zis.close();
                                } finally {
                                    inputStream.close();
                                }
                            } catch (IOException e) {
                                System.err.println("the HttpResponse failed");
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
        t.start();
    }
    
    // copy the contents of a file to the given output
    private static void copyContents(ZipInputStream zis, OutputStream fos) {
        byte[] buffer = new byte[4096];
        // reads/writes each file 
        int len;
        try {
            while((len = zis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            System.err.println("write failed");
            e.printStackTrace();
        }
    }

    private void populateListView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFileArray = new String[mResultList.size()];
                int i = 0;
                for(File tmp : mResultList) {
                    mFileArray[i] = tmp.getTitle();
                    i++;
                }
                mAdapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_list_item_1, mFileArray);
			    mListView.setAdapter(mAdapter);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
		    case REQUEST_ACCOUNT_PICKER:
		        if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
		            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
		            if (accountName != null) {
		                mCredential.setSelectedAccountName(accountName);
		                mService = getDriveService(mCredential);
		            }
		        }
		        break;
		    case REQUEST_AUTHORIZATION:
		        if (resultCode == Activity.RESULT_OK) {
		            //account already picked
		        } else {
		            startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
		        }
		        break;
		    case RESULT_STORE_FILE:
		        mFileUri = data.getData();
		        // Save the file to Google Drive
		        saveFileToDrive();
		        break;
        }
    }

    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
            .build();
    }


    private void saveFileToDrive()  {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Create URI from real path
                    String path;
                    path = getPathFromUri(mFileUri);
                    mFileUri = Uri.fromFile(new java.io.File(path));
			
                    ContentResolver cR = MainActivity.this.getContentResolver();
			
                    // File's binary content
                    java.io.File fileContent = new java.io.File(mFileUri.getPath());
                    FileContent mediaContent = new FileContent(cR.getType(mFileUri), fileContent);

                    showToast("Selected " + mFileUri.getPath() + "to upload");

                    // File's meta data. 
                    File body = new File();
                    body.setTitle(fileContent.getName());
                    body.setMimeType(cR.getType(mFileUri));

                    com.google.api.services.drive.Drive.Files f1 = mService.files();
                    com.google.api.services.drive.Drive.Files.Insert i1 = f1.insert(body, mediaContent);
                    File file = i1.execute();
			    
                    if (file != null) {
                        showToast("Uploaded: " + file.getTitle());
                    }
                } catch (UserRecoverableAuthIOException e) {
                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (IOException e) {
                    e.printStackTrace();
                    showToast("Transfer ERROR: " + e.toString());
            }
    		}
    	});
        t.start();
    }

    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
            }
        });
    }
	
    public String getPathFromUri(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}