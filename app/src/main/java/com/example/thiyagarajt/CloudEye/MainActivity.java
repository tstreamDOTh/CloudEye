package com.example.thiyagarajt.CloudEye;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.Tag;
import com.clarifai.api.exception.ClarifaiException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity {
    int TAKE_PHOTO_CODE = 0;
    public int f = 0 ;
    public static int count = 0;
    public Uri targetfile;
    public StringBuilder b ;
    private TextToSpeech t1;
    private ArrayList<String> tags ;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int CODE_PICK = 1;
    private final ClarifaiClient client = new ClarifaiClient(Credentials.CLIENT_ID,
            Credentials.CLIENT_SECRET);
    private Button selectButton;
    private ImageView imageView;
    private TextView textView;
    String speech = "";
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);
        imageView = (ImageView) findViewById(R.id.image_view);
        textView = (TextView) findViewById(R.id.text_view);
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.UK);
                }
            }
        });

        // Here, we are making a folder named picFolder to store
        // pics taken by the camera using this application.
        final String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/eee/";
        File newdir = new File(dir);
        newdir.mkdirs();

        ImageButton capture = (ImageButton) findViewById(R.id.btnCapture);
        capture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                // Here, the counter will be incremented each time, and the
                // picture taken by camera will be stored as 1.jpg,2.jpg
                // and likewise.
                count++;
                String file = dir+count+new Date().getTime()+".jpg";
                File newfile = new File(file);
                try {
                    newfile.createNewFile();
                }
                catch (IOException e)
                {
                }

                Uri outputFileUri = Uri.fromFile(newfile);
                targetfile = outputFileUri ;

                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

               Bitmap bitmap = loadBitmapFromUri(targetfile);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    textView.setText("Recognizing...");

                    // Run recognition on a background thread since it makes a network call.
                    new AsyncTask<Bitmap, Void, RecognitionResult>() {
                        @Override protected RecognitionResult doInBackground(Bitmap... bitmaps) {
                            return recognizeBitmap(bitmaps[0]);
                        }
                        @Override protected void onPostExecute(RecognitionResult result) {
                            updateUIForResult(result);

                        }




                    }.execute(bitmap);
                } else {
                    textView.setText("Unable to load selected image.");
                }

                startActivityForResult(cameraIntent, TAKE_PHOTO_CODE);

            }
        });

    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

            // The user picked an image. Send it to Clarifai for recognition.
            Bitmap bitmap = loadBitmapFromUri(targetfile);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                textView.setText("Recognizing...");


                // Run recognition on a background thread since it makes a network call.
                new AsyncTask<Bitmap, Void, RecognitionResult>() {
                    @Override protected RecognitionResult doInBackground(Bitmap... bitmaps) {
                        return recognizeBitmap(bitmaps[0]);
                    }
                    @Override protected void onPostExecute(RecognitionResult result) {
                        updateUIForResult(result);
                    }
                }.execute(bitmap);
            } else {
                textView.setText("Unable to load selected image.");
            }

    }

    /** Loads a Bitmap from a content URI returned by the media picker. */
    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            // The image may be large. Load an image that is sized for display. This follows best
            // practices from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
            int sampleSize = 1;
            while (opts.outWidth / (2 * sampleSize) >= imageView.getWidth() &&
                    opts.outHeight / (2 * sampleSize) >= imageView.getHeight()) {
                sampleSize *= 2;
            }

            opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
        } catch (IOException e) {
            Log.e(TAG, "Error loading image: " + uri, e);
        }
        return null;
    }

    /** Sends the given bitmap to Clarifai for recognition and returns the result. */
    private RecognitionResult recognizeBitmap(Bitmap bitmap) {
        try {
            // Scale down the image. This step is optional. However, sending large images over the
            // network is slow and  does not significantly improve recognition performance.
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320,
                    320 * bitmap.getHeight() / bitmap.getWidth(), true);

            // Compress the image as a JPEG.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
            byte[] jpeg = out.toByteArray();

            // Send the JPEG to Clarifai and return the result.
            return client.recognize(new RecognitionRequest(jpeg)).get(0);
        } catch (ClarifaiException e) {
            Log.e(TAG, "Clarifai error", e);
            return null;
        }
    }

    /** Updates the UI by displaying tags for the given result. */
    private void updateUIForResult(RecognitionResult result) {
        if (result != null) {
            if (result.getStatusCode() == RecognitionResult.StatusCode.OK) {
                // Display the list of tags in the UI.
                //b = new StringBuilder();
                tags = new ArrayList<String>(100);
                Log.d("Main Activity Tags", String.valueOf(result.getJsonResponse()));
                for (Tag tag : result.getTags()) {
                   //b.append(b.length() > 0 ? ", " : "").append(tag.getName());
                   tags.add(tag.getName());
               }
                textView.setText(tags.toString());
                speak();
            } else {
                Log.e(TAG, "Clarifai: " + result.getStatusMessage());
                textView.setText("Sorry, there was an error recognizing your image.");
            }
        } else {
            textView.setText("Sorry, there was an error recognizing your image.");
        }

        //text to speech here on 'b'






    }

    public void  speak()
    {
        boolean x ;

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE) ;
        int amStreamMusicMaxVol = am.getStreamMaxVolume(am.STREAM_MUSIC);
        am.setStreamVolume(am.STREAM_MUSIC, amStreamMusicMaxVol, 0);



        if(tags.contains("fire") || tags.contains("flame") )
        {
            t1.setSpeechRate(2);
            t1.speak("Dont Panic, Cloud Eye detected instance of fire, Retake a photo and verify, call your known one who is nearby to assist you", TextToSpeech.QUEUE_FLUSH, null);

        }
        else if (tags.contains("car") || tags.contains("bike") || tags.contains("bus")) {
                t1.setSpeechRate(2);
                t1.speak("You are a location with vehicles around. Be careful while commuting. It is recommended that you ask somebody to aid you.", TextToSpeech.QUEUE_FLUSH, null);

            }
        else if (tags.contains("blur"))
        {
            t1.setSpeechRate(2);
            t1.speak("Blurry image. Take again please.", TextToSpeech.QUEUE_FLUSH, null);

        }
        else {
            t1.setSpeechRate(2);
            String prefix = "The associations recognized in the image are ";
            t1.speak(prefix+tags.toString(),TextToSpeech.QUEUE_FLUSH,null);
            //Toast.makeText(getApplicationContext(),"SGsg", Toast.LENGTH_SHORT).show();
               // for (int i = 0; i < tags.size(); i++) {
                 //  t1.speak(tags.get(i),TextToSpeech.QUEUE_FLUSH,null);
                //}
           // t1.speak(tags.toString(),TextToSpeech.QUEUE_FLUSH,null);
                //speech = prefix.concat(" ").concat(speech);
                //Toast.makeText(getApplicationContext(), speech, Toast.LENGTH_SHORT).show();
            /*t1.setSpeechRate(2);
            t1.speak(speech , TextToSpeech.QUEUE_FLUSH, null);*/


        }
    }
}
