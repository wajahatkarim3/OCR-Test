package com.wajahatkarim3.ocrtest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.nguyenhoanglam.imagepicker.model.Config;
import com.nguyenhoanglam.imagepicker.model.Image;
import com.nguyenhoanglam.imagepicker.ui.imagepicker.ImagePicker;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import pyxis.uzuki.live.mediaresizer.MediaResizer;
import pyxis.uzuki.live.mediaresizer.data.ImageResizeOption;
import pyxis.uzuki.live.mediaresizer.data.ResizeOption;
import pyxis.uzuki.live.mediaresizer.model.ImageMode;
import pyxis.uzuki.live.mediaresizer.model.MediaType;
import pyxis.uzuki.live.richutilskt.impl.F2;

import static com.googlecode.tesseract.android.TessBaseAPI.OEM_DEFAULT;
import static com.googlecode.tesseract.android.TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED;
import static com.googlecode.tesseract.android.TessBaseAPI.OEM_TESSERACT_ONLY;

public class MainActivity extends AppCompatActivity {

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    Bitmap image;
    String targetPath;
    TessBaseAPI mTess;
    String datapath = "";

    ImageView imageView;
    TextView OCRTextView;
    ProgressDialog progressDialog;
    AsyncTask<Bitmap, Void, String> theTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView) findViewById(R.id.imageView);
        OCRTextView = (TextView) findViewById(R.id.OCRTextView);
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Processing image...");

        // init Image
        image = BitmapFactory.decodeResource(getResources(), R.drawable.test_image);

        datapath = getFilesDir()+ "/tesseract/";

        //make sure training data has been copied
        checkFile(new File(datapath + "tessdata/"));

        //init Tesseract API
        String language = "eng";

        mTess = new TessBaseAPI();
        mTess.init(datapath, language, OEM_DEFAULT);
    }


    public void copyFiles()
    {
        try {
            //location we want the file to be at
            String filepath = datapath + "/tessdata/eng.traineddata";

            //get access to AssetManager
            AssetManager assetManager = getAssets();

            //open byte streams for reading/writing
            InputStream instream = assetManager.open("tessdata/eng.traineddata");
            OutputStream outstream = new FileOutputStream(filepath);

            //copy the file to the location specified by filepath
            byte[] buffer = new byte[1024];
            int read;
            while ((read = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, read);
            }
            outstream.flush();
            outstream.close();
            instream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkFile(File dir) {
        //directory does not exist, but we can successfully create it
        if (!dir.exists()&& dir.mkdirs()){
            copyFiles();
        }
        //The directory exists, but there is no data file in it
        if(dir.exists()) {
            String datafilepath = datapath+ "/tessdata/eng.traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles();
            }
        }
    }

    public void processImage(View view){
        String OCRresult = null;
        progressDialog.show();
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if (theTask != null)
                    theTask.cancel(true);
            }
        });


        // Compress before processing it
        ImageResizeOption resizeOption = new ImageResizeOption.Builder()
                .setImageProcessMode(ImageMode.ResizeAndCompress)
                .setCompressFormat(Bitmap.CompressFormat.JPEG)
                .setCompressQuality(40)
                .build();

        File dir = new File(datapath + "/images/");
        dir.mkdirs();
        File file = new File(datapath + "/images/", "resize-"+System.currentTimeMillis()+".jpg");
        if (file.exists() == false)
        {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ResizeOption option = new ResizeOption.Builder()
                .setMediaType(MediaType.IMAGE)
                .setImageResizeOption(resizeOption)
                .setTargetPath(targetPath)
                .setOutputPath(file.getAbsolutePath())
                .setCallback(new F2<Integer, String>() {
                    @Override
                    public void invoke(Integer code, String output) {
                        Log.e("RESIZE", code + " -- " + output);


                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 3;
                        Bitmap compressImage = BitmapFactory.decodeFile(output, options);

                        // Convert the image to black white before OCR
                        Bitmap black = convertToBlackWhite(compressImage);

                        imageView.setImageBitmap(black);

                        theTask = new ImageProcessTask().execute(black);

                    }
                })
                .build();

        MediaResizer.process(option);



        //
    }

    public Bitmap convertToBlackWhite(Bitmap compressImage)
    {
        Mat imageMat = new Mat();
        Utils.bitmapToMat(compressImage, imageMat);
        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(imageMat, imageMat, new Size(3, 3), 0);
        //Imgproc.adaptiveThreshold(imageMat, imageMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 4);
        Imgproc.medianBlur(imageMat, imageMat, 3);
        Imgproc.threshold(imageMat, imageMat, 0, 255, Imgproc.THRESH_OTSU);


        Bitmap newBitmap = compressImage;
        Utils.matToBitmap(imageMat, newBitmap);
        imageView.setImageBitmap(newBitmap);

        return newBitmap;


        /*


        */

    }


    public void pickImage(View view){

        /*
        ImagePicker.with(this)
                .setCameraOnly(true)
                .setMaxSize(1)
                .setMultipleMode(false)
                .start();
                */

        // start picker to get image for cropping and then use the image in cropping activity
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Config.RC_PICK_IMAGES && resultCode == RESULT_OK && data != null) {
            ArrayList<Image> images = data.getParcelableArrayListExtra(Config.EXTRA_IMAGES);
            // do your logic here...

            if (images.size() > 0)
            {
                String path = images.get(0).getPath();
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                image = BitmapFactory.decodeFile(path,bmOptions);
                //bitmap = Bitmap.createScaledBitmap(bitmap,parent.getWidth(),parent.getHeight(),true);
                imageView.setImageBitmap(image);
            }
        }
        else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri resultUri = result.getUri();

                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                image = BitmapFactory.decodeFile(resultUri.getPath(),bmOptions);
                targetPath = resultUri.getPath();
                imageView.setImageBitmap(image);

            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
            }
        }
          // THIS METHOD SHOULD BE HERE so that ImagePicker works with fragment
    }

    class ImageProcessTask extends AsyncTask<Bitmap, Void, String>
    {

        @Override
        protected String doInBackground(Bitmap... bitmaps) {

            mTess.setImage(bitmaps[0]);
            return mTess.getUTF8Text();
        }

        @Override
        protected void onPostExecute(final String text) {
            super.onPostExecute(text);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.dismiss();
                    OCRTextView.setText(text);
                }
            });
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
}
