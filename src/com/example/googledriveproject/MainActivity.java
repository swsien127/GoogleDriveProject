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
                        if (tmp.getDownloadUrl() != null && tmp.getDownloadUrl().length() >0) {
                            try {
                                Log.i("GoogleDriveProject", "running downloadItemFromList");
                                com.google.api.client.http.HttpResponse resp = 
                                        mService.getRequestFactory()
                                        .buildGetRequest(new GenericUrl(tmp.getDownloadUrl()))
                                        .execute();
                                // gets the zip file's contents
                                InputStream inputStream = resp.getContent();
						
                                // stores the files in the zip to the device's external storage
                                try {
                                    Log.i("GoogleDriveProject", "beginning storage of file");
                                    byte[] buffer = new byte[2048];
						    
                                    ZipInputStream zis = new ZipInputStream(inputStream);
						    
                                    //get the first entry in the zip file
                                    ZipEntry ze = zis.getNextEntry();
					 
                                    // to /LaosTrainingApp
                                    String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
                                    int count = 0;
                                    while (ze != null) {
                                        count++;
                                        Log.d("DEBUG", "Extracting: " + ze.getName() + "...");
                                        // Extracted file will be saved with same file name that's in the zip drive
                                        String fileName = ze.getName();
                                        String filePath = baseDir + "/" + getString(R.string.local_storage_folder) + "/" + fileName;
                                        java.io.File newFile = new java.io.File(filePath);
                                        showToast("Downloading: " + newFile.getName() + " to " + newFile.getPath());
                                        Log.e("downloadItemFromList", "Downloading: " + newFile.getName() + " to " + newFile.getPath());
                                
                                        //if (ze.isDirectory()) {
                                            new java.io.File(newFile.getParent()).mkdirs();
                                        //} else {
                                            // reads/writes each file 
                                            FileOutputStream fos = new FileOutputStream(newFile);
                                            int len;
                                            while((len = zis.read(buffer)) != -1) {
                                                fos.write(buffer, 0, len);
                                            }
                                            fos.close();
                                        //}
                                
                                        //zis.closeEntry();
                                        ze = zis.getNextEntry();
                                    }
                                    System.out.println("zipentry count = " + count);
                                    zis.close();
							    
                                } finally {
                                    inputStream.close();
                                }
							
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
        t.start();
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
